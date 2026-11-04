package com.aiplatform.server.security;

import com.aiplatform.common.security.LoginUser;
import com.aiplatform.server.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 认证主体。将数据库用户转换为 Spring Security 可识别的 UserDetails，
 * 同时实现模块间共享的 LoginUser 契约。
 */
@Getter
public class UserPrincipal implements LoginUser, UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final String email;
    private final String role;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final int tokenVersion;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.enabled = Boolean.TRUE.equals(user.getEnabled());
        this.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
        this.tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
