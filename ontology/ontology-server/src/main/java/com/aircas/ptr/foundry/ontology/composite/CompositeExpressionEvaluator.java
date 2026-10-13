package com.aircas.ptr.foundry.ontology.composite;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 分支节点条件表达式求值。
 * <p>原型编排画布上的分支表达式是“端口名.字段 比较 字面量”形式的简单脚本表达式，
 * 后端没有 JS 引擎可用，这里实现最小求值器：按 {@code >= <= != == > <} 顺序找第一个比较运算符，
 * 左侧按点路径取值，右侧按字面量（单引号字符串、{@code true/false/null}、数字）解析，
 * 没有比较运算符时按真值判断。</p>
 * <p>任何解析异常都按“不成立”处理：分支条件写错时走 false 支路比抛异常更容易排查，
 * 且静态校验阶段已保证表达式非空。</p>
 */
public final class CompositeExpressionEvaluator {

    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** 长运算符在前，保证 {@code >=} 不会被识别成 {@code >} */
    private static final String[] OPERATORS = {">=", "<=", "!=", "==", ">", "<"};

    private CompositeExpressionEvaluator() {
    }

    /**
     * 求值，表达式为空或解析失败返回 false。
     *
     * @param expression 条件表达式
     * @param inputs     分支节点的入参表（键为入参名）
     */
    public static boolean evaluate(String expression, Map<String, Object> inputs) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            String text = expression.trim();
            for (String operator : OPERATORS) {
                int index = text.indexOf(operator);
                if (index > 0) {
                    Object left = resolve(text.substring(0, index).trim(), inputs);
                    Object right = literal(text.substring(index + operator.length()).trim(), inputs);
                    return compare(left, operator, right);
                }
            }
            return truthy(literal(text, inputs));
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 取表达式左操作数的根标识符，供分支节点把被判定的值透传到 true/false 输出端口。
     *
     * @param expression 条件表达式
     * @return 根标识符，无法解析时返回 null
     */
    public static String root(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        String text = expression.trim();
        for (String operator : OPERATORS) {
            int index = text.indexOf(operator);
            if (index > 0) {
                text = text.substring(0, index).trim();
                break;
            }
        }
        int dot = text.indexOf('.');
        return dot < 0 ? text : text.substring(0, dot);
    }

    /**
     * 按点路径取值，如 {@code value.confidence}；取不到返回 null。
     */
    public static Object resolve(String path, Map<String, Object> inputs) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String text = path.trim();
        if (isQuoted(text)) {
            return text.substring(1, text.length() - 1);
        }
        String[] segments = text.split("\\.");
        Object current = inputs == null ? null : inputs.get(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segments[i]);
        }
        return current;
    }

    private static Object literal(String token, Map<String, Object> inputs) {
        String text = token.trim();
        if (isQuoted(text)) {
            return text.substring(1, text.length() - 1);
        }
        if ("true".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text)) {
            return Boolean.FALSE;
        }
        if ("null".equals(text)) {
            return null;
        }
        if (isNumeric(text)) {
            return Double.parseDouble(text);
        }
        return resolve(text, inputs);
    }

    /**
     * 比较语义：{@code ==}/{@code !=} 宽松比较；其余比较任一值为 null 时不成立，
     * 两侧都是数字按数值比较，否则按字符串比较。
     */
    private static boolean compare(Object left, String operator, Object right) {
        if ("==".equals(operator)) {
            return looseEquals(left, right);
        }
        if ("!=".equals(operator)) {
            return !looseEquals(left, right);
        }
        if (left == null || right == null) {
            return false;
        }
        Double leftNumber = asNumber(left);
        Double rightNumber = asNumber(right);
        int comparison;
        if (leftNumber != null && rightNumber != null) {
            comparison = Double.compare(leftNumber, rightNumber);
        } else {
            comparison = String.valueOf(left).compareTo(String.valueOf(right));
        }
        return switch (operator) {
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            default -> false;
        };
    }

    /**
     * 宽松相等：null 只等于 null，数字按数值比较，其余按字符串比较。
     */
    private static boolean looseEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        Double leftNumber = asNumber(left);
        Double rightNumber = asNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return Double.compare(leftNumber, rightNumber) == 0;
        }
        if (Objects.equals(left, right)) {
            return true;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private static Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        return isNumeric(text) ? Double.parseDouble(text) : null;
    }

    private static boolean isNumeric(String text) {
        return NUMERIC.matcher(text).matches();
    }

    private static boolean isQuoted(String text) {
        return text.length() >= 2 && text.startsWith("'") && text.endsWith("'");
    }

    /**
     * 真值判断：null/false/空集合/0 为假，字符串 {@code "false"}、{@code "null"} 也为假。
     */
    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0d;
        }
        String text = String.valueOf(value);
        return !text.isEmpty() && !"false".equals(text) && !"null".equals(text);
    }
}
