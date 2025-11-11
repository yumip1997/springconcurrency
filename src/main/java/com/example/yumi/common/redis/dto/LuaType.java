package com.example.yumi.common.redis.dto;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
public enum LuaType {
    DECREASE("lua/decrease.lua");

    private final String path;

    public RedisScript<Long> createRedisScript() {
        try {
            ClassPathResource resource = new ClassPathResource(this.path);
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return RedisScript.of(script, Long.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script from path: " + this.path, e);
        }
    }
}

