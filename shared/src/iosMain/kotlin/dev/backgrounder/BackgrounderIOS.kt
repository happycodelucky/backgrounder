package dev.backgrounder

import co.touchlab.kermit.Logger
import dev.backgrounder.ios.BGTaskHandlerRegistration
import dev.backgrounder.ios.IOSEphemeralSweep
import org.koin.mp.KoinPlatform

private val log = Logger.withTag("Backgrounder")

internal actual fun platformAttachTo(application: Any?) {
    // No-op on iOS — there's no Application analog and no JobScheduler-fires-during-init
    // race to defend against. Cold-start ephemeral cleanup runs in registerHandlers().
    log.d { "attachTo: no-op on iOS" }
}

internal actual fun platformRegisterHandlers() {
    val koin = runCatching { KoinPlatform.getKoin() }.getOrElse {
        error(
            "Backgrounder.registerHandlers() requires Koin to be started. " +
                "Call startKoin { modules(backgrounderCommonModule, backgrounderIOSModule, ...) } first.",
        )
    }
    koin.get<IOSEphemeralSweep>().run()
    koin.get<BGTaskHandlerRegistration>().run()
    log.i { "registerHandlers: ephemeral sweep + BGTaskScheduler registration + resurrection complete" }
}

internal actual fun platformMarkReady() {
    log.d { "markReady: no-op on iOS" }
}
