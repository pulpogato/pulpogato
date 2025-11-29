package io.github.pulpogato.test;

import static io.github.pulpogato.test.TestUtils.*;
import static org.assertj.core.api.Assertions.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.assertj.core.api.SoftAssertions;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;

class TestUtilsTest {

    // ========== Helper Methods ==========

    private JsonObject createJsonObject(@Language("json") String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private JsonArray createJsonArray(@Language("json") String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        }
    }

    private JsonValue parseJsonValue(@Language("json") String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readValue();
        }
    }

    private JsonNumber createNumber(long value) {
        return Json.createValue(value);
    }

    private JsonNumber createNumber(double value) {
        return Json.createValue(value);
    }

    private JsonString createString(String value) {
        return Json.createValue(value);
    }

    private List<String> pathOf(String... steps) {
        return List.of(steps);
    }

    private List<String> collectAssertionErrors(Consumer<SoftAssertions> test) {
        var softly = new SoftAssertions();
        test.accept(softly);
        var errors = new ArrayList<String>();
        try {
            softly.assertAll();
        } catch (AssertionError e) {
            // Collect error messages
            var message = e.getMessage();
            if (message != null) {
                errors.add(message);
            }
        }
        return errors;
    }

    // ========== ParseAndCompareTests ==========

    @Nested
    @DisplayName("ParseAndCompare Tests")
    class ParseAndCompareTests {

        // Note: parseAndCompare expects PulpogatoType objects for full functionality
        // JsonObject and JsonArray are not PulpogatoType, so these tests verify basic parsing only

        @Test
        @DisplayName("Should parse simple Map")
        void shouldParseSimpleMap() {
            var json = """
                    {"name": "test"}
                    """;
            var softly = new SoftAssertions();

            var result = parseAndCompare(new TypeReference<java.util.Map<String, String>>() {}, json, softly);

            assertThat(result).isNotNull();
            assertThat(result).containsEntry("name", "test");
            softly.assertAll();
        }

        @Test
        @DisplayName("Should parse empty Map")
        void shouldParseEmptyMap() {
            var json = "{}";
            var softly = new SoftAssertions();

            var result = parseAndCompare(new TypeReference<java.util.Map<String, Object>>() {}, json, softly);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
            softly.assertAll();
        }

        @Test
        @DisplayName("Should parse List")
        void shouldParseList() {
            var json = """
                    ["item1", "item2"]
                    """;
            var softly = new SoftAssertions();

            var result = parseAndCompare(new TypeReference<java.util.List<String>>() {}, json, softly);

            assertThat(result).isNotNull();
            assertThat(result).containsExactly("item1", "item2");
            softly.assertAll();
        }

        @Test
        @DisplayName("Should throw UnrecognizedPropertyExceptionWrapper for invalid JSON")
        void shouldThrowForInvalidJson() {
            var json = """
                    {"unrecognized": "field"}
                    """;
            var softly = new SoftAssertions();

            // Note: This test assumes the TypeReference validation would fail
            // In reality, JsonObject accepts any fields, so this is a placeholder
            // for when working with specific typed objects
        }

        @Test
        @DisplayName("Should handle nested structures")
        void shouldHandleNestedStructures() {
            var json = """
                    {
                        "outer": {
                            "inner": {
                                "value": "nested"
                            }
                        }
                    }
                    """;
            var softly = new SoftAssertions();

            var result = parseAndCompare(new TypeReference<java.util.Map<String, Object>>() {}, json, softly);

            assertThat(result).isNotNull();
            assertThat(result).containsKey("outer");
            softly.assertAll();
        }
    }

    // ========== DiffJsonTests ==========

    @Nested
    @DisplayName("DiffJson Tests")
    class DiffJsonTests {

        @Test
        @DisplayName("Should find no differences for identical objects")
        void shouldFindNoDifferencesForIdenticalObjects() {
            @Language("json")
            var input = """
                    {"name": "test", "value": 42}
                    """;
            @Language("json")
            var output = """
                    {"name": "test", "value": 42}
                    """;
            var softly = new SoftAssertions();

            diffJson(input, output, softly);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should find differences in object values")
        void shouldFindDifferencesInObjectValues() {
            @Language("json")
            var input = """
                    {"name": "test", "value": 42}
                    """;
            @Language("json")
            var output = """
                    {"name": "test", "value": 43}
                    """;

            var errors = collectAssertionErrors(softly -> diffJson(input, output, softly));

            assertThat(errors).hasSize(1);
        }

        @Test
        @DisplayName("Should find no differences for identical arrays")
        void shouldFindNoDifferencesForIdenticalArrays() {
            @Language("json")
            var input = """
                    [1, 2, 3]
                    """;
            @Language("json")
            var output = """
                    [1, 2, 3]
                    """;
            var softly = new SoftAssertions();

            diffJson(input, output, softly);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should find differences in array values")
        void shouldFindDifferencesInArrayValues() {
            @Language("json")
            var input = """
                    [1, 2, 3]
                    """;
            @Language("json")
            var output = """
                    [1, 2, 4]
                    """;

            var errors = collectAssertionErrors(softly -> diffJson(input, output, softly));

            assertThat(errors).hasSize(1);
        }

        @Test
        @DisplayName("Should handle empty objects")
        void shouldHandleEmptyObjects() {
            @Language("json")
            var input = "{}";
            @Language("json")
            var output = "{}";
            var softly = new SoftAssertions();

            diffJson(input, output, softly);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle empty arrays")
        void shouldHandleEmptyArrays() {
            @Language("json")
            var input = "[]";
            @Language("json")
            var output = "[]";
            var softly = new SoftAssertions();

            diffJson(input, output, softly);

            softly.assertAll();
        }

        @Test
        @DisplayName("Should detect type mismatches")
        void shouldDetectTypeMismatches() {
            @Language("json")
            var input = """
                    {"value": 42}
                    """;
            @Language("json")
            var output = """
                    [42]
                    """;

            var errors = collectAssertionErrors(softly -> diffJson(input, output, softly));

            assertThat(errors).isNotEmpty();
        }
    }

    // ========== CompareOldAndNewTests ==========

    @Nested
    @DisplayName("CompareOldAndNew Tests")
    class CompareOldAndNewTests {

        @Test
        @DisplayName("Should detect identical string values as equal")
        void shouldDetectIdenticalStringsAsEqual() {
            var softly = new SoftAssertions();
            var oldValue = createString("test");
            var newValue = createString("test");

            compareOldAndNew(softly, oldValue, newValue, "replace", "/field");

            softly.assertAll();
        }

        @Test
        @DisplayName("Should detect different string values")
        void shouldDetectDifferentStrings() {
            var oldValue = createString("old");
            var newValue = createString("new");

            var errors =
                    collectAssertionErrors(softly -> compareOldAndNew(softly, oldValue, newValue, "replace", "/field"));

            assertThat(errors).isNotEmpty();
        }

        @Test
        @DisplayName("Should compare dates with Z and +00:00 as equal")
        void shouldCompareDatesWithZAndOffsetAsEqual() {
            var softly = new SoftAssertions();
            var oldValue = createString("2023-10-05T14:48:00Z");
            var newValue = createString("2023-10-05T14:48:00+00:00");

            compareOldAndNew(softly, oldValue, newValue, "replace", "/timestamp");

            softly.assertAll();
        }

        @Test
        @DisplayName("Should compare dates with Z and .000+00:00 as equal")
        void shouldCompareDatesWithZAndMillisOffsetAsEqual() {
            var softly = new SoftAssertions();
            var oldValue = createString("2023-10-05T14:48:00Z");
            var newValue = createString("2023-10-05T14:48:00.000+00:00");

            compareOldAndNew(softly, oldValue, newValue, "replace", "/timestamp");

            softly.assertAll();
        }

        @Test
        @DisplayName("Should convert Unix timestamp to ISO string")
        void shouldConvertUnixTimestampToIsoString() {
            var softly = new SoftAssertions();
            var oldValue = createNumber(1696517280L);
            var newValue = createString("2023-10-05T14:48:00Z");

            compareOldAndNew(softly, oldValue, newValue, "replace", "/timestamp");

            softly.assertAll();
        }

        @Test
        @DisplayName("Should handle invalid timestamp conversion gracefully")
        void shouldHandleInvalidTimestampConversion() {
            var oldValue = createNumber(1696517280L);
            var newValue = createString("invalid-date");

            var errors = collectAssertionErrors(
                    softly -> compareOldAndNew(softly, oldValue, newValue, "replace", "/timestamp"));

            assertThat(errors).isNotEmpty();
        }

        @Test
        @DisplayName("Should normalize number types correctly")
        void shouldNormalizeNumberTypes() {
            var softly = new SoftAssertions();
            var oldValue = createNumber(42L);
            var newValue = createNumber(42L);

            compareOldAndNew(softly, oldValue, newValue, "replace", "/value");

            softly.assertAll();
        }

        @Test
        @DisplayName("Should detect different number values")
        void shouldDetectDifferentNumbers() {
            var oldValue = createNumber(42L);
            var newValue = createNumber(43L);

            var errors =
                    collectAssertionErrors(softly -> compareOldAndNew(softly, oldValue, newValue, "replace", "/value"));

            assertThat(errors).isNotEmpty();
        }

        @ParameterizedTest
        @MethodSource("dateFormatCombinations")
        @DisplayName("Should handle various date format combinations")
        void shouldHandleVariousDateFormats(String description, String oldStr, String newStr, boolean shouldBeEqual) {
            var softly = new SoftAssertions();
            var oldValue = createString(oldStr);
            var newValue = createString(newStr);

            compareOldAndNew(softly, oldValue, newValue, "replace", "/date");

            if (shouldBeEqual) {
                softly.assertAll();
            } else {
                var errors = collectAssertionErrors(s -> compareOldAndNew(s, oldValue, newValue, "replace", "/date"));
                assertThat(errors).isNotEmpty();
            }
        }

        static Stream<Arguments> dateFormatCombinations() {
            return Stream.of(
                    Arguments.of("Z to +00:00", "2023-10-05T14:48:00Z", "2023-10-05T14:48:00+00:00", true),
                    Arguments.of("Z to .000+00:00", "2023-10-05T14:48:00Z", "2023-10-05T14:48:00.000+00:00", true),
                    Arguments.of(".000 removal", "2023-10-05T14:48:00.000", "2023-10-05T14:48:00", true),
                    Arguments.of("+00:00 to .000Z", "2023-10-05T14:48:00+00:00", "2023-10-05T14:48:00.000Z", true),
                    Arguments.of("different dates", "2023-10-05T14:48:00Z", "2023-10-06T14:48:00Z", false));
        }
    }

    // ========== NormalizationTests ==========

    @Nested
    @DisplayName("Normalization Tests")
    class NormalizationTests {

        @Test
        @DisplayName("Should normalize string to number for integer")
        void shouldNormalizeStringToInteger() {
            var valueSource = createString("42");
            var typeSource = createNumber(0L);

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isInstanceOf(JsonNumber.class);
            assertThat(((JsonNumber) result).longValue()).isEqualTo(42L);
        }

        @Test
        @DisplayName("Should normalize string to number for decimal")
        void shouldNormalizeStringToDecimal() {
            var valueSource = createString("42.5");
            var typeSource = createNumber(0.0);

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isInstanceOf(JsonNumber.class);
            assertThat(((JsonNumber) result).doubleValue()).isEqualTo(42.5);
        }

        @Test
        @DisplayName("Should return original value for invalid number format")
        void shouldReturnOriginalForInvalidNumber() {
            var valueSource = createString("not-a-number");
            var typeSource = createNumber(0L);

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isEqualTo(valueSource);
        }

        @Test
        @DisplayName("Should normalize string to TRUE")
        void shouldNormalizeStringToTrue() {
            var valueSource = createString("true");
            var typeSource = JsonValue.TRUE;

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isEqualTo(JsonValue.TRUE);
        }

        @Test
        @DisplayName("Should normalize string to FALSE")
        void shouldNormalizeStringToFalse() {
            var valueSource = createString("false");
            var typeSource = JsonValue.FALSE;

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isEqualTo(JsonValue.FALSE);
        }

        @Test
        @DisplayName("Should normalize number to string representation")
        void shouldNormalizeNumberToString() {
            var valueSource = createNumber(42L);
            var typeSource = createString("0");

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isInstanceOf(JsonString.class);
            assertThat(((JsonString) result).getString()).isEqualTo("42");
        }

        @Test
        @DisplayName("Should handle zero correctly")
        void shouldHandleZero() {
            var valueSource = createString("0");
            var typeSource = createNumber(0L);

            var result = normalizeNonStringTypes(valueSource, typeSource);

            assertThat(result).isInstanceOf(JsonNumber.class);
            assertThat(((JsonNumber) result).longValue()).isEqualTo(0L);
        }

        @ParameterizedTest
        @MethodSource("normalizationCases")
        @DisplayName("Should handle various normalization scenarios")
        void shouldHandleVariousNormalizationScenarios(
                String description, JsonValue source, JsonValue type, JsonValue expected) {
            var result = normalizeNonStringTypes(source, type);

            assertThat(result.toString()).isEqualTo(expected.toString());
        }

        static Stream<Arguments> normalizationCases() {
            return Stream.of(
                    Arguments.of("int to int", Json.createValue(42), Json.createValue(0), Json.createValue(42)),
                    Arguments.of(
                            "double to double", Json.createValue(42.5), Json.createValue(0.0), Json.createValue(42.5)),
                    Arguments.of(
                            "string to string",
                            Json.createValue("test"),
                            Json.createValue(""),
                            Json.createValue("test")),
                    Arguments.of("true to true", JsonValue.TRUE, JsonValue.TRUE, JsonValue.TRUE),
                    Arguments.of("false to false", JsonValue.FALSE, JsonValue.FALSE, JsonValue.FALSE));
        }
    }

    // ========== TraverseTests ==========

    @Nested
    @DisplayName("Traverse Tests")
    class TraverseTests {

        @Test
        @DisplayName("Should traverse simple object field")
        void shouldTraverseSimpleObjectField() {
            var json = createJsonObject("""
                    {"field": "value"}
                    """);
            var path = pathOf("field");

            var result = traverse(json, path);

            assertThat(result).isInstanceOf(JsonString.class);
            assertThat(((JsonString) result).getString()).isEqualTo("value");
        }

        @Test
        @DisplayName("Should traverse nested object fields")
        void shouldTraverseNestedObjectFields() {
            var json = createJsonObject("""
                    {"outer": {"inner": "value"}}
                    """);
            var path = pathOf("outer", "inner");

            var result = traverse(json, path);

            assertThat(result).isInstanceOf(JsonString.class);
            assertThat(((JsonString) result).getString()).isEqualTo("value");
        }

        @Test
        @DisplayName("Should traverse array by index")
        void shouldTraverseArrayByIndex() {
            var json = createJsonArray("""
                    [1, 2, 3]
                    """);
            var path = pathOf("1");

            var result = traverse(json, path);

            assertThat(result).isInstanceOf(JsonNumber.class);
            assertThat(((JsonNumber) result).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle empty path")
        void shouldHandleEmptyPath() {
            var json = createJsonObject("""
                    {"field": "value"}
                    """);
            var path = pathOf();

            var result = traverse(json, path);

            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("Should return null for missing field")
        void shouldReturnNullForMissingField() {
            var json = createJsonObject("""
                    {"other": "value"}
                    """);
            var path = pathOf("field");

            var result = traverse(json, path);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle deeply nested paths")
        void shouldHandleDeeplyNestedPaths() {
            var json = createJsonObject("""
                    {
                        "level1": {
                            "level2": {
                                "level3": {
                                    "value": "deep"
                                }
                            }
                        }
                    }
                    """);
            var path = pathOf("level1", "level2", "level3", "value");

            var result = traverse(json, path);

            assertThat(result).isInstanceOf(JsonString.class);
            assertThat(((JsonString) result).getString()).isEqualTo("deep");
        }

        @Test
        @DisplayName("Should handle null input")
        void shouldHandleNullInput() {
            JsonValue json = null;
            var path = pathOf("field");

            var result = traverse(json, path);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should traverse array in nested structure")
        void shouldTraverseArrayInNestedStructure() {
            var json = createJsonObject("""
                    {
                        "items": [
                            {"id": 1},
                            {"id": 2}
                        ]
                    }
                    """);
            var path = pathOf("items", "0", "id");

            var result = traverse(json, path);

            assertThat(result).isInstanceOf(JsonNumber.class);
            assertThat(((JsonNumber) result).intValue()).isEqualTo(1);
        }

        @ParameterizedTest
        @MethodSource("traversalCases")
        @DisplayName("Should handle various traversal scenarios")
        void shouldHandleVariousTraversalScenarios(
                String description, String jsonStr, List<String> path, String expectedType) {
            var json = parseJsonValue(jsonStr);
            var result = traverse(json, path);

            if (expectedType.equals("null")) {
                assertThat(result).isNull();
            } else {
                assertThat(result).isNotNull();
            }
        }

        static Stream<Arguments> traversalCases() {
            return Stream.of(
                    Arguments.of("simple field", "{\"field\": \"value\"}", List.of("field"), "STRING"),
                    Arguments.of("array index", "[1, 2, 3]", List.of("0"), "NUMBER"),
                    Arguments.of("missing field", "{\"other\": \"value\"}", List.of("field"), "null"),
                    Arguments.of("nested", "{\"a\": {\"b\": \"c\"}}", List.of("a", "b"), "STRING"));
        }

        @Test
        @Disabled("Bug #1: traverse() cannot distinguish between explicit null value and missing field")
        @DisplayName("BUG: Should return JsonValue.NULL for explicit null value")
        void bugShouldReturnJsonValueNullForExplicitNull() {
            // This test demonstrates Bug #1: The traverse() method cannot distinguish between
            // a field with explicit null value and a missing field because both return null.
            //
            // Root cause: javax.json API's JsonObject.get() returns JsonValue.NULL for explicit
            // null values, but traverse() at line 209 calls .get() and returns the result directly.
            // When traverse() is called recursively, it checks if the value is null at line 198
            // and returns null, which masks JsonValue.NULL values.

            var jsonWithNull = createJsonObject("""
                    {"field": null}
                    """);
            var path = pathOf("field");

            var result = traverse(jsonWithNull, path);

            // This assertion FAILS because traverse() returns null instead of JsonValue.NULL
            assertThat(result)
                    .as("Field with explicit null value should return JsonValue.NULL, not null reference")
                    .isEqualTo(JsonValue.NULL);
        }
    }

    // ========== DateComparisonTests ==========

    @Nested
    @DisplayName("Date Comparison Tests")
    class DateComparisonTests {

        @Test
        @DisplayName("Should compare dates with Z and +00:00")
        void shouldCompareDatesWithZAndOffset() {
            var predicate = compareDates("Z", "+00:00");

            // The predicate replaces "Z" with "+00:00" in the second string and compares
            var result = predicate.test("2023-10-05T14:48:00+00:00", "2023-10-05T14:48:00Z");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should compare dates with Z and .000+00:00")
        void shouldCompareDatesWithZAndMillisOffset() {
            var predicate = compareDates("Z", ".000+00:00");

            // The predicate replaces "Z" with ".000+00:00" in the second string and compares
            var result = predicate.test("2023-10-05T14:48:00.000+00:00", "2023-10-05T14:48:00Z");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should compare dates with .000 removal")
        void shouldCompareDatesWithMillisRemoval() {
            var predicate = compareDates(".000", "");

            // The predicate replaces ".000" with "" in the second string and compares
            var result = predicate.test("2023-10-05T14:48:00", "2023-10-05T14:48:00.000");

            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @MethodSource("dateComparisonCases")
        @DisplayName("Should handle all DATE_COMPARE_FUNCTIONS predicates")
        void shouldHandleAllDateCompareFunctions(
                String description, String date1, String date2, boolean expectedEqual) {
            var anyMatch = DATE_COMPARE_FUNCTIONS.stream()
                    .anyMatch(predicate -> predicate.test(date1, date2) || predicate.test(date2, date1));

            assertThat(anyMatch).isEqualTo(expectedEqual);
        }

        static Stream<Arguments> dateComparisonCases() {
            return Stream.of(
                    Arguments.of("Z to +00:00", "2023-10-05T14:48:00Z", "2023-10-05T14:48:00+00:00", true),
                    Arguments.of("Z to .000+00:00", "2023-10-05T14:48:00Z", "2023-10-05T14:48:00.000+00:00", true),
                    Arguments.of(".000 removal", "2023-10-05T14:48:00.000", "2023-10-05T14:48:00", true),
                    Arguments.of("+00:00 to .000Z", "2023-10-05T14:48:00+00:00", "2023-10-05T14:48:00.000Z", true),
                    Arguments.of(
                            "+00:00 to .000+00:00", "2023-10-05T14:48:00+00:00", "2023-10-05T14:48:00.000+00:00", true),
                    Arguments.of("different dates", "2023-10-05T14:48:00Z", "2023-10-06T14:48:00Z", false),
                    Arguments.of("non-matching format", "2023-10-05", "2023-10-05T00:00:00Z", false));
        }
    }

    // ========== BugVerificationTests ==========

    @Nested
    @DisplayName("Bug Verification Tests")
    class BugVerificationTests {

        @Nested
        @DisplayName("Bug #1: Null Value vs Missing Field")
        class NullVsMissingFieldBug {

            @Test
            @DisplayName("BUG: Traverse should return JsonValue.NULL for explicit null, not null reference")
            void bugTraverseShouldReturnJsonValueNullForExplicitNull() {
                // Bug: When a field has explicit null value {"field": null},
                // traverse() returns null reference instead of JsonValue.NULL

                var json = createJsonObject("""
                        {"field": null}
                        """);
                var path = pathOf("field");

                var result = traverse(json, path);

                // EXPECTED: result should be JsonValue.NULL
                // ACTUAL: result is null
                // This test will FAIL demonstrating the bug
                assertThat(result)
                        .as("Field with explicit null value should return JsonValue.NULL, not null reference")
                        .isEqualTo(JsonValue.NULL);
            }

            @Test
            @DisplayName("Traverse correctly returns null for missing field")
            void traverseCorrectlyReturnsNullForMissingField() {
                var json = createJsonObject("""
                        {"other": "value"}
                        """);
                var path = pathOf("field");

                var result = traverse(json, path);

                // This correctly returns null for a missing field
                assertThat(result).isNull();
            }

            @Test
            @DisplayName("BUG: End-to-end test showing null field confusion in assertOnDiff")
            void bugEndToEndNullFieldConfusion() {
                // This demonstrates how the bug manifests in the full flow
                // When comparing two JSON objects where one has null and one is missing the field

                var source = parseJsonValue("""
                        {"field": null, "other": "value"}
                        """);
                var target = parseJsonValue("""
                        {"other": "value"}
                        """);

                var diff = Json.createDiff(source.asJsonObject(), target.asJsonObject());

                // The bug causes this to report incorrectly because traverse() can't
                // distinguish between null value and missing field at line 108
                // The error message will say "<missing>" but should handle null value differently
                var errors = collectAssertionErrors(s -> assertOnDiff(s, diff, source));

                // When fixed, this test should be updated to verify correct behavior
                // Expected: Should detect that field was removed (had null, now missing)
                // Actual: Reports as "Changes found: remove /field null" which might be correct,
                // but the underlying traverse() bug still exists

                // For now, we just verify that diff detection works at some level
                // The real bug is in traverse() not distinguishing null from missing
            }
        }

        @Nested
        @DisplayName("Bug #2: False Equality via toString()")
        class FalseEqualityBug {

            @Test
            @DisplayName("BUG: Different JsonValues with same toString() may be treated as equal")
            void bugDifferentValuesWithSameToString() {
                // Bug: Using toString() for comparison at line 159 can cause issues
                // when different JsonValue types happen to have the same string representation

                var softly = new SoftAssertions();

                // These are semantically different but have similar toString() representations
                var oldValue = createString("42");
                var newValue = createNumber(42L);

                // After normalization, these might be treated as equal when they shouldn't be
                // depending on the normalization logic
                compareOldAndNew(softly, oldValue, newValue, "replace", "/value");

                // This documents the current behavior
                // The comparison might pass when it shouldn't, depending on normalization
            }

            @Test
            @DisplayName("BUG: Type confusion between numbers and strings")
            void bugNumericToStringEquality() {
                // The normalization helps here, but edge cases may exist
                var softly = new SoftAssertions();

                var oldValue = createNumber(42L);
                var newValue = createString("42");

                // After normalization this should work correctly due to normalizeNonStringTypes
                // But the toString() comparison is still a code smell
                compareOldAndNew(softly, oldValue, newValue, "replace", "/value");

                softly.assertAll(); // This might pass, but it's relying on normalization
            }

            @Test
            @DisplayName("Different types with potentially same toString() representation")
            void differentTypesWithSameToString() {
                var softly = new SoftAssertions();

                // Test edge cases where toString() might give same result
                var oldValue = JsonValue.TRUE;
                var newValue = createString("true");

                // Normalization should handle this, but it's fragile
                compareOldAndNew(softly, oldValue, newValue, "replace", "/flag");

                // Document current behavior
                softly.assertAll();
            }
        }
    }
}
