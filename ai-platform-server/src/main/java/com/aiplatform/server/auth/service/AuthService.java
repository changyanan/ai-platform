package com.aiplatform.server.auth.service;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.server.auth.dto.ChangePasswordRequest;
import com.aiplatform.server.auth.dto.LoginRequest;
import com.aiplatform.server.auth.vo.LoginResponse;
import com.aiplatform.server.security.JwtService;
import com.aiplatform.server.security.UserPrincipal;
import com.aiplatform.server.user.entity.LoginLog;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.LoginLogMapper;
import com.aiplatform.server.user.mapper.UserMapper;
import com.aiplatform.server.user.vo.UserProfileVO;
import com.aiplatform.server.web.RequestUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, principal.getId())
                    .set(User::getLastLoginAt, LocalDateTime.now()));
            recordLogin(principal.getId(), username, true, null, httpRequest);
            return buildLoginResponse(principal);
        } catch (DisabledException ex) {
            recordLogin(findUserId(username), username, false, "账号已停用", httpRequest);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用，请联系管理员", HttpStatus.FORBIDDEN);
        } catch (AuthenticationException ex) {
            recordLogin(findUserId(username), username, false, "用户名或密码错误", httpRequest);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
    }

    public UserProfileVO currentProfile() {
        User user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在或已失效");
        }
        return UserProfileVO.from(user);
    }

    @Transactional
    public LoginResponse changePassword(ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("两次输入的新密码不一致");
        }
        Long currentUserId = SecurityUtils.currentUserId();
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在或已失效");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("新密码不能与当前密码相同");
        }

        int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, currentUserId)
                .set(User::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .set(User::getMustChangePassword, false)
                .set(User::getTokenVersion, nextVersion));

        User updated = userMapper.selectById(currentUserId);
        UserPrincipal principal = new UserPrincipal(updated);
        log.info("User {} changed password; token version -> {}", updated.getUsername(), nextVersion);
        return buildLoginResponse(principal);
    }

    private LoginResponse buildLoginResponse(UserPrincipal principal) {
        String token = jwtService.generateAccessToken(principal);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiresInSeconds(),
                principal.isMustChangePassword(),
                UserProfileVO.from(principal)
        );
    }

    private Long findUserId(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .select(User::getId)
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        return user == null ? null : user.getId();
    }

    private void recordLogin(Long userId, String username, boolean success, String reason,
                             HttpServletRequest request) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setSuccess(success);
            loginLog.setFailureReason(reason);
            loginLog.setIp(RequestUtils.clientIp(request));
            loginLog.setUserAgent(RequestUtils.userAgent(request));
            loginLog.setLoginAt(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception ex) {
            // 登录日志失败不能阻断正常认证，但必须保留服务端告警。
            log.warn("Failed to persist login log for user {}", username, ex);
        }
    }
}
