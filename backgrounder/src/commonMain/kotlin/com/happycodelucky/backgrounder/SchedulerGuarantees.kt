package com.happycodelucky.backgrounder

import kotlin.time.Duration

/**
 * What the current platform's [Scheduler] actually guarantees.
 *
 * Honest about platform differences. Read via [Scheduler.guarantees] and use
 * for UX decisions ("on iOS, show 'open the app daily so we can sync'").
 *
 * v1 values per platform:
 *
 * | Field                        | Android | iOS 18 | macOS 15 |
 * |------------------------------|---------|--------|----------|
 * | survivesProcessDeath         | true    | true   | true     |
 * | survivesReboot               | true    | true   | true     |
 * | survivesForceQuit            | **true**| **false** | true  |
 * | honoursWallClock             | approx  | **false** (hint only) | approx |
 * | supportsRetryBackoff         | true    | true (emulated) | true (emulated) |
 * | cancelsInFlight              | **true**| **false** | true  |
 * | minimumPeriodicInterval      | 15 min  | 15 min recommended | 1 sec |
 * | maxConcurrentTasks           | null    | ~1000  | null     |
 */
public data class SchedulerGuarantees(
    public val survivesProcessDeath: Boolean,
    public val survivesReboot: Boolean,
    public val survivesForceQuit: Boolean,
    public val honoursWallClock: Boolean,
    public val supportsRetryBackoff: Boolean,
    public val cancelsInFlight: Boolean,
    public val minimumPeriodicInterval: Duration?,
    public val maxConcurrentTasks: Int?,
)
