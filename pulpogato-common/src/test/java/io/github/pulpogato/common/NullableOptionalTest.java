package io.github.pulpogato.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class NullableOptionalTest {

    @Test
    void testNotSet() {
        var optional = NullableOptional.notSet();
        assertThat(optional.isNotSet()).isTrue();
        assertThat(optional.isNull()).isFalse();
        assertThat(optional.isValue()).isFalse();
    }

    @Test
    void testOfNull() {
        var optional = NullableOptional.ofNull();
        assertThat(optional.isNotSet()).isFalse();
        assertThat(optional.isNull()).isTrue();
        assertThat(optional.isValue()).isFalse();
    }

    @Test
    void testOfValue() {
        var optional = NullableOptional.of("test");
        assertThat(optional.isNotSet()).isFalse();
        assertThat(optional.isNull()).isFalse();
        assertThat(optional.isValue()).isTrue();
        assertThat(optional.getValue()).isEqualTo("test");
    }

    @Test
    void testOfNullThrows() {
        assertThatThrownBy(() -> NullableOptional.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Use ofNull()");
    }

    @Test
    void testOfNullableWithNullValueReturnsNullState() {
        var optional = NullableOptional.ofNullable(null);
        assertThat(optional.isNull()).isTrue();
        assertThat(optional.isNotSet()).isFalse();
        assertThat(optional.isValue()).isFalse();
    }

    @Test
    void testOfNullableWithNonNullValueReturnsValueState() {
        var optional = NullableOptional.ofNullable("test");
        assertThat(optional.isValue()).isTrue();
        assertThat(optional.getValue()).isEqualTo("test");
        assertThat(optional.isNotSet()).isFalse();
        assertThat(optional.isNull()).isFalse();
    }

    @Test
    void testGetValueThrowsWhenNotSet() {
        var optional = NullableOptional.notSet();
        assertThatThrownBy(optional::getValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No value present");
    }

    @Test
    void testGetValueThrowsWhenNull() {
        var optional = NullableOptional.ofNull();
        assertThatThrownBy(optional::getValue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No value present");
    }

    @Test
    void testOrElse() {
        assertThat(NullableOptional.notSet().orElse("default")).isEqualTo("default");
        assertThat(NullableOptional.ofNull().orElse("default")).isEqualTo("default");
        assertThat(NullableOptional.of("value").orElse("default")).isEqualTo("value");
    }

    @Test
    void testSerializationNotSetJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.notSet();

        String json = om.writeValueAsString(wrapper);
        assertThat(json).doesNotContain("\"value\"");
    }

    @Test
    void testSerializationNullJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.ofNull();

        String json = om.writeValueAsString(wrapper);
        assertThat(json).isEqualTo("{\"field\":null}");
    }

    @Test
    void testSerializationValueJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.of("test");

        String json = om.writeValueAsString(wrapper);
        assertThat(json).isEqualTo("{\"field\":\"test\"}");
    }

    @Test
    void testDeserializationAbsentJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        String json = "{}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isNotSet()).isTrue();
    }

    @Test
    void testDeserializationNullJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        String json = "{\"field\":null}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isNull()).isTrue();
    }

    @Test
    void testDeserializationValueJackson3() {
        var om = new tools.jackson.databind.ObjectMapper();
        String json = "{\"field\":\"test\"}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isValue()).isTrue();
        assertThat(result.field.getValue()).isEqualTo("test");
    }

    @Test
    void testSerializationNotSetJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.notSet();

        String json = om.writeValueAsString(wrapper);
        assertThat(json).doesNotContain("\"value\"");
    }

    @Test
    void testSerializationNullJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.ofNull();

        String json = om.writeValueAsString(wrapper);
        assertThat(json).isEqualTo("{\"field\":null}");
    }

    @Test
    void testSerializationValueJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        var wrapper = new TestWrapper();
        wrapper.field = NullableOptional.of("test");

        String json = om.writeValueAsString(wrapper);
        assertThat(json).isEqualTo("{\"field\":\"test\"}");
    }

    @Test
    void testDeserializationAbsentJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        String json = "{}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isNotSet()).isTrue();
    }

    @Test
    void testDeserializationNullJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        String json = "{\"field\":null}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isNull()).isTrue();
    }

    @Test
    void testDeserializationValueJackson2() throws Exception {
        var om = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());
        String json = "{\"field\":\"test\"}";

        TestWrapper result = om.readValue(json, TestWrapper.class);
        assertThat(result.field).isNotNull();
        assertThat(result.field.isValue()).isTrue();
        assertThat(result.field.getValue()).isEqualTo("test");
    }

    static class TestWrapper {
        public NullableOptional<String> field = NullableOptional.notSet();
    }
}
