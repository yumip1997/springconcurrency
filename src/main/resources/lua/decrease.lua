local key = KEYS[1]
local value = tonumber(ARGV[1])

local current = tonumber(redis.call('GET', key) or 0)

if current < value then
    return -1
end

local result = redis.call('DECRBY', key, value)
return result

