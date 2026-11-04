package com.aiplatform.multimodal.document;

import com.aiplatform.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsUtf8Text() {
        String text = extractor.extract("md", "# 标题\n\n这是一段中文内容。".getBytes(StandardCharsets.UTF_8));
        assertThat(text).contains("标题", "中文内容");
    }

    @Test
    void extractsDocx() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("供应商准入要求：提供审计报告。");
            document.createParagraph().createRun().setText("交付计划需在五个工作日内提交。");
            document.write(output);
            bytes = output.toByteArray();
        }
        String text = extractor.extract("docx", bytes);
        assertThat(text).contains("供应商准入", "五个工作日");
    }

    @Test
    void extractsXlsx() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("服务等级");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("可用性");
            row.createCell(1).setCellValue("99.9%");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        String text = extractor.extract("xlsx", bytes);
        assertThat(text).contains("服务等级", "可用性", "99.9%");
    }

    @Test
    void extractsPdfTextLayer() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Supplier delivery requirements");
                content.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }
        String text = extractor.extract("pdf", bytes);
        assertThat(text).contains("Supplier delivery");
    }

    @Test
    void rejectsUnsupportedExtensionAndEmptyContent() {
        assertThatThrownBy(() -> extractor.extract("exe", new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不支持");
        assertThatThrownBy(() -> extractor.extract("pdf", new byte[0]))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("为空");
    }
}
