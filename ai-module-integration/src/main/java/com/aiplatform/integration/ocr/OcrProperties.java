package com.aiplatform.integration.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地 OCR 兜底配置。Tesseract 只用于图片文字识别，
 * 识别结果交给文本模型抽取，不写入数据库。
 */
@Data
@ConfigurationProperties(prefix = "ai-platform.ocr")
public class OcrProperties {

    private boolean enabled = true;
    private String tesseractCommand = "tesseract";
    private String tessdataDir = "./data/tessdata";
    private String languages = "chi_sim+eng";
    private int timeoutSeconds = 60;
}
