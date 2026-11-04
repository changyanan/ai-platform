package com.aiplatform.server.user.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.server.user.dto.ResetPasswordRequest;
import com.aiplatform.server.user.dto.UpdateUserStatusRequest;
import com.aiplatform.server.user.dto.UserCreateRequest;
import com.aiplatform.server.user.dto.UserQuery;
import com.aiplatform.server.user.service.UserAdminService;
import com.aiplatform.server.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理（管理员）")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "用户分页列表")
    @GetMapping
    public ApiResponse<PageResult<UserVO>> page(@Valid UserQuery query) {
        return ApiResponse.success(userAdminService.page(query));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public ApiResponse<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userAdminService.create(request));
    }

    @Operation(summary = "启用或停用用户")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminService.updateStatus(id, request.enabled());
        return ApiResponse.success();
    }

    @Operation(summary = "重置用户密码（重置后强制修改）")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResponse.success();
    }
}
