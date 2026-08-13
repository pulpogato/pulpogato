package io.github.pulpogato.restcodegen

/**
 * Constants for Sonar rule identifiers used with [Annotations.suppressWarnings].
 */
object SonarRules {
    /**
     * Sonar rule `java:S100`: "Method names should comply with a naming convention".
     * Suppressed on generated internal helper methods whose names intentionally contain '$' (such as `$fillValuesFrom`).
     */
    const val METHOD_NAMING = "java:S100"

    /**
     * Sonar rule `java:S1452`: "Generic wildcard types should not be used in return types".
     * Suppressed on `builder()`/`toBuilder()` methods since SuperBuilder's builder returns a wildcarded builder type (`Builder<?, ?>`)
     * because the caller's concrete self-type isn't known at that point; this is a known Sonar false positive for the Lombok `@SuperBuilder` pattern,
     * which this codegen replicates by hand.
     */
    const val GENERIC_WILDCARD_RETURN = "java:S1452"

    /**
     * Sonar rule `java:S5976`: "Tests with similar assertions should be combined into a parameterized test".
     * Suppressed on generated OpenAPI example tests because test cases are auto-generated as individual `@Test` methods for each schema example.
     */
    const val SIMILAR_TESTS = "java:S5976"

    /**
     * Sonar rule `java:S1192`: "String literals should not be duplicated".
     * Suppressed on generated code where string literals are intentionally duplicated for clarity or consistency.
     */
    const val STRING_LITERAL_DUPLICATION = "java:S1192"
}