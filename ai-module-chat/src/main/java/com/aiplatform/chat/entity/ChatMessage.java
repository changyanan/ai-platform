package com.aiplatform.chat.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class ChatMessage extends BaseEntity {

    private Long conversationId;
    private String role;
    private String content;
    private String reasoningContent;
    private String status;
    private Long modelId;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean tokenEstimated;
    private Long latencyMs;
    private String attachmentsJson;
}
