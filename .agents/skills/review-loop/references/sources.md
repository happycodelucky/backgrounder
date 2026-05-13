# Curated sources for KMP modernization scouting

Use these when the modernization scout needs an authoritative answer. Prefer the *primary* source where one exists; fall back to the curated blogs/release pages.

## Toolchain — primary sources

| Concern | URL | What to read |
|---|---|---|
| Kotlin stable releases | https://kotlinlang.org/docs/releases.html | Latest stable + the K2 status |
| Kotlin roadmap | https://kotlinlang.org/docs/roadmap.html | Multi-quarter view of what's coming |
| Gradle releases | https://gradle.org/releases/ | Stable line; LTS notes |
| Gradle KMP plugin | https://kotlinlang.org/docs/multiplatform.html | Source-set DSL changes |
| Android Gradle Plugin | https://developer.android.com/build/releases/gradle-plugin | AGP versions, new KMP plugin (`com.android.kotlin.multiplatform.library`) |
| Android KMP plugin docs | https://developer.android.com/kotlin/multiplatform | The new `android { }` block, deprecation of `androidTarget()` |

## SKIE — the Kotlin-version ceiling

| Source | URL | Why |
|---|---|---|
| SKIE docs | https://skie.touchlab.co/ | Supported Kotlin range, feature matrix |
| SKIE releases | https://github.com/touchlab/SKIE/releases | Per-release Kotlin compatibility |
| Touchlab blog | https://touchlab.co/blog | Pre-release notes on SKIE/KMP issues |

**Rule:** if SKIE's latest release lists "Supports Kotlin X.Y.Z–X.Y.W", we never bump Kotlin past `X.Y.W` on `main`.

## kotlinx ecosystem

| Library | Project page | Maven Central group |
|---|---|---|
| kotlinx.coroutines | https://github.com/Kotlin/kotlinx.coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| kotlinx.datetime | https://github.com/Kotlin/kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` |
| kotlinx.io | https://github.com/Kotlin/kotlinx-io | `org.jetbrains.kotlinx:kotlinx-io-core` |
| kotlinx.collections.immutable | https://github.com/Kotlin/kotlinx.collections.immutable | `org.jetbrains.kotlinx:kotlinx-collections-immutable` |
| kotlinx.atomicfu | https://github.com/Kotlin/kotlinx-atomicfu | `org.jetbrains.kotlinx:atomicfu` |

## KMMBridge

| Source | URL |
|---|---|
| KMMBridge docs | https://kmmbridge.touchlab.co/ |
| KMMBridge releases | https://github.com/touchlab/KMMBridge/releases |

## Koin

| Source | URL |
|---|---|
| Koin docs | https://insert-koin.io/ |
| Koin releases | https://github.com/InsertKoinIO/koin/releases |
| `koin-bom` Maven | https://central.sonatype.com/artifact/io.insert-koin/koin-bom |

## AndroidX / Jetpack libraries used here

| Library | URL |
|---|---|
| WorkManager | https://developer.android.com/jetpack/androidx/releases/work |
| Lifecycle | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| DataStore | https://developer.android.com/jetpack/androidx/releases/datastore |
| App Startup | https://developer.android.com/jetpack/androidx/releases/startup |

## Apple-side reference (when designing the iOS handle)

| Source | URL |
|---|---|
| BGTaskScheduler | https://developer.apple.com/documentation/backgroundtasks/bgtaskscheduler |
| NSBackgroundActivityScheduler | https://developer.apple.com/documentation/foundation/nsbackgroundactivityscheduler |

## Editorial / community

Use as secondary opinion, not primary truth.

- Touchlab blog — https://touchlab.co/blog
- Kotlin Weekly — https://kotlinweekly.net (for "what shipped this week" context)
- JetBrains' Talking Kotlin podcast and YouTube — release-cycle context
- Android Developers blog — https://android-developers.googleblog.com (KMP and AGP posts)

## Cadence guidance for the scout

- **Every round** — re-fetch the SKIE release page if the diff touches `gradle/libs.versions.toml`. Ceilings change weekly during Kotlin RC cycles.
- **First round of a session** — fetch the Kotlin releases page to anchor the latest stable.
- **Only if the diff touches Android** — fetch AGP/AndroidX pages.
- **Only if the diff adds a new library** — fetch its Maven Central page to confirm latest stable + KMP support.

Cache aggressively in your reasoning — don't refetch within a round. The `WebFetch` tool deduplicates per-URL within a session.
