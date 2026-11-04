package com.aiplatform.tool.util;

import com.aiplatform.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 安全的数学表达式求值器：只解析数字、运算符、括号与白名单函数，
 * 不加载脚本、不反射、不执行任意代码。
 */
public final class MathExpressionEvaluator {

    private MathExpressionEvaluator() {
    }

    public static BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw BusinessException.badRequest("计算表达式不能为空");
        }
        if (expression.length() > 500) {
            throw BusinessException.badRequest("计算表达式过长");
        }
        Parser parser = new Parser(expression);
        double value = parser.parse();
        if (!Double.isFinite(value)) {
            throw BusinessException.badRequest("计算结果不是有限数值");
        }
        return BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static final class Parser {

        private final String expression;
        private int position;

        private Parser(String expression) {
            this.expression = expression;
        }

        private double parse() {
            double result = parseExpression();
            skipWhitespace();
            if (position < expression.length()) {
                throw error("无法识别的字符：" + expression.charAt(position));
            }
            return result;
        }

        private double parseExpression() {
            double result = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    result += parseTerm();
                } else if (match('-')) {
                    result -= parseTerm();
                } else {
                    return result;
                }
            }
        }

        private double parseTerm() {
            double result = parseUnary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    result *= parseUnary();
                } else if (match('/')) {
                    double divisor = parseUnary();
                    if (divisor == 0) {
                        throw error("除数不能为 0");
                    }
                    result /= divisor;
                } else if (match('%')) {
                    double divisor = parseUnary();
                    if (divisor == 0) {
                        throw error("取模除数不能为 0");
                    }
                    result %= divisor;
                } else {
                    return result;
                }
            }
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return -parseUnary();
            }
            return parsePower();
        }

        private double parsePower() {
            double base = parsePrimary();
            skipWhitespace();
            if (match('^')) {
                double exponent = parseUnary();
                return Math.pow(base, exponent);
            }
            return base;
        }

        private double parsePrimary() {
            skipWhitespace();
            if (position >= expression.length()) {
                throw error("表达式不完整");
            }
            if (match('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw error("缺少右括号");
                }
                return value;
            }
            char current = expression.charAt(position);
            if (Character.isDigit(current) || current == '.') {
                return parseNumber();
            }
            if (Character.isLetter(current)) {
                return parseFunction();
            }
            throw error("无法识别的字符：" + current);
        }

        private double parseNumber() {
            int start = position;
            boolean hasDot = false;
            while (position < expression.length()) {
                char ch = expression.charAt(position);
                if (Character.isDigit(ch)) {
                    position++;
                } else if (ch == '.' && !hasDot) {
                    hasDot = true;
                    position++;
                } else {
                    break;
                }
            }
            String number = expression.substring(start, position);
            if (number.equals(".")) {
                throw error("数字格式不正确");
            }
            try {
                return Double.parseDouble(number);
            } catch (NumberFormatException ex) {
                throw error("数字格式不正确：" + number);
            }
        }

        private double parseFunction() {
            int start = position;
            while (position < expression.length()
                    && (Character.isLetterOrDigit(expression.charAt(position)) || expression.charAt(position) == '_')) {
                position++;
            }
            String name = expression.substring(start, position).toLowerCase(Locale.ROOT);
            skipWhitespace();
            if (!match('(')) {
                throw error("函数 " + name + " 缺少参数括号");
            }
            List<Double> arguments = new ArrayList<>();
            skipWhitespace();
            if (!match(')')) {
                arguments.add(parseExpression());
                while (match(',')) {
                    arguments.add(parseExpression());
                }
                skipWhitespace();
                if (!match(')')) {
                    throw error("函数 " + name + " 缺少右括号");
                }
            }
            return applyFunction(name, arguments);
        }

        private double applyFunction(String name, List<Double> args) {
            return switch (name) {
                case "sqrt" -> {
                    requireArgs(name, args, 1);
                    if (args.get(0) < 0) {
                        throw error("sqrt 参数不能为负数");
                    }
                    yield Math.sqrt(args.get(0));
                }
                case "abs" -> {
                    requireArgs(name, args, 1);
                    yield Math.abs(args.get(0));
                }
                case "round" -> {
                    requireArgs(name, args, 1);
                    yield Math.rint(args.get(0));
                }
                case "floor" -> {
                    requireArgs(name, args, 1);
                    yield Math.floor(args.get(0));
                }
                case "ceil" -> {
                    requireArgs(name, args, 1);
                    yield Math.ceil(args.get(0));
                }
                case "min" -> {
                    if (args.isEmpty()) {
                        throw error("min 至少需要一个参数");
                    }
                    yield args.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                }
                case "max" -> {
                    if (args.isEmpty()) {
                        throw error("max 至少需要一个参数");
                    }
                    yield args.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                }
                default -> throw error("不支持的函数：" + name);
            };
        }

        private void requireArgs(String name, List<Double> args, int count) {
            if (args.size() != count) {
                throw error(name + " 需要 " + count + " 个参数");
            }
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (position < expression.length() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }

        private BusinessException error(String message) {
            return BusinessException.badRequest("表达式错误：" + message);
        }
    }
}
