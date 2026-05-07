package dev.backgrounder.android

import androidx.work.Constraints
import androidx.work.NetworkType
import dev.backgrounder.NetworkRequirement
import dev.backgrounder.WorkConstraints

/** Translate cross-platform [WorkConstraints] to a WorkManager [Constraints]. */
internal fun WorkConstraints.toWorkManagerConstraints(): Constraints =
    Constraints.Builder()
        .setRequiredNetworkType(networkRequired.toNetworkType())
        .setRequiresCharging(requiresCharging)
        .build()

private fun NetworkRequirement.toNetworkType(): NetworkType = when (this) {
    NetworkRequirement.None -> NetworkType.NOT_REQUIRED
    NetworkRequirement.Any -> NetworkType.CONNECTED
    NetworkRequirement.Unmetered -> NetworkType.UNMETERED
}
