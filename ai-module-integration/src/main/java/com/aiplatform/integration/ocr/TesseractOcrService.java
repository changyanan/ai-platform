package com.aiplatform.integration.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Tesseract CLI 封装，作为视觉模型无法直接输出 JSON 时的本地 OCR 兜底。
 * 不调用外部网络、不保存图片；临时文件在请求结束后删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrService {

    private final OcrProperties properties;
    private volatile boolean resolved;
    private volatile String command;

    public boolean isAvailable() {
        return resolveCommand() != null;
    }

    public String extractText(byte[] imageBytes, String extension) {
        List<String> variants = extractTextVariants(imageBytes, extension);
        return variants.isEmpty() ? "" : variants.get(0);
    }

    /**
     * 返回两种 OCR 结果：灰度版和阈值版。
     * 阈值版能识别浅色/橙色文字，灰度版对中文标签更稳定；调用方可以择优解析。
     */
    public List<String> extractTextVariants(byte[] imageBytes, String extension) {
        String executable = resolveCommand();
        if (executable == null) {
            throw new IllegalStateException("Tesseract OCR 未安装或未启用");
        }
        Path temp = null;
        List<Path> preprocessed = new ArrayList<>();
        try {
            temp = Files.createTempFile("address-ocr-", "." + extension);
            Files.write(temp, imageBytes);
            preprocessed = preprocessVariants(imageBytes);
            if (preprocessed.isEmpty()) {
                preprocessed.add(temp);
            }
            List<String> results = new ArrayList<>();
            for (Path input : preprocessed) {
                String output = runTesseract(executable, input);
                if (StringUtils.hasText(output)) {
                    results.add(output);
                }
            }
            if (results.isEmpty()) {
                throw new IllegalStateException("Tesseract OCR 未识别到文字");
            }
            log.info("Tesseract OCR produced {} text variant(s)", results.size());
            return results;
        } catch (IOException ex) {
            throw new IllegalStateException("Tesseract OCR 执行失败：" + ex.getMessage(), ex);
        } finally {
            deleteQuietly(temp);
            for (Path path : preprocessed) {
                if (path != null && !path.equals(temp)) {
                    deleteQuietly(path);
                }
            }
        }
    }

    private String runTesseract(String executable, Path input) throws IOException {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(executable);
        commandLine.add(input.toAbsolutePath().toString());
        commandLine.add("stdout");
        commandLine.add("-l");
        commandLine.add(properties.getLanguages());
        commandLine.add("--psm");
        commandLine.add("6");
        Path tessdata = Path.of(properties.getTessdataDir()).toAbsolutePath().normalize();
        if (Files.isRegularFile(tessdata.resolve("chi_sim.traineddata"))) {
            commandLine.add("--tessdata-dir");
            commandLine.add(tessdata.toString());
        }
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try {
                process.getInputStream().transferTo(captured);
            } catch (IOException ex) {
                log.debug("Failed to read Tesseract output: {}", ex.getMessage());
            }
        }, "tesseract-output-reader");
        reader.setDaemon(true);
        reader.start();
        boolean finished;
        try {
            finished = process.waitFor(Math.max(properties.getTimeoutSeconds(), 5), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract OCR 被中断", ex);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract OCR 超时");
        }
        try {
            reader.join(2_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        String output = captured.toString(StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Tesseract OCR 退出码 " + process.exitValue());
        }
        return output.length() > 100_000 ? output.substring(0, 100_000) : output;
    }

    /**
     * 对截图类图片做灰度 + 放大，并额外生成阈值图，改善浅色文字识别。
     * 处理失败时返回空列表，调用方继续使用原图。
     */
    private List<Path> preprocessVariants(byte[] imageBytes) {
        List<Path> outputs = new ArrayList<>();
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                return outputs;
            }
            int scale = source.getWidth() < 1200 ? 5 : (source.getWidth() < 2400 ? 2 : 1);
            int width = Math.min(source.getWidth() * scale, 4000);
            int height = Math.max(1, (int) Math.round(
                    source.getHeight() * (width / (double) source.getWidth())));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();

            BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D grayGraphics = gray.createGraphics();
            grayGraphics.drawImage(scaled, 0, 0, null);
            grayGraphics.dispose();
            autocontrast(gray);

            Path normal = Files.createTempFile("address-ocr-gray-", ".png");
            ImageIO.write(gray, "png", normal.toFile());
            outputs.add(normal);

            BufferedImage threshold = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] sourceData = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
            byte[] targetData = ((DataBufferByte) threshold.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < sourceData.length; i++) {
                targetData[i] = (byte) ((sourceData[i] & 0xFF) < 210 ? 0 : 255);
            }
            Path binary = Files.createTempFile("address-ocr-bin-", ".png");
            ImageIO.write(threshold, "png", binary.toFile());
            outputs.add(binary);
            return outputs;
        } catch (Exception ex) {
            log.debug("OCR preprocessing skipped: {}", ex.getMessage());
            for (Path path : outputs) {
                deleteQuietly(path);
            }
            return List.of();
        }
    }

    private void autocontrast(BufferedImage image) {
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        int min = 255;
        int max = 0;
        for (byte value : data) {
            int pixel = value & 0xFF;
            min = Math.min(min, pixel);
            max = Math.max(max, pixel);
        }
        if (max <= min || (min == 0 && max == 255)) {
            return;
        }
        int range = max - min;
        for (int i = 0; i < data.length; i++) {
            int pixel = (data[i] & 0xFF) - min;
            data[i] = (byte) Math.max(0, Math.min(255, pixel * 255 / range));
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Failed to delete OCR temp file {}", path, ex);
        }
    }

    private String resolveCommand() {
        if (resolved) {
            return command;
        }
        synchronized (this) {
            if (resolved) {
                return command;
            }
            command = resolveCommandInternal();
            resolved = true;
            if (command == null) {
                log.warn("Tesseract OCR 不可用；图片识别将只依赖视觉模型");
            } else {
                log.info("Tesseract OCR command resolved: {}", command);
            }
            return command;
        }
    }

    private String resolveCommandInternal() {
        if (!properties.isEnabled()) {
            return null;
        }
        String configured = properties.getTesseractCommand();
        if (StringUtils.hasText(configured)) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().toString();
            }
            if (!configured.contains("/") && !configured.contains("\\")) {
                String located = locateOnPath(configured);
                if (located != null) {
                    return located;
                }
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> candidates = os.contains("win")
                ? List.of("C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe")
                : List.of("/usr/bin/tesseract", "/usr/local/bin/tesseract");
        for (String candidate : candidates) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private String locateOnPath(String name) {
        String path = System.getenv("PATH");
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? name + ".exe" : name;
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (!StringUtils.hasText(directory)) {
                continue;
            }
            Path candidate = Path.of(directory, executable);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }
}
