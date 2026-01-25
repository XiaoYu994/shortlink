-- stats_rank_migrate.lua
-- 参数说明：
-- KEYS[1]: 旧榜单 Key
-- KEYS[2]: 新榜单 Key
-- ARGV[1]: fullShortUrl (成员)
-- ARGV[2]: expireTime (过期时间，秒，通常 48小时)

-- 1. 获取旧分数
local score = redis.call('ZSCORE', KEYS[1], ARGV[1])

-- 2. 如果旧榜单里有分，才进行迁移
if score then
    -- 从旧榜单移除
    redis.call('ZREM', KEYS[1], ARGV[1])
    -- 写入新榜单 (直接使用查出来的 score)
    redis.call('ZADD', KEYS[2], score, ARGV[1])

    -- 检查新榜单是否需要设置过期时间
    local ttl = redis.call('TTL', KEYS[2])
    if ttl == -1 then
        redis.call('EXPIRE', KEYS[2], ARGV[2])
    end
    return 1 -- 迁移成功
end

return 0 -- 无需迁移