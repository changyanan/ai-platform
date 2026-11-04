package com.aiplatform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_step")
public class AgentStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Integer stepIndex;
    private String stepType;
    private String title;
    private String content;
    private String toolCode;
    private String argumentsJson;
    private String toolResult;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long durationMs;
    private Boolean success;
    private LocalDateTime createdAt;
}
