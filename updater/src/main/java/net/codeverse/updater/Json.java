package net.codeverse.updater;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, enough to walk a GitHub releases response and no more.
 *
 * The library takes no third party dependencies on purpose: a plugin adopting
 * it should not inherit a JSON library it then has to relocate. This parser is
 * complete enough for the shapes GitHub returns, objects, arrays, strings,
 * numbers, booleans and null, and rejects anything malformed rather than
 * guessing, so a mangled response fails loudly instead of producing a
 * half read release.
 *
 * It is not a general purpose JSON library and does not try to be. It exists
 * so this library can read one well known API without a dependency.
 */
final class Json {

    private final String source;
    private int index;

    private Json(String source) {
        this.source = source;
    }

    static Object parse(String source) {
        Json json = new Json(source);
        json.skipWhitespace();
        Object value = json.readValue();
        json.skipWhitespace();
        if (json.index < json.source.length()) {
            throw new JsonException("Trailing content after JSON value at index " + json.index);
        }
        return value;
    }

    /** Reads an object as a map, or throws when the value is not an object. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new JsonException("Expected a JSON object");
    }

    /** Reads an array as a list, or throws when the value is not an array. */
    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new JsonException("Expected a JSON array");
    }

    /** A string field, or null when absent or JSON null. */
    static String string(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String s ? s : null;
    }

    static boolean bool(Map<String, Object> object, String key) {
        return object.get(key) instanceof Boolean b && b;
    }

    static long number(Map<String, Object> object, String key) {
        return object.get(key) instanceof Number n ? n.longValue() : 0L;
    }

    private Object readValue() {
        char c = peek();
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw new JsonException("Expected ',' or '}' at index " + index);
            }
        }
    }

    private List<Object> readArray() {
        List<Object> array = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return array;
            }
            if (c != ',') {
                throw new JsonException("Expected ',' or ']' at index " + index);
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return builder.toString();
            }
            if (c == '\\') {
                char escape = next();
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        String hex = source.substring(index, index + 4);
                        index += 4;
                        builder.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new JsonException("Invalid escape \\" + escape);
                }
            } else {
                builder.append(c);
            }
        }
    }

    private Boolean readBoolean() {
        if (source.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (source.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("Invalid literal at index " + index);
    }

    private Object readNull() {
        if (source.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new JsonException("Invalid literal at index " + index);
    }

    private Number readNumber() {
        int start = index;
        while (index < source.length() && "-+.eE0123456789".indexOf(source.charAt(index)) >= 0) {
            index++;
        }
        String token = source.substring(start, index);
        if (token.isEmpty()) {
            throw new JsonException("Expected a value at index " + start);
        }
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
            return Double.parseDouble(token);
        }
        return Long.parseLong(token);
    }

    private void skipWhitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private char peek() {
        if (index >= source.length()) {
            throw new JsonException("Unexpected end of input");
        }
        return source.charAt(index);
    }

    private char next() {
        if (index >= source.length()) {
            throw new JsonException("Unexpected end of input");
        }
        return source.charAt(index++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new JsonException("Expected '" + expected + "' but found '" + c + "' at index " + (index - 1));
        }
    }

    static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }
}
