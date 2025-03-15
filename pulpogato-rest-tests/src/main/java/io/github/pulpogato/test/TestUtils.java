package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.assertj.core.api.SoftAssertions;

import javax.json.*;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
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
        final JsonValue source = Json.createReader(new StringReader(input)).readValue();
        final JsonValue target = Json.createReader(new StringReader(output)).readValue();

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

    private static void compareOldAndNew(final SoftAssertions softly, final JsonValue oldValue1, final JsonValue newValue1, final String op, final String path) {
        final JsonValue newValue = flattenNewArray(oldValue1, newValue1);
        final JsonValue oldValue = normalizeNonStringTypes(oldValue1, newValue);

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

    private static JsonValue normalizeNonStringTypes(final JsonValue oldValue, final JsonValue newValue) {
        final var convertableTypes = Set.of(
                JsonValue.ValueType.FALSE,
                JsonValue.ValueType.TRUE,
                JsonValue.ValueType.NUMBER
        );
        if (newValue.getValueType() == JsonValue.ValueType.STRING && convertableTypes.contains(oldValue.getValueType()) ) {
            return Json.createValue(oldValue.toString());
        }
        return oldValue;
    }

    private static JsonValue flattenNewArray(final JsonValue oldValue, final JsonValue newValue) {
        if (oldValue.getValueType() == JsonValue.ValueType.STRING && newValue.getValueType() == JsonValue.ValueType.ARRAY) {
            if (newValue.asJsonArray().size() == 1) {
                return newValue.asJsonArray().getFirst();
            }
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
