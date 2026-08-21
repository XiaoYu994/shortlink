# 多短码跳转压测（场 B / C / D）

- 时间：2026-08-21 10:10–10:16 CST
- 数据：热库 **10,000** 条 `perf-gen-10k`（16 表分片），`mix.csv` / `miss.csv`
- 环境：本机 Docker Desktop，Nginx :80 → aggregation
- 统一负载：20 线程、10s 爬坡、60s；不跟随 302

HTML：

- 场 B：`b-html/index.html`
- 场 C：`c-html/index.html`
- 场 D：`d-html/index.html`

对照（上一轮单 key `Zvldb`）：242,034 样本，4,091 req/s，P99 12ms，错误 0。

## 总表

| 场 | 场景 | 样本 | 错误 | 吞吐 | 平均 | P90 / P95 / P99 | 最大 |
|---|---|---|---|---|---|---|---|
| 预热 | 1 线程扫 mix | 9,524 | 0 | 106/s | 9ms | — | 252ms |
| **B** | 1 万短码，L1 已预热 | **213,664** | **0** | **3,579/s** | 5.0ms | 11 / 13 / **19ms** | 117ms |
| **C** | 重启后 L1 空，走 Redis | **173,599** | **0** | **2,906/s** | 6.1ms | 11 / 13 / **16ms** | 222ms |
| **D** | 1 千个不存在短码 | **229,652** | **0** | **3,895/s** | 4.6ms | 8 / 9 / **12ms** | 113ms |

JMeter 的「错误 0」：B/C 断言 Location 含 `example.com`；D 断言 Location 含 `page/notfound`。

## 缓存

`shortlink_cache_*` 把 Caffeine **和** Redis 命中都记成 hit。

| 场 | hit Δ | miss Δ | 综合命中率 |
|---|---|---|---|
| B | +213,188 | +476 | **99.78%** |
| C | +173,599 | **0** | **100%**（L1 空，全部 Redis 命中后再回填 Caffeine） |
| D | 0 | +229,652 | 0%（布隆未命中，按设计不回源） |

场 C 证明多级缓存的第二层在工作：进程重启后没有打爆 MySQL（C 期间 MySQL CPU 约 35%，与 B 接近），吞吐只从 3.6k 降到 2.9k。

场 D 应用侧把 404 记为 `redirect_failure`（代码里 `sendRedirect(PAGE_NOT_FOUND)` 走 failure 计数），这是指标命名，不是 JMeter 失败。

## 统计跟上率（异步高德仍是瓶颈）

| 区间 | 跳转样本 | `t_link_access_logs` 增量 | 跟上率 |
|---|---|---|---|
| 预热 | 9,524 | +3,861 | 40% |
| B | 213,664 | +2,703 | **1.3%** |
| C 结束到 D 开始 | 173,599 | +3,705 | **2.1%** |
| D | 229,652（应为 404，不写访问日志） | +2,533 | 仍是前面堆积的消费 |

跳转 302 与统计落库不能写在同一句「都过了」里。

## 资源峰值（Docker stats）

| 容器 | B 峰值 CPU | C 峰值 CPU | D 峰值 CPU | 内存 |
|---|---|---|---|---|
| aggregation | 722% | **900%** | 623% | ~1.2 GiB / 0.97 GiB |
| frontend (Nginx) | 774% | 693% | 792% | ~30 MiB |
| Redis | 65% | 89% | 115% | ~330 MiB |
| MySQL | 44% | 53% | 43% | ~590 MiB |
| RocketMQ broker | 163% | 139% | **18%** | ~3.5 GiB |

D 几乎不发统计消息，broker CPU 掉下来，印证跳转热路径不依赖 MQ。B/C 的 broker 高负载来自异步统计，不是 302 本身。

压完 aggregation 仍是 UP。

## 通过标准对照

| 预定标准 | 结果 |
|---|---|
| B 错误率 &lt; 0.1%，P99 &lt; 50ms | **过**（0%，19ms） |
| C 仍几乎全 302，允许变慢、不许 5xx | **过**（0 错误，2.9k QPS） |
| D 302 到 404 页，MySQL 不随线程线性涨 | **过**（P99 12ms，MySQL CPU 与 B 同级） |

## 这轮能写进结论的

- 1 万真实分片短码下，热缓存跳转约 **3.6k QPS、P99 19ms、错误 0**。
- 清掉 Caffeine 后 Redis 能托住，约 **2.9k QPS**，没有回源打崩。
- 布隆对不存在短码有效，约 **3.9k QPS** 落到 404 页。
- 统计消费仍然跟不上跳转（跟上率约 1～2%），高德超时问题还在。

## 仍不能写

- 1 亿数据、4C8G、命中率 99%（那是单 key / 1 万 key 热路径，不是 1 亿 URI）。
- 「统计完全不影响跳转」——B/C 里 broker 和 aggregation CPU 明显高于 D。
- 冷热 70%。

## 复跑

```bash
bash tests/performance/gen/run-load.sh   # 已灌过可跳过
# 场 B
jmeter -n -t tests/performance/jmeter/redirect-mix.jmx \
  -Jcsvfile=$PWD/tests/performance/data/mix.csv \
  -Jthreads=20 -Jramp=10 -Jduration=60 -l /tmp/b.jtl -e -o /tmp/b-html
# 场 C
docker restart shortlink-aggregation   # 等 health UP
# 再跑同一条 mix
# 场 D
jmeter -n -t tests/performance/jmeter/redirect-miss.jmx \
  -Jcsvfile=$PWD/tests/performance/data/miss.csv \
  -Jthreads=20 -Jramp=10 -Jduration=60 -l /tmp/d.jtl -e -o /tmp/d-html
```
