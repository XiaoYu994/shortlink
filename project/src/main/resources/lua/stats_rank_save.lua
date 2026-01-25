-- 进行 pv uv uip 自增操作 使用 Redis Zset 实现排行榜的效果
local pvKey = KEYS[1]
local uvKey = KEYS[2]
local uipKey = KEYS[3]

local member = ARGV[1]
local uvFlag = ARGV[2]
local uipFlag = ARGV[3]
local score = tonumber(ARGV[4])
local expireTime = tonumber(ARGV[5])

-- 定义一个局部函数来处理通用的更新逻辑
local function update_stat(key, add_score, member_val, expire_time)
    -- 执行 ZINCRBY
    redis.call('ZINCRBY', key, add_score, member_val)

    -- 获取剩余生存时间 (TTL)
    -- -1: 永不过期 (说明是新 Key，或者没设置过期时间)
    -- -2: Key 不存在 (ZINCRBY 刚执行完，理论上不可能为 -2)
    -- >0: 还有多少秒过期 (说明已经设置过了，不用再设置)
    local ttl = redis.call('TTL', key)
    if ttl == -1 then
        redis.call('EXPIRE', key, expire_time)
    end
end

-- 1. 更新 PV (无条件更新)
update_stat(pvKey, score, member, expireTime)

-- 2. 更新 UV
-- 如果是新访客(true)，分数 +score(1)
-- 如果是老访客(false)，分数 +0 (为了占位，保证 Redis 里有这个 Key)
local uvStep = 0
if uvFlag == 'true' then
    uvStep = score
end
update_stat(uvKey, uvStep, member, expireTime)

-- 3. 更新 UIP
local uipStep = 0
if uipFlag == 'true' then
    uipStep = score
end
update_stat(uipKey, uipStep, member, expireTime)

return nil