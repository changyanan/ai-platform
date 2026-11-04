package com.aiplatform.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "新密码长度需为 8-64 位")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码必须同时包含字母和数字")
        String newPassword
) {
}
