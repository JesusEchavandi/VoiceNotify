package com.notifyvoice.app.domain

sealed interface Extraction {
    data class Messages(val values: List<ChatMessage>) : Extraction
    data class Ignored(val reason: Reason) : Extraction
}

/** App-specific channel evidence is deliberately narrow; a title alone is not a chat. */
sealed class NotificationAdapter(private val app: ChatApp, private val chatChannel: Regex) {
    fun extract(snapshot: NotificationSnapshot): Extraction {
        if (snapshot.summary) return Extraction.Ignored(Reason.SUMMARY)
        if (snapshot.ongoing || snapshot.category == "call" || snapshot.category == "transport") {
            return Extraction.Ignored(Reason.NOT_CHAT)
        }
        val hasChatEvidence = snapshot.hasMessagingStyle || snapshot.isConversation ||
            snapshot.category == "msg" || chatChannel.matches(normalize(snapshot.channelId))
        if (!hasChatEvidence) return Extraction.Ignored(Reason.NOT_CHAT)

        val group = snapshot.isGroup
        val senderTitle = snapshot.title?.trim()?.takeIf { it.isNotBlank() && normalize(it) != normalize(app.label) }
        val conversation = snapshot.conversationTitle?.trim()?.takeIf(String::isNotBlank)
            ?: senderTitle ?: "Conversación sin nombre"
        val conversationId = stableId(app.name, snapshot.shortcutId?.let { "shortcut:$it" }
            ?: "title:${normalize(conversation)}")
        val occurrences = mutableMapOf<String, Int>()
        val messages = if (snapshot.hasMessagingStyle && snapshot.messages.isNotEmpty()) {
            // Null senders in MessagingStyle are the device owner's outgoing messages.
            snapshot.messages.filter { !it.sender.isNullOrBlank() }.map { item ->
                val content = stableId(item.timestamp.toString(), item.sender.orEmpty(), item.text)
                val occurrence = occurrences.getOrDefault(content, 0)
                occurrences[content] = occurrence + 1
                ChatMessage(app, snapshot.key, conversationId, conversation,
                    item.sender?.takeIf(String::isNotBlank), item.text.takeIf(String::isNotBlank),
                    item.timestamp.takeIf { it > 0 } ?: snapshot.postedAt, group,
                    stableId(snapshot.key, content, occurrence.toString()))
            }
        } else {
            val sender = if (group == true) null else senderTitle
            listOf(ChatMessage(app, snapshot.key, conversationId, conversation,
                sender, snapshot.text?.takeIf(String::isNotBlank), snapshot.postedAt, group,
                // postTime changes on many cosmetic updates; Notification.when is the
                // original event time and allows identical text in a later message.
                stableId(snapshot.key, snapshot.notificationWhen.toString(),
                    snapshot.title.orEmpty(), snapshot.text.orEmpty())))
        }
        return if (messages.isEmpty()) Extraction.Ignored(Reason.NOT_CHAT) else Extraction.Messages(messages)
    }

    class WhatsApp : NotificationAdapter(ChatApp.WHATSAPP, Regex("(?:individual|group|message|chat)_.*"))
    class TikTok : NotificationAdapter(ChatApp.TIKTOK, Regex("(?:direct_messages|im_messages|chat_messages)"))
    class Messenger : NotificationAdapter(ChatApp.MESSENGER, Regex("(?:messages|messaging|chats)(?:_.*)?"))
    class Instagram : NotificationAdapter(ChatApp.INSTAGRAM, Regex("(?:direct|direct_messages|ig_direct)(?:_.*)?"))

    companion object {
        private val adapters = mapOf(ChatApp.WHATSAPP to WhatsApp(), ChatApp.TIKTOK to TikTok(),
            ChatApp.MESSENGER to Messenger(), ChatApp.INSTAGRAM to Instagram())
        fun forApp(app: ChatApp): NotificationAdapter = adapters.getValue(app)
    }
}
