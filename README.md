# WhatStatus
### By Vørem

WhatStatus V1 es una aplicación Android local para detectar, visualizar y guardar fotos y videos de estados de WhatsApp que ya estén disponibles en el dispositivo.

## Características
- Detección mediante MediaStore cuando Android expone los estados.
- Flujo oficial Storage Access Framework como alternativa cuando se requiere autorización de una carpeta.
- Galería de fotos y videos.
- Visor de imágenes y reproductor de video integrados.
- Guardado en `Pictures/WhatStatus` y `Movies/WhatStatus` mediante MediaStore.
- Sección Guardados con eliminación por gesto hacia la izquierda y botón de borrado.
- Sin servidores, cuentas, publicidad, analytics ni conexión a Internet requerida.

## Permisos
Android 13+ usa `READ_MEDIA_IMAGES` y `READ_MEDIA_VIDEO` para leer medios de otras aplicaciones. Android 12 e inferiores usan el permiso compatible con su versión. Si Android no expone una ubicación de estados a MediaStore, WhatStatus puede solicitar acceso mediante el selector oficial de carpetas y conserva el permiso persistente.

## Limitación importante de Android
Android moderno aplica almacenamiento con alcance y no garantiza que una aplicación pueda leer directamente todas las carpetas privadas o no indexadas de otra aplicación. WhatStatus no utiliza root, exploits, APIs privadas ni trucos para saltarse esas restricciones. Si el sistema no expone `.Statuses`, la alternativa oficial disponible en el dispositivo es el selector de carpetas de Android.

## WhatsApp Business
La arquitectura del escáner permite añadir una fuente separada para `com.whatsapp.w4b` sin acoplar el resto de la aplicación. V1 prioriza estabilidad y el WhatsApp normal.

## Compilación local
Requiere Android Studio reciente, JDK 17, Android SDK 35 y conexión para descargar dependencias de Gradle.

```bash
chmod +x gradlew
./gradlew test
./gradlew assembleDebug
```

## GitHub Actions
El workflow de `.github/workflows/android.yml` ejecuta tests, lint y `assembleDebug`, y publica el APK como artifact.

## Privacidad
Los archivos se procesan localmente. No se suben imágenes ni videos a servidores externos.

## Identidad
**WhatStatus**  
**By Vørem**

WhatStatus no está afiliado a WhatsApp ni a Meta.

## CI / GitHub Actions

El workflow genera un Gradle Wrapper oficial de Gradle 8.9 dentro del runner antes de ejecutar la compilación. Esto evita depender de un `gradle-wrapper.jar` binario generado o alterado fuera de Gradle y permite validar el proyecto en un runner limpio.
