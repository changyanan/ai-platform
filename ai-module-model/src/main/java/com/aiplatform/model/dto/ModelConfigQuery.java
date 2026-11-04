package com.aiplatform.model.dto;

import com.aiplatform.common.api.PageQuery;
import com.aiplatform.model.enums.ModelType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModelConfigQuery extends PageQuery {

    private String keyword;
    private ModelType modelType;
    private Boolean enabled;
    private Long providerId;
}
