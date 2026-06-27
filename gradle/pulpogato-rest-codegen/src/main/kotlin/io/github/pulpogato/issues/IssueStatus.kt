package io.github.pulpogato.issues

/**
 * Represents the status of a GitHub issue.
 * This class holds information about a GitHub issue's state, number, and URL.
 *
 * Kept as mutable [var]s with an all-args primary constructor so that the plain
 * [com.fasterxml.jackson.databind.ObjectMapper] (no Kotlin module registered) can
 * deserialize `gh issue view` JSON via either the constructor or the setters.
 */
internal data class IssueStatus(
    /** The issue number on GitHub */
    var number: Int? = null,
    /** The current state of the issue (e.g., OPEN, CLOSED) */
    var state: String? = null,
    /** The URL of the GitHub issue */
    var url: String? = null,
)