package com.dosalmas.launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser JSON minimo y sin dependencias.
 *
 * Los valores se representan con tipos de Java:
 *   objeto  -> Map&lt;String,Object&gt; (LinkedHashMap, conserva el orden)
 *   arreglo -> List&lt;Object&gt;
 *   texto   -> String
 *   numero  -> Double o Long
 *   bool    -> Boolean
 *   nulo    -> null
 *
 * Es suficiente para leer los JSON de version de Minecraft, que no usan
 * nada exotico. Se prefiere esto a una libreria externa para que el
 * launcher sea un unico .jar sin dependencias.
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
        this.pos = 0;
    }

    // ---------------------------------------------------------------- API

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.src.length()) {
            throw p.error("contenido sobrante despues del valor JSON");
        }
        return value;
    }

    public static Object parse(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return parse(sb.toString());
    }

    public static Map<String, Object> parseObject(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IOException("se esperaba un objeto JSON en " + file);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) value;
        return obj;
    }

    /** Escribe un objeto plano (String/Number/Boolean) como JSON legible. */
    public static String writeFlat(Map<String, Object> obj) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            sb.append("  ").append(quote(e.getKey())).append(": ");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                sb.append(quote((String) v));
            } else {
                sb.append(v.toString());
            }
            if (++i < obj.size()) sb.append(',');
            sb.append('\n');
        }
        return sb.append("}\n").toString();
    }

    // ----------------------------------------------------------- Helpers

    /** Devuelve obj[key] como Map, o null si no existe o no es objeto. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object parent, String key) {
        if (!(parent instanceof Map)) return null;
        Object v = ((Map<String, Object>) parent).get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    /** Devuelve obj[key] como List, o lista vacia si no existe. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object parent, String key) {
        if (!(parent instanceof Map)) return new ArrayList<>();
        Object v = ((Map<String, Object>) parent).get(key);
        return (v instanceof List) ? (List<Object>) v : new ArrayList<>();
    }

    /** Devuelve obj[key] como String, o null. */
    @SuppressWarnings("unchecked")
    public static String str(Object parent, String key) {
        if (!(parent instanceof Map)) return null;
        Object v = ((Map<String, Object>) parent).get(key);
        return (v instanceof String) ? (String) v : (v == null ? null : String.valueOf(v));
    }

    /** Devuelve obj[key] como long, o def. */
    @SuppressWarnings("unchecked")
    public static long num(Object parent, String key, long def) {
        if (!(parent instanceof Map)) return def;
        Object v = ((Map<String, Object>) parent).get(key);
        return (v instanceof Number) ? ((Number) v).longValue() : def;
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ------------------------------------------------------------ Parser

    private Object readValue() {
        if (pos >= src.length()) throw error("fin inesperado");
        char c = src.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true");  return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null");  return null;
            default:  return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // '{'
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("se esperaba una clave entre comillas");
            String key = readString();
            skipWhitespace();
            if (peek() != ':') throw error("se esperaba ':'");
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; return map; }
            throw error("se esperaba ',' o '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> out = new ArrayList<>();
        pos++; // '['
        skipWhitespace();
        if (peek() == ']') { pos++; return out; }
        while (true) {
            skipWhitespace();
            out.add(readValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; return out; }
            throw error("se esperaba ',' o ']'");
        }
    }

    private String readString() {
        pos++; // '"'
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) throw error("texto sin cerrar");
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (pos >= src.length()) throw error("escape sin cerrar");
            char e = src.charAt(pos++);
            switch (e) {
                case '"':  sb.append('"');  break;
                case '\\': sb.append('\\'); break;
                case '/':  sb.append('/');  break;
                case 'b':  sb.append('\b'); break;
                case 'f':  sb.append('\f'); break;
                case 'n':  sb.append('\n'); break;
                case 'r':  sb.append('\r'); break;
                case 't':  sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) throw error("escape unicode incompleto");
                    sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw error("escape desconocido: \\" + e);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && isNumberChar(src.charAt(pos))) pos++;
        String text = src.substring(start, pos);
        if (text.isEmpty()) throw error("numero invalido");
        if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
            return Double.parseDouble(text);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return Double.parseDouble(text);
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private char peek() {
        if (pos >= src.length()) throw error("fin inesperado");
        return src.charAt(pos);
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) throw error("se esperaba '" + word + "'");
        pos += word.length();
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
            else break;
        }
    }

    private RuntimeException error(String msg) {
        int line = 1;
        for (int i = 0; i < pos && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return new IllegalArgumentException("JSON invalido (linea " + line + "): " + msg);
    }
}
