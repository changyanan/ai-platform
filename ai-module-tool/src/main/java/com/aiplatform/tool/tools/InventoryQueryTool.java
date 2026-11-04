package com.aiplatform.tool.tools;

import com.aiplatform.tool.entity.DemoInventory;
import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.aiplatform.tool.mapper.DemoInventoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("inventoryQueryTool")
@RequiredArgsConstructor
public class InventoryQueryTool implements PlatformTool {

    private final DemoInventoryMapper demoInventoryMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String keyword = ToolSupport.optionalString(arguments, "keyword");
        boolean onlyLowStock = ToolSupport.optionalBoolean(arguments, "onlyLowStock");

        LambdaQueryWrapper<DemoInventory> wrapper = Wrappers.<DemoInventory>lambdaQuery()
                .orderByAsc(DemoInventory::getQuantity)
                .last("LIMIT 20");
        if (keyword != null) {
            wrapper.and(w -> w.like(DemoInventory::getProductName, keyword)
                    .or().like(DemoInventory::getSku, keyword));
        }
        if (onlyLowStock) {
            wrapper.apply("quantity < safety_stock");
        }
        List<DemoInventory> rows = demoInventoryMapper.selectList(wrapper);
        List<Map<String, Object>> items = rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sku", row.getSku());
            item.put("productName", row.getProductName());
            item.put("warehouse", row.getWarehouse());
            item.put("quantity", row.getQuantity());
            item.put("safetyStock", row.getSafetyStock());
            item.put("unit", row.getUnit());
            item.put("lowStock", row.getQuantity() < row.getSafetyStock());
            item.put("updatedAt", row.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("keyword", keyword);
        result.put("onlyLowStock", onlyLowStock);
        result.put("items", items);
        result.put("dataSource", "SIMULATED_LOCAL_DB");
        result.put("simulated", true);
        result.put("note", "本项目本地模拟库存数据，不连接 ERP/WMS");
        return ToolSupport.toJson(objectMapper, result);
    }
}
