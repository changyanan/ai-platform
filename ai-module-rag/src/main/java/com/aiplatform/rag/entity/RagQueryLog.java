package com.aiplatform.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_rag_query_log")
public class RagQueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String knowledgeBaseIds;
    private String question;
    private String answer;
    private Long modelConfigId;
    private String modelName;
    private Integer topK;
    private Integer sourceCount;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean tokenEstimated;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private String traceId;
    private LocalDateTime createdAt;
}
