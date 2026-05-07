package dev.backgrounder

internal actual fun platformAttachTo(application: Any?) {
    // No-op on iOS / macOS — Android-only concept.
}

internal actual fun platformRegisterHandlers() {
    // TODO(impl): plist sanity check, register BGTask / NSBackgroundActivityScheduler
    //   handlers, run ephemeral sweep, run periodic resurrection.
}

internal actual fun platformMarkReady() {
    // No-op on iOS / macOS (no JobScheduler-fires-during-init race).
}
