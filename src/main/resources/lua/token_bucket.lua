local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local refill_rate = capacity / window
local time = redis.call("TIME")
local now = tonumber(time[1]) + (tonumber(time[2]) / 1000000)
local data = redis.call("HMGET", key, "tokens", "ts")
local tokens = tonumber(data[1])
local ts = tonumber(data[2])
if tokens == nil then
  tokens = capacity
  ts = now
end
local elapsed = now - ts
if elapsed < 0 then
  elapsed = 0
end
tokens = math.min(capacity, tokens + (elapsed * refill_rate))
local allowed = 0
local retry_after = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retry_after = math.ceil((1 - tokens) / refill_rate)
end
redis.call("HMSET", key, "tokens", tostring(tokens), "ts", tostring(now))
redis.call("EXPIRE", key, math.max(window, 1))
local remaining = math.floor(tokens)
if remaining < 0 then
  remaining = 0
end
return {allowed, remaining, retry_after}
