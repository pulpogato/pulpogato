package io.github.pulpogato.test;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

final class GeneratedTestFailureReports {
    private GeneratedTestFailureReports() {}

    static void report(List<Map<String, String>> failures) {
        report(
                Path.of("build/reports/generated-test-failures"),
                Long.toString(ProcessHandle.current().pid()),
                failures,
                System.out,
                System.err);
    }

    static void report(
            Path directory, String worker, List<Map<String, String>> failures, PrintStream output, PrintStream errors) {
        try {
            var report = write(directory, worker, failures);
            output.println("Generated test failure report written to: " + report);
        } catch (IOException failure) {
            errors.println("Failed to write generated test failure report: " + failure.getMessage());
        }
    }

    static Path write(Path directory, String worker, List<Map<String, String>> failures) throws IOException {
        var report = directory.resolve(worker).resolve("generated-test-failures.jsonl");
        Files.createDirectories(report.getParent());
        var mapper = new ObjectMapper();
        try (var writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
            for (var failure : failures) {
                writer.write(mapper.writeValueAsString(failure));
                writer.newLine();
            }
        }
        return report;
    }
}
