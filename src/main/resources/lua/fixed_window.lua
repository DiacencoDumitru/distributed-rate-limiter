local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local current = tonumber(redis.call("GET", key) or "0")
if current < limit then
  local updated = redis.call("INCR", key)
  if updated == 1 then
    redis.call("EXPIRE", key, window)
  end
  local remaining = limit - updated
  return {1, remaining, 0}
end
local ttl = tonumber(redis.call("TTL", key) or "0")
if ttl < 0 then
  ttl = window
end
return {0, 0, ttl}
