local stringKey = KEYS[1]
local stringVal = tonumber(ARGV[1])
local expireAt = tonumber(ARGV[2])

local keyExist = redis.call("SETNX",stringKey,stringVal);

if (keyExist >= 1) then
    redis.call("EXPIRE",stringKey,expireAt)
    return true
end

return false