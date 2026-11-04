-- ============================================================
-- P1: 模型配置、调用日志、会话、消息、文件资产
-- ============================================================

CREATE TABLE ai_model_provider
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    provider_code    VARCHAR(64)  NOT NULL COMMENT '供应商编码，如 OPENAI/DEEPSEEK/DASHSCOPE/OLLAMA',
    provider_name    VARCHAR(100) NOT NULL COMMENT '展示名称',
    protocol         VARCHAR(40)  NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '协议',
    default_base_url VARCHAR(500) NOT NULL COMMENT '默认 API Base URL',
    requires_api_key TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否必须配置 API Key',
    description      VARCHAR(500) NULL,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_provider_code (provider_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型供应商';

CREATE TABLE ai_model_config
(
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    provider_id             BIGINT         NOT NULL COMMENT '逻辑关联 ai_model_provider.id',
    display_name            VARCHAR(100)   NOT NULL COMMENT '界面展示名',
    model_name              VARCHAR(160)   NOT NULL COMMENT '供应商模型名',
    model_type              VARCHAR(30)    NOT NULL COMMENT 'TEXT/EMBEDDING/VISION/ASR',
    api_base_url            VARCHAR(500)   NULL COMMENT '覆盖供应商默认 Base URL',
    api_key_cipher          VARCHAR(1024)  NULL COMMENT 'AES-GCM 密文，绝不返回前端',
    api_key_mask            VARCHAR(64)    NULL COMMENT '仅用于前端展示的掩码',
    temperature             DECIMAL(4, 2)  NOT NULL DEFAULT 0.70,
    max_tokens              INT            NULL COMMENT '最大输出 Token',
    context_window          INT            NULL COMMENT '上下文窗口 Token',
    embedding_dimension     INT            NULL COMMENT 'Embedding 维度',
    capabilities            JSON           NOT NULL COMMENT '能力标签数组',
    input_price_per_1k      DECIMAL(20, 8) NULL COMMENT '每 1K 输入 Token 价格，NULL 表示未知',
    output_price_per_1k     DECIMAL(20, 8) NULL COMMENT '每 1K 输出 Token 价格，NULL 表示未知',
    currency                VARCHAR(8)     NULL DEFAULT 'CNY',
    enabled                 TINYINT(1)     NOT NULL DEFAULT 1,
    is_default              TINYINT(1)     NOT NULL DEFAULT 0,
    deleted                 TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    last_test_status        VARCHAR(32)    NOT NULL DEFAULT 'NOT_CONFIGURED' COMMENT 'NOT_CONFIGURED/UNTESTED/SUCCESS/FAILED',
    last_test_message       VARCHAR(1000)  NULL,
    last_test_at            DATETIME(3)    NULL,
    created_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_model_provider (provider_id),
    KEY idx_ai_model_type_enabled (model_type, enabled),
    KEY idx_ai_model_last_test (last_test_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型配置';

CREATE TABLE ai_model_call_log
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    trace_id              VARCHAR(32)    NULL,
    user_id               BIGINT         NULL,
    model_config_id       BIGINT         NULL,
    provider_code         VARCHAR(64)    NULL,
    model_name            VARCHAR(160)   NULL,
    request_type          VARCHAR(32)    NOT NULL COMMENT 'CHAT/EMBEDDING/VISION/CONNECTIVITY/ASR',
    conversation_id       BIGINT         NULL,
    prompt_tokens         INT            NULL,
    completion_tokens     INT            NULL,
    total_tokens          INT            NULL,
    latency_ms            BIGINT         NULL,
    success               TINYINT(1)     NOT NULL DEFAULT 0,
    http_status           INT            NULL,
    error_code            VARCHAR(64)    NULL,
    error_message         VARCHAR(1000)  NULL,
    estimated             TINYINT(1)     NOT NULL DEFAULT 0 COMMENT 'Token 是否为估算值',
    data_source           VARCHAR(16)    NOT NULL DEFAULT 'REAL' COMMENT 'REAL/SIMULATED',
    input_price_snapshot  DECIMAL(20, 8) NULL COMMENT '调用时输入单价快照',
    output_price_snapshot DECIMAL(20, 8) NULL COMMENT '调用时输出单价快照',
    currency              VARCHAR(8)     NULL,
    estimated_cost        DECIMAL(20, 8) NULL COMMENT '未知价格时为空，绝不编造',
    request_summary       VARCHAR(500)   NULL COMMENT '脱敏摘要，不保存完整 Prompt',
    created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_call_created (created_at),
    KEY idx_ai_call_model (model_config_id, created_at),
    KEY idx_ai_call_type (request_type, created_at),
    KEY idx_ai_call_user (user_id, created_at),
    KEY idx_ai_call_trace (trace_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型调用日志';

CREATE TABLE ai_conversation
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    title                VARCHAR(200) NOT NULL DEFAULT '新对话',
    model_id             BIGINT       NOT NULL,
    model_name           VARCHAR(160) NULL COMMENT '创建时模型展示名快照',
    agent_id             BIGINT       NULL,
    message_count        INT          NOT NULL DEFAULT 0,
    last_message_preview VARCHAR(240) NULL,
    last_message_at      DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_conversation_user (user_id, updated_at),
    KEY idx_ai_conversation_model (model_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 会话';

CREATE TABLE ai_message
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id   BIGINT        NOT NULL,
    role              VARCHAR(20)   NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content           LONGTEXT      NOT NULL,
    reasoning_content LONGTEXT      NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED' COMMENT 'STREAMING/COMPLETED/FAILED',
    model_id          BIGINT        NULL,
    model_name        VARCHAR(160)  NULL,
    prompt_tokens     INT           NULL,
    completion_tokens INT           NULL,
    total_tokens      INT           NULL,
    token_estimated   TINYINT(1)    NOT NULL DEFAULT 0,
    latency_ms        BIGINT        NULL,
    attachments_json  JSON          NULL,
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_message_conversation (conversation_id, id),
    KEY idx_ai_message_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 消息';

CREATE TABLE ai_file_asset
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    original_name  VARCHAR(255) NOT NULL,
    stored_name    VARCHAR(120) NOT NULL,
    file_category  VARCHAR(20)  NOT NULL COMMENT 'IMAGE/TEXT',
    extension      VARCHAR(20)  NOT NULL,
    mime_type      VARCHAR(120) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    storage_path   VARCHAR(500) NOT NULL COMMENT '相对上传根目录，禁止对外暴露',
    extracted_text MEDIUMTEXT   NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'READY' COMMENT 'READY/FAILED',
    error_message  VARCHAR(500) NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_file_user (user_id, created_at),
    UNIQUE KEY uk_ai_file_stored_name (stored_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '上传文件资产';

-- ============================================================
-- 初始化：供应商与 3 个示例模型
-- 未配置真实 API Key 时连接状态为 NOT_CONFIGURED，绝不伪造“已连接”。
-- ============================================================

INSERT INTO ai_model_provider (id, provider_code, provider_name, protocol, default_base_url, requires_api_key,
                               description, enabled)
VALUES (1, 'OPENAI', 'OpenAI', 'OPENAI_COMPATIBLE', 'https://api.openai.com/v1', 1,
        'OpenAI 官方兼容接口', 1),
       (2, 'DEEPSEEK', 'DeepSeek', 'OPENAI_COMPATIBLE', 'https://api.deepseek.com/v1', 1,
        'DeepSeek OpenAI-compatible API', 1),
       (3, 'DASHSCOPE', '阿里云百炼 DashScope', 'OPENAI_COMPATIBLE',
        'https://dashscope.aliyuncs.com/compatible-mode/v1', 1,
        '通义千问 OpenAI 兼容模式', 1),
       (4, 'OLLAMA', 'Ollama 本地模型', 'OPENAI_COMPATIBLE', 'http://127.0.0.1:11434/v1', 0,
        '本地 Ollama OpenAI-compatible API，可按需启动', 1);

INSERT INTO ai_model_config (provider_id, display_name, model_name, model_type, temperature, max_tokens,
                             context_window, embedding_dimension, capabilities, enabled, is_default,
                             last_test_status, currency)
VALUES (2, 'DeepSeek Chat', 'deepseek-chat', 'TEXT', 0.70, 8192, 64000, NULL,
        JSON_ARRAY('TEXT', 'TOOL_CALLING'), 1, 1, 'NOT_CONFIGURED', NULL),
       (3, '通义 Embedding v3', 'text-embedding-v3', 'EMBEDDING', 0.00, NULL, 8192, 1024,
        JSON_ARRAY('EMBEDDING'), 1, 0, 'NOT_CONFIGURED', NULL),
       (3, '通义千问 VL Max', 'qwen-vl-max', 'VISION', 0.50, 4096, 32768, NULL,
        JSON_ARRAY('VISION', 'TEXT'), 1, 0, 'NOT_CONFIGURED', NULL);
