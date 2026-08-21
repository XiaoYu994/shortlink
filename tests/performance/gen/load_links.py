#!/usr/bin/env python3
"""Generate 16-shard short-link rows and JMeter CSVs.

Sharding matches ShardingSphere HASH_MOD:
  t_link      -> abs(gid.hashCode()) % 16
  t_link_goto -> abs(full_short_url.hashCode()) % 16
"""

from __future__ import annotations

import argparse
import csv
import random
import subprocess
import sys
from collections import Counter
from pathlib import Path

ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
SHARD_COUNT = 16
MARKER = "perf-gen-10k"
MYSQL_CONTAINER = "shortlink-mysql"
MYSQL_USER = "root"
MYSQL_PASSWORD = "root"
MYSQL_DB = "link"


def java_hash_code(value: str) -> int:
    h = 0
    for ch in value:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def hash_mod(value: str, shard_count: int = SHARD_COUNT) -> int:
    return abs(java_hash_code(value)) % shard_count


def mysql(sql: str) -> str:
    result = subprocess.run(
        [
            "docker",
            "exec",
            "-i",
            MYSQL_CONTAINER,
            "mysql",
            f"-u{MYSQL_USER}",
            f"-p{MYSQL_PASSWORD}",
            MYSQL_DB,
            "-N",
            "-e",
            sql,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    if result.stderr and "Using a password" not in result.stderr:
        print(result.stderr, file=sys.stderr)
    return result.stdout


def existing_short_uris() -> set[str]:
    uris: set[str] = set()
    for shard in range(SHARD_COUNT):
        out = mysql(f"SELECT short_uri FROM t_link_{shard} WHERE short_uri IS NOT NULL")
        uris.update(line.strip() for line in out.splitlines() if line.strip())
    return uris


def random_uri(rng: random.Random, length: int, taken: set[str]) -> str:
    while True:
        uri = "".join(rng.choice(ALPHABET) for _ in range(length))
        if uri not in taken:
            taken.add(uri)
            return uri


def build_rows(count: int, gid_count: int, domain: str, seed: int) -> list[dict[str, str | int]]:
    rng = random.Random(seed)
    taken = existing_short_uris()
    gids = [f"perf{i:04d}" for i in range(gid_count)]
    rows: list[dict[str, str | int]] = []
    for index in range(count):
        short_uri = random_uri(rng, 6, taken)
        gid = gids[index % gid_count]
        full_short_url = f"{domain}/{short_uri}"
        rows.append(
            {
                "short_uri": short_uri,
                "gid": gid,
                "full_short_url": full_short_url,
                "origin_url": f"https://example.com/p/{index}",
                "link_shard": hash_mod(gid),
                "goto_shard": hash_mod(full_short_url),
            }
        )
    return rows


def write_csvs(rows: list[dict[str, str | int]], out_dir: Path, miss_count: int, seed: int) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    mix_path = out_dir / "mix.csv"
    hot_path = out_dir / "hot.csv"
    miss_path = out_dir / "miss.csv"
    urls_path = out_dir / "full-urls.txt"

    def write_uri_csv(path: Path, uris: list[str]) -> None:
        with path.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle, lineterminator="\n")
            writer.writerow(["shortUri"])
            for uri in uris:
                writer.writerow([uri])

    write_uri_csv(mix_path, [str(row["short_uri"]) for row in rows])
    hot_n = min(2000, len(rows))
    write_uri_csv(hot_path, [str(row["short_uri"]) for row in rows[:hot_n]])

    taken = {str(row["short_uri"]) for row in rows} | existing_short_uris()
    rng = random.Random(seed + 1)
    write_uri_csv(miss_path, [random_uri(rng, 6, taken) for _ in range(miss_count)])

    urls_path.write_text("".join(f"{row['full_short_url']}\n" for row in rows), encoding="utf-8")
    print(f"wrote {mix_path} ({len(rows)}), {hot_path} ({hot_n}), {miss_path} ({miss_count})")


def insert_rows(rows: list[dict[str, str | int]], marker: str) -> None:
    grouped_link: dict[int, list[dict[str, str | int]]] = {i: [] for i in range(SHARD_COUNT)}
    grouped_goto: dict[int, list[dict[str, str | int]]] = {i: [] for i in range(SHARD_COUNT)}
    for row in rows:
        grouped_link[int(row["link_shard"])].append(row)
        grouped_goto[int(row["goto_shard"])].append(row)

    batch = 400
    for shard, shard_rows in grouped_link.items():
        for start in range(0, len(shard_rows), batch):
            chunk = shard_rows[start : start + batch]
            values = ",".join(
                "('{domain}','{uri}','{full}','{origin}','{gid}',0,1,0,'{marker}',"
                "NOW(),NOW(),0,NOW())".format(
                    domain=str(chunk_row["full_short_url"]).split("/", 1)[0],
                    uri=chunk_row["short_uri"],
                    full=chunk_row["full_short_url"],
                    origin=chunk_row["origin_url"],
                    gid=chunk_row["gid"],
                    marker=marker,
                )
                for chunk_row in chunk
            )
            mysql(
                "INSERT INTO t_link_{shard} "
                "(domain, short_uri, full_short_url, origin_url, gid, enable_status, "
                "created_type, valid_date_type, description, create_time, update_time, "
                "del_flag, last_access_time) VALUES {values}".format(shard=shard, values=values)
            )
        print(f"t_link_{shard}: +{len(shard_rows)}")

    for shard, shard_rows in grouped_goto.items():
        for start in range(0, len(shard_rows), batch):
            chunk = shard_rows[start : start + batch]
            values = ",".join(
                "('{gid}','{full}')".format(gid=chunk_row["gid"], full=chunk_row["full_short_url"])
                for chunk_row in chunk
            )
            mysql(
                "INSERT INTO t_link_goto_{shard} (gid, full_short_url) VALUES {values}".format(
                    shard=shard, values=values
                )
            )
        print(f"t_link_goto_{shard}: +{len(shard_rows)}")


def purge(marker: str) -> None:
    urls: list[str] = []
    for shard in range(SHARD_COUNT):
        out = mysql(f"SELECT full_short_url FROM t_link_{shard} WHERE description = '{marker}'")
        urls.extend(line.strip() for line in out.splitlines() if line.strip())
    for shard in range(SHARD_COUNT):
        mysql(f"DELETE FROM t_link_{shard} WHERE description = '{marker}'")
    for start in range(0, len(urls), 400):
        chunk = urls[start : start + 400]
        in_list = ",".join("'{0}'".format(url.replace("'", "''")) for url in chunk)
        for shard in range(SHARD_COUNT):
            mysql(f"DELETE FROM t_link_goto_{shard} WHERE full_short_url IN ({in_list})")
    print(f"purged marker={marker} links={len(urls)}")


def print_histogram(rows: list[dict[str, str | int]]) -> None:
    link_hist = Counter(int(row["link_shard"]) for row in rows)
    goto_hist = Counter(int(row["goto_shard"]) for row in rows)
    print("t_link shards:", dict(sorted(link_hist.items())))
    print("t_link_goto shards:", dict(sorted(goto_hist.items())))
    if link_hist:
        avg = sum(link_hist.values()) / len(link_hist)
        peak = max(link_hist.values())
        print(f"t_link peak/avg = {peak / avg:.2f}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=10000)
    parser.add_argument("--gids", type=int, default=512)
    parser.add_argument("--miss-count", type=int, default=1000)
    parser.add_argument("--domain", default="localhost")
    parser.add_argument("--seed", type=int, default=20260821)
    parser.add_argument("--marker", default=MARKER)
    parser.add_argument("--out-dir", type=Path, default=Path("tests/performance/data"))
    parser.add_argument("--load", action="store_true", help="INSERT into MySQL via docker exec")
    parser.add_argument("--purge", action="store_true", help="delete rows with --marker")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.purge:
        purge(args.marker)
        return 0
    if args.gids < 16:
        print("--gids should be >= 16 so t_link shards are not empty", file=sys.stderr)
        return 1
    rows = build_rows(args.count, args.gids, args.domain, args.seed)
    print_histogram(rows)
    write_csvs(rows, args.out_dir, args.miss_count, args.seed)
    if args.load:
        insert_rows(rows, args.marker)
        print(f"loaded {len(rows)} links marker={args.marker}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
