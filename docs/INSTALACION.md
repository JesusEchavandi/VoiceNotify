# Instalar VoiceNotify

## Descarga y configuración

Descarga el APK firmado desde [la última release](https://github.com/JesusEchavandi/VoiceNotify/releases/latest), cópialo al teléfono y ábrelo. Permite la instalación desde esa fuente cuando Android lo solicite. El nombre visible será **VoiceNotify**.

Si Android o Samsung bloquea una instalación personal, revisa el aviso del sistema. Para conceder acceso a notificaciones puede ser necesario habilitar **Permitir ajustes restringidos** desde la información de la aplicación; el texto depende de Android y One UI.

1. En VoiceNotify abre **Inicio → Abrir permiso** y concede acceso a notificaciones.
2. Conecta y autoriza tus audífonos. Autoriza solo dispositivos que reconoces como audífonos.
3. Instala una voz local en español desde los ajustes del motor TTS, vuelve y pulsa **Probar voz en mis audífonos**.
4. Activa el interruptor de lectura. En Aplicaciones elige qué se anuncia y qué grupos se omiten.
5. Las conversaciones aparecen al llegar mensajes nuevos, incluso con lectura pausada. Allí puedes excluir conversaciones o personas.

Funciona en **Sonido y Vibración**, con audífonos autorizados. Silencio, No molestar y volumen multimedia en cero bloquean la voz. La prueba manual funciona con la lectura automática pausada.

## Instalación con ADB

Activa la depuración USB y autoriza tu computadora desde el teléfono. Con el APK descargado en `dist/`:

```powershell
adb devices -l
adb install -r ./dist/VoiceNotify-0.1.4-personal-preview.apk
```

El identificador Android `com.notifyvoice.app` se conserva para actualizar el prototipo anterior con la misma firma. El cambio de nombre no crea otra aplicación ni borra preferencias.

## Actualizar y detener

Instala versiones nuevas encima de la anterior con la misma firma. Si compilas tu propia versión, respalda de forma privada el almacén de firma y `signing.properties`; no los publiques. Una clave distinta no actualiza una instalación firmada con la original.

**Inicio → Detener lectura actual** cancela la voz y la cola. Desactiva el interruptor de lectura para pausar avisos futuros. Revoca el acceso desde el botón de permiso de notificaciones si deseas detener la recepción.

## Si no habla

Revisa Diagnóstico: acceso, servicio, audífonos autorizados, voz local, volumen multimedia, modo Silencio y No molestar. Los avisos omitidos no se acumulan para escucharlos después.

Si Samsung suspende el proceso, revisa **Información de la aplicación → Batería → Sin restricciones**. Forzar detención puede impedir la recepción hasta abrir la aplicación.

Tras reiniciar, Android debe volver a enlazar el servicio una vez desbloqueado el teléfono y restauradas las condiciones de uso. La recuperación todavía requiere una prueba de reinicio real; no se presenta como verificada.
