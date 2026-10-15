package dev.codexcommit.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Small bounded JSON reader for local CLI replies. Never renders or logs their contents. */
final class Json {
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private final String text;
    private int position;

    private Json(String text) { this.text = text; }

    static Object parse(String text) throws IOException {
        if (text.length() > 1024 * 1024) throw invalid();
        Json reader = new Json(text);
        Object result = reader.value(0);
        reader.space();
        if (reader.position != text.length()) throw invalid();
        return result;
    }

    static Map<?, ?> object(Object value) throws IOException {
        if (value instanceof Map<?, ?> map) return map;
        throw invalid();
    }

    private Object value(int depth) throws IOException {
        if (depth > 32) throw invalid();
        space();
        if (position >= text.length()) throw invalid();
        char next = text.charAt(position);
        if (next == '"') return string();
        if (next == '{') {
            position++;
            Map<String, Object> object = new LinkedHashMap<>();
            if (take('}')) return object;
            do {
                space();
                String key = string();
                if (!take(':') || object.containsKey(key)) throw invalid();
                object.put(key, value(depth + 1));
                if (take('}')) return object;
            } while (take(','));
            throw invalid();
        }
        if (next == '[') {
            position++;
            var array = new ArrayList<Object>();
            if (take(']')) return array;
            do {
                array.add(value(depth + 1));
                if (take(']')) return array;
            } while (take(','));
            throw invalid();
        }
        for (String literal : new String[]{"true", "false", "null"}) {
            if (text.startsWith(literal, position)) {
                position += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        var number = NUMBER.matcher(text).region(position, text.length());
        if (!number.lookingAt()) throw invalid();
        position = number.end();
        try { return Double.valueOf(number.group()); }
        catch (NumberFormatException ignored) { throw invalid(); }
    }

    private String string() throws IOException {
        if (position >= text.length() || text.charAt(position++) != '"') throw invalid();
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char next = text.charAt(position++);
            if (next == '"') return result.toString();
            if (next < 32) throw invalid();
            if (next == '\\') {
                if (position >= text.length()) throw invalid();
                next = text.charAt(position++);
                next = switch (next) {
                    case '"', '\\', '/' -> next;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> unicode();
                    default -> throw invalid();
                };
            }
            result.append(next);
        }
        throw invalid();
    }

    private char unicode() throws IOException {
        if (position + 4 > text.length()) throw invalid();
        int result = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(text.charAt(position++), 16);
            if (digit < 0) throw invalid();
            result = result * 16 + digit;
        }
        return (char) result;
    }

    private boolean take(char expected) {
        space();
        if (position < text.length() && text.charAt(position) == expected) { position++; return true; }
        return false;
    }

    private void space() {
        while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) position++;
    }

    private static IOException invalid() { return new IOException("Codex returned an invalid model selection response."); }
}
