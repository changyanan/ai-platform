package com.aiplatform.agent.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.tool.executor.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将数据库中的 JSON Schema 转换为 LangChain4j ToolSpecification。
 * 这是 Tool 元数据与模型工具调用协议之间的唯一转换点。
 */
@Component
@RequiredArgsConstructor
public class ToolSchemaConverter {

    private final ObjectMapper objectMapper;

    public ToolSpecification toSpecification(ToolDefinition definition) {
        try {
            JsonNode schema = objectMapper.readTree(definition.parametersSchema());
            JsonObjectSchema parameters = objectSchema(schema);
            return ToolSpecification.builder()
                    .name(definition.toolCode())
                    .description(definition.name() + "：" + definition.description())
                    .parameters(parameters)
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("工具 " + definition.toolCode() + " 的 Schema 无法转换：" + ex.getMessage());
        }
    }

    private JsonObjectSchema objectSchema(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw BusinessException.badRequest("工具参数 Schema 必须是 object");
        }
        if (!"object".equals(text(node, "type"))) {
            throw BusinessException.badRequest("工具参数 Schema 根类型必须是 object");
        }
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        String description = text(node, "description");
        if (description != null) {
            builder.description(description);
        }
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            var fields = properties.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                builder.addProperty(entry.getKey(), element(entry.getValue()));
            }
        }
        JsonNode required = node.get("required");
        if (required != null && required.isArray()) {
            List<String> names = new ArrayList<>();
            required.forEach(item -> names.add(item.asText()));
            builder.required(names);
        }
        JsonNode additional = node.get("additionalProperties");
        if (additional != null && additional.isBoolean()) {
            builder.additionalProperties(additional.asBoolean());
        }
        return builder.build();
    }

    private JsonSchemaElement element(JsonNode node) {
        if (node == null || !node.isObject()) {
            return JsonStringSchema.builder().build();
        }
        String type = text(node, "type");
        String description = text(node, "description");
        if (node.has("enum") && node.get("enum").isArray()) {
            List<String> values = new ArrayList<>();
            node.get("enum").forEach(value -> values.add(value.asText()));
            JsonEnumSchema.Builder builder = JsonEnumSchema.builder().enumValues(values);
            if (description != null) {
                builder.description(description);
            }
            return builder.build();
        }
        if (type == null) {
            type = "string";
        }
        return switch (type) {
            case "integer" -> {
                JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
                if (description != null) {
                    builder.description(description);
                }
                yield builder.build();
            }
            case "number" -> {
                JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
                if (description != null) {
                    builder.description(description);
                }
                yield builder.build();
            }
            case "boolean" -> {
                JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
                if (description != null) {
                    builder.description(description);
                }
                yield builder.build();
            }
            case "array" -> {
                JsonArraySchema.Builder builder = JsonArraySchema.builder();
                if (description != null) {
                    builder.description(description);
                }
                builder.items(element(node.get("items")));
                yield builder.build();
            }
            case "object" -> objectSchema(node);
            default -> {
                JsonStringSchema.Builder builder = JsonStringSchema.builder();
                if (description != null) {
                    builder.description(description);
                }
                yield builder.build();
            }
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
