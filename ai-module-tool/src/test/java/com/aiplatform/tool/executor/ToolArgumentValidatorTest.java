package com.aiplatform.tool.executor;

import com.aiplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgumentValidatorTest {

    private final ToolArgumentValidator validator = new ToolArgumentValidator(new ObjectMapper());

    private static final String ORDER_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "orderNo": {"type": "string", "description": "订单号"}
              },
              "required": ["orderNo"],
              "additionalProperties": false
            }
            """;

    private static final String INVENTORY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "keyword": {"type": "string"},
                "onlyLowStock": {"type": "boolean"},
                "topK": {"type": "integer"}
              },
              "additionalProperties": false
            }
            """;

    @Test
    void acceptsRequiredString() {
        Map<String, Object> arguments = validator.validateAndNormalize(
                ORDER_SCHEMA, Map.of("orderNo", "SO20260001"));
        assertThat(arguments).containsEntry("orderNo", "SO20260001");
    }

    @Test
    void rejectsUnknownAndMissingArguments() {
        assertThatThrownBy(() -> validator.validateAndNormalize(
                ORDER_SCHEMA, Map.of("orderNo", "SO20260001", "sql", "DROP TABLE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的工具参数");

        assertThatThrownBy(() -> validator.validateAndNormalize(ORDER_SCHEMA, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少必填工具参数");
    }

    @Test
    void rejectsWrongTypesAndInvalidNumberRanges() {
        assertThatThrownBy(() -> validator.validateAndNormalize(
                ORDER_SCHEMA, Map.of("orderNo", 1001)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类型必须为 string");

        assertThatThrownBy(() -> validator.validateAndNormalize(
                INVENTORY_SCHEMA, Map.of("onlyLowStock", "true")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类型必须为 boolean");

        assertThatThrownBy(() -> validator.validateAndNormalize(
                INVENTORY_SCHEMA, Map.of("topK", 1.5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类型必须为 integer");
    }

    @Test
    void acceptsOptionalFieldsAndArrays() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "tags": {"type": "array", "items": {"type": "string"}},
                    "channel": {"type": "string", "enum": ["WEB", "APP"]}
                  },
                  "required": ["tags"],
                  "additionalProperties": false
                }
                """;
        Map<String, Object> arguments = validator.validateAndNormalize(
                schema, Map.of("tags", List.of("AI", "RAG"), "channel", "WEB"));
        assertThat(arguments).containsEntry("channel", "WEB");

        assertThatThrownBy(() -> validator.validateAndNormalize(
                schema, Map.of("tags", List.of("AI"), "channel", "OTHER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("枚举范围");
    }
}
