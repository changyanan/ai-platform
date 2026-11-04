package com.aiplatform.agent.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent")
public class Agent extends BaseEntity {

    private String name;
    private String description;
    private String systemPrompt;
    private Long modelId;
    private String toolIds;
    private Integer maxToolCalls;
    private Integer maxExecutionSeconds;
    private BigDecimal maxCost;
    private Boolean enabled;
    private Boolean isPreset;
    private Integer currentVersion;
    private Long createdBy;

    @TableLogic
    private Boolean deleted;
}
