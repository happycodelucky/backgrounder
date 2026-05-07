// ExperimentalForeignApi: required for cinterop memScoped/alloc/ptr APIs used to
// allocate the `NSError**` out-pointer. Stable in practice.
// BetaInteropApi: required to read `ObjCObjectVar<NSError?>.value` (the ObjC
// reference value-extension is still gated beta in Kotlin/Native, but stable in
// the in-tree iOS interop tests).
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.backgrounder.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSError

/**
 * Result of a `BGTaskScheduler.submitTaskRequest(_:error:)` call.
 *
 * The Foundation API signals failure by populating an `NSError**` out-parameter
 * — passing `null` for that parameter causes iOS to raise an `NSException`
 * instead, which crosses the K/N FFI boundary as an unhandled crash. We always
 * pass a real out-pointer and surface the error here.
 */
internal sealed interface BGSubmitResult {
    object Success : BGSubmitResult

    data class Failure(
        val message: String,
    ) : BGSubmitResult
}

/**
 * Submit [request] to the shared `BGTaskScheduler` with a real `NSError**`
 * out-pointer so failures don't escape as unhandled `NSException`s.
 */
internal fun submitBGTaskRequest(request: BGTaskRequest): BGSubmitResult =
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, errorPtr.ptr)
        val err = errorPtr.value
        if (err != null) {
            BGSubmitResult.Failure(err.localizedDescription)
        } else {
            BGSubmitResult.Success
        }
    }
