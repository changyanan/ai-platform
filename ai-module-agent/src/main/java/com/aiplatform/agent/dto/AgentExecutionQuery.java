package com.aiplatform.agent.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentExecutionQuery extends PageQuery {

    private Long agentId;
    private String status;
}
