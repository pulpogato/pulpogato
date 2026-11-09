package io.github.pulpogato.issues

import java.io.File

internal fun failureReportFiles(buildDir: File): List<File> {
    val legacy = File(buildDir, "reports/generated-test-failures.jsonl")
    val workerReports =
        File(buildDir, "reports/generated-test-failures")
            .walkTopDown()
            .filter { it.isFile && it.name == "generated-test-failures.jsonl" }
    return (listOf(legacy).filter { it.isFile } + workerReports).sortedBy { it.path }
}