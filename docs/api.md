# API 接口文档

> 基线版本：P0–P5。覆盖认证、模型、对话、Agent、Tool、Prompt、RAG、多模态与监控。
> 在线调试：`http://localhost:8080/swagger-ui.html`

## 1. 基础约定

- Base URL：`http://localhost:8080`（前端开发通过 Vite 代理 `/api`）。
- 鉴权：除登录、健康检查、Swagger、`/api/open/**` 外，请求头必须携带 `Authorization: Bearer <JWT>`。
- 开放接口 `/api/open/**` 使用 `X-API-Key`，与管理员 JWT 分离；默认关闭，需设置 `AI_PLATFORM_OPEN_API_ENABLED=true` 与 `AI_PLATFORM_OPEN_API_KEY`。
- 时间：JSON 中为 `yyyy-MM-dd'T'HH:mm:ss`；数据库以 Asia/Shanghai 保存。
- Trace：响应头与响应体的 `traceId` 可用于定位服务端日志。

### 统一响应

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "traceId": "f74a91ae90324af3",
  "timestamp": "2026-11-03T07:34:59.384Z"
}
```

- `code = 0` 表示成功；非 0 表示业务失败。
- HTTP 状态码同时表达语义：401 未认证、403 无权限、404 不存在、413 文件过大、500 未预期异常。

### 主要错误码

| code | 含义 |
|---|---|
| `40000` | 请求参数不合法 |
| `40100` | 未登录或 Token 失效 |
| `40102` | 开放接口 X-API-Key 缺失或无效 |
| `40300` | 无权限 |
| `40301` | 首次登录必须先修改密码 |
| `40900` | 资源状态冲突 |
| `41300` | 文件过大 |
| `41500` | 文件类型不支持 |
| `50010` | 模型未配置、未启用或连接失败 |
| `50011` | 模型服务调用失败 |
| `50020` | 工具执行失败 |
| `50030` | 知识库服务未就绪 |
| `50040` | 多模态服务暂不支持 |
| `50050` | 地址识别失败或模型未返回合法 JSON |
| `50300` | 开放接口未启用或未配置 API Key |

---

## 2. 认证

### POST `/api/auth/login`

```json
{ "username": "admin", "password": "Admin@123456" }
```

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "mustChangePassword": true,
    "user": {
      "id": 1,
      "username": "admin",
      "displayName": "平台管理员",
      "role": "ADMIN",
      "enabled": true,
      "mustChangePassword": true
    }
  }
}
```

首次登录 `mustChangePassword=true` 时，除以下接口外的请求均返回 `40301`：

- `POST /api/auth/change-password`
- `GET /api/auth/me`
- `POST /api/auth/logout`

### GET `/api/auth/me`

返回当前用户资料，不包含密码哈希。

### POST `/api/auth/change-password`

```json
{
  "currentPassword": "Admin@123456",
  "newPassword": "NewPass@2026",
  "confirmPassword": "NewPass@2026"
}
```

成功后返回新的 `LoginResponse`。`token_version` 递增，旧 JWT 立即失效。

### POST `/api/auth/logout`

无状态 JWT 登出；服务端返回成功，前端负责丢弃 Token。

---

## 3. 用户管理（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users?page=1&size=10&keyword=&role=&enabled=` | 分页查询 |
| POST | `/api/admin/users` | 新增用户 |
| PUT | `/api/admin/users/{id}/status` | 启用/停用 |
| POST | `/api/admin/users/{id}/reset-password` | 重置密码，强制下次登录修改 |

新增用户请求：

```json
{
  "username": "demo_user",
  "displayName": "演示用户",
  "email": "demo@example.com",
  "role": "USER",
  "password": "Demo@2026"
}
```

安全约束：

- 不能停用自己。
- 至少保留一个启用状态的管理员。
- 停用、改密会递增 `token_version`，旧 Token 立即失效。
- 密码哈希使用 BCrypt，任何接口不返回。

---

## 4. 模型管理

### GET `/api/models/options`（登录用户）

给 AI 工作台使用，只返回模型元数据与可用性：

```json
{
  "id": 4,
  "displayName": "本地 Qwen2.5 0.5B",
  "modelName": "qwen2.5:0.5b",
  "modelType": "TEXT",
  "providerName": "Ollama 本地模型",
  "capabilities": ["TEXT"],
  "available": true,
  "isDefault": false,
  "temperature": 0.3,
  "maxTokens": 1024,
  "contextWindow": 4096,
  "connectionStatus": "SUCCESS"
}
```

不返回 `apiKey`、`apiKeyCipher` 或实际 Base URL 中的敏感参数。

### 管理接口（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/models?page=&size=&keyword=&modelType=&enabled=` | 分页 |
| GET | `/api/admin/models/providers` | 供应商列表 |
| POST | `/api/admin/models` | 新增 |
| PUT | `/api/admin/models/{id}` | 编辑 |
| DELETE | `/api/admin/models/{id}` | 逻辑删除 |
| PUT | `/api/admin/models/{id}/status` | 启用/停用 |
| POST | `/api/admin/models/{id}/test` | 真实连通性测试 |

新增/编辑请求：

```json
{
  "providerId": 4,
  "displayName": "本地 Qwen2.5 0.5B",
  "modelName": "qwen2.5:0.5b",
  "modelType": "TEXT",
  "apiBaseUrl": null,
  "apiKey": "可选；编辑时留空表示不修改",
  "clearApiKey": false,
  "temperature": 0.3,
  "maxTokens": 1024,
  "contextWindow": 4096,
  "embeddingDimension": null,
  "capabilities": ["TEXT"],
  "inputPricePer1k": null,
  "outputPricePer1k": null,
  "currency": null,
  "enabled": true,
  "isDefault": false
}
```

说明：

- `modelType`：`TEXT | VISION | EMBEDDING | ASR`。
- Embedding 必须提供 `embeddingDimension`，且与后续向量表维度一致。
- 输入/输出单价必须同时配置或同时留空；留空时费用显示“未知”。
- API Key 使用 AES-GCM 加密保存，响应仅返回 `apiKeyMask` 与 `hasApiKey`。
- 供应商允许不要求 Key（Ollama）时，`requiresApiKey=false`。

连通性测试响应：

```json
{
  "code": 0,
  "data": {
    "success": true,
    "status": "SUCCESS",
    "message": "连接成功 · 模型响应：pong",
    "latencyMs": 1302,
    "modelName": "qwen2.5:0.5b",
    "promptTokens": 34,
    "completionTokens": 2,
    "testedAt": "2026-11-03T15:47:55.644"
  }
}
```

- 未配置 Key 时返回 `success=false`、`status=NOT_CONFIGURED`，并发真实请求。
- 测试记录会写入 `ai_model_call_log`，`data_source=REAL`。

---

## 5. AI 对话

### GET `/api/chat/conversations?page=1&size=30&keyword=`

仅返回当前用户会话。

### POST `/api/chat/conversations`

```json
{ "modelId": 4, "title": "可选标题" }
```

### PATCH `/api/chat/conversations/{id}`

```json
{ "title": "新的会话标题" }
```

### DELETE `/api/chat/conversations/{id}`

删除当前用户会话及消息；同时清理 Redis 短期记忆。

### GET `/api/chat/conversations/{id}/messages`

返回用户/助手消息，包含：

- `status`: `STREAMING | COMPLETED | FAILED`
- `promptTokens / completionTokens / totalTokens`
- `tokenEstimated`
- `latencyMs`
- `attachments`: 文件元数据数组（不含物理路径）

### POST `/api/chat/stream`（SSE）

请求：

```json
{
  "conversationId": 123,
  "modelId": 4,
  "content": "请解释一下 RAG",
  "fileIds": [12, 13]
}
```

- `conversationId` 可省略；省略时由服务端自动创建会话。
- 消息必须非空，最多 20000 字符。
- 附件最多 10 个，且必须属于当前用户。
- 图片只能使用具备 `VISION` 能力的模型；否则返回 400。

响应：

```text
event:meta
data:{"conversationId":123,"title":"请解释一下 RAG","userMessageId":1,"assistantMessageId":2,"modelId":4,"modelName":"本地 Qwen2.5 0.5B","traceId":"..."}

event:delta
data:{"content":"RAG"}

event:delta
data:{"content":" 是检索增强生成……"}

event:usage
data:{"promptTokens":120,"completionTokens":86,"totalTokens":206,"estimated":false,"latencyMs":980}

event:done
data:{"assistantMessageId":2,"status":"COMPLETED"}
```

错误事件：

```text
event:error
data:{"code":50011,"message":"模型调用失败：...","traceId":"..."}
```

- 事件名：`meta / delta / usage / done / error`。
- 供应商未返回 `TokenUsage` 时，使用估算并标记 `estimated=true`。
- 前端使用 `fetch + ReadableStream` 解析，因为浏览器 `EventSource` 不支持自定义 Authorization Header 与 POST 请求。
- Nginx 对 `/api/chat/stream` 单独关闭 `proxy_buffering`。

---

## 6. 文件资产

支持类型：`txt / md / markdown / csv / json / log / yaml / yml / png / jpg / jpeg / webp / pdf / docx / xlsx / mp3 / wav / m4a / webm / ogg / flac`。

### POST `/api/files`

`multipart/form-data`，字段名 `file`。

响应：

```json
{
  "id": 2,
  "originalName": "attachment-demo.txt",
  "fileCategory": "TEXT",
  "extension": "txt",
  "mimeType": "text/plain",
  "sizeBytes": 115,
  "status": "READY",
  "textPreview": "企业 AI 平台演示附件……",
  "createdAt": "2026-11-03T15:35:30.268"
}
```

- 图片 15MB、文档 20MB、音频 25MB 上限。
- 扩展名白名单 + 图片/音频/文档魔数校验 + UTF-8 文本校验。
- 存储名为随机 UUID，原路径不对外暴露。
- 图片不支持对应 VISION 模型时不会进入 SSE 调用；RAG/多模态按各自能力路由。
- PDF/DOCX/XLSX 由 RAG 或多模态模块解析；音频由 ASR 适配器处理。

### GET `/api/files/{id}`

仅所有者可读，返回元数据。

### GET `/api/files/{id}/content`

仅所有者可读，返回文件内容；`Content-Disposition: inline`。

### DELETE `/api/files/{id}`

仅所有者可删除；同时删除物理文件。

---

## 7. 健康检查

### GET `/api/health`（公开）

```json
{
  "status": "UP",
  "application": "ai-platform",
  "timestamp": "2026-11-03T15:01:59.277+08:00"
}
```

Actuator：`GET /actuator/health`。

---

## 8. 数据来源与费用

- `ai_model_call_log.data_source`：
  - `REAL`：由真实模型请求写入。
  - `SIMULATED`：仅用于初始化演示监控，不得冒充真实调用。
- 费用仅在模型配置了输入/输出单价且供应商返回 Token 时计算。
- 未配置价格或 Token 不完整时 `estimated_cost=NULL`，监控页面显示“未知”。
- `estimated=true` 只表示 Token 用量为估算值，不表示费用真实。

---

## 9. Agent 管理（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/agents?page=&size=&keyword=&enabled=&modelId=` | Agent 分页 |
| GET | `/api/admin/agents/{id}` | 详情 |
| POST | `/api/admin/agents` | 创建，生成 v1 |
| PUT | `/api/admin/agents/{id}` | 编辑，生成新版本 |
| DELETE | `/api/admin/agents/{id}` | 逻辑删除 |
| PUT | `/api/admin/agents/{id}/status` | 启用/停用 |
| GET | `/api/admin/agents/{id}/versions` | 版本历史 |
| POST | `/api/admin/agents/{id}/debug` | 真实模型 + Tool 执行 |
| GET | `/api/admin/agents/{id}/executions` | 执行历史 |
| GET | `/api/admin/agent-executions/{id}` | 执行详情与步骤 |

创建/编辑：

```json
{
  "name": "数据分析助手",
  "description": "结合本地演示订单、库存、客户数据进行分析",
  "systemPrompt": "你是数据分析助手，必须调用工具获取业务数据。",
  "modelId": 1,
  "toolIds": [2, 3, 4, 5],
  "maxToolCalls": 6,
  "maxExecutionSeconds": 120,
  "maxCost": null,
  "enabled": true,
  "changeNote": "切换到本地模型验证 Tool Calling"
}
```

执行结果包含：

- `execution.status`：`SUCCESS / FAILED / LIMIT_EXCEEDED`
- `toolCallCount / modelCallCount`
- `promptTokens / completionTokens / totalTokens`
- `estimatedCost`：价格缺失时为 `null`
- `steps`：`MODEL_REQUEST / MODEL_RESPONSE / TOOL_REQUEST / TOOL_RESULT / ERROR`

限额在服务端强制执行；超限执行写入 `LIMIT_EXCEEDED`，不伪造成功回答。

---

## 10. Tool 管理（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/tools` | 分页与筛选 |
| GET | `/api/admin/tools/beans` | 已实现 `PlatformTool` Bean |
| GET | `/api/admin/tools/categories` | 分类 |
| POST | `/api/admin/tools` | 注册工具 |
| PUT | `/api/admin/tools/{id}` | 编辑元数据 |
| PUT | `/api/admin/tools/{id}/status` | 启用/停用 |
| POST | `/api/admin/tools/{id}/debug` | 真实 Java Bean 调用 |
| GET | `/api/admin/tools/executions` | 工具执行记录 |

调试请求：

```json
{
  "arguments": {
    "expression": "(1280*3+960)/4 + sqrt(16)"
  }
}
```

调试响应：

```json
{
  "logId": 9,
  "toolCode": "calculator",
  "success": true,
  "result": "{\"result\":\"1204\",\"dataSource\":\"REAL_CALCULATION\"}",
  "durationMs": 2
}
```

工具参数先经 JSON Schema 校验；校验失败或 Java 方法异常时 `success=false`，不会进入伪造成功分支。

---

## 11. Prompt 管理（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/prompts` | 分页 |
| GET | `/api/admin/prompts/categories` | 分类 |
| GET | `/api/admin/prompts/{id}` | 详情 |
| POST | `/api/admin/prompts` | 创建 v1 |
| PUT | `/api/admin/prompts/{id}` | 保存新版本（DRAFT） |
| GET | `/api/admin/prompts/{id}/versions` | 版本历史 |
| POST | `/api/admin/prompts/{id}/publish` | 发布当前版本 |
| POST | `/api/admin/prompts/{id}/rollback/{version}` | 回滚并生成新版本 |
| POST | `/api/admin/prompts/{id}/test` | 真实模型测试 |

保存请求：

```json
{
  "name": "客服问题回复",
  "description": "标准化、专业且克制的客服回复模板",
  "category": "客户服务",
  "content": "客户问题：{{customer_question}}\n客户等级：{{customer_level}}",
  "changeNote": "新增变量"
}
```

变量通过 `{{variable}}` 自动识别；测试请求：

```json
{
  "modelId": 4,
  "variables": {
    "customer_question": "我的订单什么时候发货？",
    "customer_level": "核心客户"
  }
}
```

测试返回渲染后 Prompt、模型输出、Token 与耗时；真实调用写入模型调用日志。

---

## 12. RAG 知识库

### 12.1 管理接口（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/rag/knowledge-bases` | 知识库列表 |
| GET | `/api/admin/rag/knowledge-bases/{id}` | 详情 |
| POST | `/api/admin/rag/knowledge-bases` | 创建 |
| PUT | `/api/admin/rag/knowledge-bases/{id}` | 编辑 |
| DELETE | `/api/admin/rag/knowledge-bases/{id}` | 删除知识库、文档、切片与向量 |
| GET | `/api/admin/rag/knowledge-bases/{id}/documents` | 文档列表 |
| POST | `/api/admin/rag/knowledge-bases/{id}/documents` | multipart 上传并向量化 |
| POST | `/api/admin/rag/knowledge-bases/{id}/demo-document` | 导入内置示例文档 |
| POST | `/api/admin/rag/documents/{id}/reprocess` | 重新解析/向量化 |
| DELETE | `/api/admin/rag/documents/{id}` | 删除文档、切片、向量 |
| GET | `/api/admin/rag/documents/{id}/chunks` | 切片预览 |

创建知识库：

```json
{
  "name": "企业产品知识库",
  "description": "产品资料",
  "embeddingModelId": 2,
  "chunkSize": 600,
  "chunkOverlap": 100,
  "topK": 5,
  "status": "ACTIVE"
}
```

上传支持 TXT/Markdown/PDF/DOCX/XLSX。文档状态：`PROCESSING / READY / FAILED`；失败原因落库并在页面展示。

### 12.2 问答接口（登录）

`GET /api/rag/knowledge-bases`：可用于问答的知识库。

`POST /api/rag/query`：

```json
{
  "knowledgeBaseIds": [1],
  "question": "标准版支持多少并发对话？专业版呢？",
  "modelId": 4,
  "topK": 5
}
```

响应：

```json
{
  "code": 0,
  "data": {
    "question": "标准版支持多少并发对话？专业版呢？",
    "answer": "标准版支持 20 个并发对话，专业版支持 100 个并发对话。",
    "modelName": "本地 Qwen2.5 0.5B",
    "sources": [
      {
        "knowledgeBaseId": 1,
        "knowledgeBaseName": "企业产品知识库",
        "documentId": 1,
        "documentName": "企业产品知识库示例.md",
        "chunkIndex": 0,
        "content": "……",
        "score": 0.6412
      }
    ],
    "promptTokens": 1061,
    "completionTokens": 20,
    "totalTokens": 1081,
    "tokenEstimated": false,
    "latencyMs": 9037,
    "hasContext": true
  }
}
```

未检索到内容时 `hasContext=false`，回答明确说明知识库未提供相关信息，并返回空 `sources`。

---

## 13. 多模态

### POST `/api/multimodal/tasks`（登录，multipart）

字段：

- `file`：图片/PDF/DOCX/XLSX/TXT/Markdown/音频
- `taskType`：`OCR | IMAGE_UNDERSTANDING | DOCUMENT_EXTRACT | DOCUMENT_SUMMARY | AUDIO_TRANSCRIBE`
- `modelId`：图片/音频必填；文档可选（省略时仅解析文本）
- `prompt`：可选补充指令

路由：

| 文件 | 处理器 | 必需能力 |
|---|---|---|
| 图片 | `VisionModelAdapter` + LangChain4j `ImageContent` | Vision |
| PDF/Word/Excel/文本 | PDFBox/POI 文本抽取；可选文本模型摘要/抽取 | TEXT/VISION（可选） |
| 音频 | `AsrModelAdapter` → `/audio/transcriptions` | ASR |

响应包含 `status`、`extractedText`、`resultText`、`modelName`、Token、耗时；失败时 `status=FAILED` 且 `errorMessage` 明确。

### GET `/api/multimodal/tasks?limit=20`（登录）

仅返回当前用户任务。

### GET `/api/multimodal/tasks/{id}`（登录）

任务详情，按所有者隔离。

---

## 14. 调用监控（ADMIN）

### GET `/api/admin/monitor/overview?days=7`

返回：

- `summary`：total/real/simulated、成功率、平均耗时、Token、已知费用、未知费用数量
- Agent/Tool/RAG/多模态执行计数
- `trend`：按天调用量/Token/成功率
- `types`：请求类型分布
- `models`：模型调用 Top 6
- `recentCalls`：最近 8 条

### GET `/api/admin/monitor/calls`

参数：`page / size / requestType / success / dataSource / keyword / days`。

响应中的每条日志包含 `dataSource`（`REAL`/`SIMULATED`）、`costKnown`、`estimatedCost`。费用未知时 `costKnown=false`，不返回猜测值。

---

## 15. 管理操作审计

管理端 `POST/PUT/PATCH/DELETE /api/admin/**` 请求由 `OperationAuditFilter` 写入 `ai_operation_audit`：

- 用户、模块、动作、资源类型/ID、HTTP 状态、耗时、IP、TraceId
- 不记录请求体，避免密码/密钥落入审计
- 审计写入失败不会阻断主请求

---

## 16. 地址识别（不落库）

地址识别只做「模型抽取 → 地址拆分 → 返回页面」，不写入订单表、不保存图片、不保存识别结果。
识别结果由调用方页面直接回填输入框；刷新页面即清空。

### POST `/api/address/extract`（登录）

```json
{
  "text": "张三 13800001234 浙江省杭州市西湖区文三路90号5楼",
  "modelId": null
}
```

- `modelId` 可省略；省略时自动选择可用的 TEXT/VISION 模型。
- 文本最多 20000 字符。
- 返回：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "receiverName": "张三",
    "phone": "13800001234",
    "province": "浙江省",
    "city": "杭州市",
    "district": "西湖区",
    "street": "文三路",
    "detailAddress": "90号5楼",
    "fullAddress": "浙江省杭州市西湖区文三路90号5楼",
    "confidence": 0.9600,
    "sourceType": "TEXT",
    "addressType": "DOMESTIC",
    "modelConfigId": 4,
    "modelName": "本地 Qwen2.5 0.5B",
    "promptTokens": 331,
    "completionTokens": 102,
    "totalTokens": 433,
    "tokenEstimated": false,
    "ocrUsed": false,
    "latencyMs": 2029
  }
}
```

国外地址返回示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "receiverName": "Cheryll Ann Gibson",
    "phone": "61238138871-3170",
    "email": "nweozk5jjt12074@gs.shipping.ter",
    "country": "Australia",
    "state": "QLD",
    "city": "BOROREN",
    "addressLine1": "46 Daisy Dell Road",
    "addressLine2": null,
    "postalCode": "4678",
    "fullAddress": "46 Daisy Dell Road, BOROREN, QLD, 4678, Australia",
    "addressType": "INTERNATIONAL",
    "orderNo": "CEDP990321333534CN",
    "sourceType": "IMAGE",
    "modelName": "Tesseract OCR",
    "ocrUsed": true
  }
}
```

- `addressType=DOMESTIC` 时使用 `province/city/district/street/detailAddress`。
- `addressType=INTERNATIONAL` 时使用 `country/state/city/addressLine1/addressLine2/postalCode`，不会强行填充中国省市区。
- `orderNo`、`buyerId`、`email` 为可选字段，仅用于页面回填，不落库。

### POST `/api/address/extract/image`（登录，multipart）

- `file`：PNG/JPG/WEBP，最大 15MB。
- `modelId`：可选；省略时自动选择可用的 VISION 模型。
- 图片只在内存中转为 Base64 发给模型，不写文件资产表。
- 处理顺序：先让视觉模型直接输出 JSON；若模型不支持中文 OCR 或未返回 JSON，则调用本地 Tesseract `chi_sim+eng` 识别文字，再交给文本模型抽取，响应中 `ocrUsed=true`。
- 支持整页订单详情截图：会识别 `Order details / Order ID`、`Recipient name`、`Phone number`、`Shipping address` 地址块、`Virtual email`，并结合标签解析、地址块解析和宽松正则兜底。
- Tesseract 通过 `ai-platform.ocr.*` 配置；未安装时跳过 OCR，保留视觉模型的明确错误。

### POST `/api/open/v1/address/extract`（ERP 服务端）

```http
POST /api/open/v1/address/extract
X-API-Key: <AI_PLATFORM_OPEN_API_KEY>
Content-Type: application/json
```

请求体与返回结构同 `/api/address/extract`。

### POST `/api/open/v1/address/extract/image`（ERP 服务端，multipart）

字段 `file`、`modelId` 同上，使用 `X-API-Key` 认证。

### 安全与限制

- `X-API-Key` 使用常量时间比较；缺失/错误返回 HTTP 401 + `40102`。
- 未启用开放接口返回 HTTP 503 + `50300`。
- 地址识别不新增 `ai_model_call_log`、订单表或地址表记录；测试断言调用前后数据库记录数不变。
- 返回的是文本地址字段，不包含省市区行政编码；ERP 如需编码，应在 ERP 侧或字典表做映射。
- 开放接口未内置限流、IP 白名单和 HMAC 签名；生产环境建议通过 API 网关增加这些能力。

### 图片 OCR 兜底配置

```yaml
ai-platform:
  ocr:
    enabled: true
    tesseract-command: tesseract
    tessdata-dir: ./data/tessdata
    languages: chi_sim+eng
    timeout-seconds: 60
```

- 需要安装 Tesseract 5，并准备 `chi_sim.traineddata`；本地可放在 `data/tessdata/`。
- Dockerfile 已安装 `tesseract-ocr` 与 `tesseract-ocr-chi-sim`。
- 若 Tesseract 不可用，图片识别只走视觉模型；不会回退到伪造数据。
