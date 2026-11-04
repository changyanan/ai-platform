package com.aiplatform.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;
    private Long embeddingModelId;
    private Integer embeddingDim;
    private LocalDateTime createdAt;
}
