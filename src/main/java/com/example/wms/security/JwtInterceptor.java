package com.example.wms.security;


import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        String auth = request.getHeader("Authorization");
        if(null == auth || !auth.startsWith("Bearer ")){
            response.setStatus(401);
            return false;
        }

        String token = auth.substring(7);

        try {
            Claims claims = jwtUtil.parseToken(token);
            Number userId = claims.get("userId", Number.class);
            String redisToken = redis.opsForValue().get("login:user:"+userId.longValue());

            if(!token.equals(redisToken)){
                response.setStatus(401);
                return false;
            }

            return true;
        }catch (Exception e){
            response.setStatus(402);
            return false;
        }

    }
}
