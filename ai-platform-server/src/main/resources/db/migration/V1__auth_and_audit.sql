-- ============================================================
-- P0: 用户、登录日志、操作审计
-- 数据库：MySQL 8
-- 约定：所有跨模块关联均为逻辑外键，由应用层保证一致性
-- ============================================================

CREATE TABLE ai_user
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username             VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name         VARCHAR(64)  NOT NULL COMMENT '显示名',
    email                VARCHAR(128) NULL COMMENT '邮箱',
    role                 VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    enabled              TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    must_change_password TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否必须修改密码',
    token_version        INT          NOT NULL DEFAULT 0 COMMENT '令牌版本，修改密码/停用时递增',
    last_login_at        DATETIME(3)  NULL COMMENT '最近登录时间',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_user_username (username),
    KEY idx_ai_user_role (role)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '平台用户';

CREATE TABLE ai_login_log
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NULL COMMENT '用户 ID，失败且用户不存在时为空',
    username       VARCHAR(64)  NOT NULL COMMENT '尝试登录名',
    success        TINYINT(1)   NOT NULL COMMENT '是否成功',
    failure_reason VARCHAR(255) NULL COMMENT '失败原因（脱敏）',
    ip             VARCHAR(64)  NULL COMMENT '客户端 IP',
    user_agent     VARCHAR(512) NULL COMMENT '客户端 UA',
    login_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_login_log_user (user_id, login_at),
    KEY idx_ai_login_log_time (login_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '登录日志';

CREATE TABLE ai_operation_audit
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NULL,
    username      VARCHAR(64)  NULL,
    module        VARCHAR(64)  NOT NULL COMMENT '模块',
    action        VARCHAR(64)  NOT NULL COMMENT '动作',
    resource_type VARCHAR(64)  NULL COMMENT '资源类型',
    resource_id   VARCHAR(64)  NULL COMMENT '资源 ID',
    success       TINYINT(1)   NOT NULL DEFAULT 1,
    ip            VARCHAR(64)  NULL,
    detail_json   JSON         NULL COMMENT '脱敏后的操作详情',
    duration_ms   BIGINT       NULL,
    trace_id      VARCHAR(32)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_audit_user (user_id, created_at),
    KEY idx_ai_audit_module (module, action, created_at),
    KEY idx_ai_audit_trace (trace_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作审计';
