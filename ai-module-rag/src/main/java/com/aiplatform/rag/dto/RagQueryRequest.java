package com.aiplatform.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RagQueryRequest(
        @NotEmpty(message = "请选择至少一个知识库")
        List<Long> knowledgeBaseIds,

        @NotBlank(message = "请输入问题")
        @Size(max = 5000, message = "问题不能超过 5000 个字符")
        String question,

        @NotNull(message = "请选择问答模型")
        Long modelId,

        @Min(value = 1, message = "召回数量至少为 1")
        @Max(value = 20, message = "召回数量不能超过 20")
        Integer topK
) {
}
