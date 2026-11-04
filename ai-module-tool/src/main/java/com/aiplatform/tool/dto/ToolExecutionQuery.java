package com.aiplatform.tool.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ToolExecutionQuery extends PageQuery {

    private Long toolId;
    private Boolean success;
    private Long agentExecutionId;
}
