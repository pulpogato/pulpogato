package io.github.pulpogato.issues

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class CreateIssuesTask : DefaultTask() {
    @Internal
    val objectMapper = ObjectMapper()

    @TaskAction
    fun createIssues() {
        val issues =
            project.rootProject.allprojects
                .mapNotNull { subproject ->
                    val file =
                        subproject.layout.buildDirectory
                            .file("reports/generated-test-failures.jsonl")
                            .get()
                            .asFile
                    if (file.exists()) {
                        file.readLines().map(::parse).also {
                            println("Reading issues for project: ${subproject.name}. Found ${it.size} issues")
                        }
                    } else {
                        null
                    }
                }.flatten()
        println("Creating issues from ${issues.size} entries")
        val entries =
            issues
                .groupBy { it!!["schemaRef"] }
                .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.toString() })
                .entries
        println("There are ${entries.size} unique schemaRefs with issues")
        entries
            .take(25)
            .forEach { (schemaRef, schemaRefIssues) ->
                println("Creating issue for schemaRef: $schemaRef with ${schemaRefIssues.size} entries")
                val versionsPlain = schemaRefIssues.map { it!!["ghVersion"] }.distinct()
                val versions = schemaRefIssues.map { "  - " + it!!["ghVersion"] }.distinct().joinToString("\n")
                val exampleRefsPlain = schemaRefIssues.mapNotNull { it!!["exampleRef"] }.distinct()
                val exampleRefs = exampleRefsPlain.joinToString("\n\n", "```\n", "\n```")
                val command =
                    mutableListOf(
                        "gh",
                        "issue",
                        "--repo",
                        "github/rest-api-description",
                        "create",
                        "--title",
                        "[Schema Inaccuracy] Example and schema mismatch for `$schemaRef`",
                        "--body",
                        //language=markdown
                        """
# Schema Inaccuracy

This is the json ref for the example 

$exampleRefs

${schemaRefIssues[0]!!["message"]}

Here's a snippet

```
${schemaRefIssues[0]!!["snippet"]}
```
## Expected

The schema and example are in sync

## Reproduction Steps

I could reproduce this in 

$versions""".trimEnd(),
                    )
                println("Command: $command")
                println("Continue? (y/n)")
                val input = System.`in`.bufferedReader().readLine()
                val reason =
                    if (input.lowercase() == "y") {
                        val process = ProcessBuilder(command).start()
                        process.waitFor()
                        process.inputReader().readText().trim()
                    } else {
                        "TODO: Diagnose this"
                    }
                exampleRefsPlain.forEach { exampleRef ->
                    val yaml = """
- example: "$exampleRef"
  reason: "$reason"
  versions:
${versionsPlain.joinToString("\n") { "    - $it" }}"""
                    println(yaml)
                    project.file("src/main/resources/IgnoredTests.yml").appendText(yaml)
                }
            }
    }

    private fun parse(string: String): Map<String, String>? = objectMapper.readValue(string, object : TypeReference<Map<String, String>>() {})
}