package com.notifyvoice.app.domain

import org.junit.Assert.*
import org.junit.Test

class PipelineTest {
    private fun snapshot(app: ChatApp = ChatApp.WHATSAPP) = NotificationSnapshot(app, "key", 10_000, 10_000,
        category = "msg", title = "Ana", text = "Hola", isGroup = false)
    private fun extract(snapshot: NotificationSnapshot): List<ChatMessage> =
        (NotificationAdapter.forApp(snapshot.app).extract(snapshot) as Extraction.Messages).values
    private fun message() = extract(snapshot()).single()

    @Test fun `four apps accept structured chats`() {
        ChatApp.entries.forEach { assertEquals(it, extract(snapshot(it)).single().app) }
    }
    @Test fun `advertising without chat evidence is omitted across apps`() {
        ChatApp.entries.forEach {
            assertEquals(Extraction.Ignored(Reason.NOT_CHAT), NotificationAdapter.forApp(it).extract(
                snapshot(it).copy(category = "social", channelId = "recommendations", title = "Ana publicó un video")))
        }
    }
    @Test fun `summary is never spoken`() {
        assertEquals(Extraction.Ignored(Reason.SUMMARY), NotificationAdapter.forApp(ChatApp.WHATSAPP).extract(snapshot().copy(summary = true)))
    }
    @Test fun `ongoing and calls are never chats`() {
        listOf(snapshot().copy(ongoing = true), snapshot().copy(category = "call")).forEach {
            assertEquals(Extraction.Ignored(Reason.NOT_CHAT), NotificationAdapter.forApp(it.app).extract(it))
        }
    }
    @Test fun `messaging style alone is sufficient evidence`() {
        assertEquals("Ana", extract(snapshot().copy(category = null, hasMessagingStyle = true,
            messages = listOf(CapturedMessage(10_000, "Ana", "Hola")))).single().sender)
    }
    @Test fun `outgoing messages in messaging style are ignored`() {
        val values = extract(snapshot().copy(hasMessagingStyle = true, messages = listOf(
            CapturedMessage(10_000, "Ana", "Hola"), CapturedMessage(10_001, null, "Mi respuesta"))))
        assertEquals(1, values.size)
    }
    @Test fun `all outgoing snapshot is ignored`() {
        assertEquals(Extraction.Ignored(Reason.NOT_CHAT), NotificationAdapter.forApp(ChatApp.WHATSAPP).extract(
            snapshot().copy(hasMessagingStyle = true, messages = listOf(CapturedMessage(10_000, null, "Yo")))))
    }
    @Test fun `hidden sender and body yield generic phrase`() {
        val value = extract(snapshot().copy(title = "WhatsApp", text = null)).single()
        assertEquals("Tienes un mensaje de WhatsApp.", spokenText(value, ReadingMode.FULL))
    }
    @Test fun `sender mode never reads content`() {
        assertEquals("Mensaje de Ana en WhatsApp.", spokenText(message(), ReadingMode.SENDER))
    }
    @Test fun `app mode never reads sender or content`() {
        assertEquals("Tienes un mensaje de WhatsApp.", spokenText(message(), ReadingMode.APP_ONLY))
    }
    @Test fun `full mode reads available body`() {
        assertEquals("Mensaje de Ana en WhatsApp. Hola", spokenText(message(), ReadingMode.FULL))
    }
    @Test fun `links and control characters are sanitized`() {
        val value = message().copy(text = "Mira\nhttps://example.org/private?token=123\u200b fin")
        val spoken = spokenText(value, ReadingMode.FULL)
        assertFalse(spoken.contains("token")); assertTrue(spoken.contains("enlace")); assertFalse(spoken.contains('\n'))
    }
    @Test fun `group says participant and conversation`() {
        val value = extract(snapshot().copy(hasMessagingStyle = true, isGroup = true, conversationTitle = "Familia",
            messages = listOf(CapturedMessage(10_000, "Ana", "Hola")))).single()
        assertEquals("Mensaje de Ana en el grupo Familia en WhatsApp.", spokenText(value, ReadingMode.SENDER))
    }
    @Test fun `duplicate snapshots do not repeat content`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.isNew(message(), 0)); assertFalse(dedup.isNew(message(), 1))
    }
    @Test fun `cosmetic post time update does not replay fallback message`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.isNew(message(), 0))
        assertFalse(dedup.isNew(extract(snapshot().copy(postedAt = 20_000)).single(), 1))
    }
    @Test fun `same content at a new event time is a new message`() {
        val dedup = MessageDeduplicator()
        assertTrue(dedup.isNew(message(), 0))
        assertTrue(dedup.isNew(extract(snapshot().copy(notificationWhen = 20_000)).single(), 1))
    }
    @Test fun `messaging update only appends new incoming message`() {
        val first = snapshot().copy(hasMessagingStyle = true, messages = listOf(CapturedMessage(10_000, "Ana", "Hola")))
        val second = first.copy(messages = first.messages + CapturedMessage(10_001, "Ana", "Hola"))
        val dedup = MessageDeduplicator()
        dedup.seed(extract(first), 0)
        assertEquals(1, extract(second).count { dedup.isNew(it, 1) })
    }
    @Test fun `identical messages in same millisecond preserve occurrence count`() {
        val item = CapturedMessage(10_000, "Ana", "Hola")
        val values = extract(snapshot().copy(hasMessagingStyle = true, messages = listOf(item, item)))
        assertNotEquals(values[0].identity, values[1].identity)
    }
    @Test fun `seeded active notifications are not replayed`() {
        val dedup = MessageDeduplicator(); dedup.seed(listOf(message()), 0)
        assertFalse(dedup.isNew(message(), 10))
    }
    @Test fun `dedup retention is bounded`() {
        val dedup = MessageDeduplicator(capacity = 1, retentionMs = 10)
        dedup.isNew(message(), 0)
        dedup.isNew(message().copy(identity = "another"), 1)
        assertTrue(dedup.isNew(message(), 2))
        assertTrue(dedup.isNew(message(), 20))
    }
    @Test fun `disabled master and app filters apply`() {
        assertEquals(Reason.PAUSED, filterReason(message(), Settings()))
        assertEquals(Reason.APP_DISABLED, filterReason(message(), Settings(enabled = true,
            apps = mapOf(ChatApp.WHATSAPP to AppRule(enabled = false)))))
    }
    @Test fun `conversation exclusion applies`() {
        val m = message()
        assertEquals(Reason.EXCLUDED, filterReason(m, Settings(enabled = true, excludedConversations = mapOf(m.conversationId to "Ana"))))
    }
    @Test fun `sender exclusion also applies inside group`() {
        val m = message().copy(group = true)
        assertEquals(Reason.EXCLUDED, filterReason(m, Settings(enabled = true, excludedSenders = mapOf(m.senderId!! to "Ana"))))
    }
    @Test fun `skip groups fails closed when group type is unknown`() {
        val settings = Settings(enabled = true, apps = mapOf(ChatApp.WHATSAPP to AppRule(skipGroups = true)))
        assertEquals(Reason.GROUP, filterReason(message().copy(group = true), settings))
        assertEquals(Reason.FILTER_UNKNOWN_GROUP, filterReason(message().copy(group = null), settings))
        assertNull(filterReason(message().copy(group = false), settings))
    }
    @Test fun `exclusions are scoped to each app`() {
        assertNotEquals(message().conversationId, extract(snapshot(ChatApp.INSTAGRAM)).single().conversationId)
        assertNotEquals(message().senderId, extract(snapshot(ChatApp.INSTAGRAM)).single().senderId)
    }
    @Test fun `conversation identity uses shortcut when available`() {
        val first = extract(snapshot().copy(shortcutId = "person42")).single()
        val renamed = extract(snapshot().copy(shortcutId = "person42", title = "Ana María")).single()
        assertEquals(first.conversationId, renamed.conversationId)
    }
    @Test fun `queue caps at five and drops oldest pending item`() {
        val queue = AnnouncementQueue()
        repeat(5) { assertNull(queue.offer(Announcement(null, "$it", it.toLong()))) }
        assertEquals("0", queue.offer(Announcement(null, "5", 5))?.text)
        assertEquals(5, queue.size); assertEquals("1", queue.poll()?.text)
    }
    @Test fun `queue expiration uses monotonic receive time`() {
        val queue = AnnouncementQueue(); val value = Announcement(null, "Hola", 10)
        assertFalse(queue.expired(value, 30_010)); assertTrue(queue.expired(value, 30_011))
    }
    @Test fun `disconnect clearing cannot replay old queue`() {
        val queue = AnnouncementQueue(); queue.offer(Announcement(null, "Hola", 0)); queue.clear()
        assertNull(queue.poll()); assertEquals(0, queue.size)
    }
    @Test fun `unknown and mixed routes never unmute`() {
        assertFalse(safeRoutes(emptyList()))
        assertFalse(safeRoutes(listOf(OutputKind.SPEAKER)))
        assertFalse(safeRoutes(listOf(OutputKind.HEADPHONES, OutputKind.SPEAKER)))
        assertFalse(safeRoutes(listOf(OutputKind.OTHER)))
        assertTrue(safeRoutes(listOf(OutputKind.HEADPHONES)))
    }
    @Test fun `only explicitly supported packages match`() {
        assertEquals(ChatApp.WHATSAPP, ChatApp.fromPackage("com.whatsapp.w4b"))
        assertEquals(ChatApp.TIKTOK, ChatApp.fromPackage("com.ss.android.ugc.trill"))
        assertNull(ChatApp.fromPackage("com.whatsapp.fake"))
    }
}
