package io.github.pulpogato.issues

/**
 * Represents the status of a GitHub issue.
 * This class holds information about a GitHub issue's state, number, and URL.
 */
internal class IssueStatus {
    /** The issue number on GitHub */
    var number: Int? = null

    /** The current state of the issue (e.g., OPEN, CLOSED) */
    var state: String? = null

    /** The URL of the GitHub issue */
    var url: String? = null

    override fun toString(): String = "IssueStatus(number=$number, state=$state, url=$url)"
}