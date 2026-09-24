package com.example.wms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WmsUserResponse {

    private Long id;

    private String username;

    private String realName;

    private String mobile;

    private String email;

    private Long defaultWarehouseId;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    //JWT
    private String token;
}
