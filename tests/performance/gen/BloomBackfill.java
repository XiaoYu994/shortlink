import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Adds full_short_url lines to the same Redisson bloom filter the app uses.
 * Does not re-init size/fpp when the filter already exists.
 */
public final class BloomBackfill {

    private BloomBackfill() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: BloomBackfill <redis-address> <password> <urls-file> [filter-name]");
            System.exit(1);
        }
        String address = args[0];
        String password = args[1];
        Path urlsFile = Path.of(args[2]);
        String filterName = args.length > 3 ? args[3] : "shortlink_uri_create_cache_penetration_bloom_filter";

        Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setPassword(password.isEmpty() ? null : password);

        RedissonClient client = Redisson.create(config);
        try {
            RBloomFilter<String> bloom = client.getBloomFilter(filterName);
            if (!bloom.isExists()) {
                bloom.tryInit(100_000_000L, 0.001);
            }
            List<String> urls = Files.readAllLines(urlsFile, StandardCharsets.UTF_8);
            int added = 0;
            for (String line : urls) {
                String url = line.trim();
                if (url.isEmpty() || url.startsWith("#")) {
                    continue;
                }
                bloom.add(url);
                added++;
            }
            System.out.println("bloom=" + filterName + " added=" + added + " containsSample="
                    + (urls.isEmpty() ? false : bloom.contains(urls.get(0).trim())));
        } finally {
            client.shutdown();
        }
    }
}
