package com.notifyvoice.app.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.notifyvoice.app.domain.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlaybackFailure(val reason: Reason) : Exception(reason.name)

/**
 * Prefer a user-approved endpoint, start muted, verify every actual route (API 36+),
 * rewind while muted, and only then unmute. Never requests focus or changes global volume.
 * Android delivers routing callbacks asynchronously: hardware validation is still required.
 */
class HeadphonePlayer(private val safety: DeviceSafety) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var tick: Runnable? = null

    suspend fun play(file: File, settings: Settings, gate: () -> Reason?, onAudible: () -> Unit) = withTimeout(45_000) {
        suspendCancellableCoroutine { pending ->
            val device = safety.headphoneDevices().firstOrNull { DeviceSafety.deviceId(it) in settings.approvedDevices }
            if (device == null) { pending.resumeWithException(PlaybackFailure(Reason.NO_HEADPHONES)); return@suspendCancellableCoroutine }
            continuation = pending
            val media = MediaPlayer()
            player = media
            var audible = false
            var seeking = false
            var started: Long? = null
            fun verifiedRoute(): Boolean {
                val routes: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= 36) media.routedDevices
                    else listOfNotNull(media.routedDevice)
                return safeRoutes(routes.map {
                    if (DeviceSafety.isHeadphone(it) && DeviceSafety.deviceId(it) in settings.approvedDevices)
                        OutputKind.HEADPHONES else OutputKind.OTHER
                })
            }
            fun validate() {
                if (player !== media || !pending.isActive) return
                val playbackStarted = started ?: return
                val reason = gate()
                if (reason != null) { fail(reason); return }
                val safe = runCatching { verifiedRoute() }.getOrDefault(false)
                if (!safe && (audible || SystemClock.elapsedRealtime() - playbackStarted > 1_500)) {
                    fail(Reason.ROUTE_UNSAFE); return
                }
                if (safe && !audible && !seeking) {
                    seeking = true
                    media.seekTo(0, MediaPlayer.SEEK_CLOSEST)
                }
            }
            pending.invokeOnCancellation { handler.post { if (player === media) release() } }
            try {
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                media.setVolume(0f, 0f)
                media.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener {
                    // A change while audible is cancelled even when both endpoints are approved.
                    if (player === media && audible) fail(Reason.ROUTE_UNSAFE) else validate()
                }, handler)
                media.setDataSource(file.absolutePath)
                media.setOnErrorListener { _, _, _ -> fail(Reason.AUDIO_ERROR); true }
                media.setOnCompletionListener {
                    if (audible) finish() else fail(Reason.ROUTE_UNSAFE)
                }
                media.setOnSeekCompleteListener {
                    if (player !== media || !pending.isActive) return@setOnSeekCompleteListener
                    if (gate() != null || !verifiedRoute()) { fail(Reason.ROUTE_UNSAFE); return@setOnSeekCompleteListener }
                    audible = true
                    media.setVolume(settings.volume, settings.volume)
                    onAudible()
                }
                media.setOnPreparedListener {
                    if (player !== media || !pending.isActive) return@setOnPreparedListener
                    if (gate() != null) { fail(gate() ?: Reason.ROUTE_UNSAFE); return@setOnPreparedListener }
                    // Samsung returns false before a native player exists. Select the
                    // endpoint only once prepareAsync has completed, still fully muted.
                    if (!media.setPreferredDevice(device)) { fail(Reason.ROUTE_UNSAFE); return@setOnPreparedListener }
                    started = SystemClock.elapsedRealtime()
                    media.start()
                    val check = object : Runnable {
                        override fun run() {
                            if (player !== media || !pending.isActive) return
                            validate()
                            if (player === media) handler.postDelayed(this, 25)
                        }
                    }
                    tick = check
                    handler.post(check)
                }
                media.prepareAsync()
            } catch (_: Exception) { fail(Reason.AUDIO_ERROR) }
        }
    }

    private fun finish() {
        val pending = continuation
        continuation = null
        release()
        if (pending?.isActive == true) pending.resume(Unit)
    }
    private fun fail(reason: Reason) {
        val pending = continuation
        continuation = null
        release()
        if (pending?.isActive == true) pending.resumeWithException(PlaybackFailure(reason))
    }
    private fun release() {
        tick?.let { handler.removeCallbacks(it) }; tick = null
        player?.let { media ->
            runCatching { media.setVolume(0f, 0f) }
            // release() stops playback in every state; stop() is invalid when preparation
            // has not completed and used to generate misleading native error callbacks.
            media.release()
        }
        player = null
    }
    fun stop() {
        val pending = continuation
        continuation = null
        release()
        pending?.cancel()
    }
}
