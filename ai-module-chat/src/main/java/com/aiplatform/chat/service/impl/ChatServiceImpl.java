package com.aiplatform.chat.service.impl;

import com.aiplatform.chat.config.ChatProperties;
import com.aiplatform.chat.dto.ChatStreamRequest;
import com.aiplatform.chat.dto.ConversationQuery;
import com.aiplatform.chat.dto.CreateConversationRequest;
import com.aiplatform.chat.entity.ChatMessage;
import com.aiplatform.chat.entity.Conversation;
import com.aiplatform.chat.enums.MessageRole;
import com.aiplatform.chat.enums.MessageStatus;
import com.aiplatform.chat.mapper.ChatMessageMapper;
import com.aiplatform.chat.mapper.ConversationMapper;
import com.aiplatform.chat.memory.ConversationMemoryService;
import com.aiplatform.chat.service.ChatService;
import com.aiplatform.chat.service.ChatStreamContext;
import com.aiplatform.chat.service.ChatTaskStateService;
import com.aiplatform.chat.vo.ChatMessageVO;
import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.chat.vo.ConversationVO;
import com.aiplatform.chat.vo.FileAttachmentVO;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.multimodal.entity.FileAsset;
import com.aiplatform.multimodal.enums.FileCategory;
import com.aiplatform.multimodal.service.FileAssetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_MESSAGE_QUERY = 500;
    private static final int TITLE_MAX_LENGTH = 36;
    private static final int PREVIEW_MAX_LENGTH = 240;

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final FileAssetService fileAssetService;
    private final ConversationMemoryService memoryService;
    private final ChatTaskStateService taskStateService;
    private final ModelCallLogService modelCallLogService;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<ConversationVO> conversations(ConversationQuery query) {
        Long userId = SecurityUtils.currentUserId();
        LambdaQueryWrapper<Conversation> wrapper = Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getLastMessageAt)
                .orderByDesc(Conversation::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Conversation::getTitle, keyword)
                    .or().like(Conversation::getLastMessagePreview, keyword));
        }
        Page<Conversation> page = conversationMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        List<ConversationVO> records = page.getRecords().stream().map(ConversationVO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ConversationVO createConversation(CreateConversationRequest request) {
        Long userId = SecurityUtils.currentUserId();
        ModelConfig config = modelRuntimeFactory.findConfig(request.modelId());
        if (config == null) {
            throw BusinessException.notFound("模型配置不存在");
        }
        ModelType type = ModelType.valueOf(config.getModelType());
        if (type != ModelType.TEXT && type != ModelType.VISION) {
            throw BusinessException.badRequest("只有文本或视觉模型可以用于对话");
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(request.title()) ? limit(request.title().trim(), 200) : "新对话");
        conversation.setModelId(config.getId());
        conversation.setModelName(config.getDisplayName());
        conversation.setMessageCount(0);
        conversationMapper.insert(conversation);
        return ConversationVO.from(conversationMapper.selectById(conversation.getId()));
    }

    @Override
    public ConversationVO renameConversation(Long id, String title) {
        Long userId = SecurityUtils.currentUserId();
        Conversation conversation = requireOwnedConversation(id, userId);
        if (!StringUtils.hasText(title)) {
            throw BusinessException.badRequest("会话标题不能为空");
        }
        conversationMapper.update(null, Wrappers.<Conversation>lambdaUpdate()
                .eq(Conversation::getId, conversation.getId())
                .eq(Conversation::getUserId, userId)
                .set(Conversation::getTitle, limit(title.trim(), 200)));
        return ConversationVO.from(conversationMapper.selectById(id));
    }

    @Override
    @Transactional
    public void deleteConversation(Long id) {
        Long userId = SecurityUtils.currentUserId();
        Conversation conversation = requireOwnedConversation(id, userId);
        chatMessageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
        memoryService.clear(conversation.getId());
    }

    @Override
    public List<ChatMessageVO> messages(Long conversationId) {
        Long userId = SecurityUtils.currentUserId();
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        List<ChatMessage> messages = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + MAX_MESSAGE_QUERY));
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    @Transactional
    public ChatStreamContext prepareStream(ChatStreamRequest request) {
        Long userId = SecurityUtils.currentUserId();
        String content = request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(content)) {
            throw BusinessException.badRequest("对话内容不能为空");
        }
        if (content.length() > chatProperties.getMaxInputChars()) {
            throw BusinessException.badRequest("单条消息不能超过 " + chatProperties.getMaxInputChars() + " 个字符");
        }

        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(request.modelId());
        if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw BusinessException.badRequest("当前模型不支持对话，请选择文本或视觉模型");
        }

        List<FileAsset> attachments = fileAssetService.requireOwnedEntities(userId, request.fileIds());
        boolean hasImage = attachments.stream()
                .anyMatch(asset -> FileCategory.IMAGE.name().equals(asset.getFileCategory()));
        if (hasImage && !runtime.supports(ModelCapability.VISION)) {
            throw BusinessException.badRequest("当前模型不支持图片理解，请选择带 VISION 能力的模型");
        }

        boolean newConversation = request.conversationId() == null;
        Conversation conversation;
        if (newConversation) {
            conversation = new Conversation();
            conversation.setUserId(userId);
            conversation.setTitle(deriveTitle(content));
            conversation.setModelId(runtime.configId());
            conversation.setModelName(runtime.displayName());
            conversation.setMessageCount(0);
            conversationMapper.insert(conversation);
        } else {
            conversation = requireOwnedConversation(request.conversationId(), userId);
        }

        List<ChatMessage> history = loadContextHistory(conversation.getId());
        ChatMemory memory = memoryService.loadMemory(conversation.getId(), history);
        String memoryUserText = composeUserText(content, attachments);

        List<FileAttachmentVO> attachmentSnapshots = attachments.stream()
                .map(FileAttachmentVO::from)
                .toList();
        String attachmentsJson = serializeAttachments(attachmentSnapshots);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setRole(MessageRole.USER.name());
        userMessage.setContent(content);
        userMessage.setStatus(MessageStatus.COMPLETED.name());
        userMessage.setModelId(runtime.configId());
        userMessage.setModelName(runtime.displayName());
        userMessage.setTokenEstimated(false);
        userMessage.setAttachmentsJson(attachmentsJson);
        chatMessageMapper.insert(userMessage);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole(MessageRole.ASSISTANT.name());
        assistantMessage.setContent("");
        assistantMessage.setStatus(MessageStatus.STREAMING.name());
        assistantMessage.setModelId(runtime.configId());
        assistantMessage.setModelName(runtime.displayName());
        assistantMessage.setTokenEstimated(false);
        chatMessageMapper.insert(assistantMessage);

        String title = shouldRetitle(conversation, content, newConversation)
                ? deriveTitle(content)
                : conversation.getTitle();
        String preview = limit(content.replaceAll("\\s+", " "), PREVIEW_MAX_LENGTH);
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversation.getId())
                .eq(Conversation::getUserId, userId)
                .set(Conversation::getModelId, runtime.configId())
                .set(Conversation::getModelName, runtime.displayName())
                .set(Conversation::getTitle, title)
                .set(Conversation::getLastMessagePreview, preview)
                .set(Conversation::getLastMessageAt, now)
                .setSql("message_count = message_count + 2"));

        memoryService.appendUser(memory, memoryUserText);

        return new ChatStreamContext(
                userId,
                conversation.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                title,
                content,
                memoryUserText,
                runtime,
                memory,
                attachments,
                MDC.get("traceId")
        );
    }

    @Override
    public void executeStream(ChatStreamContext context, SseEmitter emitter) {
        String previousTrace = MDC.get("traceId");
        MDC.put("traceId", context.traceId() == null ? "" : context.traceId());
        long startedAt = System.currentTimeMillis();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean clientClosed = new AtomicBoolean(false);
        StringBuilder partial = new StringBuilder();
        String promptEstimateText;
        List<dev.langchain4j.data.message.ChatMessage> promptMessages;
        try {
            promptMessages = buildPrompt(context);
            promptEstimateText = promptText(promptMessages);
        } catch (Exception ex) {
            log.warn("Failed to build prompt for conversation {}", context.conversationId(), ex);
            failBeforeModelCall(context, emitter, clientClosed, finished, "准备对话上下文失败：" + safeError(ex));
            restoreTrace(previousTrace);
            return;
        }

        if (!sendEvent(emitter, clientClosed, "meta", new SseMeta(
                context.conversationId(),
                context.conversationTitle(),
                context.userMessageId(),
                context.assistantMessageId(),
                context.runtime().configId(),
                context.runtime().displayName(),
                context.traceId()))) {
            handleClientGone(context, finished);
            safelyComplete(emitter);
            restoreTrace(previousTrace);
            return;
        }

        try {
            taskStateService.markStreaming(context.assistantMessageId());
            StreamingChatModel model = modelAdapterRegistry.streamingChatModel(context.runtime());
            ChatStreamHandler handler = new ChatStreamHandler(context, emitter, startedAt,
                    promptEstimateText, partial, finished, clientClosed);
            model.chat(promptMessages, handler);
        } catch (Exception ex) {
            if (finished.compareAndSet(false, true)) {
                log.warn("Synchronous streaming failure for conversation {}: {}",
                        context.conversationId(), safeError(ex));
                handleFailure(context, emitter, promptEstimateText, partial.toString(), startedAt,
                        ex, true, clientClosed);
                safelyComplete(emitter);
            }
        } finally {
            restoreTrace(previousTrace);
        }
    }

    private final class ChatStreamHandler implements StreamingChatResponseHandler {

        private final ChatStreamContext context;
        private final SseEmitter emitter;
        private final long startedAt;
        private final String promptEstimateText;
        private final StringBuilder partial;
        private final AtomicBoolean finished;
        private final AtomicBoolean clientClosed;

        private ChatStreamHandler(ChatStreamContext context, SseEmitter emitter, long startedAt,
                                  String promptEstimateText, StringBuilder partial,
                                  AtomicBoolean finished, AtomicBoolean clientClosed) {
            this.context = context;
            this.emitter = emitter;
            this.startedAt = startedAt;
            this.promptEstimateText = promptEstimateText;
            this.partial = partial;
            this.finished = finished;
            this.clientClosed = clientClosed;
        }

        @Override
        public void onPartialResponse(String token) {
            if (finished.get() || token == null) {
                return;
            }
            partial.append(token);
            if (!clientClosed.get()) {
                sendEvent(emitter, clientClosed, "delta", new SseDelta(token));
            }
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            String previousTrace = MDC.get("traceId");
            MDC.put("traceId", context.traceId() == null ? "" : context.traceId());
            try {
                handleSuccess(context, emitter, promptEstimateText, partial.toString(), response,
                        startedAt, clientClosed);
            } catch (Exception ex) {
                log.error("Failed to finish chat stream for conversation {}", context.conversationId(), ex);
                handleFailure(context, emitter, promptEstimateText, partial.toString(), startedAt,
                        ex, true, clientClosed);
            } finally {
                safelyComplete(emitter);
                restoreTrace(previousTrace);
            }
        }

        @Override
        public void onError(Throwable error) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            String previousTrace = MDC.get("traceId");
            MDC.put("traceId", context.traceId() == null ? "" : context.traceId());
            try {
                log.warn("Streaming model call failed for conversation {}: {}",
                        context.conversationId(), safeError(error));
                handleFailure(context, emitter, promptEstimateText, partial.toString(), startedAt,
                        error, true, clientClosed);
            } finally {
                safelyComplete(emitter);
                restoreTrace(previousTrace);
            }
        }
    }

    private void handleSuccess(ChatStreamContext context, SseEmitter emitter, String promptEstimateText,
                               String partialText, ChatResponse response, long startedAt,
                               AtomicBoolean clientClosed) {
        String finalText = response != null && response.aiMessage() != null
                && StringUtils.hasText(response.aiMessage().text())
                ? response.aiMessage().text()
                : partialText;
        if (finalText == null) {
            finalText = "";
        }

        TokenUsage usage = response == null ? null : response.tokenUsage();
        Integer promptTokens;
        Integer completionTokens;
        Integer totalTokens;
        boolean estimated;
        if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
            promptTokens = usage.inputTokenCount();
            completionTokens = usage.outputTokenCount();
            totalTokens = usage.totalTokenCount() != null
                    ? usage.totalTokenCount() : promptTokens + completionTokens;
            estimated = false;
        } else {
            promptTokens = TokenEstimator.estimate(promptEstimateText);
            completionTokens = TokenEstimator.estimate(finalText);
            totalTokens = promptTokens + completionTokens;
            estimated = true;
        }

        long latency = System.currentTimeMillis() - startedAt;
        updateAssistantMessage(context.assistantMessageId(), finalText, MessageStatus.COMPLETED,
                promptTokens, completionTokens, totalTokens, estimated, latency);
        updateConversationPreview(context.conversationId(), finalText, false);
        memoryService.appendAssistant(context.memory(), finalText);
        taskStateService.markCompleted(context.assistantMessageId());
        recordCallLog(context, true, promptTokens, completionTokens, totalTokens, latency,
                estimated, null);

        sendEvent(emitter, clientClosed, "usage", new SseUsage(
                promptTokens, completionTokens, totalTokens, estimated, latency));
        sendEvent(emitter, clientClosed, "done", new SseDone(
                context.assistantMessageId(), MessageStatus.COMPLETED.name()));
    }

    private void handleFailure(ChatStreamContext context, SseEmitter emitter, String promptEstimateText,
                               String partialText, long startedAt, Throwable error, boolean recordCall,
                               AtomicBoolean clientClosed) {
        String content = partialText == null ? "" : partialText;
        Integer promptTokens = TokenEstimator.estimate(promptEstimateText);
        Integer completionTokens = TokenEstimator.estimate(content);
        Integer totalTokens = promptTokens + completionTokens;
        long latency = Math.max(0, System.currentTimeMillis() - startedAt);
        String message = safeError(error);
        try {
            updateAssistantMessage(context.assistantMessageId(), content, MessageStatus.FAILED,
                    promptTokens, completionTokens, totalTokens, true, latency);
            updateConversationPreview(context.conversationId(),
                    StringUtils.hasText(content) ? content : "生成失败", true);
            taskStateService.markFailed(context.assistantMessageId());
        } catch (Exception persistenceError) {
            log.error("Failed to persist failed assistant message {}", context.assistantMessageId(),
                    persistenceError);
        }
        if (recordCall) {
            recordCallLog(context, false, promptTokens, completionTokens, totalTokens, latency,
                    true, message);
        }
        sendEvent(emitter, clientClosed, "error", new SseError(
                ErrorCode.AI_PROVIDER_ERROR.getCode(),
                "模型调用失败：" + message,
                context.traceId()));
    }

    private void failBeforeModelCall(ChatStreamContext context, SseEmitter emitter,
                                     AtomicBoolean clientClosed, AtomicBoolean finished, String message) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            updateAssistantMessage(context.assistantMessageId(), "", MessageStatus.FAILED,
                    0, 0, 0, true, 0);
            taskStateService.markFailed(context.assistantMessageId());
        } catch (Exception ex) {
            log.warn("Failed to mark assistant message failed: {}", ex.getMessage());
        }
        sendEvent(emitter, clientClosed, "error", new SseError(
                ErrorCode.AI_PROVIDER_ERROR.getCode(), message, context.traceId()));
        safelyComplete(emitter);
    }

    private void handleClientGone(ChatStreamContext context, AtomicBoolean finished) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            updateAssistantMessage(context.assistantMessageId(), "", MessageStatus.FAILED,
                    0, 0, 0, true, 0);
            updateConversationPreview(context.conversationId(), "连接已断开", true);
            taskStateService.markFailed(context.assistantMessageId());
        } catch (Exception ex) {
            log.warn("Failed to update disconnected chat state: {}", ex.getMessage());
        }
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildPrompt(ChatStreamContext context) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(chatProperties.getSystemPrompt()));

        List<dev.langchain4j.data.message.ChatMessage> memoryMessages =
                new ArrayList<>(context.memory().messages());
        // memory 中最后一条是本次用户输入（仅文本），实际请求会附加图片，避免重复。
        if (!memoryMessages.isEmpty()
                && memoryMessages.get(memoryMessages.size() - 1) instanceof UserMessage) {
            memoryMessages.remove(memoryMessages.size() - 1);
        }
        messages.addAll(memoryMessages);

        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(context.memoryUserText()));
        for (FileAsset asset : context.attachments()) {
            if (!FileCategory.IMAGE.name().equals(asset.getFileCategory())) {
                continue;
            }
            byte[] bytes = fileAssetService.readBytes(asset);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            contents.add(ImageContent.from(base64, asset.getMimeType()));
        }
        messages.add(UserMessage.from(contents));
        return messages;
    }

    private String promptText(List<dev.langchain4j.data.message.ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                builder.append(systemMessage.text()).append('\n');
            } else if (message instanceof UserMessage userMessage) {
                for (Content content : userMessage.contents()) {
                    if (content instanceof TextContent textContent) {
                        builder.append(textContent.text()).append('\n');
                    }
                }
            } else if (message instanceof AiMessage aiMessage && aiMessage.text() != null) {
                builder.append(aiMessage.text()).append('\n');
            }
        }
        return builder.toString();
    }

    private void updateAssistantMessage(Long messageId, String content, MessageStatus status,
                                        Integer promptTokens, Integer completionTokens,
                                        Integer totalTokens, boolean estimated, long latencyMs) {
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .set(ChatMessage::getContent, content == null ? "" : content)
                .set(ChatMessage::getStatus, status.name())
                .set(ChatMessage::getPromptTokens, promptTokens)
                .set(ChatMessage::getCompletionTokens, completionTokens)
                .set(ChatMessage::getTotalTokens, totalTokens)
                .set(ChatMessage::getTokenEstimated, estimated)
                .set(ChatMessage::getLatencyMs, latencyMs));
    }

    private void updateConversationPreview(Long conversationId, String preview, boolean failed) {
        String normalized = StringUtils.hasText(preview)
                ? preview.replaceAll("\\s+", " ").trim()
                : (failed ? "生成失败" : "新对话");
        conversationMapper.update(null, Wrappers.<Conversation>lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getLastMessagePreview, limit(normalized, PREVIEW_MAX_LENGTH))
                .set(Conversation::getLastMessageAt, LocalDateTime.now()));
    }

    private void recordCallLog(ChatStreamContext context, boolean success, Integer promptTokens,
                               Integer completionTokens, Integer totalTokens, long latency,
                               boolean estimated, String errorMessage) {
        ModelRuntime runtime = context.runtime();
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(context.traceId())
                .userId(context.userId())
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.CHAT.name())
                .conversationId(context.conversationId())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "CHAT_STREAM_FAILED")
                .errorMessage(errorMessage)
                .estimated(estimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("conversation #" + context.conversationId()
                        + " · prompt chars " + (context.userContent() == null ? 0 : context.userContent().length()))
                .build());
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        List<FileAttachmentVO> attachments = parseAttachments(message.getAttachmentsJson());
        return new ChatMessageVO(
                message.getId(),
                message.getConversationId(),
                message.getRole(),
                message.getContent(),
                message.getStatus(),
                message.getModelId(),
                message.getModelName(),
                message.getPromptTokens(),
                message.getCompletionTokens(),
                message.getTotalTokens(),
                Boolean.TRUE.equals(message.getTokenEstimated()),
                message.getLatencyMs(),
                attachments,
                message.getCreatedAt()
        );
    }

    private String serializeAttachments(List<FileAttachmentVO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件信息序列化失败");
        }
    }

    private List<FileAttachmentVO> parseAttachments(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse message attachments: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ChatMessage> loadContextHistory(Long conversationId) {
        int limit = Math.min(Math.max(chatProperties.getMaxContextMessages() * 2, 20), 100);
        List<ChatMessage> descending = chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));
        Collections.reverse(descending);
        return descending;
    }

    private Conversation requireOwnedConversation(Long id, Long userId) {
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getId, id)
                .eq(Conversation::getUserId, userId)
                .last("LIMIT 1"));
        if (conversation == null) {
            throw BusinessException.notFound("会话不存在或无权访问");
        }
        return conversation;
    }

    private String composeUserText(String content, List<FileAsset> attachments) {
        StringBuilder builder = new StringBuilder(content);
        int max = chatProperties.getMaxInputChars();
        for (FileAsset asset : attachments) {
            if (!FileCategory.TEXT.name().equals(asset.getFileCategory()) || !StringUtils.hasText(asset.getExtractedText())) {
                continue;
            }
            if (builder.length() >= max) {
                break;
            }
            String header = "\n\n---\n[附件：" + asset.getOriginalName() + "]\n";
            if (builder.length() + header.length() >= max) {
                break;
            }
            builder.append(header);
            int remaining = max - builder.length();
            String text = asset.getExtractedText();
            builder.append(text, 0, Math.min(text.length(), remaining));
        }
        if (builder.length() > max) {
            builder.setLength(max);
        }
        return builder.toString();
    }

    private boolean shouldRetitle(Conversation conversation, String content, boolean newConversation) {
        if (newConversation) {
            return true;
        }
        return !StringUtils.hasText(conversation.getTitle()) || "新对话".equals(conversation.getTitle());
    }

    private String deriveTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "新对话";
        }
        return normalized.length() > TITLE_MAX_LENGTH
                ? normalized.substring(0, TITLE_MAX_LENGTH) + "…"
                : normalized;
    }

    private String limit(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private boolean sendEvent(SseEmitter emitter, AtomicBoolean clientClosed, String name, Object data) {
        if (clientClosed.get()) {
            return false;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(name)
                        .data(data, MediaType.APPLICATION_JSON));
            }
            return true;
        } catch (Exception ex) {
            clientClosed.set(true);
            log.debug("SSE client disconnected while sending event {}: {}", name, ex.getMessage());
            return false;
        }
    }

    private void safelyComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("SSE emitter already completed: {}", ex.getMessage());
        }
    }

    private String safeError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return limit(message
                .replaceAll("(?i)(sk-|api[_-]?key[\"'=:\\s]+)[A-Za-z0-9_\\-]{6,}", "$1***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim(), 500);
    }

    private void restoreTrace(String previousTrace) {
        if (StringUtils.hasText(previousTrace)) {
            MDC.put("traceId", previousTrace);
        } else {
            MDC.remove("traceId");
        }
    }

    private record SseMeta(
            Long conversationId,
            String title,
            Long userMessageId,
            Long assistantMessageId,
            Long modelId,
            String modelName,
            String traceId
    ) {
    }

    private record SseDelta(String content) {
    }

    private record SseUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            boolean estimated,
            long latencyMs
    ) {
    }

    private record SseDone(Long assistantMessageId, String status) {
    }

    private record SseError(int code, String message, String traceId) {
    }
}
