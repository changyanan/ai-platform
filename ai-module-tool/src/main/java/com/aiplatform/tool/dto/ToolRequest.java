package com.aiplatform.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ToolRequest(
        @NotBlank(message = "请输入工具编码")
        @Pattern(regexp = "^[a-z][a-z0-9_]{2,79}$",
                message = "工具编码需为小写字母开头的 3-80 位字母、数字或下划线")
        String toolCode,

        @NotBlank(message = "请输入工具名称")
        @Size(max = 100, message = "工具名称不能超过 100 个字符")
        String name,

        @NotBlank(message = "请输入工具描述")
        @Size(max = 1000, message = "工具描述不能超过 1000 个字符")
        String description,

        @NotBlank(message = "请输入参数 Schema")
        @Size(max = 20000, message = "参数 Schema 过长")
        String parametersSchema,

        @Size(max = 20000, message = "示例参数过长")
        String exampleArguments,

        @NotBlank(message = "请选择工具分类")
        @Size(max = 40, message = "分类不能超过 40 个字符")
        String category,

        @NotBlank(message = "请选择 Java 工具 Bean")
        @Size(max = 120, message = "Bean 名称过长")
        String beanName,

        Boolean enabled
) {
}
