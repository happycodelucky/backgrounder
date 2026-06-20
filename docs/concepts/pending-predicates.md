# Why work is pending

When you call [`backgrounder.scheduled()`](../recipes/inspect.md), each `ScheduledTask` carries a `pendingPredicates` list — the set of conditions currently keeping that task from running. A task isn't just `Pending` or `Running`; it's *pending **on** something*, and this list tells you what.

```kotlin
backgrounder.scheduled().forEach { task ->
    if (task.pendingPredicates.isNotEmpty()) {
        println("${task.taskId} is waiting on:")
        task.pendingPredicates.forEach { println("  • $it") }
    }
}
```

Use it to drive inspector UIs, to explain "syncing soon…" copy, or to decide whether a stuck task needs a nudge. More than one predicate can apply at once — an unmet network requirement *and* an active backoff window, for example — so it's a list, not a single value.

## The predicates

`PendingPredicate` is a `sealed interface`. Swift consumers get an exhaustive `enum` and can `switch` over it with `onEnum(of:)`; Kotlin consumers get the same exhaustiveness from `when`.

| Predicate | Carries | Means |
| --- | --- | --- |
| `NetworkRequired` | `requirement: NetworkRequirement` | The request's [`networkRequired`](../recipes/network-required.md) is not currently satisfied. The task waits for the network to come up. |
| `RequiresCharging` | — | The request's `requiresCharging` is set and the device is not on external power. |
| `RequiresDeviceIdle` | — | The request's [`requiresDeviceIdle`](../recipes/network-required.md#require-the-device-to-be-idle) is set and the device is not idle. |
| `WaitingForBackoff` | `until: Instant?` | The task failed and is in a [backoff](../recipes/retry-backoff.md) window before its next attempt. `until` is a best-effort estimate of when backoff releases. |
| `WaitingForEarliestBeginDate` | `at: Instant?` | The scheduler accepted the request but its earliest-begin window hasn't elapsed. `at` is when that window opens. |

Each predicate maps back to a [`WorkConstraints`](../recipes/network-required.md) field you set, or to a scheduling decision (`WaitingForBackoff`, `WaitingForEarliestBeginDate`) the library or OS made. If you didn't set a constraint, its predicate never appears.

Switching over them in Swift:

```swift
for predicate in task.pendingPredicates {
    switch onEnum(of: predicate) {
    case .networkRequired(let p):            status = "Waiting for \(p.requirement) network"
    case .requiresCharging:                  status = "Waiting to charge"
    case .requiresDeviceIdle:                status = "Waiting for the device to be idle"
    case .waitingForBackoff(let p):          status = "Retrying \(p.until.map(format) ?? "soon")"
    case .waitingForEarliestBeginDate(let p): status = "Scheduled for \(p.at.map(format) ?? "later")"
    }
}
```

## Which predicates each platform reports

`pendingPredicates` is **best-effort per platform.** A condition that's observable from one platform's scheduler state model may be invisible on another, even when that platform *is* honouring the underlying constraint. An empty list means "nothing observable is blocking dispatch" — not a guarantee that the task will run this instant.

| Predicate | Android | iOS | macOS | JVM |
| --- | --- | --- | --- | --- |
| `NetworkRequired` | ✅ Reported | ✅ Reported | ✅ Reported | ✅ Reported |
| `RequiresCharging` | ✅ Reported | ❌ Not reported[^charging] | ❌ Not reported | ❌ Not reported |
| `RequiresDeviceIdle` | ✅ Reported | ❌ Not reported | ❌ Not reported | ❌ Not reported |
| `WaitingForBackoff` | ✅ Reported | ✅ Reported | ✅ Reported | ✅ Reported |
| `WaitingForEarliestBeginDate` | ✅ Reported | ✅ Reported | ✅ Reported | ✅ Reported |

[^charging]: Setting `requiresCharging` still gates dispatch on iOS — the OS holds the task until the device is on power. What's unavailable on iOS is *read-back*: `scheduled()` can't surface `RequiresCharging` as the reason. Treat its absence from the list as "unknown," not "not charging-gated."

!!! warning "Setting a constraint and observing it are different questions"
    `requiresCharging` and `requiresDeviceIdle` gate dispatch on more platforms than report a predicate for it. On iOS, a charging-gated task waits for power, but `scheduled()` won't tell you that's why it's waiting. Don't write logic that assumes `RequiresCharging` *absent from the list* means the task isn't charging-gated — on every platform except Android, you can't observe that condition. For the per-platform enforcement story (which platform *honours* each constraint, separate from which *reports* it), see [Require a network connection](../recipes/network-required.md#require-the-device-to-be-idle) and [Opportunistic dispatch](opportunistic-dispatch.md).

## What an empty list does and doesn't mean

An empty `pendingPredicates` means no *observable* condition is blocking the task right now. It does **not** mean the task is about to run. Background dispatch is opportunistic on every platform — the OS still decides the moment based on its own budget, app-usage prediction, and system state, none of which surface as predicates. A task with no pending predicates on iOS may still wait hours for the system to give it a turn. See [Opportunistic dispatch](opportunistic-dispatch.md) for the timing model.

Conversely, a non-empty list is actionable: it names a concrete condition (`RequiresCharging`, an in-progress `WaitingForBackoff` with a release estimate) you can reflect in UX — "syncing when you next charge," "retrying in 2 minutes" — instead of a bare spinner.

## See also

- [Inspect scheduled work](../recipes/inspect.md) — the `scheduled()` snapshot and every field on `ScheduledTask`.
- [Require a network connection](../recipes/network-required.md) — the `WorkConstraints` fields these predicates report on, and per-platform enforcement.
- [Opportunistic dispatch](opportunistic-dispatch.md) — why an unblocked task still runs on the OS's schedule, not yours.
- [Retries & backoff](../recipes/retry-backoff.md) — what puts a task into `WaitingForBackoff`.
