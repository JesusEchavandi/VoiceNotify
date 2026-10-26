package com.notifyvoice.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.notifyvoice.app.data.SettingsRepository
import com.notifyvoice.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class ConnectedHeadphone(val id: String, val label: String)
data class RuntimeStatus(
    val notificationAccess: Boolean = false,
    val listenerConnected: Boolean = false,
    val headphones: List<ConnectedHeadphone> = emptyList(),
    val blocked: Reason? = null,
    val speaking: Boolean = false,
    val pending: Int = 0,
)

class AnnouncementController(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    val safety = DeviceSafety(context)
    private val _diagnostics = MutableStateFlow<List<Diagnostic>>(emptyList())
    val diagnostics = _diagnostics.asStateFlow()
    val preferences = SettingsRepository(context, scope) { record(Reason.STORAGE) }
    val speech = OfflineSpeech(context)
    private val player = HeadphonePlayer(safety)
    private val deduplicator = MessageDeduplicator()
    private val queue = AnnouncementQueue()
    private val _runtime = MutableStateFlow(RuntimeStatus())
    val runtime = _runtime.asStateFlow()
    private val _observed = MutableStateFlow<List<ObservedConversation>>(emptyList())
    val observed = _observed.asStateFlow()
    private var worker: Job? = null
    private var connected = false

    init {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop(Reason.ROUTE_UNSAFE)
                else if (gate(false) != null) stop(gate(false) ?: Reason.ROUTE_UNSAFE)
                refresh()
            }
        }, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        safety.audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any { DeviceSafety.isHeadphone(it) }) stop(Reason.ROUTE_UNSAFE)
                refresh()
            }
        }, handler)
        scope.launch {
            preferences.settings.drop(1).collect {
                // Includes exclusions, mute, device approvals, and voice changes.
                stop(Reason.SETTINGS_CHANGED, logWhenIdle = false)
                refresh()
            }
        }
        scope.launch { speech.status.collect { refresh() } }
        // Bound listener keeps the process active; no polling service or silent foreground audio.
        refresh()
    }

    fun refresh() {
        val headphones = safety.headphoneDevices().map {
            ConnectedHeadphone(DeviceSafety.deviceId(it), it.productName.toString().take(100))
        }.distinctBy { it.id }
        _runtime.value = _runtime.value.copy(notificationAccess = safety.notificationAccess(),
            listenerConnected = connected, headphones = headphones,
            blocked = gate(true), pending = queue.size)
    }

    private fun gate(requirePermission: Boolean): Reason? {
        val blocked = safety.blockedReason(preferences.settings.value.approvedDevices, requirePermission)
        if (blocked != null) return blocked
        if (speech.status.value.voices.isEmpty()) return Reason.NO_VOICE
        return null
    }

    fun listenerConnected(active: List<NotificationSnapshot>) {
        connected = true
        val now = SystemClock.elapsedRealtime()
        active.forEach { snapshot ->
            val extraction = NotificationAdapter.forApp(snapshot.app).extract(snapshot)
            if (extraction is Extraction.Messages) deduplicator.seed(extraction.values, now)
        }
        record(Reason.CONNECTED)
        refresh()
    }

    fun listenerDisconnected() {
        connected = false
        stop(Reason.DISCONNECTED)
        refresh()
    }

    fun receive(snapshot: NotificationSnapshot) {
        if (!preferences.loaded.value) return // Never queue until stored safety preferences are loaded.
        val result = NotificationAdapter.forApp(snapshot.app).extract(snapshot)
        if (result is Extraction.Ignored) { record(result.reason, snapshot.app); return }
        val messages = (result as Extraction.Messages).values
        val now = SystemClock.elapsedRealtime()
        var foundNew = false
        messages.forEach { message ->
            if (!deduplicator.isNew(message, now)) return@forEach
            foundNew = true
            observe(message)
            // Do not read backlog embedded in a newly delivered MessagingStyle snapshot.
            if (snapshot.hasMessagingStyle && System.currentTimeMillis() - message.timestamp > 30_000) {
                record(Reason.EXPIRED, message.app); return@forEach
            }
            val reason = filterReason(message, preferences.settings.value) ?: gate(true)
            if (reason != null) { record(reason, message.app); return@forEach }
            enqueue(Announcement(message, spokenText(message, preferences.settings.value.rule(message.app).mode), now))
        }
        if (!foundNew) record(Reason.DUPLICATE, snapshot.app)
    }

    private fun observe(message: ChatMessage) {
        val item = ObservedConversation(message.app, message.conversationId, message.conversationLabel.take(160),
            message.senderId, message.sender?.take(160), message.group)
        // Key includes sender so individuals inside group chats can be excluded too.
        _observed.value = (listOf(item) + _observed.value.filterNot { it.id == item.id && it.senderId == item.senderId }).take(60)
    }

    fun testVoice() {
        val reason = gate(false)
        if (reason != null) { record(reason); refresh(); return }
        enqueue(Announcement(null, "VoiceNotify está listo. Escucharás tus mensajes solo en estos audífonos.", SystemClock.elapsedRealtime()))
    }

    private fun enqueue(value: Announcement) {
        queue.offer(value)?.let { record(Reason.QUEUE_FULL, it.message?.app) }
        refresh()
        if (worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (isActive) {
                    val next = queue.poll() ?: break
                    refresh()
                    if (queue.expired(next, SystemClock.elapsedRealtime())) { record(Reason.EXPIRED, next.message?.app); continue }
                    val reason = next.message?.let { filterReason(it, preferences.settings.value) } ?: gate(next.message != null)
                    if (reason != null) { record(reason, next.message?.app); continue }
                    var file: File? = null
                    try {
                        file = speech.synthesize(next.text, preferences.settings.value)
                        if (queue.expired(next, SystemClock.elapsedRealtime())) { record(Reason.EXPIRED, next.message?.app); continue }
                        var latency: Long? = null
                        player.play(file, preferences.settings.value, {
                            next.message?.let { filterReason(it, preferences.settings.value) } ?: gate(next.message != null)
                        }) {
                            latency = SystemClock.elapsedRealtime() - next.receivedElapsed
                            _runtime.value = _runtime.value.copy(speaking = true)
                        }
                        record(Reason.SPOKEN, next.message?.app, latency)
                    } catch (failure: PlaybackFailure) {
                        record(failure.reason, next.message?.app)
                        if (failure.reason in setOf(Reason.ROUTE_UNSAFE, Reason.NO_HEADPHONES, Reason.UNAPPROVED_DEVICE)) queue.clear()
                    } catch (timeout: TimeoutCancellationException) {
                        speech.cancel(); player.stop(); record(Reason.TTS_ERROR, next.message?.app)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        record(Reason.TTS_ERROR, next.message?.app)
                    } finally {
                        file?.delete()
                        _runtime.value = _runtime.value.copy(speaking = false)
                    }
                }
            } finally { refresh() }
        }
    }

    fun stop(reason: Reason = Reason.STOPPED, logWhenIdle: Boolean = true) {
        val hadWork = worker?.isActive == true || queue.size > 0
        worker?.cancel(); worker = null
        queue.clear(); player.stop(); speech.cancel()
        _runtime.value = _runtime.value.copy(speaking = false, pending = 0)
        if (logWhenIdle || hadWork) record(reason)
    }

    fun record(reason: Reason, app: ChatApp? = null, latencyMs: Long? = null) {
        _diagnostics.value = (listOf(Diagnostic(System.currentTimeMillis(), reason, app, latencyMs)) + _diagnostics.value).take(80)
    }
    fun clearDiagnostics() { _diagnostics.value = emptyList() }
}
