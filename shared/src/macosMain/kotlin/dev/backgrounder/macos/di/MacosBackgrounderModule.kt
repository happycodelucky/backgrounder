@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.macos.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.Scheduler
import dev.backgrounder.di.SettingsQualifier
import dev.backgrounder.macos.NsBackgroundActivityBackedScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

public val backgrounderMacosModule: Module = module {
    single<Settings>(qualifier = SettingsQualifier) {
        NSUserDefaultsSettings(
            NSUserDefaults(suiteName = "dev.backgrounder.shared"),
        )
    }
    single<Scheduler> {
        NsBackgroundActivityBackedScheduler(
            registry = get(),
            ephemeral = get<EphemeralRegistry>(),
            eventListener = get(),
        )
    }
}
