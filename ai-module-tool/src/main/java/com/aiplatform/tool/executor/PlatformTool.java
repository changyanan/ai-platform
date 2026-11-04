package com.aiplatform.tool.executor;

import java.util.Map;

/**
 * 平台内置/扩展 Java 工具契约。
 * 工具实现只做真实业务，不感知模型；参数已经由 ToolArgumentValidator 校验。
 */
public interface PlatformTool {

    String execute(Map<String, Object> arguments);
}
