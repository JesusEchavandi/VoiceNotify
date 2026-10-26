package com.notifyvoice.app.domain

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

enum class ChatApp(val label: String, val packages: Set<String>, val initials: String) {
    WHATSAPP("WhatsApp", setOf("com.whatsapp", "com.whatsapp.w4b"), "WA"),
    TIKTOK("TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TK"),
    MESSENGER("Messenger", setOf("com.facebook.orca", "com.facebook.mlite"), "ME"),
    INSTAGRAM("Instagram", setOf("com.instagram.android"), "IG");

    companion object { fun fromPackage(name: String) = entries.firstOrNull { name in it.packages } }
}

enum class ReadingMode(val label: String) {
    APP_ONLY("Solo aplicación"), SENDER("Aplicación y remitente"), FULL("También leer el mensaje")
}

data class AppRule(
    val enabled: Boolean = true,
    val mode: ReadingMode = ReadingMode.SENDER,
    val skipGroups: Boolean = false,
)

data class Settings(
    val enabled: Boolean = false,
    val speed: Float = 1f,
    val volume: Float = .7f,
    val voiceName: String = "",
    val approvedDevices: Set<String> = emptySet(),
    val apps: Map<ChatApp, AppRule> = ChatApp.entries.associateWith { AppRule() },
    // Only explicitly excluded labels are persisted. Observed chats stay in RAM.
    val excludedConversations: Map<String, String> = emptyMap(),
    val excludedSenders: Map<String, String> = emptyMap(),
) {
    fun rule(app: ChatApp) = apps[app] ?: AppRule()
}

data class CapturedMessage(val timestamp: Long, val sender: String?, val text: String)

data class NotificationSnapshot(
    val app: ChatApp,
    val key: String,
    val postedAt: Long,
    val notificationWhen: Long,
    val category: String? = null,
    val channelId: String = "",
    val shortcutId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val conversationTitle: String? = null,
    val isGroup: Boolean? = null,
    val isConversation: Boolean = false,
    val hasMessagingStyle: Boolean = false,
    val messages: List<CapturedMessage> = emptyList(),
    val summary: Boolean = false,
    val ongoing: Boolean = false,
)

data class ChatMessage(
    val app: ChatApp,
    val notificationKey: String,
    val conversationId: String,
    val conversationLabel: String,
    val sender: String?,
    val text: String?,
    val timestamp: Long,
    val group: Boolean?,
    val identity: String,
) {
    val senderId: String? get() = sender?.let { stableId(app.name, normalize(it)) }
}

data class ObservedConversation(
    val app: ChatApp,
    val id: String,
    val label: String,
    val senderId: String?,
    val senderLabel: String?,
    val group: Boolean?,
)

enum class Reason(val label: String) {
    SPOKEN("Aviso reproducido"), PAUSED("Lectura pausada"), APP_DISABLED("Aplicación desactivada"),
    NOT_CHAT("Aviso sin evidencia de chat"), SUMMARY("Resumen agrupado omitido"),
    DUPLICATE("Actualización sin mensajes nuevos"), EXCLUDED("Conversación o persona excluida"),
    GROUP("Grupo omitido"), NO_HEADPHONES("Sin audífonos compatibles"),
    ROUTE_UNSAFE("Ruta de audio no segura"), SILENT("Celular en modo silencio"),
    MEDIA_MUTED("Volumen multimedia en cero"),
    DND("No molestar activo o estado desconocido"), CALL("Llamada o comunicación activa"),
    NO_VOICE("Falta una voz local en español"), TTS_ERROR("No se pudo sintetizar la voz"),
    AUDIO_ERROR("No se pudo reproducir el audio"), EXPIRED("Aviso antiguo descartado"),
    QUEUE_FULL("Cola llena: se descartó el aviso más antiguo"),
    PERMISSION("Falta acceso a notificaciones"), DISCONNECTED("Servicio de notificaciones desconectado"),
    CONNECTED("Servicio conectado; avisos anteriores omitidos"), STOPPED("Lectura detenida"),
    SETTINGS_CHANGED("Configuración actualizada; cola vaciada"), STORAGE("No se pudieron guardar los ajustes"),
    FILTER_UNKNOWN_GROUP("No se pudo confirmar si es un grupo"),
    UNAPPROVED_DEVICE("Autoriza tus audífonos en Inicio"),
}

data class Diagnostic(
    val at: Long,
    val reason: Reason,
    val app: ChatApp? = null,
    val latencyMs: Long? = null,
)

fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

fun stableId(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("\u0000").toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
