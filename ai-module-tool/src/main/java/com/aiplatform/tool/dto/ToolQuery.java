package com.aiplatform.tool.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ToolQuery extends PageQuery {

    private String keyword;
    private String category;
    private Boolean enabled;
}
