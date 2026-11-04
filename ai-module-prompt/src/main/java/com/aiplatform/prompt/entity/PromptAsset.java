package com.aiplatform.prompt.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt")
public class PromptAsset extends BaseEntity {

    private String name;
    private String description;
    private String category;
    private String content;
    private String variablesJson;
    private Integer currentVersion;
    private Integer publishedVersion;
    private String status;
    private Long createdBy;
}
