package com.printdesk.json;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers to move between records and JSON-shaped maps. */
public final class Records {
    private Records() {}

    /** Record → map with snake_case keys, so the API speaks the same field names as the frontend. */
    public static Map<String, Object> toMap(Record r) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (RecordComponent c : r.getClass().getRecordComponents()) {
            try {
                m.put(snake(c.getName()), c.getAccessor().invoke(r));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return m;
    }

    static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    // ---- tolerant readers for untyped JSON maps
    public static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null || "null".equals(v) ? null : String.valueOf(v);
    }

    public static String str(Map<String, Object> m, String k, String def) {
        String v = str(m, k);
        return v == null ? def : v;
    }

    public static double num(Map<String, Object> m, String k, double def) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        return def;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s);
        return def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof List ? (List<Object>) v : List.of();
    }
}
