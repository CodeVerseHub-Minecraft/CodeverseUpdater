package net.codeverse.updater;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesAnObjectWithMixedValueTypes() {
        Map<String, Object> object = Json.object(Json.parse(
                "{\"name\":\"v1.0.0\",\"draft\":false,\"size\":1234,\"body\":null}"));
        assertEquals("v1.0.0", Json.string(object, "name"));
        assertEquals(false, Json.bool(object, "draft"));
        assertEquals(1234L, Json.number(object, "size"));
        assertNull(Json.string(object, "body"), "a JSON null reads as absent");
    }

    @Test
    void parsesNestedArraysOfObjects() {
        List<Object> array = Json.array(Json.parse(
                "[{\"tag_name\":\"v1.0.0\"},{\"tag_name\":\"v2.0.0\"}]"));
        assertEquals(2, array.size());
        assertEquals("v2.0.0", Json.string(Json.object(array.get(1)), "tag_name"));
    }

    @Test
    void handlesEscapesInStrings() {
        Map<String, Object> object = Json.object(Json.parse(
                "{\"body\":\"line one\\nline two \\\"quoted\\\"\"}"));
        assertTrue(Json.string(object, "body").contains("\n"));
        assertTrue(Json.string(object, "body").contains("\"quoted\""));
    }

    @Test
    void handlesUnicodeEscapes() {
        Map<String, Object> object = Json.object(Json.parse("{\"name\":\"caf\\u00e9\"}"));
        assertEquals("caf\u00e9", Json.string(object, "name"));
    }

    @Test
    void rejectsMalformedInputRatherThanGuessing() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(Json.JsonException.class, () -> Json.parse("[1,2"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1} trailing"));
    }

    @Test
    void emptyContainersParse() {
        assertTrue(Json.array(Json.parse("[]")).isEmpty());
        assertTrue(Json.object(Json.parse("{}")).isEmpty());
    }
}
