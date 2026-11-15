package ru.stalkerzone.zonemap.atlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Маленький разборщик JSON для атласа (Э1-13): без Minecraft и без библиотек.
 * Типы результата: Map (объект, порядок ключей сохранён), List, String, Double, Boolean, null.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("лишний текст после значения");
        return v;
    }

    // ---- удобные чтения для вызывающего кода ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : null; }

    public static String str(Object o) { return o instanceof String ? (String) o : null; }

    /** Число как double; NaN, если не число. */
    public static double num(Object o) { return o instanceof Double ? (Double) o : Double.NaN; }

    /** Список чисел как double[]; null, если не список чисел. */
    public static double[] nums(Object o) {
        List<Object> l = list(o);
        if (l == null) return null;
        double[] r = new double[l.size()];
        for (int k = 0; k < r.length; k++) {
            r[k] = num(l.get(k));
            if (Double.isNaN(r[k])) return null;
        }
        return r;
    }

    // ---- разбор ----

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException("JSON: " + m + " (позиция " + i + ")");
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) throw err("неожиданный конец");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return number();
        }
    }

    private void lit(String w) {
        if (!s.startsWith(w, i)) throw err("ожидалось " + w);
        i += w.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek('}')) { i++; return m; }
        while (true) {
            ws();
            if (!peek('"')) throw err("ожидался ключ");
            String k = string();
            ws();
            if (!peek(':')) throw err("ожидалось ':'");
            i++;
            ws();
            if (m.containsKey(k)) throw err("повтор ключа " + k);
            m.put(k, value());
            ws();
            if (peek(',')) { i++; continue; }
            if (peek('}')) { i++; return m; }
            throw err("ожидалось ',' или '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek(']')) { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (peek(',')) { i++; continue; }
            if (peek(']')) { i++; return l; }
            throw err("ожидалось ',' или ']'");
        }
    }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw err("строка не закрыта");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw err("обрыв escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"': b.append('"'); break;
                case '\\': b.append('\\'); break;
                case '/': b.append('/'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw err("обрыв \\u");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: throw err("неизвестный escape \\" + e);
            }
        }
    }

    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("неожиданный символ '" + s.charAt(i) + "'");
        try {
            return Double.parseDouble(s.substring(st, i));
        } catch (NumberFormatException e) {
            i = st;
            throw err("плохое число");
        }
    }
}
