package com.aiplatform.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_demo_inventory")
public class DemoInventory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sku;
    private String productName;
    private String warehouse;
    private Integer quantity;
    private Integer safetyStock;
    private String unit;
    private LocalDateTime updatedAt;
}
