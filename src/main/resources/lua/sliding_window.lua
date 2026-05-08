local key = KEYS[1]
local seq_key = key .. ":seq"
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local time = redis.call("TIME")
local now_us = tonumber(time[1]) * 1000000 + tonumber(time[2])
local window_us = window * 1000000
local cutoff = now_us - window_us
redis.call("ZREMRANGEBYSCORE", key, "-inf", "(" .. cutoff)
local count = tonumber(redis.call("ZCARD", key))
if count < limit then
  local seq = redis.call("INCR", seq_key)
  local member = now_us .. ":" .. seq
  redis.call("ZADD", key, now_us, member)
  redis.call("EXPIRE", key, window)
  redis.call("EXPIRE", seq_key, window)
  return {1, limit - (count + 1), 0}
end
local oldest = redis.call("ZRANGE", key, 0, 0, "WITHSCORES")
local retry_after = window
if oldest[2] then
  local oldest_us = tonumber(oldest[2])
  retry_after = math.max(1, math.ceil((oldest_us + window_us - now_us) / 1000000))
end
return {0, 0, retry_after}
