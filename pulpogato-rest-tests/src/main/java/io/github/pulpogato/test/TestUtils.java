package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.assertj.core.api.SoftAssertions;

import javax.json.*;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
     * @throws JsonProcessingException If there is an error parsing the JSON
     */
    public static <T> T parseAndCompare(final TypeReference<T> typeReference, final String input, final SoftAssertions softly) throws JsonProcessingException {
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
        try (
                final var sourceReader = Json.createReader(new StringReader(input));
                final var targetReader = Json.createReader(new StringReader(output))
        ) {
            final JsonValue source = sourceReader.readValue();
            final JsonValue target = targetReader.readValue();

            if (source instanceof JsonObject && target instanceof JsonObject) {
                assertOnDiff(softly, Json.createDiff(source.asJsonObject(), target.asJsonObject()), source);
            } else if (source instanceof JsonArray && target instanceof JsonArray) {
                assertOnDiff(softly, Json.createDiff(source.asJsonArray(), target.asJsonArray()), source);
            } else if (source == null) {
                softly.fail("Invalid source: " + null);
            } else if (target == null) {
                softly.fail("Invalid target: " + null);
            } else {
                softly.fail("Invalid inputs:: Source:" + source.getValueType() + " Target:" + target.getValueType());
            }
        }
    }

    private static void assertOnDiff(final SoftAssertions softly, final JsonPatch diff, final JsonValue source) {
        diff.toJsonArray()
                .stream()
                .map(JsonValue::asJsonObject)
                .forEach(it -> {
                    final String op = it.getString("op");
                    final String path = it.getString("path");
                    final JsonValue newValue = it.get("value");
                    if (op.equals("replace")) {
                        final var pathSteps = Arrays.stream(path.split("/")).dropWhile(String::isEmpty).toList();
                        final JsonValue oldValue = traverse(source, pathSteps);
                        if (oldValue == null) {
                            softly.fail(MessageFormat.format("Changes found: {0} {1} <missing> -> {2}", op, path, newValue));
                        } else {
                            compareOldAndNew(softly, oldValue, newValue, op, path);
                        }
                    } else if (op.equals("remove") && newValue != null) {
                        softly.fail(MessageFormat.format("Changes found: {0} {1} {2}", op, path, newValue));
                    }
                });
    }

    private static void compareOldAndNew(final SoftAssertions softly, final JsonValue oldValue, final JsonValue newValue1, final String op, final String path) {
        final JsonValue newValue2 = flattenNewArray(oldValue, newValue1);
        final JsonValue newValue = normalizeNonStringTypes(newValue2, oldValue);

        if (oldValue.getValueType() == JsonValue.ValueType.STRING && newValue.getValueType() == JsonValue.ValueType.STRING) {
            if (oldValue.toString().equals(newValue.toString().replace("Z", "+00:00"))) {
                return;
            }
            if (oldValue.toString().equals(newValue.toString().replace("Z", ".000Z"))) {
                return;
            }
        }

        if (!oldValue.toString().equals(newValue.toString())) {
            softly.fail(MessageFormat.format("Changes found: {0} {1} {2}({3}) -> {4}({5})",
                    op, path, oldValue, oldValue.getValueType(), newValue, newValue.getValueType()));
        }
    }

    private static JsonValue normalizeNonStringTypes(final JsonValue valueSource, final JsonValue typeSource) {

        String stringValue = switch(valueSource.getValueType()) {
            case NUMBER -> ((JsonNumber) valueSource).numberValue().toString();
            case TRUE, FALSE -> Boolean.toString(valueSource == JsonValue.TRUE);
            case STRING -> ((JsonString) valueSource).getString();
            default ->  valueSource.toString();
        };

        switch (typeSource.getValueType()) {
            case NUMBER:
                try {
                    if (stringValue.contains(".")) {
                        return Json.createValue(Double.parseDouble(stringValue));
                    } else {
                        return Json.createValue(Long.parseLong(stringValue));
                    }
                } catch (NumberFormatException e) {
                    return valueSource;
                }
            case TRUE, FALSE:
                boolean boolValue = Boolean.parseBoolean(stringValue);
                return boolValue ? JsonValue.TRUE : JsonValue.FALSE;
            case STRING:
                return Json.createValue(stringValue);
            default:
                return valueSource;
        }
    }

    private static JsonValue flattenNewArray(final JsonValue oldValue, final JsonValue newValue) {
        if (oldValue.getValueType() == JsonValue.ValueType.STRING && newValue.getValueType() == JsonValue.ValueType.ARRAY && newValue.asJsonArray().size() == 1) {
            return newValue.asJsonArray().getFirst();
        }
        return newValue;
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
            return traverse(value.asJsonArray().get(Integer.parseInt(pathStep)), pathSteps.subList(1, pathSteps.size()));
        } else {
            return traverse(value.asJsonObject().get(pathStep), pathSteps.subList(1, pathSteps.size()));
        }
    }
}
