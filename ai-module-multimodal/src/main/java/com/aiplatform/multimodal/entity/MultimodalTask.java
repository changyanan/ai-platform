package com.aiplatform.multimodal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_multimodal_task")
public class MultimodalTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileAssetId;
    private String fileName;
    private String fileCategory;
    private String taskType;
    private Long modelConfigId;
    private String modelName;
    private String status;
    private String extractedText;
    private String resultText;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean tokenEstimated;
    private Long latencyMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
