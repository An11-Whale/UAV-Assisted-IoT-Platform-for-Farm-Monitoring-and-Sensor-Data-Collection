package farm.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON parser tối giản (không cần thư viện ngoài). Object->Map, Array->List, số->Double. */
public final class Json {
    private final String s;
    private int i = 0;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("unexpected trailing data");
        return v;
    }

    public static String quote(String str) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : null; }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : null; }

    public static String str(Object o) { return o instanceof String ? (String) o : null; }

    /** Parser trả Double; helper vẫn chấp nhận mọi Number để code/test tạo object trực tiếp an toàn. */
    public static Double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : null; }

    // ---------- parser ----------
    private RuntimeException err(String m) { return new IllegalArgumentException("JSON error at " + i + ": " + m); }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

    private void expect(char c) {
        if (!peek(c)) throw err("expected '" + c + "'");
        i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return lit("true", Boolean.TRUE);
            case 'f': return lit("false", Boolean.FALSE);
            case 'n': return lit("null", null);
            default: return number();
        }
    }

    private Object lit(String word, Object val) {
        if (s.startsWith(word, i)) { i += word.length(); return val; }
        throw err("bad literal");
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        expect('{');
        ws();
        if (peek('}')) { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws();
            expect(':');
            m.put(k, value());
            ws();
            if (peek(',')) { i++; continue; }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        expect('[');
        ws();
        if (peek(']')) { i++; return l; }
        while (true) {
            l.add(value());
            ws();
            if (peek(',')) { i++; continue; }
            expect(']');
            return l;
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case '/': b.append('/'); break;
                    case '\\': b.append('\\'); break;
                    case '"': b.append('"'); break;
                    case 'u':
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw err("bad escape");
                }
            } else {
                b.append(c);
            }
        }
        throw err("unterminated string");
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw err("unexpected character '" + s.charAt(i) + "'");
        return Double.parseDouble(s.substring(start, i));
    }
}
