local key = KEYS[1]
local current = redis.call('GET', key)

if not current then
    return {-1, 0}
end

local ttl = redis.call('TTL', key)
if ttl < 0 then
    ttl = 0
end

return {current, ttl}
