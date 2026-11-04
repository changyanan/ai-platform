package com.aiplatform.server.user.service;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.server.user.dto.ResetPasswordRequest;
import com.aiplatform.server.user.dto.UserCreateRequest;
import com.aiplatform.server.user.dto.UserQuery;
import com.aiplatform.server.user.entity.User;
import com.aiplatform.server.user.mapper.UserMapper;
import com.aiplatform.server.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .orderByDesc(User::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getDisplayName, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole().trim().toUpperCase(Locale.ROOT));
        }
        if (query.getEnabled() != null) {
            wrapper.eq(User::getEnabled, query.getEnabled());
        }
        Page<User> page = userMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public UserVO create(UserCreateRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists > 0) {
            throw BusinessException.conflict("登录名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(StringUtils.hasText(request.email()) ? request.email().trim() : null);
        user.setRole(request.role().trim().toUpperCase(Locale.ROOT));
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setTokenVersion(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("登录名已存在");
        }
        log.info("Admin {} created user {}", SecurityUtils.currentUsername(), username);
        return UserVO.from(user);
    }

    @Transactional
    public void updateStatus(Long userId, boolean enabled) {
        User user = requireUser(userId);
        if (!enabled) {
            if (user.getId().equals(SecurityUtils.currentUserId())) {
                throw BusinessException.forbidden("不能停用当前登录账号");
            }
            if ("ADMIN".equals(user.getRole()) && countActiveAdmins() <= 1) {
                throw BusinessException.conflict("至少需要保留一个启用状态的管理员");
            }
        }
        int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getEnabled, enabled)
                .set(User::getTokenVersion, nextVersion));
        log.info("Admin {} set user {} enabled={}", SecurityUtils.currentUsername(), user.getUsername(), enabled);
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        User user = requireUser(userId);
        int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .set(User::getMustChangePassword, true)
                .set(User::getTokenVersion, nextVersion));
        log.info("Admin {} reset password for user {}", SecurityUtils.currentUsername(), user.getUsername());
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private long countActiveAdmins() {
        return userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getRole, "ADMIN")
                .eq(User::getEnabled, true));
    }
}
