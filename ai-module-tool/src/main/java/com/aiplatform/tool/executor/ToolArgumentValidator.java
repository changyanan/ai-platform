package com.aiplatform.tool.executor;

import com.aiplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量级 JSON Schema 参数校验：只支持演示工具需要的对象、字符串、整数、
 * 数字、布尔、数组和枚举类型。非法参数不会进入任何 Java 工具方法。
 */
@Component
@RequiredArgsConstructor
public class ToolArgumentValidator {

    private final ObjectMapper objectMapper;

    public Map<String, Object> validateAndNormalize(String schemaJson, Map<String, Object> rawArguments) {
        JsonNode schema = parse(schemaJson, "参数 Schema");
        String rootType = text(schema, "type");
        if (!"object".equals(rootType)) {
            throw BusinessException.badRequest("工具参数 Schema 根类型必须是 object");
        }
        Map<String, Object> arguments = rawArguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawArguments);
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw BusinessException.badRequest("工具参数 Schema 缺少 properties 对象");
        }

        Set<String> allowed = new HashSet<>();
        properties.fieldNames().forEachRemaining(allowed::add);

        if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true)) {
            for (String key : arguments.keySet()) {
                if (!allowed.contains(key)) {
                    throw BusinessException.badRequest("不支持的工具参数：" + key);
                }
            }
        }

        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode field : required) {
                String name = field.asText();
                Object value = arguments.get(name);
                if (value == null || (value instanceof String s && !StringUtils.hasText(s))) {
                    throw BusinessException.badRequest("缺少必填工具参数：" + name);
                }
            }
        }

        var fields = properties.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (arguments.containsKey(entry.getKey()) && arguments.get(entry.getKey()) != null) {
                validateValue(entry.getKey(), entry.getValue(), arguments.get(entry.getKey()));
            }
        }
        return arguments;
    }

    public void validateSchema(String schemaJson) {
        JsonNode schema = parse(schemaJson, "参数 Schema");
        if (!"object".equals(text(schema, "type"))) {
            throw BusinessException.badRequest("参数 Schema 根类型必须是 object");
        }
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject()) {
            throw BusinessException.badRequest("参数 Schema 缺少 properties 对象");
        }
        JsonNode required = schema.get("required");
        if (required != null) {
            if (!required.isArray()) {
                throw BusinessException.badRequest("参数 Schema 的 required 必须是数组");
            }
            for (JsonNode item : required) {
                if (!item.isTextual() || !properties.has(item.asText())) {
                    throw BusinessException.badRequest("required 中声明了不存在的字段");
                }
            }
        }
        validatePropertySchemas(properties, "properties");
    }

    private void validatePropertySchemas(JsonNode properties, String path) {
        var fields = properties.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            validateSchemaNode(entry.getValue(), path + "." + entry.getKey());
        }
    }

    public void validateExample(String exampleJson) {
        if (!StringUtils.hasText(exampleJson)) {
            return;
        }
        parse(exampleJson, "示例参数");
    }

    private void validateSchemaNode(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                validateSchemaNode(item, path);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String type = text(node, "type");
        if (StringUtils.hasText(type) && !Set.of("object", "string", "integer", "number", "boolean", "array")
                .contains(type)) {
            throw BusinessException.badRequest("参数 Schema 存在不支持的类型：" + type);
        }
        if ("object".equals(type)) {
            JsonNode properties = node.get("properties");
            if (properties == null || !properties.isObject()) {
                throw BusinessException.badRequest("嵌套对象参数必须声明 properties");
            }
            validatePropertySchemas(properties, path + ".properties");
        }
        if ("array".equals(type)) {
            JsonNode items = node.get("items");
            if (items == null || !items.isObject()) {
                throw BusinessException.badRequest("数组参数必须声明 items");
            }
            validateSchemaNode(items, path + ".items");
        }
    }

    private void validateValue(String name, JsonNode schema, Object value) {
        String type = text(schema, "type");
        if (!StringUtils.hasText(type)) {
            return;
        }
        boolean valid = switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Number number && isIntegral(number);
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest("工具参数 " + name + " 类型必须为 " + type);
        }
        if ("string".equals(type)) {
            JsonNode enumValues = schema.get("enum");
            if (enumValues != null && enumValues.isArray()) {
                boolean matched = false;
                for (JsonNode allowed : enumValues) {
                    if (allowed.asText().equals(value)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    throw BusinessException.badRequest("工具参数 " + name + " 不在枚举范围内");
                }
            }
        }
        if ("array".equals(type) && schema.has("items") && value instanceof List<?> list) {
            for (Object item : list) {
                validateValue(name, schema.get("items"), item);
            }
        }
        if ("object".equals(type) && schema.has("properties") && value instanceof Map<?, ?> map) {
            JsonNode properties = schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) map;
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode field : required) {
                    if (!nested.containsKey(field.asText())) {
                        throw BusinessException.badRequest("嵌套参数 " + field.asText() + " 为必填");
                    }
                }
            }
            var fields = properties.fields();
            List<String> names = new ArrayList<>();
            while (fields.hasNext()) {
                var entry = fields.next();
                names.add(entry.getKey());
                if (nested.containsKey(entry.getKey()) && nested.get(entry.getKey()) != null) {
                    validateValue(entry.getKey(), entry.getValue(), nested.get(entry.getKey()));
                }
            }
            if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean(true)) {
                for (String key : nested.keySet()) {
                    if (!names.contains(key)) {
                        throw BusinessException.badRequest("不支持的嵌套参数：" + key);
                    }
                }
            }
        }
    }

    private boolean isIntegral(Number value) {
        try {
            return new BigDecimal(value.toString()).stripTrailingZeros().scale() <= 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private JsonNode parse(String json, String label) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw BusinessException.badRequest(label + "必须是 JSON 对象");
            }
            return node;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(label + "不是合法 JSON");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
