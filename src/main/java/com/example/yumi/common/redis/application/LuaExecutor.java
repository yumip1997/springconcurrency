package com.example.yumi.common.redis.application;

import com.example.yumi.common.redis.dto.LuaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class LuaExecutor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LuaFactory luaFactory;

    /**
     * Lazy loading을 포함한 Lua 스크립트 실행
     * Redis에 key가 없으면 Supplier로부터 값을 로드하여 저장한 후 스크립트 실행
     */
    public <T> Long executeWithLazyLoad(
            LuaType luaType, 
            String key, 
            Supplier<T> valueLoader,
            Object... args) {
        
        // Lazy loading: key가 없으면 Supplier로부터 값을 로드
        loadValueIfAbsent(key, valueLoader);
        
        // Lua 스크립트 실행
        return execute(luaType, key, args);
    }

    private <T> void loadValueIfAbsent(String key, Supplier<T> valueLoader) {
        // Redis에 key가 있는지 확인
        if (redisTemplate.hasKey(key)) {
            log.debug("Redis에 key가 이미 존재합니다. key: {}", key);
            return;
        }

        // Redis에 없으면 Supplier로부터 값을 로드
        Object value = valueLoader.get();
        
        // SETNX를 사용하여 동시성 문제 방지 true: 저장완료, false: 다른쓰레드에서 이미 저장
        redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    public Long execute(LuaType luaType, String key, Object... args) {
        RedisScript<Long> script = luaFactory.getRedisScript(luaType);
        List<String> keys = Collections.singletonList(key);
        return redisTemplate.execute(script, keys, args);
    }

}

