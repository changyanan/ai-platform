package com.aiplatform.multimodal.vo;

import com.aiplatform.multimodal.entity.FileAsset;

import java.time.LocalDateTime;

public record FileAssetVO(
        Long id,
        String originalName,
        String fileCategory,
        String extension,
        String mimeType,
        Long sizeBytes,
        String status,
        String textPreview,
        LocalDateTime createdAt
) {

    public static FileAssetVO from(FileAsset asset) {
        String preview = asset.getExtractedText();
        if (preview != null && preview.length() > 240) {
            preview = preview.substring(0, 240) + "…";
        }
        return new FileAssetVO(
                asset.getId(),
                asset.getOriginalName(),
                asset.getFileCategory(),
                asset.getExtension(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getStatus(),
                preview,
                asset.getCreatedAt()
        );
    }
}
