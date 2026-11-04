package com.aiplatform.chat.vo;

import com.aiplatform.multimodal.entity.FileAsset;

public record FileAttachmentVO(
        Long id,
        String originalName,
        String fileCategory,
        String mimeType,
        Long sizeBytes
) {

    public static FileAttachmentVO from(FileAsset asset) {
        return new FileAttachmentVO(
                asset.getId(),
                asset.getOriginalName(),
                asset.getFileCategory(),
                asset.getMimeType(),
                asset.getSizeBytes()
        );
    }
}
