package dev.backgrounder

internal actual fun platformAttachTo(application: Any?) {
    // TODO(impl): EphemeralSweep — see plan §Android implementation.
}

internal actual fun platformRegisterHandlers() {
    // No-op on Android (kept for API symmetry).
}

internal actual fun platformMarkReady() {
    // TODO(impl): set ephemeralReady AtomicBoolean.
}
