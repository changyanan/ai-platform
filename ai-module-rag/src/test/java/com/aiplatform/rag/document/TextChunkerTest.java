package com.aiplatform.rag.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void splitsLongTextAndKeepsChunkSizeBound() {
        String text = ("企业知识库要求回答必须标注来源。检索时使用余弦距离，并返回文档名、片段序号和原文。" +
                "系统不会伪造不存在的知识。").repeat(18);
        List<String> chunks = chunker.chunk(text, 300, 60);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk).isNotBlank();
            assertThat(chunk.length()).isLessThanOrEqualTo(300);
        });
    }

    @Test
    void handlesShortTextAndBlankText() {
        assertThat(chunker.chunk("只有一句话。", 300, 50)).containsExactly("只有一句话。");
        assertThat(chunker.chunk("  \n ", 300, 50)).isEmpty();
        assertThat(chunker.chunk(null, 300, 50)).isEmpty();
    }

    @Test
    void overlapIsBoundedAndDoesNotLoopForever() {
        String text = "第一段内容。".repeat(100);
        List<String> chunks = chunker.chunk(text, 200, 500);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(200));
    }
}
