package io.github.pulpogato.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.pulpogato.common.PulpogatoType;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonPatch;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import org.assertj.core.api.SoftAssertions;
import org.springframework.scripting.groovy.GroovyScriptEvaluator;
import org.springframework.scripting.support.StaticScriptSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class TestUtils {

    public static final List<BiPredicate<String, String>> DATE_COMPARE_FUNCTIONS = List.of(
            compareDates("Z", "+00:00"),
            compareDates("Z", ".000+00:00"),
            compareDates(".000", ""),
            compareDates("+00:00", ".000Z"),
            compareDates("+00:00", ".000+00:00"));

    private static final JsonWriterFactory PRETTY_WRITER_FACTORY =
            Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));

    private TestUtils() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2_OBJECT_MAPPER =
            com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .defaultPropertyInclusion(
                            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.USE_DEFAULTS))
                    .addModule(new JavaTimeModule())
                    .build();

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

            if (parsed instanceof PulpogatoType p) {
                final var code = p.toCode();
                GroovyScriptEvaluator evaluator = new GroovyScriptEvaluator();
                final var evaluation = evaluator.evaluate(new StaticScriptSource(code));
                softly.assertThat(evaluation)
                        .isNotNull()
                        .usingRecursiveComparison()
                        .isEqualTo(p);
            }

            return parsed;
        } catch (JacksonException e) {
            throw new UnrecognizedPropertyExceptionWrapper(e, input);
        }
    }

    /**
     * Parses the input and compares it to the generated JSON.
     *
     * @param typeReference The type reference to parse to
     * @param input         The input
     * @param <T>           The type
     * @return The parsed object
     */
    public static <T> T parseAndCompare(
            final com.fasterxml.jackson.core.type.TypeReference<T> typeReference,
            final String input,
            final SoftAssertions softly) {
        try {
            final T parsed = JACKSON2_OBJECT_MAPPER.readValue(input, typeReference);
            final String generated = JACKSON2_OBJECT_MAPPER.writeValueAsString(parsed);
            diffJson(input, generated, softly);

            return parsed;
        } catch (JacksonException e) {
            throw new UnrecognizedPropertyExceptionWrapper(e, input);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void diffJson(final String input, final String output, final SoftAssertions softly) {
        try (final var sourceReader = Json.createReader(new StringReader(input));
                final var targetReader = Json.createReader(new StringReader(output))) {
            final JsonValue source = sourceReader.readValue();
            final JsonValue target = targetReader.readValue();

            softly.assertThat(source).isNotNull();
            softly.assertThat(target).isNotNull();

            if (source instanceof JsonObject && target instanceof JsonObject) {
                assertOnDiff(softly, Json.createDiff(source.asJsonObject(), target.asJsonObject()), source);
            } else if (source instanceof JsonArray && target instanceof JsonArray) {
                assertOnDiff(softly, Json.createDiff(source.asJsonArray(), target.asJsonArray()), source);
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

    private static BiPredicate<String, String> compareDates(String target, String replacement) {
        return (oldStr, newStr) -> oldStr.equals(newStr.replace(target, replacement));
    }

    private static void compareOldAndNew(
            final SoftAssertions softly,
            final JsonValue oldValue,
            final JsonValue newValue1,
            final String op,
            final String path) {
        final JsonValue newValue = normalizeNonStringTypes(newValue1, oldValue);

        if (oldValue instanceof JsonString o && newValue instanceof JsonString n) {
            var areDatesSame = DATE_COMPARE_FUNCTIONS.stream()
                    .anyMatch(c -> c.test(o.getString(), n.getString()) || c.test(n.getString(), o.getString()));
            if (areDatesSame) {
                return;
            }
        }

        // Handle Unix timestamp (NUMBER) to ISO string (STRING) conversion
        if (oldValue instanceof JsonNumber o && newValue instanceof JsonString n) {
            try {
                var isoString = n.getString();
                // Convert timestamp to ISO string and compare
                final var timestampAtUtc = Instant.ofEpochSecond(o.longValue()).atOffset(ZoneOffset.UTC);
                var timestampAsIso = timestampAtUtc.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                if (timestampAsIso.equals(isoString)) {
                    return;
                }
                var timestampAsIso2 =
                        timestampAtUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
                if (timestampAsIso2.equals(isoString)) {
                    return;
                }
            } catch (Exception e) {
                // If conversion fails, fall through to regular comparison
            }
        }

        if (!oldValue.toString().equals(newValue.toString())) {
            softly.fail(formatStructuredDiff(op, path, oldValue, newValue));
        }
    }

    private static String formatStructuredDiff(String op, String path, JsonValue oldValue, JsonValue newValue) {
        var sb = new StringBuilder();
        sb.append("\n");
        sb.append("Difference at path: ").append(path).append("\n");
        sb.append("Operation: ").append(op).append("\n\n");
        sb.append("Expected (from input):\n");
        sb.append(indent(prettyPrint(oldValue))).append("\n\n");
        sb.append("Actual (after roundtrip):\n");
        sb.append(indent(prettyPrint(newValue)));
        return sb.toString();
    }

    private static String prettyPrint(JsonValue value) {
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "null";
        }
        if (value instanceof JsonString
                || value instanceof JsonNumber
                || value.getValueType() == JsonValue.ValueType.TRUE
                || value.getValueType() == JsonValue.ValueType.FALSE) {
            return value.toString();
        }
        var sw = new StringWriter();
        try (var writer = PRETTY_WRITER_FACTORY.createWriter(sw)) {
            writer.write(value);
        }
        return sw.toString();
    }

    private static String indent(String text) {
        return text.lines()
                .map(line -> "  " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
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
