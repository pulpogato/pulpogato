package io.github.pulpogato.test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.Getter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

/**
 * JUnit extension to ignore tests based on the version of the API and the schemaRef of the test.
 */
class IgnoredTests {
    private IgnoredTests() {
        // Empty Default Private Constructor. This should not be instantiated.
    }

    static Map<String, Map<String, String>> compute() {
        var tests = readTests();
        return tests.stream()
                .flatMap(ignoredTest -> ignoredTest.getVersions().stream()
                        .map(version ->
                                Map.entry(version, Map.entry(ignoredTest.getExample(), ignoredTest.getReason()))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Map.of(e.getValue().getKey(), e.getValue().getValue()),
                        (existing, replacement) -> {
                            Map<String, String> merged = new java.util.HashMap<>(existing);
                            merged.putAll(replacement);
                            return merged;
                        }));
    }

    private static List<IgnoredTest> readTests() {
        return new ObjectMapper(new YAMLFactory())
                .readValue(IgnoredTests.class.getResourceAsStream("/IgnoredTests.yml"), new TypeReference<>() {});
    }

    @Data
    static class IgnoredTest {
        private String example;
        private String reason;
        private List<String> versions;
    }

    /**
     * This maps the schemaRef of the test to the reason why the test is ignored.
     * Ideally, this should be a link to an issue on
     * <a href="https://github.com/github/rest-api-description/issues">GitHub/rest-api-description</a>.
     */
    @Getter
    private static final Map<String, Map<String, String>> causes = compute();
}
