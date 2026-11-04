package com.aiplatform.integration.service;

import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.integration.ocr.TesseractOcrService;
import com.aiplatform.integration.order.AddressJsonParser;
import com.aiplatform.integration.order.AddressParser;
import com.aiplatform.integration.order.LabeledAddressExtractor;
import com.aiplatform.integration.vo.AddressExtractionVO;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelConfigService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.model.vo.ModelOptionVO;
import com.aiplatform.multimodal.config.UploadProperties;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 地址识别服务。
 *
 * <p>该服务只做“识别并返回”，不写入订单表、不保存图片、不保存识别结果。
 * 需要监控时由调用方自行记录；平台侧仅输出不含地址内容的运行日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressExtractionService {

    private static final int MAX_TEXT_CHARS = 20_000;

    private static final String PROMPT = """
            你是企业收货地址识别引擎。请从输入内容中提取收货信息，并严格输出一个 JSON 对象。
            不要输出 Markdown 代码块、解释、前后缀或任何额外文字。
            支持中国国内地址和国外地址，不要用中国省市区结构强行套用国外地址。

            JSON 结构如下：
            {
              "addressType": "DOMESTIC 或 INTERNATIONAL",
              "orderNo": "订单号，找不到填 null",
              "buyerId": "买家ID，找不到填 null",
              "receiverName": "收货人/买家姓名，找不到填 null",
              "phone": "联系电话，找不到填 null",
              "email": "邮箱，找不到填 null",
              "country": "国家/地区，找不到填 null",
              "state": "国外州/省，找不到填 null",
              "province": "中国省份/自治区/直辖市，找不到填 null",
              "city": "城市，找不到填 null",
              "district": "中国区/县/旗，找不到填 null",
              "street": "中国街道/镇/乡，找不到填 null",
              "addressLine1": "地址第一行，国内外都可用，找不到填 null",
              "addressLine2": "地址第二行/公寓/单元，找不到填 null",
              "detailAddress": "中国详细地址；国外可留空，找不到填 null",
              "postalCode": "邮编，找不到填 null",
              "doorNumber": "门牌号，找不到填 null",
              "fullAddress": "完整收货地址，找不到填 null",
              "confidence": 0.95
            }

            规则：
            1. 中国地址：拆分为 province、city、district、street、detailAddress。
            2. 国外地址：addressType=INTERNATIONAL，填写 country、state、city、addressLine1、addressLine2、postalCode；不要编造不存在的中国省市区。
            3. fullAddress 国外地址用英文逗号拼接：地址1, 地址2, 城市, 州/省, 邮编, 国家。
            4. 找不到的字段填 null，严禁编造订单号、姓名、电话、邮箱或地址。
            5. confidence 为 0 到 1 之间的小数，表示整体识别置信度。
            6. 只输出 JSON 对象本身。
            """;

    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ModelConfigService modelConfigService;
    private final AddressJsonParser addressJsonParser;
    private final LabeledAddressExtractor labeledAddressExtractor;
    private final TesseractOcrService tesseractOcrService;
    private final UploadProperties uploadProperties;

    public AddressExtractionVO extractText(String text, Long modelId) {
        if (!StringUtils.hasText(text)) {
            throw BusinessException.badRequest("请输入需要识别的地址文本");
        }
        String normalized = text.trim();
        if (normalized.length() > MAX_TEXT_CHARS) {
            throw BusinessException.badRequest("文本不能超过 " + MAX_TEXT_CHARS + " 个字符");
        }
        ModelRuntime runtime = resolveModel(modelId, false);
        String prompt = PROMPT + "\n\n待识别内容：\n" + normalized;
        return invoke(runtime, prompt, null, false, "TEXT", null, normalized, false);
    }

    public AddressExtractionVO extractImage(MultipartFile file, Long modelId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择需要识别的地址图片");
        }
        if (file.getSize() > uploadProperties.getMaxImageSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "图片大小超出限制，最大允许 " + uploadProperties.getMaxImageSize().toMegabytes() + "MB");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!List.of("png", "jpg", "jpeg", "webp").contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "仅支持 PNG/JPG/WEBP 图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败");
        }
        validateMagic(extension, bytes);
        ModelRuntime runtime;
        try {
            runtime = resolveModel(modelId, true);
        } catch (BusinessException modelError) {
            if (tesseractOcrService.isAvailable()) {
                return extractImageWithOcr(bytes, extension, modelError);
            }
            throw modelError;
        }
        String prompt = PROMPT + "\n\n图片中可能是订单截图、快递面单、手写收货信息或聊天记录。"
                + "请先识别图片中的文字，再按上述结构抽取。";
        try {
            return invoke(runtime, prompt, bytes, true, "IMAGE", mimeTypeOf(extension),
                    null, false);
        } catch (BusinessException visionError) {
            if (!tesseractOcrService.isAvailable()) {
                throw visionError;
            }
            return extractImageWithOcr(bytes, extension, visionError);
        }
    }

    /**
     * 视觉模型不能直接输出 JSON 时，用本地 Tesseract OCR 识别文字，
     * 再交给文本模型抽取；整个兜底过程仍不落库。
     */
    private AddressExtractionVO extractImageWithOcr(byte[] bytes, String extension,
                                                    BusinessException visionError) {
        long ocrStart = System.currentTimeMillis();
        List<String> ocrVariants;
        try {
            ocrVariants = tesseractOcrService.extractTextVariants(bytes, extension);
        } catch (Exception ocrError) {
            throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                    visionError.getMessage() + "；OCR 兜底失败：" + safeMessage(ocrError));
        }
        AddressJsonParser.ParsedAddress labeled = null;
        int bestScore = -1;
        for (String variant : ocrVariants) {
            AddressJsonParser.ParsedAddress parsed = labeledAddressExtractor.parse(variant);
            if (parsed == null) {
                continue;
            }
            int score = labeledScore(parsed);
            if (score > bestScore) {
                labeled = parsed;
                bestScore = score;
            }
        }
        if (labeled != null) {
            long latency = System.currentTimeMillis() - ocrStart;
            log.info("Address extraction succeeded via Tesseract OCR labels, {}ms", latency);
            return AddressExtractionVO.from(labeled, "IMAGE", null, "Tesseract OCR",
                    null, null, false, true, latency);
        }
        String ocrText = String.join("\n\n", ocrVariants);
        ModelRuntime textRuntime;
        try {
            textRuntime = resolveModel(null, false);
        } catch (BusinessException modelError) {
            throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                    visionError.getMessage() + "；OCR 已识别文字，但没有可用文本模型："
                            + modelError.getMessage());
        }
        String prompt = PROMPT + "\n\n以下文字来自图片 OCR，请只做字段抽取，不要改写原文：\n" + ocrText;
        try {
            return invoke(textRuntime, prompt, null, false, "IMAGE", null, ocrText, true);
        } catch (BusinessException ocrError) {
            throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                    visionError.getMessage() + "；OCR 兜底失败：" + ocrError.getMessage());
        }
    }

    private AddressExtractionVO invoke(ModelRuntime runtime, String prompt, byte[] imageBytes,
                                       boolean visionInput, String sourceType, String mimeType,
                                       String sourceText, boolean ocrUsed) {
        long start = System.currentTimeMillis();
        try {
            ChatModel model = visionInput
                    ? modelAdapterRegistry.visionModel(runtime)
                    : modelAdapterRegistry.chatModel(runtime);
            UserMessage message;
            if (visionInput) {
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                message = UserMessage.from(prompt,
                        ImageContent.from(base64, mimeType == null ? "image/jpeg" : mimeType,
                                ImageContent.DetailLevel.HIGH));
            } else {
                message = UserMessage.from(prompt);
            }
            ChatResponse response = model.chat(message);
            String output = response.aiMessage() == null ? "" : response.aiMessage().text();
            TokenUsage usage = response.tokenUsage();
            int promptTokens;
            int completionTokens;
            boolean estimated = false;
            if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                promptTokens = usage.inputTokenCount();
                completionTokens = usage.outputTokenCount();
            } else {
                promptTokens = TokenEstimator.estimate(prompt);
                completionTokens = TokenEstimator.estimate(output);
                estimated = true;
            }
            AddressJsonParser.ParsedAddress address;
            try {
                address = addressJsonParser.parse(output);
                if (StringUtils.hasText(sourceText)) {
                    address = mergeSourceAddress(address, sourceText);
                }
            } catch (Exception ex) {
                String parseError = "识别结果解析失败：" + safeMessage(ex);
                if (visionInput) {
                    parseError += "；请确认视觉模型支持中文 OCR 并能按提示输出 JSON";
                }
                throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED, parseError);
            }
            long latency = System.currentTimeMillis() - start;
            log.info("Address extraction succeeded via model {} ({}), {}ms",
                    runtime.displayName(), sourceType, latency);
            return AddressExtractionVO.from(address, sourceType, runtime.configId(),
                    runtime.displayName(), promptTokens, completionTokens, estimated,
                    ocrUsed, latency);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Address extraction failed via model {} ({}): {}",
                    runtime.displayName(), sourceType, safeMessage(ex));
            throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                    "地址识别失败：" + safeMessage(ex));
        }
    }

    /**
     * 文本输入时用原文做确定性兜底：模型偶尔会漏掉门牌号，
     * 但原文中的完整地址可以作为更可靠的事实来源。
     */
    private AddressJsonParser.ParsedAddress mergeSourceAddress(
            AddressJsonParser.ParsedAddress model, String sourceText) {
        if (!StringUtils.hasText(sourceText)) {
            return model;
        }
        AddressParser.AddressParts source = AddressParser.refine(
                AddressParser.parseFromText(sourceText));
        if (!StringUtils.hasText(source.fullAddress())
                || "INTERNATIONAL".equals(model.addressType())) {
            return model;
        }
        if (model.fullAddress() != null
                && model.fullAddress().length() >= source.fullAddress().length()) {
            return model;
        }
        return new AddressJsonParser.ParsedAddress(
                model.addressType(),
                model.orderNo(),
                model.buyerId(),
                model.receiverName(),
                model.phone(),
                model.email(),
                model.country(),
                model.state(),
                firstText(source.province(), model.province()),
                firstText(source.city(), model.city()),
                firstText(source.district(), model.district()),
                firstText(source.street(), model.street()),
                model.addressLine1(),
                model.addressLine2(),
                firstText(source.detailAddress(), model.detailAddress()),
                model.postalCode(),
                model.doorNumber(),
                source.fullAddress(),
                model.confidence()
        );
    }

    private int labeledScore(AddressJsonParser.ParsedAddress address) {
        int score = 0;
        score += StringUtils.hasText(address.receiverName()) ? 3 : 0;
        score += StringUtils.hasText(address.addressLine1()) ? 3 : 0;
        score += StringUtils.hasText(address.phone()) ? 2 : 0;
        score += StringUtils.hasText(address.email()) ? 2 : 0;
        score += StringUtils.hasText(address.orderNo()) ? 1 : 0;
        score += StringUtils.hasText(address.city()) ? 1 : 0;
        score += StringUtils.hasText(address.state()) ? 1 : 0;
        score += StringUtils.hasText(address.postalCode()) ? 1 : 0;
        score += StringUtils.hasText(address.country()) ? 1 : 0;
        return score;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private ModelRuntime resolveModel(Long modelId, boolean image) {
        if (modelId != null) {
            ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(modelId);
            validateCapability(runtime, image);
            return runtime;
        }
        List<ModelOptionVO> options = modelConfigService.options();
        Comparator<ModelOptionVO> comparator = Comparator
                .comparing(ModelOptionVO::isDefault).reversed()
                .thenComparing(ModelOptionVO::id);
        ModelOptionVO option = options.stream()
                .filter(ModelOptionVO::available)
                .filter(item -> supports(item, image))
                .sorted(comparator)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                        image ? "没有可用的视觉模型，请先在模型管理中配置 VISION 模型"
                                : "没有可用的文本模型，请先在模型管理中配置 TEXT 或 VISION 模型"));
        return modelRuntimeFactory.resolveForExecution(option.id());
    }

    private boolean supports(ModelOptionVO option, boolean image) {
        if (image) {
            return option.modelType() == ModelType.VISION
                    || (option.capabilities() != null && option.capabilities().contains("VISION"));
        }
        return option.modelType() == ModelType.TEXT || option.modelType() == ModelType.VISION;
    }

    private void validateCapability(ModelRuntime runtime, boolean image) {
        if (image) {
            if (runtime.modelType() != ModelType.VISION && !runtime.supports(ModelCapability.VISION)) {
                throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                        "图片地址识别必须选择声明 VISION 能力的视觉模型");
            }
        } else if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw new BusinessException(ErrorCode.ADDRESS_EXTRACTION_FAILED,
                    "文本地址识别需要 TEXT 或 VISION 模型");
        }
    }

    private String mimeTypeOf(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateMagic(String extension, byte[] content) {
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

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
