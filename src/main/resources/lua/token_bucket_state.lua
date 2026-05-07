local key = KEYS[1]
local state = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = state[1]
local ts = state[2]

if not tokens or not ts then
    return {-1, -1, 0}
end

local ttl = redis.call('TTL', key)
if ttl < 0 then
    ttl = 0
end

return {tokens, ts, ttl}
