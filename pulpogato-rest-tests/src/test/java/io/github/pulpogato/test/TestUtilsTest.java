package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonValue;
import org.assertj.core.api.SoftAssertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;

class TestUtilsTest {

    @Nested
    class Traverse {
        @Test
        void shouldTraverseJsonObject() {
            var json =
                    Json.createReader(new StringReader(/* language=json */ """
                            {"a": {"b": 1}}""")).read();
            var result = TestUtils.traverse(json, List.of("a", "b"));
            assertThat(result).isEqualTo(Json.createValue(1));
        }

        @Test
        void shouldTraverseJsonArray() {
            var json =
                    Json.createReader(new StringReader(/* language=json */ """
                    {"a": [10, 20]}""")).read();
            var result = TestUtils.traverse(json, List.of("a", "1"));
            assertThat(result).isEqualTo(Json.createValue(20));
        }

        @Test
        void shouldReturnNullForMissingPath() {
            var json =
                    Json.createReader(new StringReader(/* language=json */ """
                    {"a": 1}""")).read();
            var result = TestUtils.traverse(json, List.of("b"));
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForDeepMissingPath() {
            var json =
                    Json.createReader(new StringReader(/* language=json */ """
                            {"a": {"b": 1}}""")).read();
            var result = TestUtils.traverse(json, List.of("a", "c"));
            assertThat(result).isNull();
        }
    }

    @Nested
    class DateComparison {
        @Test
        void compareDatesShouldReturnCorrectPredicate() {
            var predicate = TestUtils.compareDates("Z", "+00:00");
            assertThat(predicate.test("2023-01-01T12:00:00+00:00", "2023-01-01T12:00:00Z"))
                    .isTrue();
            assertThat(predicate.test("2023-01-01T12:00:00", "2023-01-01T12:00:00"))
                    .isTrue();
        }

        @ParameterizedTest(name = "Non-matching Mapping {0} -> {1}")
        @CsvSource({"Z, +00:00", "Z, .000+00:00", ".000, ''", "+00:00, .000Z", "+00:00, .000+00:00"})
        void shouldNotMatchIncorrectlyFormattedDates(String target, String replacement) {
            String prefix1 = "2023-01-01T12:00:00";
            String prefix2 = "2024-01-01T12:00:00"; // Different year
            String withTarget = prefix1 + target;
            String withReplacement = prefix2 + replacement;

            assertThat(TestUtils.DATE_COMPARE_FUNCTIONS)
                    .as("Mapping %s -> %s should not match due to different date", target, replacement)
                    .noneMatch(p -> p.test(withReplacement, withTarget) || p.test(withTarget, withReplacement));
        }

        @Test
        void shouldNotMatchUnrelatedDates() {
            String oldDate = "2023-01-01T12:00:00Z";
            String newDate = "2023-01-02T12:00:00Z"; // Different day
            assertThat(TestUtils.DATE_COMPARE_FUNCTIONS).noneMatch(p -> p.test(oldDate, newDate));
        }
    }

    @Nested
    class NormalizeNonStringTypes {
        @Test
        void shouldNormalizeNumbers() {
            var original = Json.createValue(123);
            var targetType = Json.createValue(0); // number type
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue(123));
        }

        @Test
        void shouldNormalizeNumberToString() {
            var original = Json.createValue(123);
            var targetType = Json.createValue("string");
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue("123"));
        }

        @Test
        void shouldNormalizeBooleanToString() {
            var original = JsonValue.TRUE;
            var targetType = Json.createValue("string");
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue("true"));
        }

        @Test
        void shouldKeepStringAsString() {
            var original = Json.createValue("test");
            var targetType = Json.createValue("other");
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue("test"));
        }

        @Test
        void shouldHandleDoubleValuesInString() {
            var original = Json.createValue("123.45");
            var targetType = Json.createValue(0); // Expects number
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue(123.45));
        }

        @Test
        void shouldHandleLongValuesInString() {
            var original = Json.createValue("123");
            var targetType = Json.createValue(0); // Expects number
            var result = TestUtils.normalizeNonStringTypes(original, targetType);
            assertThat(result).isEqualTo(Json.createValue(123));
        }
    }

    @Nested
    class DiffJson {
        @Test
        void shouldPassForIdenticalJson() {
            @Language("json")
            String json = """
                    {"a": 1, "b": "test"}""";
            SoftAssertions softly = new SoftAssertions();
            TestUtils.diffJson(json, json, softly);
            assertThat(softly.errorsCollected()).isEmpty();
        }

        @Test
        void shouldFailForDifferentValues() {
            @Language("json")
            String oldJson = """
                    {"a": 1}""";
            @Language("json")
            String newJson = """
                    {"a": 2}""";
            SoftAssertions softly = new SoftAssertions();
            TestUtils.diffJson(oldJson, newJson, softly);
            assertThat(softly.errorsCollected()).isNotEmpty();
        }

        @Test
        @Disabled("We would like this to pass, but that causes a lot of acceptance tests to fail.")
        void shouldFailForMissingKeys() {
            @Language("json")
            String oldJson = """
                    {"a": 1}""";
            @Language("json")
            String newJson = "{}";
            SoftAssertions softly = new SoftAssertions();
            TestUtils.diffJson(oldJson, newJson, softly);
            assertThat(softly.errorsCollected()).isNotEmpty();
        }

        public static Stream<Arguments> passingPairs() {
            return Stream.of(
                    arguments(/* language=json */ """
                    {"date": "2023-01-01T12:00:00Z"}""", /* language=json */ """
                    {"date": "2023-01-01T12:00:00+00:00"}""", "allowed date differences"),
                    arguments(/* language=json */ """
                    {"date": 1672574400}""", /* language=json */ """
                    {"date": "2023-01-01T12:00:00Z"}""", "timestamp to iso conversion"),
                    arguments(
                            /* language=json */ """
                    {"date": 1672574400}""", /* language=json */
                            """
                    {"date": "2023-01-01T12:00:00.000Z"}""",
                            "timestamp to iso conversion with milliseconds"));
        }

        @ParameterizedTest
        @MethodSource("passingPairs")
        void shouldPass(@Language("json") String oldJson, @Language("json") String newJson, String description) {
            SoftAssertions softly = new SoftAssertions();
            TestUtils.diffJson(oldJson, newJson, softly);
            assertThat(softly.errorsCollected()).as(description).isEmpty();
        }
    }

    @Nested
    class ParseAndCompare {
        @Test
        void shouldParseAndCompareSuccessfully() {
            @Language("json")
            String json = """
                    {"key": "value"}""";
            SoftAssertions softly = new SoftAssertions();
            var result = TestUtils.parseAndCompare(new TypeReference<Map<String, String>>() {}, json, softly);
            assertThat(softly.errorsCollected()).isEmpty();
            assertThat(result).containsEntry("key", "value");
        }

        @Test
        void shouldParseAndCompareSuccessfullyWithJackson2() {
            @Language("json")
            String json = """
                    {"key": "value"}""";
            SoftAssertions softly = new SoftAssertions();
            var result = TestUtils.parseAndCompare(
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}, json, softly);
            assertThat(softly.errorsCollected()).isEmpty();
            assertThat(result).containsEntry("key", "value");
        }
    }
}
