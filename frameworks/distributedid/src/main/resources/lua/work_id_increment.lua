-- 原子递增 WorkId 计数器
-- KEYS[1]: WorkId 或 DatacenterId 的 Redis key
-- 返回: 递增后的值
local current = redis.call('INCR', KEYS[1])
return current
