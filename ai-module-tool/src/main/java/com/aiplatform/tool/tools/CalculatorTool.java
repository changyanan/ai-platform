package com.aiplatform.tool.tools;

import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.aiplatform.tool.util.MathExpressionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("calculatorTool")
@RequiredArgsConstructor
public class CalculatorTool implements PlatformTool {

    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String expression = ToolSupport.requireString(arguments, "expression", "数学表达式");
        BigDecimal value = MathExpressionEvaluator.evaluate(expression);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expression", expression);
        result.put("result", value.toPlainString());
        result.put("dataSource", "REAL_CALCULATION");
        result.put("note", "由服务端表达式解析器真实计算，不执行任意代码");
        return ToolSupport.toJson(objectMapper, result);
    }
}
