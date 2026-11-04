package com.aiplatform.multimodal.service;

import com.aiplatform.multimodal.entity.FileAsset;
import com.aiplatform.multimodal.vo.FileAssetVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileAssetService {

    FileAssetVO upload(MultipartFile file);

    FileAssetVO getOwned(Long userId, Long fileId);

    List<FileAsset> requireOwnedEntities(Long userId, List<Long> fileIds);

    byte[] readBytes(FileAsset asset);

    FileDownload download(Long userId, Long fileId);

    void delete(Long fileId);
}
