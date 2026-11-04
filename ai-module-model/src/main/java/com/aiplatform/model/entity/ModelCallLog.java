package com.aiplatform.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_model_call_log")
public class ModelCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private Long modelConfigId;
    private String providerCode;
    private String modelName;
    private String requestType;
    private Long conversationId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private Boolean success;
    private Integer httpStatus;
    private String errorCode;
    private String errorMessage;
    private Boolean estimated;
    private String dataSource;
    private BigDecimal inputPriceSnapshot;
    private BigDecimal outputPriceSnapshot;
    private String currency;
    private BigDecimal estimatedCost;
    private String requestSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
