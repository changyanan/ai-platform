package com.aiplatform.agent.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentQuery extends PageQuery {

    private String keyword;
    private Boolean enabled;
    private Long modelId;
}
