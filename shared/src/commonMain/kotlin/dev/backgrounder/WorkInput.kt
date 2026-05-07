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
    public val size: Int get() = map.size
    public val isEmpty: Boolean get() = map.isEmpty()

    public operator fun get(key: String): WorkValue? = map[key]

    /** Read-only view of the underlying map; iteration order is insertion order. */
    public fun entries(): Set<Map.Entry<String, WorkValue>> = map.entries

    public fun toJson(): String = json.encodeToString(serializer(), this)

    override fun equals(other: Any?): Boolean = this === other || (other is WorkInput && map == other.map)

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "WorkInput(${map.keys.joinToString()})"

    public companion object {
        public const val MAX_SERIALIZED_BYTES: Int = 10240

        // Compact JSON: no pretty-printing, no defaults, sealed-class polymorphism.
        // Used both for size-validation here and as the persisted shape on iOS / macOS.
        internal val json: Json =
            Json {
                encodeDefaults = false
                classDiscriminator = "type"
                ignoreUnknownKeys = false
            }

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
