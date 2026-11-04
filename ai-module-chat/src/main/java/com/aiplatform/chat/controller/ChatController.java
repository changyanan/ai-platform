package com.aiplatform.chat.controller;

import com.aiplatform.chat.config.ChatProperties;
import com.aiplatform.chat.dto.ChatStreamRequest;
import com.aiplatform.chat.dto.ConversationQuery;
import com.aiplatform.chat.dto.CreateConversationRequest;
import com.aiplatform.chat.dto.RenameConversationRequest;
import com.aiplatform.chat.service.ChatService;
import com.aiplatform.chat.service.ChatStreamContext;
import com.aiplatform.chat.vo.ChatMessageVO;
import com.aiplatform.chat.vo.ConversationVO;
import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Tag(name = "AI 对话")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatProperties chatProperties;
    private final ExecutorService chatExecutor;

    public ChatController(ChatService chatService,
                          ChatProperties chatProperties,
                          @Qualifier("chatExecutor") ExecutorService chatExecutor) {
        this.chatService = chatService;
        this.chatProperties = chatProperties;
        this.chatExecutor = chatExecutor;
    }

    @Operation(summary = "我的会话分页")
    @GetMapping("/conversations")
    public ApiResponse<PageResult<ConversationVO>> conversations(@Valid ConversationQuery query) {
        return ApiResponse.success(chatService.conversations(query));
    }

    @Operation(summary = "新建空会话")
    @PostMapping("/conversations")
    public ApiResponse<ConversationVO> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success(chatService.createConversation(request));
    }

    @Operation(summary = "重命名会话")
    @PatchMapping("/conversations/{id}")
    public ApiResponse<ConversationVO> renameConversation(
            @PathVariable Long id,
            @Valid @RequestBody RenameConversationRequest request) {
        return ApiResponse.success(chatService.renameConversation(id, request.title()));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return ApiResponse.success();
    }

    @Operation(summary = "会话消息")
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return ApiResponse.success(chatService.messages(id));
    }

    @Operation(summary = "SSE 流式对话",
            description = "事件类型：meta / delta / usage / done / error。回答由真实模型产生。")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request,
                             HttpServletResponse response) {
        ChatStreamContext context = chatService.prepareStream(request);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(chatProperties.getSseTimeoutMs());
        emitter.onTimeout(() -> log.warn("SSE timeout for conversation {}", context.conversationId()));
        emitter.onError(error -> log.warn("SSE error for conversation {}: {}",
                context.conversationId(), error.getMessage()));
        emitter.onCompletion(() -> log.debug("SSE completed for conversation {}", context.conversationId()));
        try {
            chatExecutor.execute(() -> chatService.executeStream(context, emitter));
        } catch (RuntimeException ex) {
            log.error("Failed to submit chat stream task", ex);
            emitter.completeWithError(ex);
        }
        return emitter;
    }
}
