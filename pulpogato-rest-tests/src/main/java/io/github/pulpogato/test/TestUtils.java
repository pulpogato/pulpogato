package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.PulpogatoModule;
import org.assertj.core.api.SoftAssertions;

import javax.json.*;
import java.io.StringReader;
import java.util.Arrays;

public class TestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule())
            .registerModule(new PulpogatoModule())
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
    public static <T> T parseAndCompare(TypeReference<T> typeReference, String input, SoftAssertions softly) throws JsonProcessingException {
        try {
            final T parsed = OBJECT_MAPPER.readValue(input, typeReference);
            final String generated = OBJECT_MAPPER.writeValueAsString(parsed);
            diffJson(input, generated, softly);
            return parsed;
        } catch (JacksonException e) {
            throw new UnrecognizedPropertyExceptionWrapper(e, input);
        }
    }

    public static void diffJson(String input, String output, SoftAssertions softly) {
        final JsonValue source = Json.createReader(new StringReader(input)).readValue();
        final JsonValue target = Json.createReader(new StringReader(output)).readValue();

        if (source instanceof JsonObject && target instanceof JsonObject) {
            final JsonPatch diff = Json.createDiff(source.asJsonObject(), target.asJsonObject());
            assertOnDiff(softly, diff, source);
        } else if (source instanceof JsonArray && target instanceof JsonArray) {
            final JsonPatch diff = Json.createDiff(source.asJsonArray(), target.asJsonArray());
            assertOnDiff(softly, diff, source);
        } else if (source == null) {
            softly.fail("Invalid input: " + null);
        } else {
            softly.fail("Invalid inputs:: Source:" + source.getValueType() + " Target:" + target.getValueType());
        }

    }

    private static void assertOnDiff(SoftAssertions softly, JsonPatch diff, JsonValue source) {
        diff.toJsonArray()
                .stream()
                .map(JsonValue::asJsonObject)
                // .filter(jo -> !jo.getString("op").equals("add") || !jo.get("value").toString().equals("null"))
                .forEach(it -> {
                    final String op = it.getString("op");
                    final String path = it.getString("path");
                    JsonValue newValue = it.get("value");
                    if (op.equals("replace")) {
                        final String[] pathSteps = Arrays.stream(path.split("/")).dropWhile(String::isEmpty).toList().toArray(new String[0]);
                        JsonValue oldValue = source.asJsonObject();
                        for (final String pathStep : pathSteps) {
                            if (oldValue != null) {
                                if (pathStep.matches("\\d+")) {
                                    oldValue = oldValue.asJsonArray().get(Integer.parseInt(pathStep));
                                } else {
                                    oldValue = oldValue.asJsonObject().get(pathStep);
                                }
                            }
                        }

                        if (oldValue.getValueType() == JsonValue.ValueType.STRING && newValue.getValueType() == JsonValue.ValueType.ARRAY) {
                            if (newValue.asJsonArray().size() == 1) {
                                newValue = newValue.asJsonArray().get(0);
                            }
                        }

                        if (oldValue.getValueType() == JsonValue.ValueType.FALSE && newValue.getValueType() == JsonValue.ValueType.STRING) {
                            oldValue = Json.createValue("false");
                        }
                        if (oldValue.getValueType() == JsonValue.ValueType.TRUE && newValue.getValueType() == JsonValue.ValueType.STRING) {
                            oldValue = Json.createValue("true");
                        }

                        if (newValue.getValueType() == JsonValue.ValueType.STRING && oldValue.getValueType() == JsonValue.ValueType.NUMBER) {
                            oldValue = Json.createValue(oldValue.toString());
                        }
                        if (oldValue.getValueType() == JsonValue.ValueType.STRING && newValue.getValueType() == JsonValue.ValueType.STRING) {
                            if (oldValue.toString().equals(newValue.toString().replace("Z", "+00:00"))) {
                                return;
                            }
                            if (newValue.toString().equals(oldValue.toString().replace("Z", "+00:00"))) {
                                return;
                            }
                            if (newValue.toString().equals(oldValue.toString().replace("Z", ".000Z"))) {
                                return;
                            }
                            if (oldValue.toString().equals(newValue.toString().replace("Z", ".000Z"))) {
                                return;
                            }
                        }

                        if (!oldValue.toString().equals(newValue.toString())) {
                            softly.fail("Changes found: " + op + " " + path + " " + newValue + "(" + newValue.getValueType() + ")" + " " + oldValue + "(" + oldValue.getValueType() + ")");
                        }
                    } else if (op.equals("remove") && newValue != null) {
                        softly.fail("Changes found: " + op + " " + path + " " + newValue);
                    }

                });
    }
}
