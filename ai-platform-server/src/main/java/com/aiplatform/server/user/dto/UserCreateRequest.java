package com.aiplatform.server.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "请输入登录名")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "登录名需为 3-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入显示名")
        @Size(max = 64, message = "显示名不能超过 64 个字符")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email,

        @NotBlank(message = "请选择角色")
        @Pattern(regexp = "^(ADMIN|USER)$", message = "角色必须是 ADMIN 或 USER")
        String role,

        @NotBlank(message = "请输入初始密码")
        @Size(min = 8, max = 64, message = "初始密码长度需为 8-64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "初始密码必须同时包含字母和数字")
        String password
) {
}
