package dev.backgrounder

import co.touchlab.kermit.Logger
import dev.backgrounder.macos.NSBackgroundActivityBackedScheduler
import org.koin.mp.KoinPlatform

private val log = Logger.withTag("Backgrounder")

internal actual fun platformAttachTo(application: Any?) {
    log.d { "attachTo: no-op on macOS" }
}

internal actual fun platformRegisterHandlers() {
    val koin =
        KoinPlatform.getKoinOrNull() ?: error(
            "Backgrounder.registerHandlers() requires Koin to be started. " +
                "Call startKoin { modules(backgrounderCommonModule, backgrounderMacOSModule, ...) } first.",
        )
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

internal actual fun platformShutdown() {
    val koin = KoinPlatform.getKoinOrNull()
    if (koin == null) {
        log.d { "shutdown: Koin not started; nothing to tear down" }
        return
    }
    // NSBackgroundActivityBackedScheduler owns its own SupervisorJob-rooted scope
    // and exposes shutdown() that cancels it after invalidating outstanding activities.
    koin.getOrNull<NSBackgroundActivityBackedScheduler>()?.shutdown()
        ?: log.d { "shutdown: macOS scheduler not registered; nothing to tear down" }
    log.i { "shutdown: macOS scheduler cancelled" }
}
