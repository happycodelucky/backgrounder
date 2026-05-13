package com.happycodelucky.backgrounder.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.happycodelucky.backgrounder.BackgrounderEventListener
import com.happycodelucky.backgrounder.PlatformCapabilities
import com.happycodelucky.backgrounder.WorkResult
import com.happycodelucky.backgrounder.WorkerContext
import com.happycodelucky.backgrounder.WorkerRegistry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import androidx.work.ListenableWorker.Result as AndroidResult

/**
 * The *only* [androidx.work.Worker] class registered with WorkManager.
 *
 * Reads the [TaskId][com.happycodelucky.backgrounder.TaskId] from `inputData`, asks the
 * [WorkerRegistry] for a fresh [BackgroundWorker][com.happycodelucky.backgrounder.BackgroundWorker],
 * runs it, maps [WorkResult] back to a WorkManager [AndroidResult], and applies
 * the cross-platform `maxAttempts` cap on [WorkResult.Retry].
 *
 * Why one bridge worker for every task id (plan §Android implementation):
 *  - `enqueueUniqueWork` keys uniqueness by `uniqueWorkName`, *not* by Worker
 *    class — so one class handles all task ids.
 *  - All per-request configuration (constraints, expedited, backoff,
 *    inputData) lives on the request, not on the Worker class.
 *  - It keeps the Android model symmetric with iOS, which is task-id-keyed
 *    by the OS itself.
 *
 * Observability cost (also documented in the plan): every log includes the
 * task id in its tag, the thread is renamed for the duration of `execute`,
 * and exception messages are prepended with `[<taskId>]`.
 */
internal class RegistryDispatchWorker(
    context: Context,
    params: WorkerParameters,
    private val registry: WorkerRegistry,
    private val eventListener: BackgrounderEventListener,
    private val readyGate: kotlinx.atomicfu.AtomicBoolean,
) : CoroutineWorker(context, params) {
    private val log = Logger.withTag("Backgrounder")

    override suspend fun doWork(): AndroidResult {
        val taskId =
            AndroidWorkInputMapper.readTaskId(inputData) ?: run {
                log.e { "RegistryDispatchWorker fired without a task id in inputData" }
                return AndroidResult.failure()
            }

        val tagged = log.withTag("Backgrounder/$taskId")
        // Capture inside doWork() — the worker's runtime thread can differ from
        // the thread WorkManager / our WorkerFactory instantiated us on. Restoring
        // the field-init snapshot would set the wrong name on whatever pool thread
        // doWork actually ran on.
        val originalThreadName = Thread.currentThread().name
        renameThread("Backgrounder/$taskId")
        try {
            val ephemeral = AndroidWorkInputMapper.readEphemeral(inputData)
            val ready = readyGate.value
            if (ephemeral && !ready) {
                tagged.w {
                    "fired before Backgrounder.markReady(); ephemeral request bailed " +
                        "(retry will happen after init completes)"
                }
                eventListener.onCompleted(taskId, runAttemptCount, WorkResult.Failure(REASON_NOT_READY))
                return AndroidResult.failure()
            }

            val input =
                runCatching { AndroidWorkInputMapper.readInput(inputData) }.getOrElse {
                    tagged.e(it) { "failed to deserialize WorkInput; failing the worker" }
                    return AndroidResult.failure()
                }

            val worker =
                try {
                    registry.create(taskId)
                } catch (cause: WorkerRegistry.NoFactoryRegisteredException) {
                    tagged.e(cause) { "no factory registered; failing the worker" }
                    return AndroidResult.failure()
                }

            val attempt = runAttemptCount
            eventListener.onStarted(taskId, attempt)
            tagged.d { "execute() attempt=$attempt" }

            val ctx =
                WorkerContext(
                    taskId = taskId,
                    attempt = attempt,
                    input = input,
                    capabilities =
                        PlatformCapabilities(
                            maxExecutionTime = ANDROID_EXECUTION_BUDGET,
                            cancelsInFlight = true,
                        ),
                )

            val result: WorkResult =
                try {
                    worker.execute(ctx)
                } catch (e: CancellationException) {
                    tagged.i { "cancelled: ${e.message}" }
                    throw e
                } catch (t: Throwable) {
                    tagged.e(t) { "[$taskId] threw; treating as Retry" }
                    WorkResult.Retry
                }

            eventListener.onCompleted(taskId, attempt, result)

            return when (result) {
                WorkResult.Success -> {
                    AndroidResult.success()
                }

                is WorkResult.Failure -> {
                    AndroidResult.failure()
                }

                WorkResult.Retry -> {
                    val cap = AndroidWorkInputMapper.readMaxAttempts(inputData)
                    if (attempt + 1 >= cap) {
                        tagged.w { "max attempts ($cap) reached; converting Retry to failure" }
                        AndroidResult.failure()
                    } else {
                        AndroidResult.retry()
                    }
                }
            }
        } finally {
            renameThread(originalThreadName)
        }
    }

    private fun renameThread(name: String) {
        runCatching { Thread.currentThread().name = name }
    }

    internal companion object {
        // Documented Android floor for a regular CoroutineWorker; expedited gets the same
        // budget but at higher dispatch priority. Used as a hint via PlatformCapabilities.
        internal val ANDROID_EXECUTION_BUDGET = 10.minutes

        internal const val REASON_NOT_READY: String = "dispatched before ephemeralReady"
    }
}
