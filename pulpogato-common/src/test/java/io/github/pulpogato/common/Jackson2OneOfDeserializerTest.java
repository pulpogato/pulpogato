package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.pulpogato.common.jackson.Jackson2OneOfDeserializer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Jackson2OneOfDeserializerTest {

    @JsonDeserialize(using = ShapeDeserializer.class)
    sealed interface Shape permits Circle, Square {}

    // The None.class reset prevents this class from inheriting the interface's deserializer,
    // which would otherwise cause infinite recursion.
    @JsonDeserialize(using = JsonDeserializer.None.class)
    record Circle(@JsonProperty("radius") double radius) implements Shape {}

    @JsonDeserialize(using = JsonDeserializer.None.class)
    record Square(@JsonProperty("side") double side) implements Shape {}

    static class ShapeDeserializer extends Jackson2OneOfDeserializer<Shape> {
        public ShapeDeserializer() {
            super(Shape.class, List.of(Circle.class, Square.class));
        }
    }

    static class Wrapper {
        public Shape shape;
    }

    static Stream<Arguments> correctBranchParams() {
        return Stream.of(
                Arguments.of("{\"shape\":{\"radius\":3.0}}", Circle.class, 3.0, 0.0),
                Arguments.of("{\"shape\":{\"side\":5.0}}", Square.class, 0.0, 5.0));
    }

    @ParameterizedTest
    @MethodSource("correctBranchParams")
    void picksCorrectBranch(String json, Class<?> expectedType, double radius, double side) throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = om.readValue(json, Wrapper.class);
        assertThat(result.shape).isInstanceOf(expectedType);
        if (result.shape instanceof Circle c) {
            assertThat(c.radius()).isEqualTo(radius);
        } else if (result.shape instanceof Square s) {
            assertThat(s.side()).isEqualTo(side);
        }
    }

    @Test
    void firstCandidateWinsWhenBothCouldMatch() throws Exception {
        // Both Circle and Square could accept a payload with only shared fields;
        // Circle is first in the candidate list, so it wins.
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = om.readValue("{\"shape\":{}}", Wrapper.class);
        assertThat(result.shape).isInstanceOf(Circle.class);
    }

    @Test
    void returnsNullWhenNoMatchFound() throws Exception {
        // A payload with a field unknown to all candidates fails every branch and returns null.
        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var result = om.readValue("{\"shape\":{\"unknown_field\":true}}", Wrapper.class);
        assertThat(result.shape).isNull();
    }
}
