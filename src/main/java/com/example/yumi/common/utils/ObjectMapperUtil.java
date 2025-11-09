package com.example.yumi.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ObjectMapperUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object object) {
        try{
            return objectMapper.writeValueAsString(object);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
