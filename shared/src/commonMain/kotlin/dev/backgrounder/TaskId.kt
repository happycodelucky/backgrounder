@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package dev.backgrounder

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * A stable, reverse-DNS identifier for a [BackgroundWorker] task.
 *
 * Reverse-DNS shape (e.g. `com.example.app.sync`) matches Apple's
 * `BGTaskSchedulerPermittedIdentifiers` convention; we enforce it cross-platform
 * so a task id you accept on Android also works as an iOS Info.plist entry.
 *
 * Validated:
 * - 1..128 characters
 * - At least one `.`
 * - Only `a-z`, `A-Z`, `0-9`, `-`, `_`, `.`
 * - No leading or trailing `.`, no consecutive `..`
 *
 * Cheaply represented at runtime (`value class` over [String]).
 */
@JvmInline
@Serializable
public value class TaskId(public val value: String) {

    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH) {
            "TaskId length must be 1..$MAX_LENGTH, was ${value.length}"
        }
        require(value.contains('.')) {
            "TaskId must look like reverse-DNS (contain '.'); was '$value'"
        }
        require(!value.startsWith('.') && !value.endsWith('.')) {
            "TaskId must not start or end with '.'; was '$value'"
        }
        require(!value.contains("..")) {
            "TaskId must not contain consecutive '..'; was '$value'"
        }
        require(value.all { it.isAllowed() }) {
            "TaskId may only contain a-z, A-Z, 0-9, '-', '_', '.'; was '$value'"
        }
    }

    override fun toString(): String = value

    public companion object {
        public const val MAX_LENGTH: Int = 128
    }
}

private fun Char.isAllowed(): Boolean =
    this in 'a'..'z' ||
        this in 'A'..'Z' ||
        this in '0'..'9' ||
        this == '-' || this == '_' || this == '.'
