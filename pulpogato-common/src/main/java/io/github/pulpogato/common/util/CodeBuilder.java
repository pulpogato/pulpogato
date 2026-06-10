package io.github.pulpogato.common.util;

import io.github.pulpogato.common.PulpogatoType;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/**
 * A builder class for constructing code representations of objects with properties.
 */
public class CodeBuilder {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    private static final int INDENT_SIZE = 2;

    private static String indentOnly(String input) {
        return input.indent(INDENT_SIZE).replaceAll("\n$", "");
    }

    /**
     * The type of the object being built.
     */
    private final String type;

    /**
     * Creates a new CodeBuilder for the given type.
     *
     * @param type the fully qualified class name of the object to build
     */
    public CodeBuilder(String type) {
        this.type = type;
    }

    private final Map<String, Object> properties = new LinkedHashMap<>();

    /**
     * Adds a property to the object being built.
     *
     * @param name  the name of the property
     * @param value the property's value
     * @return the current CodeBuilder instance for method chaining
     */
    public CodeBuilder addProperty(String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
        return this;
    }

    /**
     * Builds the code representation of the object with its properties.
     *
     * @return a string representing the code to create the object
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(".builder()");
        properties.forEach((key, value) -> sb.append("\n")
                .append(indentOnly("."))
                .append(key)
                .append("(")
                .append(render(value))
                .append(")"));
        sb.append("\n").append(indentOnly(".build()"));
        return sb.toString();
    }

    /**
     * Renders a value into its code representation.
     *
     * @param value the value to render
     * @return a string representing the code for the value
     */
    public static String render(Object value) {
        if (value == null) {
            return "null";
        }
        return switch (value) {
            case String s -> "\"" + escapeString(s) + "\"";
            case Long n -> n + "L";
            case Integer n -> n + "";
            case Boolean b -> b + "";
            case Double n -> n + "D";
            case Float n -> n + "F";
            case BigDecimal bd -> "new java.math.BigDecimal(\"" + bd + "\")";
            case PulpogatoType pt -> pt.toCode().indent(INDENT_SIZE).trim();
            case Enum<?> e -> e.getClass().getName() + "." + e.name();
            case URI u -> "URI.create(\"" + u + "\")";
            case UUID uuid -> "UUID.fromString(\"" + uuid + "\")";
            case OffsetDateTime odt -> formatDateTime(odt);
            case LocalDate ld -> "LocalDate.parse(\"" + ld + "\")";
            case Map<?, ?> map -> formatMap(map).indent(INDENT_SIZE).trim();
            case List<?> list -> formatList(list).indent(INDENT_SIZE).trim();
            default -> throw new UnsupportedOperationException(getMessage(value));
        };
    }

    private static @NonNull String getMessage(Object value) {
        String name = value.getClass().getName();
        return "Unsupported type in CodeBuilder.render: " + name + " (value=" + value + ")";
    }

    private static @NonNull String formatMap(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(entry -> "\n" + indentOnly("io.github.pulpogato.common.util.LinkedHashMapBuilder.entry(")
                        + render(entry.getKey()) + ", " + render(entry.getValue()) + ")")
                .collect(Collectors.joining(",", "io.github.pulpogato.common.util.LinkedHashMapBuilder.of(", ")"));
    }

    private static @NonNull String formatDateTime(OffsetDateTime odt) {
        final var formattedDate = odt.format(DATE_TIME_FORMATTER);
        return "OffsetDateTime.parse(\"" + formattedDate + "\")";
    }

    private static @NonNull String formatList(List<?> list) {
        return list.stream().map(it -> "\n" + indentOnly(render(it))).collect(Collectors.joining(",", "List.of(", ")"));
    }

    /**
     * Escapes special characters in a string for Java code generation.
     *
     * @param s the string to escape
     * @return the escaped string
     */
    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
