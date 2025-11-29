package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.pulpogato.common.PulpogatoType;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonPatch;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.assertj.core.api.SoftAssertions;
import org.intellij.lang.annotations.Language;
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

    private TestUtils() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
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
                assertThat(evaluation).isNotNull().usingRecursiveComparison().isEqualTo(p);
            }

            return parsed;
        } catch (JacksonException e) {
            throw new UnrecognizedPropertyExceptionWrapper(e, input);
        }
    }

    public static void diffJson(
            @Language("json") final String input, @Language("json") final String output, final SoftAssertions softly) {
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

    static void assertOnDiff(final SoftAssertions softly, final JsonPatch diff, final JsonValue source) {
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

    static BiPredicate<String, String> compareDates(String target, String replacement) {
        return (oldStr, newStr) -> oldStr.equals(newStr.replace(target, replacement));
    }

    static void compareOldAndNew(
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
            softly.fail(
                    "Changes found: %s %s %s(%s) -> %s(%s)",
                    op, path, oldValue, oldValue.getValueType(), newValue, newValue.getValueType());
        }
    }

    static JsonValue normalizeNonStringTypes(final JsonValue valueSource, final JsonValue typeSource) {

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

    static JsonValue traverse(final JsonValue value, final List<String> pathSteps) {
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
