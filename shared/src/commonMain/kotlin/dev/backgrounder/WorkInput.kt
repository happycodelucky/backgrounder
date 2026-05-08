package dev.backgrounder

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.native.HiddenFromObjC

/**
 * A typed key/value bag passed to a [BackgroundWorker] at scheduling time.
 *
 * Size-capped at [MAX_SERIALIZED_BYTES] (10240 — matching Android
 * `androidx.work.Data`'s hard cap) to keep cross-platform behavior identical.
 *
 * Construct via [of]; the constructor validates serialized size.
 */
@Serializable
public class WorkInput private constructor(
    private val map: Map<String, WorkValue>,
) {
    /** Number of key/value pairs in this input. */
    public val size: Int get() = map.size

    /** `true` if this input contains no key/value pairs. */
    public val isEmpty: Boolean get() = map.isEmpty()

    /** Returns the [WorkValue] for [key], or `null` if the key is absent. */
    public operator fun get(key: String): WorkValue? = map[key]

    /**
     * Read-only view of the underlying map; iteration order is insertion order.
     *
     * Hidden from the iOS / macOS surface: `Map.Entry<K, V>` is a Java
     * interface that doesn't bridge to a usable Swift form (no exhaustive
     * `switch`, no value-type semantics) — H-4 (review-loop round 1) and the
     * matching CLAUDE.md §8 rule. Swift consumers should iterate with [get]
     * over [keysSnapshot], or build the input from a `Map<String, WorkValue>`
     * (see [ofMap]) and hold their own reference.
     *
     * `@OptIn(ExperimentalObjCRefinement::class)`: standard SKIE-recognised
     * annotation; stable in practice.
     */
    @OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    public fun entries(): Set<Map.Entry<String, WorkValue>> = map.entries

    /**
     * Snapshot of the keys in this input. Swift-friendly alternative to
     * [entries]: callers iterate the keys and look up values via [get].
     */
    public fun keysSnapshot(): Set<String> = map.keys.toSet()

    /** Serialize this input to its compact JSON form. Useful for debugging or manual persistence. */
    public fun toJson(): String = json.encodeToString(serializer(), this)

    override fun equals(other: Any?): Boolean = this === other || (other is WorkInput && map == other.map)

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "WorkInput(${map.keys.joinToString()})"

    public companion object {
        /**
         * Hard cap on the serialized size of a [WorkInput], in bytes. Matches
         * Android's `androidx.work.Data` cap so cross-platform payloads never
         * exceed the Android limit.
         */
        public const val MAX_SERIALIZED_BYTES: Int = 10240

        // Compact JSON: no pretty-printing, no defaults, sealed-class polymorphism.
        // Used both for size-validation here and as the persisted shape on iOS / macOS.
        internal val json: Json =
            Json {
                encodeDefaults = false
                classDiscriminator = "type"
                ignoreUnknownKeys = false
            }

        /** Returns an empty [WorkInput] with no key/value pairs. */
        public fun empty(): WorkInput = WorkInput(emptyMap())

        /**
         * Build a [WorkInput] from key/value pairs. Kotlin-only (the `Pair`
         * vararg form doesn't translate cleanly to Swift; Swift consumers should
         * use [ofMap] or compose entries via a `Map`).
         *
         * `@OptIn(ExperimentalObjCRefinement::class)`: standard SKIE-recognised
         * annotation for hiding a Kotlin-only API from the generated ObjC
         * header. Stable in practice.
         *
         * @throws IllegalArgumentException if the serialized size exceeds
         *   [MAX_SERIALIZED_BYTES] or a key is empty.
         */
        @OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
        @HiddenFromObjC
        public fun of(vararg pairs: Pair<String, WorkValue>): WorkInput = ofMap(pairs.toMap(LinkedHashMap()))

        /**
         * Build a [WorkInput] from a [Map]. Preferred for Swift consumers
         * (the `Pair` vararg form of [of] doesn't bridge cleanly to Swift).
         *
         * @throws IllegalArgumentException if any key is empty or the serialized
         *   size exceeds [MAX_SERIALIZED_BYTES].
         */
        public fun ofMap(map: Map<String, WorkValue>): WorkInput {
            require(map.keys.all { it.isNotEmpty() }) { "WorkInput keys must be non-empty" }
            val candidate = WorkInput(LinkedHashMap(map))
            val bytes = candidate.toJson().encodeToByteArray()
            require(bytes.size <= MAX_SERIALIZED_BYTES) {
                "WorkInput exceeds the $MAX_SERIALIZED_BYTES-byte cap: ${bytes.size} bytes"
            }
            return candidate
        }

        /** Parse a [WorkInput] from its serialized form. Internal: used by platform stores. */
        internal fun fromJson(text: String): WorkInput = json.decodeFromString(serializer(), text)
    }
}
