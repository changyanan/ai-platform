package com.aiplatform.model.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_provider")
public class ModelProvider extends BaseEntity {

    private String providerCode;
    private String providerName;
    private String protocol;
    private String defaultBaseUrl;
    private Boolean requiresApiKey;
    private String description;
    private Boolean enabled;
}
