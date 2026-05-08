local key = KEYS[1]
local window = tonumber(ARGV[1])
local exists = redis.call("EXISTS", key)
if exists == 0 then
  return {-1, 0}
end
local time = redis.call("TIME")
local now_us = tonumber(time[1]) * 1000000 + tonumber(time[2])
local cutoff = now_us - window * 1000000
redis.call("ZREMRANGEBYSCORE", key, "-inf", "(" .. cutoff)
local count = tonumber(redis.call("ZCARD", key))
local ttl = redis.call("TTL", key)
if ttl < 0 then
  ttl = 0
end
return {count, ttl}
