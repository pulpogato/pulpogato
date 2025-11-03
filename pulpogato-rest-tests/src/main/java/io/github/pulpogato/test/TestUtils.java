package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonPatch;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.assertj.core.api.SoftAssertions;

public class TestUtils {
    private TestUtils() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    /**
     * Parses the input and compares it to the generated JSON.
     *
     * @param typeReference The type reference to parse to
     * @param input         The input
     * @param <T>           The type
     * @return The parsed object
     */
    public static <T> T parseAndCompare(
            final TypeReference<T> typeReference, final String input, final SoftAssertions softly) {
        try {
            final T parsed = OBJECT_MAPPER.readValue(input, typeReference);
            final String generated = OBJECT_MAPPER.writeValueAsString(parsed);
            diffJson(input, generated, softly);
            return parsed;
        } catch (JacksonException e) {
            throw new UnrecognizedPropertyExceptionWrapper(e, input);
        }
    }

    public static void diffJson(final String input, final String output, final SoftAssertions softly) {
        try (final var sourceReader = Json.createReader(new StringReader(input));
                final var targetReader = Json.createReader(new StringReader(output))) {
            final JsonValue source = sourceReader.readValue();
            final JsonValue target = targetReader.readValue();

            if (source instanceof JsonObject && target instanceof JsonObject) {
                assertOnDiff(softly, Json.createDiff(source.asJsonObject(), target.asJsonObject()), source);
            } else if (source instanceof JsonArray && target instanceof JsonArray) {
                assertOnDiff(softly, Json.createDiff(source.asJsonArray(), target.asJsonArray()), source);
            } else if (source == null) {
                softly.fail("Invalid source: null");
            } else if (target == null) {
                softly.fail("Invalid target: null");
            } else {
                softly.fail("Invalid inputs:: Source:" + source.getValueType() + " Target:" + target.getValueType());
            }
        }
    }

    private static void assertOnDiff(final SoftAssertions softly, final JsonPatch diff, final JsonValue source) {
        diff.toJsonArray().stream().map(JsonValue::asJsonObject).forEach(it -> {
            final String op = it.getString("op");
            final String path = it.getString("path");
            final JsonValue newValue = it.get("value");
            if (op.equals("replace")) {
                final var pathSteps = Arrays.stream(path.split("/"))
                        .dropWhile(String::isEmpty)
                        .toList();
                final JsonValue oldValue = traverse(source, pathSteps);
                if (oldValue == null) {
                    softly.fail("Changes found: %s %s <missing> -> %s", op, path, newValue);
                } else {
                    compareOldAndNew(softly, oldValue, newValue, op, path);
                }
            } else if (op.equals("remove") && newValue != null) {
                softly.fail("Changes found: %s %s %s", op, path, newValue);
            }
        });
    }

    private static void compareOldAndNew(
            final SoftAssertions softly,
            final JsonValue oldValue,
            final JsonValue newValue1,
            final String op,
            final String path) {
        final JsonValue newValue = normalizeNonStringTypes(newValue1, oldValue);

        if (oldValue.getValueType() == JsonValue.ValueType.STRING
                && newValue.getValueType() == JsonValue.ValueType.STRING) {
            var oldStr = ((JsonString) oldValue).getString();
            var newStr = ((JsonString) newValue).getString();

            if (oldStr.equals(newStr.replace("Z", "+00:00"))) {
                return;
            }
            if (oldStr.equals(newStr.replace("Z", ".000Z"))) {
                return;
            }
            // Handle .000+00:00 vs Z normalization
            if (oldStr.replace(".000+00:00", "Z").equals(newStr)) {
                return;
            }
        }

        // Handle Unix timestamp (NUMBER) to ISO string (STRING) conversion
        if (oldValue.getValueType() == JsonValue.ValueType.NUMBER
                && newValue.getValueType() == JsonValue.ValueType.STRING) {
            try {
                var timestamp = ((JsonNumber) oldValue).longValue();
                var isoString = ((JsonString) newValue).getString();
                // Convert timestamp to ISO string and compare
                var timestampAsIso = java.time.Instant.ofEpochSecond(timestamp)
                        .atOffset(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                if (timestampAsIso.equals(isoString)) {
                    return;
                }
            } catch (Exception e) {
                // If conversion fails, fall through to regular comparison
            }
        }

        if (!oldValue.toString().equals(newValue.toString())) {
            softly.fail(
                    "Changes found: %s %s %s(%s) -> %s(%s)",
                    op, path, oldValue, oldValue.getValueType(), newValue, newValue.getValueType());
        }
    }

    private static JsonValue normalizeNonStringTypes(final JsonValue valueSource, final JsonValue typeSource) {

        String stringValue =
                switch (valueSource.getValueType()) {
                    case NUMBER -> ((JsonNumber) valueSource).numberValue().toString();
                    case TRUE, FALSE -> Boolean.toString(valueSource == JsonValue.TRUE);
                    case STRING -> ((JsonString) valueSource).getString();
                    default -> valueSource.toString();
                };

        return switch (typeSource.getValueType()) {
            case NUMBER -> getNumericValue(valueSource, stringValue);
            case TRUE -> JsonValue.TRUE;
            case FALSE -> JsonValue.FALSE;
            case STRING -> Json.createValue(stringValue);
            default -> valueSource;
        };
    }

    private static JsonValue getNumericValue(JsonValue valueSource, String stringValue) {
        try {
            if (stringValue.contains(".")) {
                return Json.createValue(Double.parseDouble(stringValue));
            } else {
                return Json.createValue(Long.parseLong(stringValue));
            }
        } catch (NumberFormatException e) {
            return valueSource;
        }
    }

    private static JsonValue traverse(final JsonValue value, final List<String> pathSteps) {
        if (value == null) {
            return null;
        }
        if (pathSteps.isEmpty()) {
            return value;
        }
        final var pathStep = pathSteps.getFirst();
        if (pathStep.matches("\\d+")) {
            return traverse(
                    value.asJsonArray().get(Integer.parseInt(pathStep)), pathSteps.subList(1, pathSteps.size()));
        } else {
            return traverse(value.asJsonObject().get(pathStep), pathSteps.subList(1, pathSteps.size()));
        }
    }
}
