-- ============================================================
-- P3 RAG：知识库、文档、切片元数据与 RAG 查询日志
-- 向量本身存放于 PostgreSQL + PGVector（ai_chunk_embedding），
-- MySQL 保存可审计的元数据与原始切片文本。
-- ============================================================

CREATE TABLE ai_knowledge_base (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    name               VARCHAR(120)  NOT NULL COMMENT '知识库名称',
    description        VARCHAR(1000) NULL,
    embedding_model_id BIGINT        NOT NULL COMMENT 'Embedding 模型配置 ID',
    chunk_size         INT           NOT NULL DEFAULT 600 COMMENT '切片字符数',
    chunk_overlap      INT           NOT NULL DEFAULT 100 COMMENT '切片重叠字符数',
    top_k              INT           NOT NULL DEFAULT 5 COMMENT '默认召回数量',
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    is_preset          TINYINT       NOT NULL DEFAULT 0,
    created_by         BIGINT        NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_kb_status (status, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG 知识库';

CREATE TABLE ai_knowledge_document (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT        NOT NULL,
    file_asset_id     BIGINT        NULL COMMENT '上传文件资产 ID；示例文档为空',
    file_name         VARCHAR(220)  NOT NULL,
    extension         VARCHAR(20)   NOT NULL,
    source_type       VARCHAR(20)   NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/DEMO',
    size_bytes        BIGINT        NULL,
    char_count        INT           NULL,
    chunk_count       INT           NOT NULL DEFAULT 0,
    status            VARCHAR(20)   NOT NULL COMMENT 'PROCESSING/READY/FAILED',
    error_message     VARCHAR(2000) NULL,
    extracted_text    MEDIUMTEXT    NULL COMMENT '解析后的文本，演示文档也使用',
    created_by        BIGINT        NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    processed_at      DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_ai_rag_doc_kb (knowledge_base_id, status),
    KEY idx_ai_rag_doc_file (file_asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG 知识库文档';

CREATE TABLE ai_knowledge_chunk (
    id                 BIGINT     NOT NULL AUTO_INCREMENT,
    knowledge_base_id  BIGINT     NOT NULL,
    document_id        BIGINT     NOT NULL,
    chunk_index        INT        NOT NULL,
    content            MEDIUMTEXT NOT NULL,
    char_count         INT        NOT NULL,
    embedding_model_id BIGINT     NOT NULL,
    embedding_dim      INT        NOT NULL,
    created_at         DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_rag_chunk_doc (document_id, chunk_index),
    KEY idx_ai_rag_chunk_kb (knowledge_base_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG 文档切片';

CREATE TABLE ai_rag_query_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    knowledge_base_ids VARCHAR(500) NOT NULL,
    question          VARCHAR(5000) NOT NULL,
    answer            MEDIUMTEXT   NULL,
    model_config_id   BIGINT       NULL,
    model_name        VARCHAR(160) NULL,
    top_k             INT          NOT NULL,
    source_count      INT          NOT NULL DEFAULT 0,
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    total_tokens      INT          NULL,
    token_estimated   TINYINT      NOT NULL DEFAULT 0,
    latency_ms        BIGINT       NULL,
    success           TINYINT      NOT NULL DEFAULT 1,
    error_message     VARCHAR(2000) NULL,
    trace_id          VARCHAR(64)  NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_rag_query_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG 查询日志';

-- 两个预置知识库：默认绑定种子 Embedding 模型；配置 Key 或切换本地模型后即可向量化。
INSERT INTO ai_knowledge_base (id, name, description, embedding_model_id, chunk_size, chunk_overlap,
                               top_k, status, is_preset, created_by)
VALUES (1, '企业产品知识库',
        '产品能力、部署方案、技术架构与售前常见问题。上传产品手册或导入示例文档后即可进行语义检索。',
        2, 600, 100, 5, 'ACTIVE', 1, NULL),
       (2, '客户服务知识库',
        '客服话术、售后政策、订单与库存处理规范。用于客服 Agent 的知识检索与来源引用。',
        2, 500, 80, 5, 'ACTIVE', 1, NULL);

-- RAG 模块已就绪：启用文档检索工具。若知识库尚未向量化，工具会明确返回不可用，不伪造结果。
UPDATE ai_tool
SET enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE tool_code = 'search_documents';
