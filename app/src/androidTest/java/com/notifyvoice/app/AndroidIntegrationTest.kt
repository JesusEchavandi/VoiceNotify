package com.notifyvoice.app

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notifyvoice.app.domain.*
import com.notifyvoice.app.notifications.AndroidNotificationReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic notifications exercise the Android boundary; these are not real app compatibility tests. */
@RunWith(AndroidJUnit4::class)
class AndroidIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<VoiceNotifyApplication>()

    @Before fun paused() {
        runBlocking { context.controller.preferences.setEnabled(false) }
        compose.waitUntil(10_000) { context.controller.preferences.loaded.value }
    }

    @Test fun homeAndAllSettingsScreensRender() {
        compose.onNodeWithText("VOICENOTIFY").assertIsDisplayed()
        compose.onNodeWithText("Tus mensajes,\na tu oído.").assertIsDisplayed()
        compose.onNodeWithText("Lectura en pausa").assertIsDisplayed()
        compose.onNodeWithText("Aplicaciones").performClick()
        compose.onNodeWithText("WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("TikTok").assertIsDisplayed()
        compose.onNodeWithText("Messenger").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Voz").onLast().performClick()
        compose.onNodeWithText("Una voz que se queda contigo").assertIsDisplayed()
        compose.onNodeWithText("Escuchar una prueba").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Diagnóstico").performClick()
        compose.onNodeWithText("Acceso a notificaciones").assertIsDisplayed()
    }

    @Test fun realAndroidMessagingStyleIsParsedForAllFourPackages() {
        val me = Person.Builder().setName("Yo").build()
        val sender = Person.Builder().setName("Ana").build()
        val now = System.currentTimeMillis()
        ChatApp.entries.forEach { app ->
            val style = Notification.MessagingStyle(me).setConversationTitle("Familia")
                .setGroupConversation(true).addMessage("Mensaje de prueba", now, sender)
                .addMessage("Mi respuesta", now + 1, null as Person?)
            val notification = Notification.Builder(context, "test_chat").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(style).setCategory(Notification.CATEGORY_MESSAGE).build()
            val sbn = StatusBarNotification(app.packages.first(), app.packages.first(), 1, null,
                Process.myUid(), 0, 0, notification, Process.myUserHandle(), now)
            val snapshot = AndroidNotificationReader.snapshot(sbn, null)!!
            assertTrue(snapshot.hasMessagingStyle)
            assertEquals(true, snapshot.isGroup)
            val messages = (NotificationAdapter.forApp(app).extract(snapshot) as Extraction.Messages).values
            assertEquals(1, messages.size)
            assertEquals("Ana", messages.single().sender)
            assertEquals("Familia", messages.single().conversationLabel)
        }
    }

    @Test fun disabledModeObservesChatButDoesNotSpeakOrLogContent() {
        val now = System.currentTimeMillis()
        compose.runOnIdle {
            context.controller.clearDiagnostics()
            context.controller.receive(NotificationSnapshot(ChatApp.WHATSAPP, "synthetic-${System.nanoTime()}", now, now,
                category = "msg", title = "Persona de prueba", text = "TEXTO_PRIVADO_NO_DEBE_APARECER_EN_DIAGNOSTICO", isGroup = false))
            assertEquals(Reason.PAUSED, context.controller.diagnostics.value.first().reason)
            assertFalse(context.controller.runtime.value.speaking)
            assertTrue(context.controller.observed.value.any { it.label == "Persona de prueba" })
            assertFalse(context.controller.diagnostics.value.toString().contains("TEXTO_PRIVADO"))
            assertFalse(context.controller.diagnostics.value.toString().contains("Persona de prueba"))
        }
    }

    @Test fun voiceTestWithoutHeadphonesCannotPlay() {
        compose.runOnIdle {
            // The emulator has only its built-in outputs.
            assertTrue(context.controller.safety.headphoneDevices().isEmpty())
            context.controller.testVoice()
            assertEquals(Reason.NO_HEADPHONES, context.controller.diagnostics.value.first().reason)
            assertFalse(context.controller.runtime.value.speaking)
            assertEquals(0, context.controller.runtime.value.pending)
        }
    }

    @Test fun settingsPersistAndExclusionsCanBeRemoved() {
        val store = context.controller.preferences
        val original = store.settings.value
        try {
            runBlocking {
                store.setRule(ChatApp.TIKTOK, AppRule(enabled = false, mode = ReadingMode.FULL, skipGroups = true))
                store.excludeConversation("test-chat-id", "Chat de prueba", true)
                store.excludeSender("test-sender-id", "Persona de prueba", true)
            }
            compose.waitUntil(5_000) {
                !store.settings.value.rule(ChatApp.TIKTOK).enabled && "test-chat-id" in store.settings.value.excludedConversations
            }
            assertEquals(ReadingMode.FULL, store.settings.value.rule(ChatApp.TIKTOK).mode)
            assertTrue(store.settings.value.rule(ChatApp.TIKTOK).skipGroups)
        } finally {
            runBlocking {
                store.setRule(ChatApp.TIKTOK, original.rule(ChatApp.TIKTOK))
                store.excludeConversation("test-chat-id", "", false)
                store.excludeSender("test-sender-id", "", false)
            }
        }
        compose.waitUntil(5_000) { "test-chat-id" !in store.settings.value.excludedConversations }
    }

    @Test fun manifestDoesNotRequestSensitiveOrNetworkPermissions() {
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        listOf("android.permission.INTERNET", "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE", "android.permission.MANAGE_EXTERNAL_STORAGE").forEach {
            assertFalse("Unexpected permission: $it", it in permissions)
        }
    }
}
