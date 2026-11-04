package com.aiplatform.multimodal.service.impl;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.multimodal.config.StorageProperties;
import com.aiplatform.multimodal.config.UploadProperties;
import com.aiplatform.multimodal.entity.FileAsset;
import com.aiplatform.multimodal.enums.FileCategory;
import com.aiplatform.multimodal.mapper.FileAssetMapper;
import com.aiplatform.multimodal.service.FileAssetService;
import com.aiplatform.multimodal.service.FileDownload;
import com.aiplatform.multimodal.vo.FileAssetVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文件资产服务：白名单校验 + 魔数校验 + 随机存储名 + 用户隔离。
 * 文本、图片、PDF/Word/Excel 均入库保存；具体解析由 RAG / 多模态模块负责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAssetServiceImpl implements FileAssetService {

    private static final long MAX_EXTRACTED_TEXT = 200_000L;
    private static final Map<String, FileCategory> ALLOWED_EXTENSIONS = new LinkedHashMap<>();
    private static final Map<String, String> MIME_TYPES = new LinkedHashMap<>();

    static {
        for (String ext : List.of("png", "jpg", "jpeg", "webp")) {
            ALLOWED_EXTENSIONS.put(ext, FileCategory.IMAGE);
        }
        for (String ext : List.of("txt", "md", "markdown", "csv", "json", "log", "yaml", "yml")) {
            ALLOWED_EXTENSIONS.put(ext, FileCategory.TEXT);
        }
        for (String ext : List.of("pdf", "docx", "xlsx")) {
            ALLOWED_EXTENSIONS.put(ext, FileCategory.DOCUMENT);
        }
        for (String ext : List.of("mp3", "wav", "m4a", "webm", "ogg", "flac")) {
            ALLOWED_EXTENSIONS.put(ext, FileCategory.AUDIO);
        }
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("txt", "text/plain");
        MIME_TYPES.put("log", "text/plain");
        MIME_TYPES.put("md", "text/markdown");
        MIME_TYPES.put("markdown", "text/markdown");
        MIME_TYPES.put("csv", "text/csv");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("yaml", "application/yaml");
        MIME_TYPES.put("yml", "application/yaml");
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("wav", "audio/wav");
        MIME_TYPES.put("m4a", "audio/mp4");
        MIME_TYPES.put("webm", "audio/webm");
        MIME_TYPES.put("ogg", "audio/ogg");
        MIME_TYPES.put("flac", "audio/flac");
    }

    private final FileAssetMapper fileAssetMapper;
    private final StorageProperties storageProperties;
    private final UploadProperties uploadProperties;

    private Path storageRoot;

    @PostConstruct
    void initStorage() {
        this.storageRoot = Path.of(storageProperties.getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建文件存储目录: " + storageRoot, ex);
        }
        log.info("File asset storage initialized at {}", storageRoot);
    }

    @Override
    public FileAssetVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的文件");
        }
        Long userId = SecurityUtils.currentUserId();
        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        FileCategory category = ALLOWED_EXTENSIONS.get(extension);
        if (category == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "支持 txt/md/csv/json 文本、png/jpg/webp 图片、pdf/docx/xlsx 文档与 mp3/wav/m4a 音频");
        }

        long sizeLimit = switch (category) {
            case IMAGE -> uploadProperties.getMaxImageSize().toBytes();
            case AUDIO -> uploadProperties.getMaxAudioSize().toBytes();
            default -> uploadProperties.getMaxDocumentSize().toBytes();
        };
        if (file.getSize() > sizeLimit) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小超出限制，最大允许 " + humanSize(sizeLimit));
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        if (content.length == 0) {
            throw BusinessException.badRequest("不能上传空文件");
        }

        validateMagic(category, extension, content);
        String extractedText = category == FileCategory.TEXT ? decodeText(content) : null;

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = String.format("user-%d/%d/%02d/%s", userId,
                LocalDate.now().getYear(), LocalDate.now().getMonthValue(), storedName);
        Path target = resolveSafe(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            log.error("Failed to save uploaded file", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存文件失败");
        }

        FileAsset asset = new FileAsset();
        asset.setUserId(userId);
        asset.setOriginalName(originalName);
        asset.setStoredName(storedName);
        asset.setFileCategory(category.name());
        asset.setExtension(extension);
        asset.setMimeType(MIME_TYPES.getOrDefault(extension, "application/octet-stream"));
        asset.setSizeBytes((long) content.length);
        asset.setSha256(sha256(content));
        asset.setStoragePath(relativePath);
        asset.setExtractedText(extractedText);
        asset.setStatus("READY");
        try {
            fileAssetMapper.insert(asset);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                log.warn("Failed to clean orphan file {}", target, cleanupError);
            }
            throw ex;
        }
        log.info("User {} uploaded file asset {} ({} bytes, {})", userId, asset.getId(),
                asset.getSizeBytes(), category);
        return FileAssetVO.from(fileAssetMapper.selectById(asset.getId()));
    }

    @Override
    public FileAssetVO getOwned(Long userId, Long fileId) {
        return FileAssetVO.from(requireOwned(userId, fileId));
    }

    @Override
    public List<FileAsset> requireOwnedEntities(Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = fileIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<FileAsset> assets = fileAssetMapper.selectBatchIds(distinctIds);
        if (assets.size() != distinctIds.size() || assets.stream().anyMatch(a -> !a.getUserId().equals(userId))) {
            throw BusinessException.badRequest("部分附件不存在或无权访问");
        }
        Map<Long, FileAsset> byId = new LinkedHashMap<>();
        assets.forEach(asset -> byId.put(asset.getId(), asset));
        List<FileAsset> ordered = new ArrayList<>(distinctIds.size());
        for (Long id : distinctIds) {
            ordered.add(byId.get(id));
        }
        return ordered;
    }

    @Override
    public byte[] readBytes(FileAsset asset) {
        Path target = resolveSafe(asset.getStoragePath());
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            log.warn("Failed to read file asset {}", asset.getId(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取附件内容失败");
        }
    }

    @Override
    public FileDownload download(Long userId, Long fileId) {
        FileAsset asset = requireOwned(userId, fileId);
        Path target = resolveSafe(asset.getStoragePath());
        if (!Files.isRegularFile(target)) {
            throw BusinessException.notFound("文件内容不存在");
        }
        return new FileDownload(new FileSystemResource(target), asset.getOriginalName(),
                asset.getMimeType(), asset.getSizeBytes());
    }

    @Override
    public void delete(Long fileId) {
        Long userId = SecurityUtils.currentUserId();
        FileAsset asset = requireOwned(userId, fileId);
        Path target = resolveSafe(asset.getStoragePath());
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete file asset {} from disk", fileId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "删除附件失败");
        }
        fileAssetMapper.deleteById(fileId);
    }

    private FileAsset requireOwned(Long userId, Long fileId) {
        FileAsset asset = fileAssetMapper.selectOne(Wrappers.<FileAsset>lambdaQuery()
                .eq(FileAsset::getId, fileId)
                .eq(FileAsset::getUserId, userId)
                .last("LIMIT 1"));
        if (asset == null) {
            throw BusinessException.notFound("文件不存在或无权访问");
        }
        return asset;
    }

    private Path resolveSafe(String relativePath) {
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw BusinessException.badRequest("非法文件路径");
        }
        return target;
    }

    private String sanitizeOriginalName(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw BusinessException.badRequest("文件名不能为空");
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        base = base.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (!StringUtils.hasText(base) || base.equals(".") || base.equals("..")) {
            throw BusinessException.badRequest("文件名不合法");
        }
        if (base.length() > 180) {
            String ext = extensionOf(base);
            base = base.substring(0, 160) + (ext.isEmpty() ? "" : "." + ext);
        }
        return base;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateMagic(FileCategory category, String extension, byte[] content) {
        if (category == FileCategory.DOCUMENT) {
            boolean valid = switch (extension) {
                case "pdf" -> content.length >= 5
                        && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                        && content[3] == 'F' && content[4] == '-';
                case "docx", "xlsx" -> content.length >= 4
                        && content[0] == 'P' && content[1] == 'K'
                        && (content[2] == 3 || content[2] == 5) && (content[3] == 4 || content[3] == 6);
                default -> false;
            };
            if (!valid) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "文档内容与扩展名不匹配");
            }
            return;
        }
        if (category == FileCategory.AUDIO) {
            boolean valid = switch (extension) {
                case "wav" -> content.length >= 12
                        && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                        && content[8] == 'W' && content[9] == 'A' && content[10] == 'V' && content[11] == 'E';
                case "mp3" -> content.length >= 3
                        && ((content[0] == 'I' && content[1] == 'D' && content[2] == '3')
                        || ((content[0] & 0xFF) == 0xFF && ((content[1] & 0xE0) == 0xE0)));
                case "m4a" -> content.length >= 12
                        && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p';
                case "webm" -> content.length >= 4
                        && (content[0] & 0xFF) == 0x1A && (content[1] & 0xFF) == 0x45
                        && (content[2] & 0xFF) == 0xDF && (content[3] & 0xFF) == 0xA3;
                case "ogg" -> content.length >= 4
                        && content[0] == 'O' && content[1] == 'g' && content[2] == 'g' && content[3] == 'S';
                case "flac" -> content.length >= 4
                        && content[0] == 'f' && content[1] == 'L' && content[2] == 'a' && content[3] == 'C';
                default -> false;
            };
            if (!valid) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "音频内容与扩展名不匹配");
            }
            return;
        }
        if (category != FileCategory.IMAGE) {
            return;
        }
        boolean valid = switch (extension) {
            case "png" -> content.length >= 8
                    && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
            case "jpg", "jpeg" -> content.length >= 3
                    && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                    && (content[2] & 0xFF) == 0xFF;
            case "webp" -> content.length >= 12
                    && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "图片内容与扩展名不匹配");
        }
    }

    private String decodeText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "文本文件包含二进制内容");
            }
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            if (text.length() > MAX_EXTRACTED_TEXT) {
                text = text.substring(0, (int) MAX_EXTRACTED_TEXT);
            }
            return text;
        } catch (CharacterCodingException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "仅支持 UTF-8 编码文本文件");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算文件摘要", ex);
        }
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return (bytes / (1024 * 1024)) + " MB";
        }
        if (bytes >= 1024) {
            return (bytes / 1024) + " KB";
        }
        return bytes + " B";
    }
}
