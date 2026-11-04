package com.aiplatform.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_demo_order")
public class DemoOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String customerName;
    private String customerPhone;
    private String productName;
    private String sku;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    private LocalDateTime orderedAt;
    private String deliveryAddress;
    private LocalDateTime createdAt;
}
