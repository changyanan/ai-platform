package com.aiplatform.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseRequest(
        @NotBlank(message = "请输入知识库名称")
        @Size(max = 120, message = "知识库名称不能超过 120 个字符")
        String name,

        @Size(max = 1000, message = "描述不能超过 1000 个字符")
        String description,

        @NotNull(message = "请选择 Embedding 模型")
        Long embeddingModelId,

        @NotNull(message = "请设置切片大小")
        @Min(value = 200, message = "切片不能小于 200 字符")
        @Max(value = 2000, message = "切片不能大于 2000 字符")
        Integer chunkSize,

        @NotNull(message = "请设置切片重叠")
        @Min(value = 0, message = "重叠不能为负数")
        @Max(value = 500, message = "重叠不能超过 500 字符")
        Integer chunkOverlap,

        @NotNull(message = "请设置召回数量")
        @Min(value = 1, message = "召回数量至少为 1")
        @Max(value = 20, message = "召回数量不能超过 20")
        Integer topK,

        String status
) {
}
