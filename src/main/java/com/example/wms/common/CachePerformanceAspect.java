package com.example.wms.common;


import com.example.wms.annotation.CachePerformanceLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class CachePerformanceAspect {

    /**
     * 使用 ThreadLocal 标记方法是否真正执行了
     * 如果方法体没执行（直接返回缓存），这个标志不会被设置
     */
    private static final ThreadLocal<Boolean> METHOD_EXECUTED = ThreadLocal.withInitial(() -> false);

    @Around("@annotation(com.example.wms.annotation.CachePerformanceLog)")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable{
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        CachePerformanceLog annotation = method.getAnnotation(CachePerformanceLog.class);

        String businessName = annotation.value().isEmpty()?method.getName(): annotation.value();
        Object[] args = joinPoint.getArgs();
        String params = Arrays.toString(args);
        if (params.length() > 100){
            params = params.substring(0, 97) + "...";
        }

        // 重置执行标志
        METHOD_EXECUTED.set(false);

        log.info("[DEBUG-AOP] {} 切面被触发 - 开始执行", businessName);

        long startTime = System.nanoTime();
        Object result = null;
        Throwable error = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long endTime = System.nanoTime();
            double elapsedMs = (endTime - startTime) / 1_000_000.0;

            // 判断是否命中缓存
            boolean methodExecuted = METHOD_EXECUTED.get();
            log.info("[DEBUG-AOP] {} 方法执行标志: {}", businessName, methodExecuted);

            String cacheStatus = methodExecuted ? "🔴 MISS (查询数据库)" : "🟢 HIT (命中缓存)";

            if (error == null) {
                log.info("[缓存性能] {} | {} | 耗时: {:.3f} ms | 参数: {}",
                        businessName, cacheStatus, elapsedMs, params);
            } else {
                log.error("[缓存性能] {} | ❌ ERROR | 耗时: {:.3f} ms | 参数: {} | 错误: {}",
                        businessName, elapsedMs, params, error.getMessage());
            }

            // 清理 ThreadLocal
            METHOD_EXECUTED.remove();
        }


    }


    /**
     * 给 service 用，标记方法已执行
     */
    public static void markMethodExecuted() {
        METHOD_EXECUTED.set(true);
    }

}
