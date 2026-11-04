package com.aiplatform.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_demo_customer")
public class DemoCustomer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerCode;
    private String name;
    private String phone;
    private String level;
    private String city;
    private Integer totalOrders;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}
