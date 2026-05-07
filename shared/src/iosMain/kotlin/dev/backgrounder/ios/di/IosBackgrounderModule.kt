@file:OptIn(ExperimentalForeignApi::class)

package dev.backgrounder.ios.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import dev.backgrounder.EphemeralRegistry
import dev.backgrounder.Scheduler
import dev.backgrounder.di.SettingsQualifier
import dev.backgrounder.ios.BGTaskBackedScheduler
import dev.backgrounder.ios.BGTaskHandlerRegistration
import dev.backgrounder.ios.IosCoroutineBridge
import dev.backgrounder.ios.IosEphemeralSweep
import dev.backgrounder.ios.IosStateStore
import dev.backgrounder.ios.IosTaskMutexes
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * iOS-specific Koin module. Wire alongside `backgrounderCommonModule`:
 *
 * ```
 * KoinKt.startKoin {
 *     modules(backgrounderCommonModule, backgrounderIosModule, /* yours */)
 * }
 * ```
 */
public val backgrounderIosModule: Module = module {
    single<Settings>(qualifier = SettingsQualifier) {
        NSUserDefaultsSettings(
            NSUserDefaults(suiteName = "dev.backgrounder.shared"),
        )
    }
    single { IosStateStore(get<Settings>(qualifier = SettingsQualifier)) }
    single { IosTaskMutexes() }

    single<BGTaskBackedScheduler> {
        BGTaskBackedScheduler(
            state = get(),
            mutexes = get(),
            ephemeral = get<EphemeralRegistry>(),
            eventListener = get(),
        )
    }
    single<Scheduler> { get<BGTaskBackedScheduler>() }

    single {
        IosCoroutineBridge(
            registry = get(),
            state = get(),
            mutexes = get(),
            eventListener = get(),
            applyResult = { task, taskId, attempt, result ->
                get<BGTaskBackedScheduler>().applyResult(task, taskId, attempt, result)
            },
        )
    }

    single { IosEphemeralSweep(get<EphemeralRegistry>(), get<IosStateStore>()) }
    single { BGTaskHandlerRegistration(get(), get(), get()) }
}
