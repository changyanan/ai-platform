-- ============================================================
-- P2: Agent / Tool / Prompt，以及本项目自有的演示业务数据
-- 演示订单、库存、客户数据均为本项目生成，不连接任何 ERP / CRM / WMS。
-- ============================================================

CREATE TABLE ai_tool
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    tool_code         VARCHAR(80)  NOT NULL COMMENT '工具编码，模型可见的名称来源',
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(1000) NOT NULL,
    parameters_schema JSON         NOT NULL COMMENT 'JSON Schema，用于模型 Tool Specification 与参数校验',
    example_arguments JSON         NULL COMMENT '调试示例参数',
    category          VARCHAR(40)  NOT NULL,
    bean_name         VARCHAR(120) NOT NULL COMMENT '实际执行工具 Bean 名称',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    built_in          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_tool_code (tool_code),
    KEY idx_ai_tool_category (category, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 工具注册表';

CREATE TABLE ai_tool_execution_log
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tool_id            BIGINT       NOT NULL,
    tool_code          VARCHAR(80)  NOT NULL,
    user_id            BIGINT       NULL,
    agent_execution_id BIGINT       NULL,
    arguments_json     JSON         NULL,
    result_text        MEDIUMTEXT   NULL,
    success            TINYINT(1)   NOT NULL DEFAULT 0,
    error_message      VARCHAR(1000) NULL,
    duration_ms        BIGINT       NULL,
    trace_id           VARCHAR(32)  NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tool_log_tool (tool_id, created_at),
    KEY idx_tool_log_agent_execution (agent_execution_id),
    KEY idx_tool_log_time (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '工具执行日志';

CREATE TABLE ai_agent
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100)   NOT NULL,
    description           VARCHAR(500)   NULL,
    system_prompt         TEXT           NOT NULL,
    model_id              BIGINT         NOT NULL,
    tool_ids              JSON           NOT NULL,
    max_tool_calls        INT            NOT NULL DEFAULT 5,
    max_execution_seconds INT            NOT NULL DEFAULT 90,
    max_cost              DECIMAL(20, 8) NULL COMMENT '成本上限；需要模型价格配置',
    enabled               TINYINT(1)     NOT NULL DEFAULT 1,
    is_preset             TINYINT(1)     NOT NULL DEFAULT 0,
    current_version       INT            NOT NULL DEFAULT 1,
    deleted               TINYINT(1)     NOT NULL DEFAULT 0,
    created_by            BIGINT         NULL,
    created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_agent_enabled (enabled, deleted),
    KEY idx_ai_agent_model (model_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 配置';

CREATE TABLE ai_agent_version
(
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    agent_id              BIGINT         NOT NULL,
    version               INT            NOT NULL,
    name                  VARCHAR(100)   NOT NULL,
    description           VARCHAR(500)   NULL,
    system_prompt         TEXT           NOT NULL,
    model_id              BIGINT         NOT NULL,
    tool_ids              JSON           NOT NULL,
    max_tool_calls        INT            NOT NULL,
    max_execution_seconds INT            NOT NULL,
    max_cost              DECIMAL(20, 8) NULL,
    change_note           VARCHAR(500)   NULL,
    created_by            BIGINT         NULL,
    created_at            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version (agent_id, version),
    KEY idx_agent_version_agent (agent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 版本快照';

CREATE TABLE ai_agent_execution
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    agent_id          BIGINT         NOT NULL,
    agent_name        VARCHAR(100)   NULL COMMENT '执行时名称快照',
    agent_version     INT            NOT NULL,
    model_name        VARCHAR(160)   NULL COMMENT '执行时模型快照',
    user_id           BIGINT         NOT NULL,
    input_text        MEDIUMTEXT     NOT NULL,
    output_text       MEDIUMTEXT     NULL,
    status            VARCHAR(30)    NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/LIMIT_EXCEEDED',
    tool_call_count   INT            NOT NULL DEFAULT 0,
    model_call_count  INT            NOT NULL DEFAULT 0,
    prompt_tokens     INT            NULL,
    completion_tokens INT            NULL,
    total_tokens      INT            NULL,
    token_estimated   TINYINT(1)     NOT NULL DEFAULT 0,
    estimated_cost    DECIMAL(20, 8) NULL,
    latency_ms        BIGINT         NULL,
    error_message     VARCHAR(2000)  NULL,
    trace_id          VARCHAR(32)    NULL,
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at       DATETIME(3)    NULL,
    PRIMARY KEY (id),
    KEY idx_agent_exec_agent (agent_id, created_at),
    KEY idx_agent_exec_user (user_id, created_at),
    KEY idx_agent_exec_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 执行记录';

CREATE TABLE ai_agent_step
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    execution_id      BIGINT       NOT NULL,
    step_index        INT          NOT NULL,
    step_type         VARCHAR(40)  NOT NULL COMMENT 'MODEL_REQUEST/MODEL_RESPONSE/TOOL_REQUEST/TOOL_RESULT/ERROR',
    title             VARCHAR(200) NULL,
    content           MEDIUMTEXT   NULL,
    tool_code         VARCHAR(80)  NULL,
    arguments_json    MEDIUMTEXT   NULL,
    tool_result       MEDIUMTEXT   NULL,
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    duration_ms       BIGINT       NULL,
    success           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_agent_step_execution (execution_id, step_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent 执行步骤';

CREATE TABLE ai_prompt
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500) NULL,
    category          VARCHAR(40)  NOT NULL,
    content           TEXT         NOT NULL,
    variables_json    JSON         NOT NULL,
    current_version   INT          NOT NULL DEFAULT 1,
    published_version INT          NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    created_by        BIGINT       NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_prompt_name (name),
    KEY idx_ai_prompt_category (category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Prompt 资产';

CREATE TABLE ai_prompt_version
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    prompt_id      BIGINT       NOT NULL,
    version        INT          NOT NULL,
    content        TEXT         NOT NULL,
    variables_json JSON         NOT NULL,
    change_note    VARCHAR(500) NULL,
    created_by     BIGINT       NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_version (prompt_id, version),
    KEY idx_prompt_version_prompt (prompt_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Prompt 版本';

-- ============================================================
-- 本地演示业务数据（与任何 ERP/CRM/WMS 无关）
-- ============================================================

CREATE TABLE ai_demo_order
(
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(40)    NOT NULL,
    customer_name    VARCHAR(80)    NOT NULL,
    customer_phone   VARCHAR(30)    NULL,
    product_name     VARCHAR(160)   NOT NULL,
    sku              VARCHAR(60)    NOT NULL,
    quantity         INT            NOT NULL,
    amount           DECIMAL(12, 2) NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    ordered_at       DATETIME(3)    NOT NULL,
    delivery_address VARCHAR(300)   NULL,
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_demo_order_no (order_no),
    KEY idx_demo_order_customer (customer_name),
    KEY idx_demo_order_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '本地模拟订单（非 ERP 数据）';

CREATE TABLE ai_demo_inventory
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    sku           VARCHAR(60)  NOT NULL,
    product_name  VARCHAR(160) NOT NULL,
    warehouse     VARCHAR(80)  NOT NULL,
    quantity      INT          NOT NULL,
    safety_stock  INT          NOT NULL,
    unit          VARCHAR(20)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_demo_inventory_sku_wh (sku, warehouse),
    KEY idx_demo_inventory_name (product_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '本地模拟库存（非 ERP 数据）';

CREATE TABLE ai_demo_customer
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    customer_code VARCHAR(40)    NOT NULL,
    name          VARCHAR(80)    NOT NULL,
    phone         VARCHAR(30)    NULL,
    level         VARCHAR(20)    NOT NULL,
    city          VARCHAR(60)    NULL,
    total_orders  INT            NOT NULL DEFAULT 0,
    total_amount  DECIMAL(14, 2) NOT NULL DEFAULT 0,
    created_at    DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_demo_customer_code (customer_code),
    KEY idx_demo_customer_name (name),
    KEY idx_demo_customer_phone (phone)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '本地模拟客户（非 CRM 数据）';

INSERT INTO ai_demo_order (order_no, customer_name, customer_phone, product_name, sku, quantity, amount, status,
                           ordered_at, delivery_address)
VALUES ('SO20260001', '星河科技', '13800001001', '企业级 AI 推理服务器', 'SRV-AI-9000', 2, 256000.00, '已发货',
        '2026-10-28 10:20:00', '上海市浦东新区张江路 88 号'),
       ('SO20260002', '云杉贸易', '13800001002', '智能数据采集终端', 'IOT-X200', 20, 86000.00, '待发货',
        '2026-10-30 14:05:00', '杭州市余杭区文一西路 66 号'),
       ('SO20260003', '北辰制造', '13800001003', '工业视觉检测模块', 'CV-M400', 8, 128000.00, '已完成',
        '2026-10-21 09:12:00', '苏州市工业园区星湖街 18 号'),
       ('SO20260004', '海川物流', '13800001004', '边缘计算网关', 'EDGE-G8', 30, 96000.00, '已发货',
        '2026-10-29 16:45:00', '宁波市鄞州区港城大道 120 号'),
       ('SO20260005', '星澜零售', '13800001005', 'AI 客服一体机', 'AICS-100', 12, 72000.00, '待发货',
        '2026-11-01 11:30:00', '成都市高新区天府三街 9 号'),
       ('SO20260006', '云杉贸易', '13800001002', '智能数据采集终端', 'IOT-X200', 10, 43000.00, '已完成',
        '2026-10-15 15:22:00', '杭州市余杭区文一西路 66 号'),
       ('SO20260007', '光屿能源', '13800001007', '能源预测分析软件', 'AI-INDUSTRY-PRO', 1, 188000.00, '已发货',
        '2026-10-26 13:08:00', '南京市建邺区江东中路 188 号'),
       ('SO20260008', '星河科技', '13800001001', '运维知识库订阅服务', 'KB-SUB-YEAR', 1, 68000.00, '已完成',
        '2026-10-10 10:00:00', '上海市浦东新区张江路 88 号'),
       ('SO20260009', '青禾医疗', '13800001009', '医疗文档 OCR 私有化节点', 'OCR-MED-2', 3, 156000.00, '待发货',
        '2026-11-02 08:50:00', '武汉市洪山区光谷大道 77 号'),
       ('SO20260010', '海川物流', '13800001004', '车队智能调度模块', 'FLEET-AI-3', 1, 98000.00, '已完成',
        '2026-10-09 17:35:00', '宁波市鄞州区港城大道 120 号');

INSERT INTO ai_demo_inventory (sku, product_name, warehouse, quantity, safety_stock, unit)
VALUES ('SRV-AI-9000', '企业级 AI 推理服务器', '上海仓', 6, 3, '台'),
       ('IOT-X200', '智能数据采集终端', '杭州仓', 42, 20, '台'),
       ('CV-M400', '工业视觉检测模块', '苏州仓', 9, 12, '件'),
       ('EDGE-G8', '边缘计算网关', '宁波仓', 55, 25, '台'),
       ('AICS-100', 'AI 客服一体机', '成都仓', 7, 8, '台'),
       ('AI-INDUSTRY-PRO', '能源预测分析软件', '数字仓', 999, 0, '授权'),
       ('KB-SUB-YEAR', '运维知识库订阅服务', '数字仓', 999, 0, '授权'),
       ('OCR-MED-2', '医疗文档 OCR 私有化节点', '武汉仓', 4, 5, '套'),
       ('FLEET-AI-3', '车队智能调度模块', '宁波仓', 11, 4, '套'),
       ('GPU-RTX-PRO', '专业级 GPU 计算卡', '上海仓', 3, 6, '张'),
       ('SSD-ENT-4T', '企业级 NVMe 固态硬盘 4TB', '上海仓', 28, 15, '块'),
       ('RAM-ECC-64G', 'ECC 服务器内存 64GB', '上海仓', 16, 10, '条'),
       ('CAM-HD-5MP', '工业高清相机 500 万像素', '苏州仓', 33, 18, '台'),
       ('LIDAR-L6', '激光雷达 L6', '苏州仓', 2, 5, '台'),
       ('SENSOR-TH-02', '温湿度传感器', '宁波仓', 120, 40, '只'),
       ('GATEWAY-4G', '4G 工业网关', '宁波仓', 18, 20, '台'),
       ('SCREEN-15', '工业触控屏 15 英寸', '成都仓', 24, 10, '台'),
       ('CABLE-IND-10', '工业级连接线 10 米', '成都仓', 80, 30, '根'),
       ('RACK-42U', '42U 服务器机柜', '武汉仓', 5, 3, '台'),
       ('UPS-10K', '10KVA 不间断电源', '武汉仓', 2, 4, '台');

INSERT INTO ai_demo_customer (customer_code, name, phone, level, city, total_orders, total_amount)
VALUES ('CUS-1001', '星河科技', '13800001001', '战略客户', '上海', 2, 324000.00),
       ('CUS-1002', '云杉贸易', '13800001002', '核心客户', '杭州', 2, 129000.00),
       ('CUS-1003', '北辰制造', '13800001003', '核心客户', '苏州', 1, 128000.00),
       ('CUS-1004', '海川物流', '13800001004', '战略客户', '宁波', 2, 194000.00),
       ('CUS-1005', '星澜零售', '13800001005', '普通客户', '成都', 1, 72000.00),
       ('CUS-1006', '光屿能源', '13800001007', '核心客户', '南京', 1, 188000.00),
       ('CUS-1007', '青禾医疗', '13800001009', '普通客户', '武汉', 1, 156000.00);

-- ============================================================
-- 初始化工具 / Agent / Prompt
-- ============================================================

INSERT INTO ai_tool (id, tool_code, name, description, parameters_schema, example_arguments, category, bean_name,
                     enabled, built_in)
VALUES (1, 'get_weather', '查询天气',
        '查询指定城市的天气。返回明确标记为 SIMULATED 的演示天气数据，不代表真实气象服务。',
        '{"type":"object","properties":{"city":{"type":"string","description":"城市名称，例如 上海"}},"required":["city"],"additionalProperties":false}',
        '{"city":"上海"}', '通用工具', 'weatherTool', 1, 1),
       (2, 'query_order', '查询订单',
        '按订单号查询本项目本地模拟订单，返回订单状态、金额与发货信息。数据非 ERP 实时数据。',
        '{"type":"object","properties":{"orderNo":{"type":"string","description":"订单号，例如 SO20260001"}},"required":["orderNo"],"additionalProperties":false}',
        '{"orderNo":"SO20260001"}', '业务查询', 'orderQueryTool', 1, 1),
       (3, 'query_inventory', '查询库存',
        '查询本地模拟库存，可按商品名称或 SKU 关键词过滤，并可只看低于安全库存的记录。',
        '{"type":"object","properties":{"keyword":{"type":"string","description":"商品名称或 SKU 关键词"},"onlyLowStock":{"type":"boolean","description":"是否只返回低于安全库存的记录"}},"required":[],"additionalProperties":false}',
        '{"keyword":"服务器","onlyLowStock":false}', '业务查询', 'inventoryQueryTool', 1, 1),
       (4, 'calculator', '计算器',
        '执行真实数值计算，支持 + - * / % ^、括号、sqrt/abs/min/max 函数。不会执行任意代码。',
        '{"type":"object","properties":{"expression":{"type":"string","description":"数学表达式，例如 (12+8)*3/sqrt(4)"}},"required":["expression"],"additionalProperties":false}',
        '{"expression":"(1280*3+960)/4"}', '通用工具', 'calculatorTool', 1, 1),
       (5, 'query_customer', '查询客户',
        '按名称或手机号关键词查询本项目本地模拟客户，返回等级、城市与消费汇总。数据非 CRM 数据。',
        '{"type":"object","properties":{"keyword":{"type":"string","description":"客户名称或手机号关键词"}},"required":["keyword"],"additionalProperties":false}',
        '{"keyword":"星河"}', '业务查询', 'customerQueryTool', 1, 1),
       (6, 'search_documents', '文档检索',
        '连接本项目 RAG 知识库执行向量检索。知识库可用性由 RAG 模块决定，未就绪时会明确返回不可用，不伪造结果。',
        '{"type":"object","properties":{"query":{"type":"string","description":"检索问题"},"topK":{"type":"integer","description":"返回条数，1-10"}},"required":["query"],"additionalProperties":false}',
        '{"query":"企业知识库权限设计","topK":3}', '知识检索', 'documentSearchTool', 0, 1);

INSERT INTO ai_agent (id, name, description, system_prompt, model_id, tool_ids, max_tool_calls,
                      max_execution_seconds, max_cost, enabled, is_preset, current_version, created_by)
VALUES (1, '通用助手', '处理日常问答、写作与分析的通用智能体',
        '你是企业级 AI 平台的通用助手。请使用简体中文，回答准确、结构清晰，必要时提供步骤与示例。不要声称执行了没有实际调用的工具。',
        1, JSON_ARRAY(), 3, 90, NULL, 1, 1, 1, NULL),
       (2, '数据分析助手', '结合本地演示订单、库存、客户数据进行分析',
        '你是数据分析助手。分析订单、库存与客户时，必须实际调用绑定工具获取数据，并在回答中说明数据来源为本地演示数据。不得编造未查询到的业务数字。',
        1, JSON_ARRAY(2, 3, 4, 5), 6, 120, NULL, 1, 1, 1, NULL),
       (3, '智能客服助手', '处理订单咨询、客户查询与知识库检索',
        '你是企业智能客服。优先使用订单、客户与文档检索工具核实信息；语气专业、简洁。遇到无法确认的问题应明确说明并建议人工介入，不得虚构订单或客户信息。',
        1, JSON_ARRAY(2, 5, 6), 5, 90, NULL, 1, 1, 1, NULL),
       (4, '文档分析助手', '基于企业知识库回答问题并展示来源',
        '你是文档分析助手。回答前应使用文档检索工具获取证据，区分“知识库引用”和“一般性建议”，并尽可能给出源文档名称。检索不可用时要明确说明。',
        1, JSON_ARRAY(6), 4, 90, NULL, 1, 1, 1, NULL),
       (5, '多模态助手', '使用视觉模型理解图片并结合文本对话',
        '你是多模态助手。收到图片时应描述可见内容并指出不确定之处，不要臆测图片中不存在的信息。当前模型若无法处理图片，应明确说明。',
        3, JSON_ARRAY(), 2, 90, NULL, 1, 1, 1, NULL);

INSERT INTO ai_agent_version (agent_id, version, name, description, system_prompt, model_id, tool_ids, max_tool_calls,
                              max_execution_seconds, max_cost, change_note, created_by)
VALUES (1, 1, '通用助手', '处理日常问答、写作与分析的通用智能体',
        '你是企业级 AI 平台的通用助手。请使用简体中文，回答准确、结构清晰，必要时提供步骤与示例。不要声称执行了没有实际调用的工具。',
        1, JSON_ARRAY(), 3, 90, NULL, '初始化版本', NULL),
       (2, 1, '数据分析助手', '结合本地演示订单、库存、客户数据进行分析',
        '你是数据分析助手。分析订单、库存与客户时，必须实际调用绑定工具获取数据，并在回答中说明数据来源为本地演示数据。不得编造未查询到的业务数字。',
        1, JSON_ARRAY(2, 3, 4, 5), 6, 120, NULL, '初始化版本', NULL),
       (3, 1, '智能客服助手', '处理订单咨询、客户查询与知识库检索',
        '你是企业智能客服。优先使用订单、客户与文档检索工具核实信息；语气专业、简洁。遇到无法确认的问题应明确说明并建议人工介入，不得虚构订单或客户信息。',
        1, JSON_ARRAY(2, 5, 6), 5, 90, NULL, '初始化版本', NULL),
       (4, 1, '文档分析助手', '基于企业知识库回答问题并展示来源',
        '你是文档分析助手。回答前应使用文档检索工具获取证据，区分“知识库引用”和“一般性建议”，并尽可能给出源文档名称。检索不可用时要明确说明。',
        1, JSON_ARRAY(6), 4, 90, NULL, '初始化版本', NULL),
       (5, 1, '多模态助手', '使用视觉模型理解图片并结合文本对话',
        '你是多模态助手。收到图片时应描述可见内容并指出不确定之处，不要臆测图片中不存在的信息。当前模型若无法处理图片，应明确说明。',
        3, JSON_ARRAY(), 2, 90, NULL, '初始化版本', NULL);

INSERT INTO ai_prompt (id, name, description, category, content, variables_json, current_version, published_version,
                       status, created_by)
VALUES (1, '客服问题回复', '标准化、专业且克制的客服回复模板', '客户服务',
        '你是一名专业的企业客服。请针对以下客户问题给出简洁、准确的回复。\n\n客户问题：{{customer_question}}\n客户等级：{{customer_level}}\n已知信息：{{known_context}}\n\n要求：\n1. 先确认客户诉求；2. 给出可执行的处理建议；3. 不承诺未确认的赔偿或时效。',
        '[{"name":"customer_question","label":"客户问题","required":true,"defaultValue":""},{"name":"customer_level","label":"客户等级","required":false,"defaultValue":"普通客户"},{"name":"known_context","label":"已知信息","required":false,"defaultValue":"无"}]',
        1, 1, 'PUBLISHED', NULL),
       (2, '经营数据周报', '基于订单、库存与客户数据分析生成周报', '数据分析',
        '请以资深经营分析师的视角，基于以下数据生成一份周报摘要：\n\n分析周期：{{period}}\n业务数据：\n{{business_data}}\n\n输出结构：\n1. 核心结论（最多 3 条）；2. 风险与异常；3. 下周建议。所有数字必须来自输入数据，不得编造。',
        '[{"name":"period","label":"分析周期","required":true,"defaultValue":"最近一周"},{"name":"business_data","label":"业务数据","required":true,"defaultValue":""}]',
        1, 1, 'PUBLISHED', NULL),
       (3, '文档智能摘要', '从长文档中提取结构化摘要与关键风险', '文档处理',
        '请阅读以下文档并输出摘要：\n\n{{document_text}}\n\n关注重点：{{focus}}\n\n请输出：一句话摘要、三个关键要点、潜在风险或待确认问题。不得引入文档之外的事实。',
        '[{"name":"document_text","label":"文档内容","required":true,"defaultValue":""},{"name":"focus","label":"关注重点","required":false,"defaultValue":"核心结论与风险"}]',
        1, 1, 'PUBLISHED', NULL),
       (4, 'Java 代码审查', '针对 Java/Spring 代码进行结构化审查', '研发效能',
        '你是一名资深 Java 架构师。请审查以下代码：\n\n```{{language}}\n{{code}}\n```\n\n需求背景：{{requirements}}\n\n请按严重程度输出问题，每个问题包含：问题、影响、证据位置、修复建议。不要臆测未提供的上下文。',
        '[{"name":"language","label":"语言","required":true,"defaultValue":"java"},{"name":"code","label":"代码","required":true,"defaultValue":""},{"name":"requirements","label":"需求背景","required":false,"defaultValue":"未提供"}]',
        1, NULL, 'DRAFT', NULL),
       (5, 'SQL 查询生成', '根据表结构生成只读 SQL 并解释', '数据分析',
        '根据以下表结构，为业务问题生成一条只读 SQL：\n\n表结构：\n{{schema}}\n\n业务问题：{{question}}\n\n仅输出 SELECT 查询，并解释关键字段与可能的性能风险。禁止生成 UPDATE/DELETE/DROP 等写入语句。',
        '[{"name":"schema","label":"表结构","required":true,"defaultValue":""},{"name":"question","label":"业务问题","required":true,"defaultValue":""}]',
        1, NULL, 'DRAFT', NULL);

INSERT INTO ai_prompt_version (prompt_id, version, content, variables_json, change_note, created_by)
SELECT id, 1, content, variables_json, '初始化版本', NULL FROM ai_prompt;
