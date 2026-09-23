package com.example.wms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateWmsUserRequest {

    @Size(max = 64, message = "真实姓名长度不能超过64")
    private String realName;

    @Size(max = 32, message = "手机号长度不能超过32")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128")
    private String email;

    private Long defaultWarehouseId;
}
