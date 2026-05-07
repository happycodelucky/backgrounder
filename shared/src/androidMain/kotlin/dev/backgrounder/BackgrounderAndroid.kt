package dev.backgrounder

import android.app.Application
import co.touchlab.kermit.Logger
import com.russhwolf.settings.SharedPreferencesSettings
import dev.backgrounder.android.AndroidEphemeralReady
import dev.backgrounder.android.AndroidEphemeralSweep

private val log = Logger.withTag("Backgrounder")

internal actual fun platformAttachTo(application: Any?) {
    val app =
        application as? Application
            ?: error(
                "Backgrounder.attachTo expects an Application; got ${application?.let { it::class }}. " +
                    "Call from Application.onCreate before startKoin.",
            )

    // Always reset readiness on cold start; Backgrounder.markReady() flips it true.
    AndroidEphemeralReady.reset()

    // Sweep ephemeral work *before* Koin starts. We can't read the EphemeralRegistry
    // out of Koin yet, so build a temporary one over the same SharedPreferences the
    // Android module will bind. This stays consistent with the post-startup view because
    // both routes write to the same backing store.
    val settings =
        SharedPreferencesSettings(
            app.getSharedPreferences("backgrounder.prefs", android.content.Context.MODE_PRIVATE),
        )
    val ephemeral = EphemeralRegistry(settings)
    AndroidEphemeralSweep(app, ephemeral).run()
    log.i { "attachTo: ephemeral sweep complete; awaiting markReady()" }
}

internal actual fun platformRegisterHandlers() {
    // Android: WorkManager is wired via Koin's WorkerFactory. registerHandlers exists
    // for iOS / macOS launch-time handler registration; on Android it's a no-op except
    // for sealing the WorkerRegistry to lock further registration.
    log.d { "registerHandlers: no-op on Android (WorkerFactory wires through Koin)" }
}

internal actual fun platformMarkReady() {
    AndroidEphemeralReady.markReady()
    log.i { "markReady: ephemeral workers may now dispatch" }
}
