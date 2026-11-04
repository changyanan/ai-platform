package com.aiplatform.prompt.dto;

import com.aiplatform.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PromptQuery extends PageQuery {

    private String keyword;
    private String category;
    private String status;
}
