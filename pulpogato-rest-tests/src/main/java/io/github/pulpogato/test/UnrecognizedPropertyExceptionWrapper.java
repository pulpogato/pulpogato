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
        int line = originalException.getLocation().getLineNr();
        int column = originalException.getLocation().getColumnNr();

        String[] lines = input.split("\r?\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println(originalException.getMessage());
        pw.append("\n");

        for (int i = Math.max(0, line - 5); i < Math.min(lines.length, line + 4); i++) {
            pw.printf("%s%4d: %s%n", i == line - 1 ? ">" : " ", (i + 1), lines[i]);
            if (i == line - 1 && lines[i].trim().length() > 50) {
                pw.printf("%-"+(1+4+column)+"s^%n", "");
            }
        }

        return sw.toString();
    }
}
