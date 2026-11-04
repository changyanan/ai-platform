package com.aiplatform.rag.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 面向中文企业文档的字符切片器：优先在段落/句号处断开，并保留重叠窗口。
 */
@Component
public class TextChunker {

    private static final int MAX_CHUNKS = 800;

    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int size = Math.max(100, chunkSize);
        int overlapSize = Math.max(0, Math.min(overlap, size / 2));
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + size, normalized.length());
            if (end < normalized.length()) {
                int boundary = findBoundary(normalized, start + (int) (size * 0.6), end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            int candidate = Math.max(start + 1, end - overlapSize);
            int nextStart = findStartBoundary(normalized, candidate, Math.min(end, candidate + 80));
            start = Math.max(start + 1, nextStart);
        }
        return chunks;
    }

    private int findStartBoundary(String text, int from, int max) {
        for (int i = from; i < max; i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                return i + 1;
            }
            if (ch == ' ' || ch == '，' || ch == '。' || ch == '；' || ch == '！' || ch == '？'
                    || ch == ',' || ch == ';' || ch == '!' || ch == '?') {
                return i + 1;
            }
        }
        return from;
    }

    private int findBoundary(String text, int min, int max) {
        int lower = Math.min(min, max);
        for (int i = max - 1; i >= lower; i--) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                return i + 1;
            }
            if (ch == '。' || ch == '！' || ch == '？' || ch == ';' || ch == '；') {
                return i + 1;
            }
            if (ch == '.' || ch == '!' || ch == '?') {
                return i + 1;
            }
        }
        return max;
    }
}
