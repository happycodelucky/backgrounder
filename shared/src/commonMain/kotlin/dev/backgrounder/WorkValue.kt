package dev.backgrounder

import kotlinx.serialization.Serializable

/**
 * A value carried in a [WorkInput] payload.
 *
 * Sealed to keep the type space small enough to map onto Android's
 * `androidx.work.Data` (which only accepts primitives and arrays of primitives)
 * and to JSON for iOS / macOS persistence.
 *
 * Sticking to the Android subset cross-platform avoids a per-platform
 * "supported types" mismatch.
 */
@Serializable
public sealed interface WorkValue {
    @Serializable public data class StringValue(
        val value: String,
    ) : WorkValue

    @Serializable public data class LongValue(
        val value: Long,
    ) : WorkValue

    @Serializable public data class DoubleValue(
        val value: Double,
    ) : WorkValue

    @Serializable public data class BooleanValue(
        val value: Boolean,
    ) : WorkValue

    @Serializable public data class BytesValue(
        val value: ByteArray,
    ) : WorkValue {
        override fun equals(other: Any?): Boolean = this === other || (other is BytesValue && value.contentEquals(other.value))

        override fun hashCode(): Int = value.contentHashCode()
    }
}
