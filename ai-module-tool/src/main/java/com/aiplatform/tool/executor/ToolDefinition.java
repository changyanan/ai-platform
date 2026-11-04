package com.aiplatform.tool.executor;

/**
 * 提供给 Agent 构造 LangChain4j ToolSpecification 与绑定视图的工具定义。
 */
public record ToolDefinition(
        Long id,
        String toolCode,
        String name,
        String description,
        String parametersSchema,
        boolean enabled
) {
}
