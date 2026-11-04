package com.aiplatform.rag.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    private Long knowledgeBaseId;
    private Long fileAssetId;
    private String fileName;
    private String extension;
    private String sourceType;
    private Long sizeBytes;
    private Integer charCount;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private String extractedText;
    private Long createdBy;
    private LocalDateTime processedAt;
}
