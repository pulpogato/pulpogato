package io.github.pulpogato.issues

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FailureReportFilesTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `discovers legacy and worker reports without unrelated files`() {
        val names =
            listOf(
                "reports/generated-test-failures.jsonl",
                "reports/generated-test-failures/first/generated-test-failures.jsonl",
                "reports/generated-test-failures/second/generated-test-failures.jsonl",
            )
        (names + "reports/generated-test-failures/first/other.jsonl").forEach { name ->
            File(directory, name).apply {
                parentFile.mkdirs()
                writeText("{}")
            }
        }
        assertThat(failureReportFiles(directory).map { it.relativeTo(directory).invariantSeparatorsPath })
            .containsExactlyInAnyOrderElementsOf(names)
    }
}