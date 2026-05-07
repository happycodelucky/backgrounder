package dev.backgrounder

import co.touchlab.kermit.Logger

private val log = Logger.withTag("Backgrounder")

internal actual fun platformAttachTo(application: Any?) {
    log.d { "attachTo: no-op on macOS" }
}

internal actual fun platformRegisterHandlers() {
    // TODO(macos): wire NSBackgroundActivityScheduler-backed registration here.
    log.w { "registerHandlers: macOS scheduler not yet wired (next commit)" }
}

internal actual fun platformMarkReady() {
    log.d { "markReady: no-op on macOS" }
}
