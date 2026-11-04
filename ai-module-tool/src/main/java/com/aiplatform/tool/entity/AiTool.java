package com.aiplatform.tool.entity;

import com.aiplatform.common.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_tool")
public class AiTool extends BaseEntity {

    private String toolCode;
    private String name;
    private String description;
    private String parametersSchema;
    private String exampleArguments;
    private String category;
    private String beanName;
    private Boolean enabled;
    private Boolean builtIn;
}
