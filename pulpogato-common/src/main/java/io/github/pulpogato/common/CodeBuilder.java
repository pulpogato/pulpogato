package io.github.pulpogato.common;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * A builder class for constructing code representations of objects with properties.
 */
@RequiredArgsConstructor
public class CodeBuilder {
    /**
     * The type of the object being built.
     */
    private final String type;

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
            case PulpogatoType pt -> pt.toCode().indent(2).trim();
            case Enum<?> e -> e.getClass().getName() + "." + e.name();
            case URI u -> "URI.create(\"" + u + "\")";
            case OffsetDateTime odt ->
                "OffsetDateTime.parse(\"" + odt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
                        + "\")";
            case LocalDate ld -> "LocalDate.parse(\"" + ld + "\")";
            case Map<?, ?> map -> {
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
                yield sb.toString();
            }
            case List<?> list -> {
                StringBuilder sb = new StringBuilder();
                sb.append("List.of(\n");
                for (Object item : list) {
                    sb.append("        ").append(render(item)).append(",\n");
                }
                if (!list.isEmpty()) {
                    sb.setLength(sb.length() - 2); // Remove last comma and newline
                }
                sb.append("\n    )");
                yield sb.toString();
            }
            default -> "/* [TODO: CodeBuilder.render]" + value + " */ " + null;
        };
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
