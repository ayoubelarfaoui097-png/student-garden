// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.video

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Copies only the decoded video surface, excluding controls and app chrome. */
interface VideoFrameCapture { suspend fun capture(surface: SurfaceView): Bitmap }
class AndroidVideoFrameCapture : VideoFrameCapture {
    override suspend fun capture(surface: SurfaceView): Bitmap = suspendCancellableCoroutine { continuation ->
        if (!surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) {
            continuation.resumeWithException(IllegalStateException("No video frame")); return@suspendCancellableCoroutine
        }
        val scale = minOf(1f, 1600f / maxOf(surface.width, surface.height))
        val bitmap = Bitmap.createBitmap((surface.width * scale).toInt().coerceAtLeast(1), (surface.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, bitmap, { result ->
                if (!continuation.isActive) bitmap.recycle()
                else if (result == PixelCopy.SUCCESS) continuation.resume(bitmap) { _, value, _ -> value.recycle() }
                else { bitmap.recycle(); continuation.resumeWithException(IllegalStateException("Frame capture failed: $result")) }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { bitmap.recycle(); if (continuation.isActive) continuation.resumeWithException(e) }
    }
}
