package com.aiplatform.multimodal.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_file_asset")
public class FileAsset extends BaseEntity {

    private Long userId;
    private String originalName;
    private String storedName;
    private String fileCategory;
    private String extension;
    private String mimeType;
    private Long sizeBytes;
    private String sha256;
    private String storagePath;
    private String extractedText;
    private String status;
    private String errorMessage;
}
