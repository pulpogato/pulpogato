package io.github.pulpogato.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class GeneratedTestFailureReportsTest {
    @TempDir
    Path directory;

    @Test
    void productionReportsUseTheCurrentWorkerNamespace() throws Exception {
        var report = Path.of(
                "build/reports/generated-test-failures",
                Long.toString(ProcessHandle.current().pid()),
                "generated-test-failures.jsonl");
        var previous = Files.exists(report) ? Files.readAllBytes(report) : null;
        try {
            var failure = Map.of("message", "production worker: café");
            GeneratedTestFailureReports.report(List.of(failure));
            var type = new TypeReference<Map<String, String>>() {};
            assertThat(new ObjectMapper().readValue(Files.readString(report), type))
                    .isEqualTo(failure);
        } finally {
            if (previous == null) Files.deleteIfExists(report);
            else Files.write(report, previous);
        }
    }

    @Test
    void writeFailuresAreReportedWithoutClaimingSuccess() throws Exception {
        var blocked = directory.resolve("not-a-directory");
        Files.writeString(blocked, "occupied");
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
                var err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            GeneratedTestFailureReports.report(blocked, "worker", List.of(Map.of("message", "failure")), out, err);
        }
        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errors.toString(StandardCharsets.UTF_8)).contains("Failed to write generated test failure report:");
        assertThat(Files.readString(blocked)).isEqualTo("occupied");
    }

    @Test
    void preservesFailuresFromConcurrentWorkersAndCreatesMissingDirectories() throws Exception {
        var first = List.of(Map.of("message", "worker one: café"));
        var second = List.of(Map.of("message", "worker two: 日本語"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Path>>of(
                    () -> GeneratedTestFailureReports.write(directory, "first", first),
                    () -> GeneratedTestFailureReports.write(directory, "second", second)));
            var mapper = new ObjectMapper();
            var firstReport = results.get(0).get();
            var secondReport = results.get(1).get();
            assertThat(firstReport).isNotEqualTo(secondReport);
            var type = new TypeReference<Map<String, String>>() {};
            assertThat(mapper.readValue(Files.readString(firstReport), type)).isEqualTo(first.getFirst());
            assertThat(mapper.readValue(Files.readString(secondReport), type)).isEqualTo(second.getFirst());
        }
    }
}
