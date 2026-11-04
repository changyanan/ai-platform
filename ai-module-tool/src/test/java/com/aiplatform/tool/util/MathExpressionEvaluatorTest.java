package com.aiplatform.tool.util;

import com.aiplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MathExpressionEvaluatorTest {

    @Test
    void evaluatesOperatorPrecedenceAndParentheses() {
        assertThat(MathExpressionEvaluator.evaluate("1 + 2 * 3"))
                .isEqualByComparingTo(new BigDecimal("7"));
        assertThat(MathExpressionEvaluator.evaluate("(1280*3+960)/4"))
                .isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(MathExpressionEvaluator.evaluate("2 ^ 3 + 1"))
                .isEqualByComparingTo(new BigDecimal("9"));
    }

    @Test
    void evaluatesWhitelistedFunctions() {
        assertThat(MathExpressionEvaluator.evaluate("sqrt(16) + abs(-2)"))
                .isEqualByComparingTo(new BigDecimal("6"));
        assertThat(MathExpressionEvaluator.evaluate("max(3, 9, 5) - min(2, 8)"))
                .isEqualByComparingTo(new BigDecimal("7"));
        assertThat(MathExpressionEvaluator.evaluate("round(3.6) + floor(2.9) + ceil(1.1)"))
                .isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void rejectsDivisionByZero() {
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("10 / 0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("除数不能为 0");
    }

    @Test
    void rejectsCodeInjectionAndUnknownSyntax() {
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("1; DROP TABLE ai_tool"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("__import__('os').system('whoami')"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MathExpressionEvaluator.evaluate("Runtime.getRuntime()"))
                .isInstanceOf(BusinessException.class);
    }
}
