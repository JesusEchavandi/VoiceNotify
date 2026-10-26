package com.notifyvoice.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.notifyvoice.app.domain.Settings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocalVoice(val id: String, val label: String)
data class SpeechStatus(val initialized: Boolean = false, val voices: List<LocalVoice> = emptyList())
class SpeechFailure : Exception("Offline synthesis failed")

/** This class never calls TTS.speak: synthesis cannot send sound to the speaker. */
class OfflineSpeech(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val folder = File(context.cacheDir, "speech").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    private val _status = MutableStateFlow(SpeechStatus())
    val status = _status.asStateFlow()
    private var engine: TextToSpeech? = null
    private var pending: CancellableContinuation<File>? = null
    private var pendingId: String? = null
    private var pendingFile: File? = null
    private val cancelledFiles = mutableMapOf<String, File>()

    init { initialize(context) }

    private fun initialize(context: Context) {
        engine = TextToSpeech(context.applicationContext) { result ->
            handler.post {
                if (result != TextToSpeech.SUCCESS) { _status.value = SpeechStatus(true); return@post }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { handler.post { complete(utteranceId, false) } }
                    @Deprecated("Legacy engine callback")
                    override fun onError(utteranceId: String?) { handler.post { complete(utteranceId, true) } }
                    override fun onError(utteranceId: String?, errorCode: Int) { handler.post { complete(utteranceId, true) } }
                })
                refreshVoices()
            }
        }
    }

    fun refreshVoices() {
        val voices = runCatching {
            engine?.voices.orEmpty().filter {
                it.locale.language == "es" && !it.isNetworkConnectionRequired &&
                    "notInstalled" !in it.features.orEmpty()
            }.sortedBy { it.name }.map { LocalVoice(it.name, "${it.locale.getDisplayName(java.util.Locale.forLanguageTag("es"))} · ${it.name}") }
        }.getOrDefault(emptyList())
        _status.value = SpeechStatus(true, voices)
    }

    suspend fun synthesize(text: String, settings: Settings): File = withTimeout(12_000) {
        suspendCancellableCoroutine { continuation ->
            val tts = engine
            val available = tts?.voices.orEmpty().filter {
                it.locale.language == "es" && !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty()
            }
            val voice = available.firstOrNull { it.name == settings.voiceName }
                ?: available.firstOrNull { it.locale.country == "PE" }
                ?: available.firstOrNull { it.locale.country == "MX" }
                ?: available.sortedBy { it.name }.firstOrNull()
            if (tts == null || voice == null) { continuation.resumeWithException(SpeechFailure()); return@suspendCancellableCoroutine }
            val id = UUID.randomUUID().toString()
            val file = File(folder, "$id.wav")
            pending = continuation; pendingId = id; pendingFile = file
            continuation.invokeOnCancellation { handler.post { if (pendingId == id) cancel() } }
            if (tts.setVoice(voice) == TextToSpeech.ERROR || tts.setSpeechRate(settings.speed) == TextToSpeech.ERROR ||
                tts.synthesizeToFile(text, null, file, id) == TextToSpeech.ERROR) complete(id, true)
        }
    }

    private fun complete(id: String?, error: Boolean) {
        if (id == null) return
        if (id != pendingId) { cancelledFiles.remove(id)?.delete(); return }
        val continuation = pending
        val file = pendingFile
        pending = null; pendingId = null; pendingFile = null
        if (continuation?.isActive != true) { file?.delete(); return }
        if (error || file == null || file.length() <= 44) {
            file?.delete(); continuation.resumeWithException(SpeechFailure())
        } else continuation.resume(file) { _, value, _ -> value.delete() }
    }

    fun cancel() {
        engine?.stop()
        val continuation = pending
        val id = pendingId
        val file = pendingFile
        if (id != null && file != null) {
            cancelledFiles[id] = file
            file.delete()
            // Some engines finish a cancelled file write asynchronously.
            handler.postDelayed({ file.delete() }, 1_000)
            handler.postDelayed({ cancelledFiles.remove(id)?.delete() }, 5_000)
        }
        pending = null; pendingId = null
        pendingFile = null
        continuation?.cancel()
    }
}
