package dev.backgrounder

import co.touchlab.kermit.Logger
import org.koin.mp.KoinPlatform

private val log = Logger.withTag("Backgrounder")

internal actual fun platformAttachTo(application: Any?) {
    log.d { "attachTo: no-op on macOS" }
}

internal actual fun platformRegisterHandlers() {
    val koin = runCatching { KoinPlatform.getKoin() }.getOrElse {
        error(
            "Backgrounder.registerHandlers() requires Koin to be started. " +
                "Call startKoin { modules(backgrounderCommonModule, backgrounderMacOSModule, ...) } first.",
        )
    }
    // macOS doesn't need handler registration ahead of time — NSBackgroundActivityScheduler
    // owns the scheduling entirely. We do still need to seal the WorkerRegistry to prevent
    // late factory registration, and we sweep ephemeral state for parity with iOS.
    val registry = koin.get<WorkerRegistry>()
    val ephemeralIds = koin.get<EphemeralRegistry>().snapshot()
    if (ephemeralIds.isNotEmpty()) {
        log.i { "registerHandlers: sweeping ${ephemeralIds.size} ephemeral entries" }
        koin.get<EphemeralRegistry>().clear()
    }
    registry.seal()
    log.i { "registerHandlers: macOS sealed registry" }
}

internal actual fun platformMarkReady() {
    log.d { "markReady: no-op on macOS" }
}
