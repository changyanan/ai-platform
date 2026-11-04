# 验收记录

## 1. 验证环境

| 项 | 值 |
|---|---|
| OS | Windows 10 x64 |
| JDK | Eclipse Temurin 21.0.13 |
| Maven | 3.9.2 |
| Node / pnpm | 24.15.0 / 12.3.4 |
| MySQL | 8.0.46 |
| Redis | 5.0.14.1（本地） |
| PostgreSQL + PGVector | 18.6 + 0.8.7（本机） |
| Ollama | 0.40.13 |
| 本地模型 | qwen2.5:0.5b、nomic-embed-text、moondream（均为本机验收补充，不属于迁移种子） |
| 浏览器 | agent-browser + Chrome（1440×960） |
| Docker | 当前机器未安装 Docker daemon |

后端：http://localhost:8080；前端：http://127.0.0.1:5173。

> 关键截图已复制到 `docs/screenshots/`；`.local/screens/` 保留本机完整截图。  
> 本文件记录历史验收快照；之后业务数据和页面模式会继续变化。当前生产模式已关闭 demo 账号并移除 DEMO 页面标识。

---

## 2. 自动化验证

### 2.1 后端

```bash
mvn -B -ntp test
```

结果：**30 个测试通过，0 失败**。

| 模块 | 用例数 | 覆盖 |
|---|---:|---|
| server | 11 | 认证 4、SSE 1、P2 管理/Agent/Prompt/Monitor 5、P4 ASR 1 |
| ai-module-model | 3 | API Key AES-GCM 往返、错误主密钥、短主密钥 |
| ai-module-tool | 8 | 数学解析器、JSON Schema 参数校验 |
| ai-module-rag | 3 | 文本切片、边界与重叠 |
| ai-module-multimodal | 5 | TXT、DOCX、XLSX、PDF 文本层解析与非法类型 |

重点覆盖：

1. **P2 Agent 真实工具调用**：本地 OpenAI-compatible stub 产生一次 `query_order` Tool Call；LangChain4j AI Services 执行真实 Java Bean，再生成最终回答。断言执行状态、工具次数、模型次数、Token、步骤、工具日志与模型调用日志。
2. **P2 限额**：工具持续请求且 `maxToolCalls=1` 时，执行状态为 `LIMIT_EXCEEDED`，错误信息明确。
3. **Prompt 版本**：创建 → 更新 → 发布 → 回滚 → 新版本号与变更说明可审计。
4. **RAG / 文档解析**：PDFBox/POI 真实解析 DOCX/XLSX/PDF；切片器遵守大小与重叠边界。
5. **ASR 路由**：WAV 上传 → 魔数校验 → OpenAI-compatible `/audio/transcriptions` 适配器 → 任务结果与 `ASR` 真实调用日志。
6. **监控**：种子中的 22 条 `SIMULATED` 调用可查询；费用未知数大于 0；趋势返回 7 天。
7. **SSE**：流式 `meta/delta/usage/done`、消息持久化、Redis ChatMemory、真实调用日志。

### 2.2 前端

```bash
cd ai-web
pnpm test
pnpm build
```

- 4 个前端单元测试通过。
- `vue-tsc` 类型检查通过。
- Vite 生产构建通过；P2 Agent/Tool/Prompt、P3 RAG、P4 多模态、P5 监控页面均成功分包构建。

---

## 3. 浏览器真实验收

### P0 认证与权限

- 登录、错误密码、强制改密、管理台布局正常。
- 未登录访问 `/users` 被路由守卫重定向。
- 用户管理真实新增/停用用户；服务端 40301/401 拦截生效。
- 截图：`.local/screens/p0-login.png`、`p0-change-password.png`、`p0-users.png`。

### P1 模型与 AI 对话

- 模型列表展示 3 个种子模型；本地 Ollama 模型真实连通。
- 真实 SSE 多轮对话、Markdown、代码块、Token、附件上下文正常。
- API Key 数据库密文以 `v1:` 开头，接口仅掩码。
- 截图：`.local/screens/p1-workbench-1440.png`、`p1-chat-multi-turn.png`、`p1-chat-with-file.png`、`p1-models-with-local.png`。

### P2 Agent / Tool / Prompt

- Agent 卡片展示模型、工具绑定、限额、版本；编辑生成新版本。
- 数据分析助手真实调用本地 Qwen，执行 `query_order`，步骤为：
  `模型请求 #1 → 模型响应（请求工具）→ 工具调用 → 工具返回 → 模型请求 #2 → 最终回答`。
- 结果包含真实 Token（示例 1265）、耗时、工具次数；执行详情可展开参数与结果。
- Tool 调试计算器返回 `1204`；订单工具返回本地模拟订单；文档检索工具在 RAG 就绪后返回 `RAG_VECTOR` 来源。
- Prompt 编辑器、自动变量、保存版本、发布、测试、版本历史、回滚均可用；真实模型测试返回渲染后 Prompt 与 Token。
- 截图：`p2-agents-1440.png`、`p2-agent-debug-result.png`、`p2-agent-history.png`、`p2-tool-debug-result.png`、`p2-tool-records.png`、`p2-prompts-1440.png`、`p2-prompt-test-result.png`、`p2-prompt-versions.png`。

### P3 RAG

- PostgreSQL 18.6 + PGVector 0.8.7 真实运行；`ai_chunk_embedding` 表已创建。
- 2 个知识库均绑定本地 Nomic Embedding（768 维）。
- 已导入 5 个文档（2 个内置示例、DOCX、XLSX、PDF），共 8 个切片。
- 示例问题“标准版支持多少并发对话？专业版呢？”返回：
  - 回答包含 20 / 100 并发；
  - 4 个来源，含文档名、片段号、余弦相似度与原文；
  - 真实 Token 与 10s 内耗时。
- 客服知识库问题“未发货订单如何退款？”返回退款政策与来源。
- Tool `search_documents` 已启用，返回 `dataSource=RAG_VECTOR`。
- 截图：`p3-rag-1440.png`、`p3-rag-answer2.png`。

### P4 多模态

- DOCX 文档摘要真实调用本地 Qwen，返回 5 条结构化摘要与 418 Token。
- XLSX 在“仅解析文本”模式下返回真实表格内容。
- 图片理解使用本地 Moondream：任务成功，记录 822 Token、28s；图片预览、任务历史、状态正常。
- 上传音频时若无 ASR 模型，任务明确失败；ASR 适配器已由集成测试通过本地 OpenAI-compatible 桩验证。
- 任务历史显示模型、Token、耗时、状态与失败原因。
- 截图：`p4-multimodal-1440.png`、`p4-multimodal-result.png`、`p4-image-result-1440.png`。

### P5 调用监控

- 最近 7 天窗口：50 次调用（30 REAL + 20 SIMULATED）、96% 成功率、30,482 Token、费用全部未知（未配置单价）；数据库中累计 52 条（30 REAL + 22 SIMULATED），较早的 2 条演示数据落在 7 天窗口外。
- 趋势图、调用类型环形图、模型分布图、平台资产/执行域统计均正常。
- 页面顶部明确提示“当前监控包含演示数据”，SIMULATED 行在表格中逐条标记。
- Agent 执行 3、Tool 执行 10、RAG 查询 3、多模态任务 6；模型 6（3 个种子 + 3 个本地验收模型）。
- 截图：`p5-monitor-final.png`。

---

## 4. 最终数据状态

| 数据 | 数量/状态 |
|---|---|
| 用户 | admin（启用、首次登录强制改密）、demo（保留但默认禁用）；QA 临时账号已清理 |
| 模型 | 3 个迁移种子（未配置 Key）+ 3 个本机 Ollama 验收模型 |
| Agent / Tool / Prompt | 5 / 6 / 5 |
| 知识库 / 文档 / 切片 | 2 / 5 / 8（PGVector 中 8 条向量） |
| 演示订单 / 库存 | 10 / 20 |
| 示例会话 | 1 个，归属 demo，含 6 条消息与附件；全新数据库由 V7 提供 4 条可复现示例消息 |
| 多模态任务 | 6（含失败样本，失败原因保留） |
| 调用日志 | 52（30 REAL + 22 SIMULATED） |
| 操作审计 | 管理端写操作写入 `ai_operation_audit` |

---

## 5. 关键证据

| 证据 | 位置/方式 |
|---|---|
| 后端测试 | `ai-platform-server/target/surefire-reports/` 及各模块 surefire 报告 |
| 前端测试 | `ai-web` Vitest 输出；生产构建 `ai-web/dist` |
| PGVector | PostgreSQL `ai_chunk_embedding`；页面切片数与数据库一致 |
| 真实调用日志 | `ai_model_call_log.data_source=REAL`；SIMULATED 数据有明确标记 |
| Agent 步骤 | `ai_agent_step`：模型请求/响应、工具请求/结果 |
| Tool 真实执行 | `ai_tool_execution_log`：计算器、订单、库存、文档检索 |
| Prompt 版本 | `ai_prompt_version`：不可变版本、发布/回滚审计 |
| 操作审计 | `ai_operation_audit`：管理端非 GET 操作，不保存请求体 |
| 费用未知 | 单价未配置时 `estimated_cost=NULL`，页面显示“未知” |

---

## 6. 已知限制

- 当前机器无 Docker daemon，镜像未实际构建；Compose/Nginx/PG 初始化已做配置校验。
- 本机 ASR 通过 OpenAI-compatible 协议桩完成端到端验证；生产需配置真实 ASR 供应商。
- 本地 Ollama 三个模型为验收补充，不属于迁移种子；客户环境可使用云 Key 或内网 Ollama。
- PGVector 使用不固定维度 `vector` 列以兼容多模型；生产建议按维度拆表并建 HNSW/IVFFlat 索引。
- SIMULATED 监控仅用于展示布局，不作为真实供应商调用或财务依据。
