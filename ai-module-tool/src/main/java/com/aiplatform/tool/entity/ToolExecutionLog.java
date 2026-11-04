package com.aiplatform.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_tool_execution_log")
public class ToolExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long toolId;
    private String toolCode;
    private Long userId;
    private Long agentExecutionId;
    private String argumentsJson;
    private String resultText;
    private Boolean success;
    private String errorMessage;
    private Long durationMs;
    private String traceId;
    private LocalDateTime createdAt;
}
