package com.notifyvoice.app.audio

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.notifyvoice.app.domain.Reason
import com.notifyvoice.app.domain.RingerMode
import com.notifyvoice.app.domain.playbackConditionsReason
import com.notifyvoice.app.domain.stableId
import com.notifyvoice.app.notifications.VoiceNotificationListener

class DeviceSafety(private val context: Context) {
    val audio: AudioManager = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun notificationAccess(): Boolean = notifications.isNotificationListenerAccessGranted(
        ComponentName(context, VoiceNotificationListener::class.java))

    fun headphoneDevices(): List<AudioDeviceInfo> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { isHeadphone(it) }

    fun blockedReason(approvedDevices: Set<String>, requireNotificationAccess: Boolean = true): Reason? {
        if (requireNotificationAccess && !notificationAccess()) return Reason.PERMISSION
        if (headphoneDevices().isEmpty()) return Reason.NO_HEADPHONES
        if (headphoneDevices().none { deviceId(it) in approvedDevices }) return Reason.UNAPPROVED_DEVICE
        val ringerMode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> RingerMode.SOUND
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            else -> null
        }
        return playbackConditionsReason(
            ringerMode = ringerMode,
            communicationActive = audio.mode != AudioManager.MODE_NORMAL,
            // Do not speak during any DND mode, including priority mode.
            dndAllowsSound = notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL,
            mediaVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        )
    }

    companion object {
        // Audio device IDs are session-local. Persist a hash of the descriptor instead.
        // Explicit approval is necessary: an A2DP endpoint may also be a loudspeaker.
        fun deviceId(device: AudioDeviceInfo): String = stableId(device.type.toString(),
            device.productName.toString(), device.address)
        fun isHeadphone(device: AudioDeviceInfo): Boolean = when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            else -> Build.VERSION.SDK_INT >= 31 && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }
}
