package com.aiplatform.multimodal.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.multimodal.service.FileAssetService;
import com.aiplatform.multimodal.service.FileDownload;
import com.aiplatform.multimodal.vo.FileAssetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Tag(name = "文件资产")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileAssetController {

    private final FileAssetService fileAssetService;

    @Operation(summary = "上传文件（用户隔离，内容与大小校验）")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileAssetVO> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(fileAssetService.upload(file));
    }

    @Operation(summary = "查看自己上传的文件元数据")
    @GetMapping("/{id}")
    public ApiResponse<FileAssetVO> detail(@PathVariable Long id) {
        return ApiResponse.success(fileAssetService.getOwned(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "下载/预览自己上传的文件")
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) {
        FileDownload download = fileAssetService.download(SecurityUtils.currentUserId(), id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.mimeType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.sizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @Operation(summary = "删除自己上传的文件")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileAssetService.delete(id);
        return ApiResponse.success();
    }
}
