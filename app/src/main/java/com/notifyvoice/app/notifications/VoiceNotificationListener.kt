package com.notifyvoice.app.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notifyvoice.app.VoiceNotifyApplication
import com.notifyvoice.app.domain.*

class VoiceNotificationListener : NotificationListenerService() {
    private val controller get() = (application as VoiceNotifyApplication).controller

    override fun onListenerConnected() {
        val active = runCatching { activeNotifications.orEmpty().mapNotNull { AndroidNotificationReader.snapshot(it, currentRanking) } }.getOrDefault(emptyList())
        controller.listenerConnected(active)
    }
    override fun onListenerDisconnected() {
        controller.listenerDisconnected()
        runCatching { requestRebind(android.content.ComponentName(this, VoiceNotificationListener::class.java)) }
    }
    override fun onDestroy() {
        controller.listenerDisconnected()
        super.onDestroy()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (sbn == null) return
        try {
            AndroidNotificationReader.snapshot(sbn, rankingMap)?.let { controller.receive(it) }
        } catch (_: RuntimeException) {
            // Untrusted, vendor-specific extras must never crash the listener or be logged.
            ChatApp.fromPackage(sbn.packageName)?.let { controller.record(Reason.NOT_CHAT, it) }
        }
    }

}

object AndroidNotificationReader {
    fun snapshot(sbn: StatusBarNotification, rankings: NotificationListenerService.RankingMap?): NotificationSnapshot? {
        val app = ChatApp.fromPackage(sbn.packageName) ?: return null
        val notification = sbn.notification
        val extras = notification.extras
        val style = runCatching { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) }.getOrNull()
        val ranking = NotificationListenerService.Ranking()
        val ranked = rankings?.getRanking(sbn.key, ranking) == true
        val group = when {
            style != null -> style.isGroupConversation
            extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) -> extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            else -> null
        }
        return NotificationSnapshot(
            app = app, key = sbn.key, postedAt = sbn.postTime, notificationWhen = notification.`when`,
            category = notification.category, channelId = notification.channelId.orEmpty(),
            shortcutId = notification.shortcutId,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(500),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.take(4_000),
            conversationTitle = style?.conversationTitle?.toString()?.take(500)
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.take(500),
            isGroup = group, isConversation = android.os.Build.VERSION.SDK_INT >= 31 && ranked && ranking.isConversation,
            hasMessagingStyle = style != null,
            messages = style?.messages.orEmpty().takeLast(25).map {
                CapturedMessage(it.timestamp, it.person?.name?.toString()?.take(500), it.text?.toString().orEmpty().take(4_000))
            },
            summary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }
}
