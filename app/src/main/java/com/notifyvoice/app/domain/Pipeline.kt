package com.notifyvoice.app.domain

/** Retains hashes only, bounded both by time and count. */
class MessageDeduplicator(private val capacity: Int = 2048, private val retentionMs: Long = 600_000) {
    private val seen = linkedMapOf<String, Long>()
    fun isNew(message: ChatMessage, now: Long): Boolean {
        seen.entries.removeAll { now - it.value > retentionMs }
        if (seen.containsKey(message.identity)) return false
        seen[message.identity] = now
        while (seen.size > capacity) seen.remove(seen.keys.first())
        return true
    }
    fun seed(messages: List<ChatMessage>, now: Long) { messages.forEach { isNew(it, now) } }
}

fun filterReason(message: ChatMessage, settings: Settings): Reason? {
    if (!settings.enabled) return Reason.PAUSED
    val rule = settings.rule(message.app)
    if (!rule.enabled) return Reason.APP_DISABLED
    if (message.conversationId in settings.excludedConversations ||
        message.senderId?.let { it in settings.excludedSenders } == true) return Reason.EXCLUDED
    if (rule.skipGroups && message.group == true) return Reason.GROUP
    if (rule.skipGroups && message.group == null) return Reason.FILTER_UNKNOWN_GROUP
    return null
}

fun spokenText(message: ChatMessage, mode: ReadingMode): String {
    val app = message.app.label
    if (mode == ReadingMode.APP_ONLY) return "Tienes un mensaje de $app."
    val sender = message.sender?.let(::speechSafe)?.takeIf(String::isNotBlank)
    val group = if (message.group == true && message.conversationLabel != "Conversación sin nombre")
        " en el grupo ${speechSafe(message.conversationLabel)}" else ""
    val intro = if (sender != null) "Mensaje de $sender$group en $app" else "Tienes un mensaje$group de $app"
    val body = message.text?.let(::speechSafe)?.takeIf(String::isNotBlank)
    return if (mode == ReadingMode.FULL && body != null) "$intro. ${body.take(400)}" else "$intro."
}

private fun speechSafe(value: String): String = value
    .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "enlace")
    .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
    .replace(Regex("\\s+"), " ").trim().take(400)

data class Announcement(val message: ChatMessage?, val text: String, val receivedElapsed: Long)

/** All methods are confined to the controller's main dispatcher. Active speech is separate. */
class AnnouncementQueue(private val capacity: Int = 5, private val maxAgeMs: Long = 30_000) {
    private val pending = ArrayDeque<Announcement>()
    val size: Int get() = pending.size
    fun offer(value: Announcement): Announcement? {
        val dropped = if (pending.size >= capacity) pending.removeFirst() else null
        pending.addLast(value)
        return dropped
    }
    fun poll(): Announcement? = pending.removeFirstOrNull()
    fun expired(value: Announcement, now: Long): Boolean = now - value.receivedElapsed > maxAgeMs
    fun clear() { pending.clear() }
}

/** Empty/unknown or multi-route output is never allowed to enable audible playback. */
enum class OutputKind { HEADPHONES, SPEAKER, OTHER }
fun safeRoutes(routes: List<OutputKind>): Boolean = routes.isNotEmpty() && routes.all { it == OutputKind.HEADPHONES }
