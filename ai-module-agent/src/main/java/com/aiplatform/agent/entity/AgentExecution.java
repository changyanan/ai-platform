package com.aiplatform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_execution")
public class AgentExecution {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentName;
    private Integer agentVersion;
    private String modelName;
    private Long userId;
    private String inputText;
    private String outputText;
    private String status;
    private Integer toolCallCount;
    private Integer modelCallCount;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean tokenEstimated;
    private BigDecimal estimatedCost;
    private Long latencyMs;
    private String errorMessage;
    private String traceId;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
