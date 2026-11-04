package com.aiplatform.server.auth.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.server.auth.dto.ChangePasswordRequest;
import com.aiplatform.server.auth.dto.LoginRequest;
import com.aiplatform.server.auth.service.AuthService;
import com.aiplatform.server.auth.vo.LoginResponse;
import com.aiplatform.server.user.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request, httpRequest));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public ApiResponse<UserProfileVO> me() {
        return ApiResponse.success(authService.currentProfile());
    }

    @Operation(summary = "修改当前用户密码")
    @PostMapping("/change-password")
    public ApiResponse<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(authService.changePassword(request));
    }

    @Operation(summary = "退出登录（前端丢弃 JWT）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }
}
