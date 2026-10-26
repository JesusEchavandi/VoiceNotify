package com.notifyvoice.app

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.net.toUri
import com.notifyvoice.app.audio.*
import com.notifyvoice.app.domain.*
import com.notifyvoice.app.notifications.VoiceNotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF176B5C)
private val Ink = Color(0xFF163B33)
private val Muted = Color(0xFF546B64)
private val Cream = Color(0xFFF5F7F3)
private val Mint = Color(0xFFE6F1E9)
private val Amber = Color(0xFF87521B)

class MainActivity : ComponentActivity() {
    private val controller get() = (application as VoiceNotifyApplication).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White,
                background = Cream, surface = Color.White, onSurface = Ink,
                secondaryContainer = Mint, onSecondaryContainer = Ink)) {
                VoiceNotify(controller, ::openNotificationAccess, ::openVoiceSettings, ::openAppSettings)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                controller.speech.refreshVoices()
                while (true) { controller.refresh(); delay(1_000) }
            }
        }
    }

    private fun openNotificationAccess() {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val detail = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, VoiceNotificationListener::class.java).flattenToString())
        runCatching { startActivity(detail) }.onFailure {
            runCatching { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
    }
    private fun openVoiceSettings() {
        runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }.onFailure {
            runCatching { startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }.onFailure {
                startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
            }
        }
    }
    private fun openAppSettings() {
        startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}

private enum class Page(val title: String, val symbol: String) {
    HOME("Inicio", "⌂"), APPS("Aplicaciones", "▦"), VOICE("Voz", "≋"), DIAGNOSTICS("Diagnóstico", "◎")
}

@Composable
private fun VoiceNotify(controller: AnnouncementController, access: () -> Unit, voices: () -> Unit, battery: () -> Unit) {
    val settings by controller.preferences.settings.collectAsStateWithLifecycle()
    val loaded by controller.preferences.loaded.collectAsStateWithLifecycle()
    val runtime by controller.runtime.collectAsStateWithLifecycle()
    val speech by controller.speech.status.collectAsStateWithLifecycle()
    val diagnostics by controller.diagnostics.collectAsStateWithLifecycle()
    val conversations by controller.observed.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(containerColor = Cream, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            Page.entries.forEach { item ->
                NavigationBarItem(selected = page == item, onClick = { page = item },
                    icon = { Text(item.symbol, fontSize = 25.sp, fontWeight = FontWeight.Medium) },
                    label = { Text(item.title, fontSize = 11.sp) })
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(scroll).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Header(page)
            when (page) {
                Page.HOME -> {
                    Hero(settings, runtime, loaded) { enabled ->
                        if (!enabled) controller.stop()
                        scope.launch { controller.preferences.setEnabled(enabled) }
                    }
                    SectionTitle("Listo en tres pasos")
                    SetupStep("01", "Permite el acceso a notificaciones",
                        if (runtime.notificationAccess) "Acceso concedido. Puedes revocarlo en Ajustes."
                        else "Android pedirá acceso a tus notificaciones. Solo procesamos las aplicaciones que elijas.",
                        runtime.notificationAccess, "Abrir permiso", access)
                    SetupStep("02", "Conecta y autoriza tus audífonos",
                        "Bluetooth o USB-C. Autoriza únicamente audífonos que reconoces; un dispositivo Bluetooth también puede ser un parlante.",
                        runtime.headphones.any { it.id in settings.approvedDevices }, null, {})
                    runtime.headphones.forEach { device ->
                        Panel {
                            ToggleRow(device.label, "Permitir voz en este dispositivo",
                                device.id in settings.approvedDevices) { approved ->
                                controller.stop()
                                scope.launch { controller.preferences.approveDevice(device.id, approved) }
                            }
                        }
                    }
                    SetupStep("03", "Comprueba la voz en español",
                        if (speech.voices.isEmpty()) "Instala una voz en español sin conexión en el motor de voz del celular."
                        else "Voz local disponible. La lectura funciona sin enviar tus mensajes a un servidor.",
                        speech.voices.isNotEmpty(), "Ajustes de voz", voices)
                    Button(onClick = controller::testVoice, modifier = Modifier.fillMaxWidth(), enabled = loaded) {
                        Text("Probar voz en mis audífonos")
                    }
                    OutlinedButton(onClick = { controller.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Detener lectura actual") }
                    diagnostics.firstOrNull()?.let { event ->
                        Note("Último estado: ${event.reason.label}")
                    }
                    Note("Solo audífonos · Sin cuentas · Sin historial de mensajes")
                }
                Page.APPS -> {
                    Note("Elige qué se anuncia. Por defecto se dice la aplicación y el remitente; el texto del mensaje requiere que lo actives.")
                    ChatApp.entries.forEach { app ->
                        val rule = settings.rule(app)
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppBadge(app)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Switch(checked = rule.enabled, onCheckedChange = { value ->
                                    scope.launch { controller.preferences.setRule(app, rule.copy(enabled = value)) }
                                })
                            }
                            if (rule.enabled) {
                                HorizontalDivider(color = Mint)
                                ModePicker(rule.mode) { mode -> scope.launch { controller.preferences.setRule(app, rule.copy(mode = mode)) } }
                                ToggleRow("Omitir grupos", "Si no podemos identificar el tipo de chat, también se omite.", rule.skipGroups) {
                                    scope.launch { controller.preferences.setRule(app, rule.copy(skipGroups = it)) }
                                }
                            }
                        }
                    }
                    SectionTitle("Conversaciones detectadas")
                    Text("Aparecen al llegar nuevos chats, incluso con la lectura pausada. Esta lista se borra al cerrar el proceso; solo se guardan las exclusiones que elijas.", color = Muted, fontSize = 13.sp)
                    if (conversations.isEmpty()) Note("Aún no hay conversaciones. Concede el acceso y recibe un mensaje en una de las cuatro aplicaciones.")
                    conversations.forEach { chat ->
                        Panel {
                            Text("${chat.app.label} · ${chat.label}", fontWeight = FontWeight.SemiBold)
                            val excluded = chat.id in settings.excludedConversations
                            TextButton(onClick = { scope.launch {
                                controller.preferences.excludeConversation(chat.id, "${chat.app.label} · ${chat.label}", !excluded)
                            } }) { Text(if (excluded) "Permitir esta conversación" else "Excluir esta conversación") }
                            if (chat.senderId != null && chat.senderLabel != null) {
                                val personExcluded = chat.senderId in settings.excludedSenders
                                TextButton(onClick = { scope.launch {
                                    controller.preferences.excludeSender(chat.senderId, "${chat.app.label} · ${chat.senderLabel}", !personExcluded)
                                } }) { Text(if (personExcluded) "Permitir a ${chat.senderLabel}" else "Excluir a ${chat.senderLabel}") }
                            }
                        }
                    }
                    if (settings.excludedConversations.isNotEmpty() || settings.excludedSenders.isNotEmpty()) {
                        SectionTitle("Exclusiones guardadas")
                        settings.excludedConversations.forEach { (id, label) ->
                            ExcludedRow(label, "Conversación") { scope.launch { controller.preferences.excludeConversation(id, label, false) } }
                        }
                        settings.excludedSenders.forEach { (id, label) ->
                            ExcludedRow(label, "Persona") { scope.launch { controller.preferences.excludeSender(id, label, false) } }
                        }
                    }
                }
                Page.VOICE -> {
                    Panel {
                        Text("Una voz que se queda contigo", style = MaterialTheme.typography.titleMedium)
                        Text("Solo se usan voces locales en español. El motor debe tener descargados sus datos.", color = Muted)
                        if (speech.voices.isEmpty()) Text("No hay una voz local disponible.", color = Amber)
                        else VoicePicker(settings.voiceName, speech.voices) { id -> scope.launch { controller.preferences.setVoice(id) } }
                        OutlinedButton(onClick = voices) { Text("Configurar motor de voz") }
                    }
                    Panel {
                        var speed by remember(settings.speed) { mutableFloatStateOf(settings.speed) }
                        Text("Velocidad · ${String.format(Locale.forLanguageTag("es"), "%.1f", speed)}×", fontWeight = FontWeight.SemiBold)
                        Slider(value = speed, onValueChange = { speed = it }, valueRange = .6f..1.6f,
                            onValueChangeFinished = { scope.launch { controller.preferences.setSpeed(speed) } })
                        var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
                        Text("Volumen de la voz · ${(volume * 100).toInt()} %", fontWeight = FontWeight.SemiBold)
                        Slider(value = volume, onValueChange = { volume = it },
                            onValueChangeFinished = { scope.launch { controller.preferences.setVolume(volume) } })
                        Text("Este nivel se aplica sobre el volumen multimedia del celular. No modifica el volumen de otras aplicaciones.", color = Muted, fontSize = 13.sp)
                    }
                    Button(onClick = controller::testVoice, modifier = Modifier.fillMaxWidth()) { Text("Escuchar una prueba") }
                    Note("La voz funciona en modo Sonido y Vibración, solo con audífonos autorizados. Se mezcla con música y videos sin bajar su volumen. Se omite durante comunicaciones detectables, modo Silencio o No molestar.")
                }
                Page.DIAGNOSTICS -> {
                    Panel {
                        StatusLine("Acceso a notificaciones", runtime.notificationAccess)
                        StatusLine("Servicio conectado", runtime.listenerConnected)
                        StatusLine("Audífonos autorizados", runtime.headphones.any { it.id in settings.approvedDevices })
                        StatusLine("Voz local en español", speech.voices.isNotEmpty())
                        Text("${runtime.pending} avisos en espera · ${if (runtime.speaking) "Reproduciendo" else "Sin reproducción"}", color = Muted)
                    }
                    Note("Si Samsung suspende la aplicación, abre sus ajustes y revisa Batería → Sin restricciones. Forzar detención impide recibir avisos hasta volver a abrirla.")
                    OutlinedButton(onClick = battery, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes de la aplicación") }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Actividad reciente", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = controller::clearDiagnostics) { Text("Borrar") }
                    }
                    Text("Hasta 80 eventos en memoria. No se guardan remitentes ni textos aquí. La latencia mide el tiempo hasta el inicio de la voz.", fontSize = 13.sp, color = Muted)
                    if (diagnostics.isEmpty()) Note("Sin actividad reciente.")
                    diagnostics.forEach { event ->
                        Panel {
                            Text(event.reason.label, fontWeight = FontWeight.Medium)
                            Text(buildString {
                                append(SimpleDateFormat("HH:mm:ss", locale).format(Date(event.at)))
                                event.app?.let { append(" · ${it.label}") }
                                event.latencyMs?.let { append(" · inicio en $it ms") }
                            }, fontSize = 12.sp, color = Muted)
                        }
                    }
                    Note("VoiceNotify ${BuildConfig.VERSION_NAME} · Android 10 o superior. La lectura de cada aplicación y la desconexión de audífonos requieren pruebas en tu dispositivo.")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(page: Page) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("VOICENOTIFY", color = Green, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (page == Page.HOME) "Tus mensajes,\na tu oído." else page.title,
                fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Box(Modifier.size(54.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) { SoundBars() }
    }
}

@Composable
private fun SoundBars() {
    Canvas(Modifier.size(30.dp)) {
        listOf(.3f, .7f, 1f, .6f, .25f).forEachIndexed { i, height ->
            val x = size.width * (.1f + i * .2f)
            drawLine(Green, androidx.compose.ui.geometry.Offset(x, size.height * (1 - height) / 2),
                androidx.compose.ui.geometry.Offset(x, size.height * (1 + height) / 2), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun Hero(settings: Settings, runtime: RuntimeStatus, loaded: Boolean, onEnabled: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (settings.enabled) "Lectura activada" else "Lectura en pausa", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("SOLO EN TUS AUDÍFONOS", color = Color(0xFFBDDFCB), fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Switch(checked = settings.enabled, enabled = loaded, onCheckedChange = onEnabled)
            }
            val status = when {
                !loaded -> "Cargando tus preferencias…"
                runtime.speaking -> "Leyendo un aviso"
                runtime.blocked != null -> runtime.blocked.label
                !runtime.listenerConnected -> "Esperando conexión del servicio"
                !settings.enabled -> "Activa la lectura cuando quieras escuchar tus mensajes."
                else -> "Preparado para nuevos mensajes."
            }
            Text(status, color = Color(0xFFDAEBDF), fontSize = 14.sp)
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable
private fun Note(text: String) {
    Text(text, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mint).padding(15.dp),
        color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun SetupStep(number: String, title: String, description: String, ready: Boolean, action: String?, onClick: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ready) "✓" else number, Modifier.clip(CircleShape).background(Mint).padding(10.dp), color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        if (action != null) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AppBadge(app: ChatApp) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Mint), contentAlignment = Alignment.Center) {
        Text(app.initials, color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ModePicker(selected: ReadingMode, onSelect: (ReadingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("${selected.label} ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReadingMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, onClick = { expanded = false; onSelect(mode) })
            }
        }
    }
}

@Composable
private fun VoicePicker(selected: String, voices: List<LocalVoice>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text((voices.firstOrNull { it.id == selected }?.label ?: "Selección automática") + " ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Selección automática") }, onClick = { expanded = false; onSelect("") })
            voices.forEach { voice ->
                DropdownMenuItem(text = { Text(voice.label) }, onClick = { expanded = false; onSelect(voice.id) })
            }
        }
    }
}

@Composable
private fun ExcludedRow(label: String, kind: String, remove: () -> Unit) {
    Panel {
        Text(label, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
            TextButton(onClick = remove) { Text("Quitar exclusión") }
        }
    }
}

@Composable
private fun StatusLine(title: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, Modifier.weight(1f))
        Text(if (ready) "Listo" else "Pendiente", color = if (ready) Green else Amber, fontSize = 13.sp)
    }
}
