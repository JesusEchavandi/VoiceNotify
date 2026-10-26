# Validación de VoiceNotify

Fecha: **26 de octubre de 2026**. Versión de publicación: **0.1.4**.

## Verificación automatizada

- **38 pruebas unitarias**: 31 de adaptadores, frases, filtros, deduplicación, cola y rutas; 7 de política de sonido, vibración, llamadas, No molestar y volumen multimedia.
- **6 pruebas instrumentadas aprobadas**, repetidas en 0.1.4 en emulador Android 16 / API 36: interfaz y nombre VoiceNotify, MessagingStyle, ajustes, omisión sin audífonos y permisos.
- Compilación Debug/Release correcta y análisis estático sin errores. El paquete de fuentes se extrajo en una carpeta limpia y compiló un APK de desarrollo sin claves ni configuración privada local.
- El APK Release 0.1.4 se instaló y abrió en el emulador; nombre e ícono comprobados visualmente. Las capturas de `docs/images/` corresponden a esta versión y no contienen conversaciones.
- El APK Release se firma con una clave privada local, excluida del repositorio y de las releases.
- No se solicita Internet, micrófono, contactos o estado del teléfono. El acceso especial a notificaciones se concede por separado.

Las pruebas sintéticas construyen notificaciones mediante APIs reales de Android; no envían mensajes ni demuestran compatibilidad general con aplicaciones de terceros.

## Pruebas guiadas en un teléfono físico

Las siguientes observaciones fueron confirmadas por el usuario en un teléfono Samsung con Android 17 / API 37 y audífonos Bluetooth. No se copiaron conversaciones al repositorio ni se ejecutó la suite instrumentada en el teléfono personal.

| Caso | Resultado y alcance |
| --- | --- |
| Instalación y actualizaciones | APK firmado instalado y actualizado conservando ajustes |
| Acceso y servicio | Acceso concedido y servicio conectado, verificados en el sistema y Diagnóstico |
| Voz local en español | Disponible; frase de prueba escuchada por los audífonos |
| WhatsApp | Un mensaje entrante anunciado después de activar la lectura |
| TikTok, Messenger e Instagram | El usuario confirmó anuncios de chats de las tres aplicaciones con pantalla bloqueada |
| Vibración | Frase de prueba escuchada en los audífonos con 0.1.2 |
| Una desconexión durante la frase | El usuario confirmó que se detuvo la voz y el altavoz no habló |
| Latencia de la frase de prueba | Un inicio observado de 156 ms en 0.1.1; no es una media ni una medición de mensajes entrantes |

Los resultados de mensajes reales corresponden a 0.1.1, el modo Vibración a 0.1.2 y el cambio de ícono a 0.1.3. La publicación 0.1.4 cambia la marca visible a **VoiceNotify** y conserva el identificador Android, el servicio, las preferencias y la firma para actualizar.

## Pendiente de validar

- Grupos, ráfagas, mensajes idénticos, contenido oculto y formatos regionales de cada aplicación.
- Recomendaciones, LIVE, likes, promociones y otros tipos de avisos en distintas versiones de las aplicaciones.
- Audífonos USB-C, salida dual, sonido por aplicación y cambios de ruta desde el panel multimedia.
- Repeticiones de desconexión antes de sintetizar, al iniciar y durante la voz.
- Música/video simultáneos, llamadas normales y VoIP y variantes de No molestar.
- Reinicio real, ahorro de batería, proceso recreado, permiso revocado y uso prolongado.
- Modo avión con datos de voz descargados y latencia fría/caliente por aplicación.

Un resultado positivo en un equipo no valida todos los teléfonos o audífonos. La preferencia de ruta no es una garantía del hardware: los cambios y callbacks son asíncronos.

## Procedimiento recomendado

1. Utiliza mensajes de prueba y anota versiones de Android, aplicación, motor de voz y modelo de audífonos.
2. Prueba con la aplicación abierta, en segundo plano y pantalla bloqueada.
3. Repite desconexiones al menos diez veces en distintos momentos de la frase y escucha también el altavoz. Si hay una fuga, detén la lectura y registra el caso sin contenido privado.
4. Usa Diagnóstico para distinguir filtros, duplicados, permisos, modo de sonido y rutas no seguras.
5. Mide al menos diez mensajes por aplicación. La latencia comienza al recibir la notificación, no al enviarse el mensaje a los servidores de la aplicación.
