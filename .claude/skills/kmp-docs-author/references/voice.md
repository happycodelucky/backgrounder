# Voice and tone

The voice rules for authoring docs in this repo. Synthesized from the best Kotlin library documentation (Square, Coil, Ktor, Koin, Dagger/Hilt, JetBrains KMP). **Do not anchor on this repo's existing pages** — the goal is to elevate tone, not match the status quo.

---

## The five voice rules

### 1. Active voice, present tense

The scheduler runs your worker. Not: your worker will be run by the scheduler.

Active present-tense prose is shorter and reads less like a legal disclaimer. Future tense ("will be") and passive constructions ("is run by") add words without adding information.

| Bad | Good |
|---|---|
| The worker will be invoked by the runtime when the constraints are satisfied. | The runtime invokes the worker when constraints are satisfied. |
| Errors thrown by the worker will be caught and logged. | The runtime catches and logs worker errors. |
| Cancellation will be propagated to the suspending call. | Cancellation propagates to the suspending call. |

### 2. Lead with the user's task, not the library's capability

Page titles, intro sentences, and section headers are framed around what the reader does — not what the library supports.

| Bad (capability-first) | Good (task-first) |
|---|---|
| Backgrounder supports one-shot scheduling | Schedule a one-shot |
| The library provides cancellation APIs | Cancel a scheduled task |
| Periodic work is implemented via … | Schedule periodic work |

This applies to recipe pages especially. A capability-first title forces the reader to translate "library supports X" into "I want to do Y" — the page should do that translation for them.

### 3. Code in the first 200 words

Every page that involves an API surface shows code within the first screen. If you can't, the page is probably too abstract and needs to be split.

The opening shape that works:

```markdown
# <Imperative title>

<One sentence stating what the page is about.>

\```kotlin
// Five-line maximum, runnable, real types.
\```

<One sentence linking the snippet to the concepts that follow.>
```

### 4. No marketing language

Forbidden words. Replace each with the concrete fact.

| Word | Why it's wrong | Replace with |
|---|---|---|
| `powerful` | No information | The specific capability |
| `easy-to-use` | The reader will judge | Show the call site |
| `simply` / `just` | Implies the reader is slow | Remove the word |
| `seamlessly` | Marketing | The mechanism |
| `blazingly fast` | Unsupported claim | A measured number, or remove |
| `under the hood` | Usually followed by vagueness | The actual fact |
| `state-of-the-art` | Hollow | Remove |
| `cutting-edge` | Hollow | Remove |
| `robust` | Hollow | The specific failure modes handled |

**Rewrites:**

> ❌ Backgrounder is a powerful, easy-to-use library that simply handles background work under the hood.

> ✅ Backgrounder schedules background work across Android, iOS, and macOS through one shared API. The runtime persists requests across process death and retries failed work per a configurable backoff policy.

### 5. One sentence per idea

Long sentences pack multiple claims and bury the important one. Split.

> ❌ The scheduler invokes the worker when all constraints are satisfied, persisting the request across process death so that the work is guaranteed to run eventually, even if the app is force-quit by the user, unless the request was marked ephemeral, in which case it is dropped on process exit.

> ✅ The scheduler invokes the worker when all constraints are satisfied. The request persists across process death, so the work eventually runs even if the user force-quits the app. Ephemeral requests are the exception — they drop on process exit.

---

## Tense and mood for specific page types

| Page type | Voice | Example |
|---|---|---|
| Recipe (title) | Imperative | "Schedule a one-shot" |
| Recipe (body) | Present, second person implicit | "Pass a `WorkConstraints` to filter when the work runs." |
| Concept (title) | Noun | "Scheduler", "Worker context" |
| Concept (body) | Present, third person | "The scheduler owns a registry of pending work." |
| Platform (title) | Platform name | "iOS", "Android" |
| Platform (body) | Present, third person, platform-specific facts | "iOS uses `BGTaskScheduler` for system-managed work." |
| Overview (body) | Present, mixed | "Backgrounder schedules background work. You write a worker; the runtime decides when to invoke it." |
| Changelog | Past tense for changes | "Added flex intervals to `WorkRequest.Periodic`." |

---

## Headings

- One H1 per page. The page title. Match the `nav:` label exactly.
- H2 for top-level sections. Lead with the noun ("Constraints", not "About constraints").
- H3 only when an H2 needs subdivisions. Avoid H4 — if you need it, split the page.
- Sentence case throughout. "Schedule a one-shot", not "Schedule A One-Shot" and not "schedule a one-shot".

---

## Code commentary

- Don't restate what the code does in prose. The code is the documentation.
- Do call out what's non-obvious: why this argument, what this return type means, what happens on the unhappy path.
- Inline comments are fine when they answer "why". `// retried per BackoffPolicy.maxAttempts` is useful. `// schedule the work` is not.

---

## "What can go wrong" — voice within the section

The mandatory closing section on every recipe page. Voice rules:

- Lead with the cause, not the symptom. The reader's symptom led them here; they need the cause.
- One line per item. If it needs two, split it.
- Action-oriented closure. "Add the identifier to `BGTaskSchedulerPermittedIdentifiers`" — not "The identifier should be in the Info.plist."

Format:

```markdown
## What can go wrong

- **<cause>** — <one-line consequence + fix>. Link to relevant page if applicable.
```

Example (synthesized from OkHttp's recipe style):

> ❌ Sometimes the iOS scheduler doesn't invoke your worker. This could be because the Info.plist is wrong or because the system is throttling. You might want to check the logs.

> ✅ - **Missing Info.plist entry** — every `TaskId` you schedule must appear in `BGTaskSchedulerPermittedIdentifiers`. Without it, `schedule()` returns `ScheduleOutcome.Rejected`.
> - **System throttling** — the iOS runtime defers low-priority work indefinitely under thermal pressure or low battery. Check the system log for `BGTaskScheduler` entries.

---

## When to break these rules

The rules optimize for the common case. Break them when:

- A historical changelog entry uses past tense — keep it (rewriting history is worse).
- A concept page needs a metaphor to explain a non-obvious model — use it, but keep the metaphor to one paragraph and follow with concrete facts.
- An API has a name that's a verb (`Backgrounder.schedule()`) — the concept page for the method is allowed to keep the verb in the title.

Breaking a rule should be a deliberate choice, not a slip.
