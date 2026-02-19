package io.github.pulpogato.common.util;

import io.github.pulpogato.common.PulpogatoType;
import java.math.BigDecimal;
import java.net.URI;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * A builder class for constructing code representations of objects with properties.
 */
public class CodeBuilder {
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
        properties.forEach((key, value) -> sb.append("\n    .")
                .append(key)
                .append("(")
                .append(render(value))
                .append(")"));
        sb.append("\n    .build()");
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
            case PulpogatoType pt -> pt.toCode().indent(2).trim();
            case Enum<?> e -> e.getClass().getName() + "." + e.name();
            case URI u -> "URI.create(\"" + u + "\")";
            case UUID uuid -> "UUID.fromString(\"" + uuid + "\")";
            case OffsetDateTime odt -> formatDateTime(odt);
            case LocalDate ld -> "LocalDate.parse(\"" + ld + "\")";
            case Map<?, ?> map -> formatMap(map);
            case List<?> list -> formatList(list);
            default -> "/* [TODO: CodeBuilder.render]" + value + " */ " + null;
        };
    }

    private static @NonNull String formatMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("new java.util.HashMap<Object, Object>() {{\n");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("        put(")
                    .append(render(entry.getKey()))
                    .append(", ")
                    .append(render(entry.getValue()))
                    .append(");\n");
        }
        sb.append("    }}");
        return sb.toString();
    }

    private static @NonNull String formatDateTime(OffsetDateTime odt) {
        return MessageFormat.format(
                "OffsetDateTime.parse(\"{0}\")",
                odt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")));
    }

    private static @NonNull String formatList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("List.of(\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append("        ").append(render(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(",\n");
            }
        }
        sb.append("\n    )");
        return sb.toString();
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
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
