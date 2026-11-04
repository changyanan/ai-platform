package com.aiplatform.tool.tools;

import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("weatherTool")
@RequiredArgsConstructor
public class WeatherTool implements PlatformTool {

    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String city = ToolSupport.requireString(arguments, "city", "城市");
        WeatherSample sample = sampleFor(city);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("condition", sample.condition);
        result.put("temperatureC", sample.temperature);
        result.put("humidityPercent", sample.humidity);
        result.put("wind", sample.wind);
        result.put("forecast", List.of(sample.condition, "夜间多云", "明日晴间多云"));
        result.put("dataSource", "SIMULATED");
        result.put("simulated", true);
        result.put("disclaimer", "本项目内置演示天气数据，不代表真实气象服务");
        result.put("observedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return ToolSupport.toJson(objectMapper, result);
    }

    private WeatherSample sampleFor(String city) {
        String normalized = city.toLowerCase();
        if (normalized.contains("上海")) {
            return new WeatherSample("多云", 24, 68, "东南风 3 级");
        }
        if (normalized.contains("北京")) {
            return new WeatherSample("晴", 21, 35, "西北风 2 级");
        }
        if (normalized.contains("深圳")) {
            return new WeatherSample("阵雨", 29, 82, "南风 3 级");
        }
        if (normalized.contains("杭州")) {
            return new WeatherSample("多云转阴", 23, 72, "东风 2 级");
        }
        return new WeatherSample("晴间多云", 23, 55, "微风");
    }

    private record WeatherSample(String condition, int temperature, int humidity, String wind) {
    }
}
