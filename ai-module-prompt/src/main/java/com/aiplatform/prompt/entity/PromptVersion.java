package com.aiplatform.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_prompt_version")
public class PromptVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long promptId;
    private Integer version;
    private String content;
    private String variablesJson;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
