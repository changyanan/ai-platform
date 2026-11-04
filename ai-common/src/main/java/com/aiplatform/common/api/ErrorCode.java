package com.aiplatform.common.api;

/**
 * 业务错误码。HTTP 状态码单独由 BusinessException 携带。
 */
public enum ErrorCode {

    BAD_REQUEST(40000, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    TOKEN_INVALID(40101, "登录凭证无效"),
    OPEN_API_UNAUTHORIZED(40102, "开放接口认证失败"),
    FORBIDDEN(40300, "没有权限执行此操作"),
    PASSWORD_CHANGE_REQUIRED(40301, "首次登录必须修改初始密码"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源状态冲突"),
    FILE_TOO_LARGE(41300, "文件大小超出限制"),
    UNSUPPORTED_FILE_TYPE(41500, "不支持的文件类型"),
    RATE_LIMITED(42900, "请求过于频繁，请稍后重试"),
    INTERNAL_ERROR(50000, "服务器内部错误"),
    OPEN_API_DISABLED(50300, "开放接口未启用"),
    MODEL_UNAVAILABLE(50010, "模型未配置、未启用或连接失败"),
    AI_PROVIDER_ERROR(50011, "模型服务调用失败"),
    TOOL_EXECUTION_ERROR(50020, "工具执行失败"),
    RAG_UNAVAILABLE(50030, "知识库服务未就绪"),
    MULTIMODAL_UNAVAILABLE(50040, "多模态处理服务未就绪"),
    ADDRESS_EXTRACTION_FAILED(50050, "地址信息识别失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
