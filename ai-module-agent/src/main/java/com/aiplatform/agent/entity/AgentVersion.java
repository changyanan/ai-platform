package com.aiplatform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_agent_version")
public class AgentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private Integer version;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelId;
    private String toolIds;
    private Integer maxToolCalls;
    private Integer maxExecutionSeconds;
    private BigDecimal maxCost;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
