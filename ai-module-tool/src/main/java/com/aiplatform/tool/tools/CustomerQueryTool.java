package com.aiplatform.tool.tools;

import com.aiplatform.tool.entity.DemoCustomer;
import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.aiplatform.tool.mapper.DemoCustomerMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("customerQueryTool")
@RequiredArgsConstructor
public class CustomerQueryTool implements PlatformTool {

    private final DemoCustomerMapper demoCustomerMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String keyword = ToolSupport.requireString(arguments, "keyword", "客户名称或手机号");
        List<DemoCustomer> customers = demoCustomerMapper.selectList(Wrappers.<DemoCustomer>lambdaQuery()
                .and(w -> w.like(DemoCustomer::getName, keyword)
                        .or().like(DemoCustomer::getPhone, keyword)
                        .or().like(DemoCustomer::getCustomerCode, keyword))
                .orderByDesc(DemoCustomer::getTotalAmount)
                .last("LIMIT 10"));

        List<Map<String, Object>> items = customers.stream().map(customer -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerCode", customer.getCustomerCode());
            item.put("name", customer.getName());
            item.put("phoneMasked", maskPhone(customer.getPhone()));
            item.put("level", customer.getLevel());
            item.put("city", customer.getCity());
            item.put("totalOrders", customer.getTotalOrders());
            item.put("totalAmount", customer.getTotalAmount());
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("items", items);
        result.put("dataSource", "SIMULATED_LOCAL_DB");
        result.put("simulated", true);
        result.put("note", "本项目本地模拟客户数据，不连接 CRM");
        return ToolSupport.toJson(objectMapper, result);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
