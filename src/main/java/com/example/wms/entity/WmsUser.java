package com.example.wms.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WmsUser {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 密码哈希
     */
    private String passwordHash;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 默认仓库ID
     */
    private Long defaultWarehouseId;

    /**
     * 用户状态
     *
     * 0：禁用
     * 1：启用
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
