package com.fs.api;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 简易 JSON 序列化/反序列化工具。
 * 仅支持本项目所需的基本类型：int、boolean、String、double、int[]、对象、数组。
 */
public class JsonUtil {

    /** 将对象序列化为 JSON 字符串。 */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return escapeString((String) obj);
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof int[]) return intArrayToJson((int[]) obj);
        if (obj instanceof Object[]) return arrayToJson((Object[]) obj);
        if (obj instanceof List) return listToJson((List<?>) obj);
        if (obj instanceof Map) return mapToJson((Map<?, ?>) obj);
        return objectToJson(obj);
    }

    /** 解析 JSON 字符串为 Map。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String json) {
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        if (result instanceof Map) return (Map<String, Object>) result;
        return new HashMap<>();
    }

    /** 解析 JSON 字符串为 List。 */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        if (result instanceof List) return (List<Object>) result;
        return new ArrayList<>();
    }

    /** 从 Map 中安全获取字符串。 */
    public static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /** 从 Map 中安全获取 int。 */
    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /** 构建成功响应。 */
    public static String successResponse(Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"message\":\"OK\"");
        if (data != null) sb.append(",\"data\":").append(toJson(data));
        sb.append("}");
        return sb.toString();
    }

    /** 构建错误响应。 */
    public static String errorResponse(String message) {
        return "{\"success\":false,\"message\":" + escapeString(message) + "}";
    }

    // ==================== 内部实现 ====================

    private static String escapeString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String objectToJson(Object obj) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Field field : obj.getClass().getFields()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(escapeString(field.getName())).append(":");
            try {
                sb.append(toJson(field.get(obj)));
            } catch (IllegalAccessException e) {
                sb.append("null");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(escapeString(entry.getKey().toString())).append(":");
            sb.append(toJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String arrayToJson(Object[] arr) {
        return listToJson(Arrays.asList(arr));
    }

    private static String intArrayToJson(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /** 简易 JSON 解析器（递归下降）。 */
    private static class JsonParser {
        private final String json;
        private int pos;

        JsonParser(String json) {
            this.json = json;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= json.length()) return null;
            char c = json.charAt(pos);
            switch (c) {
                case '"': return parseString();
                case '{': return parseObject();
                case '[': return parseArray();
                case 't': return parseLiteral("true", Boolean.TRUE);
                case 'f': return parseLiteral("false", Boolean.FALSE);
                case 'n': return parseLiteral("null", null);
                default: return parseNumber();
            }
        }

        private String parseString() {
            if (json.charAt(pos) != '"') return "";
            pos++; // skip "
            StringBuilder sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    pos++;
                    if (pos >= json.length()) return sb.toString();
                    char next = json.charAt(pos);
                    switch (next) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 < json.length()) {
                                sb.append((char) Integer.parseInt(json.substring(pos + 1, pos + 5), 16));
                                pos += 4;
                            }
                            break;
                        default: sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
                pos++;
            }
            return sb.toString();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // skip {
            skipWhitespace();
            if (pos < json.length() && json.charAt(pos) == '}') { pos++; return map; }
            while (pos < json.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos < json.length() && json.charAt(pos) == ':') pos++;
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                if (pos < json.length() && json.charAt(pos) == ',') pos++;
                else if (pos < json.length() && json.charAt(pos) == '}') { pos++; break; }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // skip [
            skipWhitespace();
            if (pos < json.length() && json.charAt(pos) == ']') { pos++; return list; }
            while (pos < json.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < json.length() && json.charAt(pos) == ',') pos++;
                else if (pos < json.length() && json.charAt(pos) == ']') { pos++; break; }
            }
            return list;
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < json.length() && json.charAt(pos) == '-') pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < json.length() && json.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            String num = json.substring(start, pos);
            if (isDouble) return Double.parseDouble(num);
            long val = Long.parseLong(num);
            if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) return (int) val;
            return val;
        }

        private Object parseLiteral(String literal, Object value) {
            pos += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        }
    }
}
