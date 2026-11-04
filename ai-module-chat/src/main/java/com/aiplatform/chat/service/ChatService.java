package com.aiplatform.chat.service;

import com.aiplatform.chat.dto.ChatStreamRequest;
import com.aiplatform.chat.dto.ConversationQuery;
import com.aiplatform.chat.dto.CreateConversationRequest;
import com.aiplatform.chat.vo.ChatMessageVO;
import com.aiplatform.chat.vo.ConversationVO;
import com.aiplatform.common.api.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    PageResult<ConversationVO> conversations(ConversationQuery query);

    ConversationVO createConversation(CreateConversationRequest request);

    ConversationVO renameConversation(Long id, String title);

    void deleteConversation(Long id);

    List<ChatMessageVO> messages(Long conversationId);

    ChatStreamContext prepareStream(ChatStreamRequest request);

    void executeStream(ChatStreamContext context, SseEmitter emitter);
}
