package com.example.yumi.common.redis.application;

import com.example.yumi.common.redis.dto.LuaType;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LuaFactory {

    private final Map<LuaType, RedisScript<Long>> scriptMap;

    public LuaFactory() {
        this.scriptMap = Arrays.stream(LuaType.values())
                .collect(Collectors.toMap(
                        luaType -> luaType,
                        LuaType::createRedisScript
                ));
    }

    public RedisScript<Long> getRedisScript(LuaType luaType) {
        RedisScript<Long> script = scriptMap.get(luaType);
        if (script == null) {
            throw new IllegalArgumentException("Lua script not found for type: " + luaType);
        }
        return script;
    }
}

