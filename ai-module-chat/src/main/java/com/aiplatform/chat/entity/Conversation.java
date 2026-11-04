package com.aiplatform.chat.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class Conversation extends BaseEntity {

    private Long userId;
    private String title;
    private Long modelId;
    private String modelName;
    private Long agentId;
    private Integer messageCount;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
}
