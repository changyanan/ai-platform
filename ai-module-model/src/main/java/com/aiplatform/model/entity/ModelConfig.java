package com.aiplatform.model.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class ModelConfig extends BaseEntity {

    private Long providerId;
    private String displayName;
    private String modelName;
    private String modelType;
    private String apiBaseUrl;
    private String apiKeyCipher;
    private String apiKeyMask;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer contextWindow;
    private Integer embeddingDimension;
    private String capabilities;

    @TableField("input_price_per_1k")
    private BigDecimal inputPricePer1k;

    @TableField("output_price_per_1k")
    private BigDecimal outputPricePer1k;
    private String currency;
    private Boolean enabled;
    private Boolean isDefault;

    @TableLogic
    private Boolean deleted;

    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
}
