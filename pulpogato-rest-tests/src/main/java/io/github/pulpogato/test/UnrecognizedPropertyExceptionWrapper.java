package io.github.pulpogato.test;

import com.fasterxml.jackson.core.JacksonException;
import java.io.PrintWriter;
import java.io.StringWriter;

class UnrecognizedPropertyExceptionWrapper extends RuntimeException {
    private final JacksonException originalException;
    private final String input;

    public UnrecognizedPropertyExceptionWrapper(JacksonException originalException, String input) {
        super(originalException);
        this.originalException = originalException;
        this.input = input;
    }

    @Override
    public String getMessage() {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);

        pw.println(originalException.getMessage());
        pw.println("");
        pw.println(getSnippet());
        return sw.toString();
    }

    public String getFieldMessage() {
        return extractFieldError(originalException.getMessage());
    }

    public String getSnippet() {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        final int line = originalException.getLocation().getLineNr();
        final int column = originalException.getLocation().getColumnNr();
        final String[] lines = input.split("\r?\n");
        for (int i = Math.max(0, line - 5); i < Math.min(lines.length, line + 4); i++) {
            pw.printf("%s%4d: %s%n", i == line - 1 ? ">" : " ", (i + 1), lines[i]);
            if (i == line - 1 && lines[i].trim().length() > 50) {
                pw.printf("%-" + (1 + 4 + column) + "s^%n", "");
            }
        }
        pw.flush();
        return sw.toString();
    }

    private String extractFieldError(String message) {
        if (message.contains("Unrecognized field")) {
            var start = message.indexOf("\"");
            var end = message.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                var fieldName = message.substring(start + 1, end);
                return "The field `" + fieldName + "` doesn't exist in the schema, but exists in the example.";
            }
        }
        return "Schema and example mismatch detected.";
    }
}
