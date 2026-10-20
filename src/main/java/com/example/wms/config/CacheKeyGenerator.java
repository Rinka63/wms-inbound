package com.example.wms.config;


import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


@Component("transactionKeyGenerator")
public class CacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        StringBuilder key = new StringBuilder();
        key.append(method.getName()).append(":");

        for (Object param : params) {
            key.append(param == null ? "null" : param.toString()).append(":");
        }

        return key.toString();
    }
}
