package com.aiplatform.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptRequest(
        @NotBlank(message = "请输入 Prompt 名称")
        @Size(max = 100, message = "Prompt 名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "Prompt 描述不能超过 500 个字符")
        String description,

        @NotBlank(message = "请输入 Prompt 分类")
        @Size(max = 40, message = "分类不能超过 40 个字符")
        String category,

        @NotBlank(message = "请输入 Prompt 内容")
        @Size(max = 20000, message = "Prompt 内容不能超过 20000 个字符")
        String content,

        @Size(max = 500, message = "变更说明不能超过 500 个字符")
        String changeNote
) {
}
