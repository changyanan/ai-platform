package com.aiplatform.server.user.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_user")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String role;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private Integer tokenVersion;
    private LocalDateTime lastLoginAt;
}
