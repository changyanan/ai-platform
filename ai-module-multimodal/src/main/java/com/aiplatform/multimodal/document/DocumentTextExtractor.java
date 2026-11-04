package com.aiplatform.multimodal.document;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文档文本抽取：TXT/Markdown → PDF → DOCX → XLSX。
 * 扫描版 PDF 没有文本层时明确失败，后续由多模态 OCR 处理，不伪造解析结果。
 */
@Component
public class DocumentTextExtractor {

    private static final int MAX_CHARS = 500_000;

    public String extract(String extension, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("文档内容为空");
        }
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String text = switch (ext) {
            case "txt", "md", "markdown", "csv", "json", "log", "yaml", "yml" -> decodeText(bytes);
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "xlsx" -> extractXlsx(bytes);
            default -> throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "暂不支持解析 ." + ext + " 文件");
        };
        text = normalize(text);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "文档中没有可用文字；扫描版 PDF 需要 OCR 处理");
        }
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        return text;
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "PDF 解析失败：" + safeMessage(ex));
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(builder, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder line = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (!line.isEmpty()) {
                                line.append(" | ");
                            }
                            line.append(cell.getText().replace('\n', ' ').trim());
                        }
                        appendParagraph(builder, line.toString());
                    }
                }
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Word 文档解析失败：" + safeMessage(ex));
        }
    }

    private String extractXlsx(byte[] bytes) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (Sheet sheet : workbook) {
                builder.append("【工作表：").append(sheet.getSheetName()).append("】\n");
                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount++ > 5000) {
                        builder.append("（表格行数过多，已截断）\n");
                        break;
                    }
                    StringBuilder line = new StringBuilder();
                    short lastCell = row.getLastCellNum();
                    for (int i = 0; i < Math.max(0, lastCell); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            if (!line.isEmpty()) {
                                line.append(" | ");
                            }
                            line.append(value);
                        }
                    }
                    appendParagraph(builder, line.toString());
                }
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Excel 文档解析失败：" + safeMessage(ex));
        }
    }

    private String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                return text.substring(1);
            }
            return text;
        } catch (CharacterCodingException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "仅支持 UTF-8 编码文本文件");
        }
    }

    private void appendParagraph(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        builder.append(text.trim()).append('\n');
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
