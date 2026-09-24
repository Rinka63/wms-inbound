package com.example.wms.controller;

import com.example.wms.dto.CreateWmsUserRequest;
import com.example.wms.dto.LoginRequest;
import com.example.wms.dto.UpdateWmsUserRequest;
import com.example.wms.dto.WmsUserResponse;
import com.example.wms.security.JwtUtil;
import com.example.wms.service.WmsUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class WmsUserController {

    private final WmsUserService wmsUserService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    /**
     * 创建用户
     */
    @PostMapping
    public ResponseEntity<Long> createUser(
            @Valid
            @RequestBody CreateWmsUserRequest request) {

        Long userId = wmsUserService.createUser(request);

        return ResponseEntity.ok(userId);
    }

    /**
     * 根据ID查询用户
     */
    @GetMapping("/userId/{id}")
    public ResponseEntity<WmsUserResponse> getUser(
            @PathVariable Long id) {

        WmsUserResponse response = wmsUserService.getUserById(id);
        

        return ResponseEntity.ok(response);
    }

    /**
     * 根据用户名查询
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<WmsUserResponse> getUserByUsername(
            @PathVariable String username) {

        WmsUserResponse response = wmsUserService.getUserByUsername(username);

        return ResponseEntity.ok(response);
    }

    /**
     * 验证用户登录信息
     */

    @PostMapping("/login")
    public ResponseEntity<WmsUserResponse> authenticate(
            @Valid
            @RequestBody LoginRequest request){

        WmsUserResponse response = wmsUserService.authenticate(request.getUsername(), request.getPassword());

        String token = jwtUtil.generateToken(response.getId(), response.getUsername());
        response.setToken(token);

        redis.opsForValue().set("login:user:" + response.getId(), token, Duration.ofHours(4));

        return ResponseEntity.ok(response);
    }

    /**
     * 修改用户基本资料
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateUser(
            @PathVariable Long id,
            @Valid
            @RequestBody UpdateWmsUserRequest request) {

        wmsUserService.updateUser(id, request);

        return ResponseEntity.noContent().build();
    }


    /**
     * 启用用户
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableUser(
            @PathVariable Long id) {

        wmsUserService.enableUser(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * 禁用用户
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableUser(
            @PathVariable Long id) {

        wmsUserService.disableUser(id);

        return ResponseEntity.noContent().build();
    }


}
