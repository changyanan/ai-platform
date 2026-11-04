-- 全新数据库的可复现示例会话。
-- demo 账号由启动后的 DemoAccountInitializer 创建，这里使用固定 id=2 作为演示用户约定；
-- 已有会话时整段迁移幂等跳过，不覆盖现场产生的真实对话。
INSERT INTO ai_conversation
(id, user_id, title, model_id, model_name, message_count, last_message_preview, last_message_at,
 created_at, updated_at)
SELECT 1000, 2, '企业 AI 平台示例对话', 1, 'DeepSeek Chat', 4,
       '企业 AI 平台提供模型接入、Agent、RAG 与多模态能力。',
       DATE_SUB(NOW(), INTERVAL 1 HOUR),
       DATE_SUB(NOW(), INTERVAL 2 HOUR),
       DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM ai_conversation WHERE user_id = 2);

INSERT INTO ai_message
(conversation_id, role, content, status, model_id, model_name, prompt_tokens, completion_tokens,
 total_tokens, token_estimated, latency_ms, created_at)
SELECT 1000, 'USER', '请介绍一下这个 AI 平台能做什么。', 'COMPLETED', 1, 'DeepSeek Chat', NULL, NULL,
       NULL, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)
WHERE EXISTS (SELECT 1 FROM ai_conversation WHERE id = 1000)
  AND NOT EXISTS (SELECT 1 FROM ai_message WHERE conversation_id = 1000);

INSERT INTO ai_message
(conversation_id, role, content, status, model_id, model_name, prompt_tokens, completion_tokens,
 total_tokens, token_estimated, latency_ms, created_at)
SELECT 1000, 'ASSISTANT',
       '企业 AI 平台提供模型接入、AI 对话、Agent 编排、真实 Tool 调用、Prompt 版本管理、RAG 知识库、多模态处理与调用监控。所有业务演示数据由平台自行维护，不连接 ERP。',
       'COMPLETED', 1, 'DeepSeek Chat', 36, 48, 84, 0, 1280,
       DATE_SUB(NOW(), INTERVAL 2 HOUR) + INTERVAL 2 SECOND
WHERE EXISTS (SELECT 1 FROM ai_conversation WHERE id = 1000)
  AND (SELECT COUNT(*) FROM ai_message WHERE conversation_id = 1000) = 1;

INSERT INTO ai_message
(conversation_id, role, content, status, model_id, model_name, prompt_tokens, completion_tokens,
 total_tokens, token_estimated, latency_ms, created_at)
SELECT 1000, 'USER', 'RAG 回答会带来源吗？', 'COMPLETED', 1, 'DeepSeek Chat', NULL, NULL,
       NULL, 0, NULL, DATE_SUB(NOW(), INTERVAL 90 MINUTE)
WHERE EXISTS (SELECT 1 FROM ai_conversation WHERE id = 1000)
  AND (SELECT COUNT(*) FROM ai_message WHERE conversation_id = 1000) = 2;

INSERT INTO ai_message
(conversation_id, role, content, status, model_id, model_name, prompt_tokens, completion_tokens,
 total_tokens, token_estimated, latency_ms, created_at)
SELECT 1000, 'ASSISTANT',
       '会。RAG 问答先检索真实知识库片段，再让模型基于片段回答，并返回文档名、片段序号、相似度和原文；没有检索到内容时会明确说明，不会编造来源。',
       'COMPLETED', 1, 'DeepSeek Chat', 42, 52, 94, 0, 960,
       DATE_SUB(NOW(), INTERVAL 90 MINUTE) + INTERVAL 2 SECOND
WHERE EXISTS (SELECT 1 FROM ai_conversation WHERE id = 1000)
  AND (SELECT COUNT(*) FROM ai_message WHERE conversation_id = 1000) = 3;
