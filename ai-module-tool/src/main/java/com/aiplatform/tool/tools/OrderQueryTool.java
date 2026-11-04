package com.aiplatform.tool.tools;

import com.aiplatform.tool.entity.DemoOrder;
import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.aiplatform.tool.mapper.DemoOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component("orderQueryTool")
@RequiredArgsConstructor
public class OrderQueryTool implements PlatformTool {

    private final DemoOrderMapper demoOrderMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String orderNo = ToolSupport.requireString(arguments, "orderNo", "订单号")
                .toUpperCase(Locale.ROOT);
        DemoOrder order = demoOrderMapper.selectOne(Wrappers.<DemoOrder>lambdaQuery()
                .eq(DemoOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", order != null);
        result.put("dataSource", "SIMULATED_LOCAL_DB");
        result.put("simulated", true);
        result.put("note", "本项目本地模拟订单数据，不连接 ERP");
        if (order != null) {
            result.put("orderNo", order.getOrderNo());
            result.put("customerName", order.getCustomerName());
            result.put("customerPhoneMasked", maskPhone(order.getCustomerPhone()));
            result.put("productName", order.getProductName());
            result.put("sku", order.getSku());
            result.put("quantity", order.getQuantity());
            result.put("amount", order.getAmount());
            result.put("status", order.getStatus());
            result.put("orderedAt", order.getOrderedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            result.put("deliveryAddress", order.getDeliveryAddress());
        } else {
            result.put("message", "未找到订单 " + orderNo + "，请不要编造订单信息");
        }
        return ToolSupport.toJson(objectMapper, result);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
