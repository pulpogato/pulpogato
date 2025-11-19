package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SingularOrPluralTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSingularFactory() {
        var result = SingularOrPlural.singular("test");

        assertThat(result.getSingular()).isEqualTo("test");
        assertThat(result.getPlural()).isNull();
    }

    @Test
    void testPluralFactory() {
        var list = List.of("item1", "item2");
        var result = SingularOrPlural.plural(list);

        assertThat(result.getPlural()).isEqualTo(list);
        assertThat(result.getSingular()).isNull();
    }

    @Test
    void testSingularSerialization() throws JsonProcessingException {
        var singularOrPlural = SingularOrPlural.singular("test-value");

        var json = objectMapper.writeValueAsString(singularOrPlural);

        assertThat(json).isEqualTo("\"test-value\"");
    }

    @Test
    void testPluralSerialization() throws JsonProcessingException {
        var singularOrPlural = SingularOrPlural.plural(List.of("item1", "item2"));

        var json = objectMapper.writeValueAsString(singularOrPlural);

        assertThat(json).isEqualTo("[\"item1\",\"item2\"]");
    }

    @Test
    void testSingularDeserialization() throws JsonProcessingException {
        var json = "\"single-item\"";

        var result = objectMapper.readValue(json, SingularOrPlural.class);

        assertThat(result.getSingular()).isEqualTo("single-item");
        assertThat(result.getPlural()).isNull();
    }

    @Test
    void testPluralDeserialization() throws JsonProcessingException {
        var json = "[\"item1\",\"item2\",\"item3\"]";

        var result = objectMapper.readValue(json, SingularOrPlural.class);

        assertThat(result.getPlural()).isEqualTo(List.of("item1", "item2", "item3"));
        assertThat(result.getSingular()).isNull();
    }

    @Test
    void testEmptyArrayDeserialization() throws JsonProcessingException {
        var json = "[]";

        var result = objectMapper.readValue(json, SingularOrPlural.class);

        assertThat(result.getPlural()).isEmpty();
        assertThat(result.getSingular()).isNull();
    }

    @ParameterizedTest
    @MethodSource("serializationTestCases")
    void testSerialization(String description, SingularOrPlural<String> input, String expectedJson)
            throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(input);
        assertThat(json).isEqualTo(expectedJson);
    }

    @ParameterizedTest
    @MethodSource("singularSerializationTestCases")
    void testSingularSerializationRoundTrip(String description, SingularOrPlural<String> input, String expectedJson)
            throws JsonProcessingException {
        var json = objectMapper.writeValueAsString(input);
        assertThat(json).isEqualTo(expectedJson);

        var deserialized = objectMapper.readValue(json, SingularOrPlural.class);
        assertThat(deserialized.getSingular()).isEqualTo(input.getSingular());
        assertThat(deserialized.getPlural()).isNull();
    }

    static Stream<Arguments> serializationTestCases() {
        return Stream.of(
                Arguments.of("singular string", SingularOrPlural.singular("hello"), "\"hello\""),
                Arguments.of(
                        "singular with special characters",
                        SingularOrPlural.singular("hello \"world\""),
                        "\"hello \\\"world\\\"\""),
                Arguments.of("plural with single item", SingularOrPlural.plural(List.of("single")), "[\"single\"]"),
                Arguments.of(
                        "plural with multiple items",
                        SingularOrPlural.plural(List.of("first", "second", "third")),
                        "[\"first\",\"second\",\"third\"]"),
                Arguments.of("empty plural", SingularOrPlural.plural(List.of()), "[]"));
    }

    static Stream<Arguments> singularSerializationTestCases() {
        return Stream.of(
                Arguments.of("singular string", SingularOrPlural.singular("hello"), "\"hello\""),
                Arguments.of(
                        "singular with special characters",
                        SingularOrPlural.singular("hello \"world\""),
                        "\"hello \\\"world\\\"\""));
    }

    @Test
    void testIntegerSingularOrPlural() throws JsonProcessingException {
        var singularInt = SingularOrPlural.singular(42);
        var pluralInt = SingularOrPlural.plural(List.of(1, 2, 3));

        var singularJson = objectMapper.writeValueAsString(singularInt);
        var pluralJson = objectMapper.writeValueAsString(pluralInt);

        assertThat(singularJson).isEqualTo("42");
        assertThat(pluralJson).isEqualTo("[1,2,3]");

        var deserializedSingular = objectMapper.readValue(singularJson, SingularOrPlural.class);
        var deserializedPlural = objectMapper.readValue(pluralJson, SingularOrPlural.class);

        // Numbers are deserialized as strings due to FancyDeserializer behavior
        assertThat(deserializedSingular.getSingular()).isEqualTo("42");
        // Arrays are now properly deserialized
        assertThat(deserializedPlural.getPlural()).isEqualTo(List.of(1, 2, 3));
        assertThat(deserializedPlural.getSingular()).isNull();
    }

    @Test
    void testNullSingularSerialization() throws JsonProcessingException {
        var singularOrPlural = SingularOrPlural.singular(null);

        var json = objectMapper.writeValueAsString(singularOrPlural);

        assertThat(json).isEqualTo("null");
    }

    @Test
    void testNullDeserialization() throws JsonProcessingException {
        var json = "null";

        var result = objectMapper.readValue(json, SingularOrPlural.class);

        // Null values return null due to FancyDeserializer behavior
        assertThat(result).isNull();
    }

    @Test
    void testBuilderPattern() {
        var result = new SingularOrPlural<String>().setSingular("test").setPlural(null);

        assertThat(result.getSingular()).isEqualTo("test");
        assertThat(result.getPlural()).isNull();
    }

    @Test
    void testFluentSetters() {
        var result = new SingularOrPlural<String>().setSingular("value1").setSingular("value2");

        assertThat(result.getSingular()).isEqualTo("value2");
    }

    @Test
    void testGettersAndSetters() {
        var instance = new SingularOrPlural<String>();

        instance.setSingular("test-singular");
        assertThat(instance.getSingular()).isEqualTo("test-singular");

        var list = List.of("item1", "item2");
        instance.setPlural(list);
        assertThat(instance.getPlural()).isEqualTo(list);
    }

    @Test
    void testDefaultConstructor() {
        var instance = new SingularOrPlural<String>();

        assertThat(instance.getSingular()).isNull();
        assertThat(instance.getPlural()).isNull();
    }

    @Test
    void testComplexObjectSerialization() throws JsonProcessingException {
        record TestObject(String name, int value) {}

        var obj1 = new TestObject("first", 1);
        var obj2 = new TestObject("second", 2);

        var singular = SingularOrPlural.singular(obj1);
        var plural = SingularOrPlural.plural(List.of(obj1, obj2));

        var singularJson = objectMapper.writeValueAsString(singular);
        var pluralJson = objectMapper.writeValueAsString(plural);

        assertThat(singularJson).isEqualTo("{\"name\":\"first\",\"value\":1}");
        assertThat(pluralJson).isEqualTo("[{\"name\":\"first\",\"value\":1},{\"name\":\"second\",\"value\":2}]");
    }

    @ParameterizedTest
    @MethodSource("stringDeserializationTestCases")
    void testStringDeserializationFormats(String description, String json, Object expectedValue)
            throws JsonProcessingException {
        var result = objectMapper.readValue(json, SingularOrPlural.class);

        assertThat(result.getSingular()).isEqualTo(expectedValue);
        assertThat(result.getPlural()).isNull();
    }

    @ParameterizedTest
    @MethodSource("arrayDeserializationTestCases")
    void testArrayDeserializationFormats(String description, String json, List<?> expectedList)
            throws JsonProcessingException {
        var result = objectMapper.readValue(json, SingularOrPlural.class);

        // Arrays are now properly deserialized
        assertThat(result.getPlural()).isEqualTo(expectedList);
        assertThat(result.getSingular()).isNull();
    }

    static Stream<Arguments> stringDeserializationTestCases() {
        return Stream.of(
                Arguments.of("string value", "\"test\"", "test"),
                Arguments.of("integer value as string", "42", "42"),
                Arguments.of("boolean value as string", "true", "true"));
    }

    static Stream<Arguments> arrayDeserializationTestCases() {
        return Stream.of(
                Arguments.of("array of strings", "[\"a\",\"b\"]", List.of("a", "b")),
                Arguments.of("array of integers", "[1,2,3]", List.of(1, 2, 3)),
                Arguments.of("array of booleans", "[true,false]", List.of(true, false)),
                Arguments.of("mixed array", "[\"text\",123,true]", List.of("text", 123, true)),
                Arguments.of("single item array", "[\"only\"]", List.of("only")));
    }

    @Nested
    class ToCodeTests {
        @Test
        void testSingularToCode() {
            var singularOrPlural = SingularOrPlural.singular("example");

            var code = singularOrPlural.toCode();

            var expectedCode = """
                    io.github.pulpogato.common.SingularOrPlural.singular("example")""";
            assertThat(code).isEqualTo(expectedCode);
        }

        @Test
        void testPluralToCode() {
            var singularOrPlural = SingularOrPlural.plural(List.of("one", "two", "three"));

            var code = singularOrPlural.toCode();

            var expectedCode = """
                    io.github.pulpogato.common.SingularOrPlural.plural(List.of(
                            "one",
                            "two",
                            "three"
                        ))""";
            assertThat(code).isEqualTo(expectedCode);
        }

        @Test
        void testSingularWithStringOrInteger() {
            var singularOrPlural = SingularOrPlural.singular(
                    StringOrInteger.builder().stringValue("test").build());

            var code = singularOrPlural.toCode();

            var expectedCode = """
                    io.github.pulpogato.common.SingularOrPlural.singular(io.github.pulpogato.common.StringOrInteger.builder()
                          .stringValue("test")
                          .build())""";
            assertThat(code).isEqualTo(expectedCode);
        }
    }
}
