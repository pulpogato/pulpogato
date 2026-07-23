package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.pulpogato.common.jackson.Jackson3OneOfDeserializer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

class Jackson3OneOfDeserializerTest {

    @JsonDeserialize(using = ShapeDeserializer.class)
    sealed interface Shape permits Circle, Square {}

    // The None.class reset prevents this class from inheriting the interface's deserializer,
    // which would otherwise cause infinite recursion.
    @JsonDeserialize(using = ValueDeserializer.None.class)
    record Circle(@JsonProperty("radius") double radius) implements Shape {}

    @JsonDeserialize(using = ValueDeserializer.None.class)
    record Square(@JsonProperty("side") double side) implements Shape {}

    static class ShapeDeserializer extends Jackson3OneOfDeserializer<Shape> {
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
    void picksCorrectBranch(String json, Class<?> expectedType, double radius, double side) {
        var om = new ObjectMapper();
        var result = om.readValue(json, Wrapper.class);
        assertThat(result.shape).isInstanceOf(expectedType);
        if (result.shape instanceof Circle(double radius1)) {
            assertThat(radius1).isEqualTo(radius);
        } else if (result.shape instanceof Square(double side1)) {
            assertThat(side1).isEqualTo(side);
        }
    }

    @Test
    void firstCandidateWinsWhenBothCouldMatch() {
        // An empty object matches both Circle and Square (neither has required fields).
        // GitHub's schemas have this overlap; we return the first candidate rather than throwing.
        var om = new ObjectMapper();
        var result = om.readValue("{\"shape\":{}}", Wrapper.class);
        assertThat(result.shape).isInstanceOf(Circle.class);
    }

    @Test
    void returnsNullWhenNoMatchFound() {
        // A payload with a field unknown to all candidates fails every branch and returns null.
        var om = new ObjectMapper();
        var result = om.readValue("{\"shape\":{\"unknown_field\":true}}", Wrapper.class);
        assertThat(result.shape).isNull();
    }
}
