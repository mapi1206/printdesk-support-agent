package com.printdesk.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void roundTripsNestedStructures() {
        String src = "{\"a\":1,\"b\":[true,null,2.5,\"x\\\"y\"],\"c\":{\"d\":\"ő ü 🚀\",\"e\":-3}}";
        Object parsed = Json.parse(src);
        assertEquals(src, Json.write(parsed));
    }

    @Test
    void parsesUnicodeEscapesAndWhitespace() {
        Map<String, Object> m = Json.parseObject(" { \"k\" : \"\\u00e9t\\n\" } ");
        assertEquals("ét\n", m.get("k"));
    }

    @Test
    void writesRecordsWithSnakeCaseKeys() {
        record Sample(String fromEmail, int stepsTried) {}
        assertEquals("{\"from_email\":\"a@b.c\",\"steps_tried\":2}", Json.write(new Sample("a@b.c", 2)));
    }

    @Test
    void writesWholeDoublesAsIntegers() {
        assertEquals("[3,3.25]", Json.write(List.of(3.0, 3.25)));
    }

    @Test
    void rejectsBrokenInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} x"));
    }
}
