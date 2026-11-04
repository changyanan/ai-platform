package com.aiplatform.rag.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_base")
public class KnowledgeBase extends BaseEntity {

    private String name;
    private String description;
    private Long embeddingModelId;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer topK;
    private String status;
    private Boolean isPreset;
    private Long createdBy;

    @TableLogic
    private Boolean deleted;
}
