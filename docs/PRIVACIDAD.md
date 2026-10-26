# Privacidad de VoiceNotify

## Qué procesa

El acceso especial a notificaciones permite recibir avisos del sistema. VoiceNotify filtra las aplicaciones admitidas, identifica chats y aplica las reglas del usuario. No consulta el historial de conversaciones, contactos ni adjuntos.

Una notificación puede incluir información personal. Se mantiene transitoriamente en memoria para filtros y lectura. El diagnóstico conserva hasta 80 eventos técnicos en RAM, sin nombres de remitentes ni texto de mensajes.

## Qué guarda en el teléfono

- Preferencias: lectura, velocidad, volumen, voz y reglas por aplicación.
- Hashes de descriptores de audífonos autorizados.
- Nombres de personas o conversaciones que el usuario excluya expresamente; pueden ser datos personales.
- WAV temporales en caché privada para la voz. Se borran al finalizar/cancelar y al siguiente inicio; un cierre inesperado puede dejarlos hasta la siguiente limpieza.

La lista de conversaciones observadas permanece en memoria y se limita a 60 entradas. No hay historial persistente de mensajes. Las preferencias y WAV del teléfono no se exportan al repositorio ni se incorporan al APK durante la compilación.

## Red, permisos y copias

Sin permiso de Internet, micrófono, contactos o estado del teléfono. Sin cuentas, servidor, analítica o publicidad. El motor TTS es un componente del sistema separado; se eligen voces que declaran síntesis local. Sus datos se pueden descargar desde los ajustes del motor.

Las copias de seguridad y transferencia de los datos de la aplicación están deshabilitadas mediante manifiesto y reglas de extracción. Android o las aplicaciones emisoras pueden ocultar o redactar contenido sensible.

## Desarrollo y firma

La clave privada y su contraseña pertenecen al entorno de quien compila y no se publican. El certificado público del APK sirve para verificarlo y no permite recuperar la clave privada.

Se excluyen de Git `signing/`, `signing.properties`, claves, `local.properties`, registros, cachés, emuladores y compilaciones locales. El paquete de fuentes usa una lista explícita de archivos y excluye esos datos. `signing.properties.example` contiene marcadores, no credenciales reales.

Antes de la publicación inicial se revisaron fuentes, entregables, registros y metadatos de imágenes. No se detectaron credenciales de cuentas ni conversaciones reales en el material publicable. Una búsqueda no garantiza detectar cualquier secreto con formato desconocido. Los reportes privados de auditoría no se publican.

## Control del usuario

Puedes pausar la lectura, detener reproducción y cola o borrar el diagnóstico. Revocar el acceso detiene la recepción. Borrar datos o desinstalar elimina las preferencias locales conforme al comportamiento de Android.
