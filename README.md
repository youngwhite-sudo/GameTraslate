# GameTranslate (Android)

Traductor de pantalla en tiempo real para videojuegos (Inglés, Japonés y Chino -> Español) diseñado para Android.

## Características principales
- **Modo Zona Redimensionable o Pantalla Completa**: Elige un marco arrastrable sobre la caja de diálogo o traduce todos los textos visibles en pantalla.
- **Camuflaje de Colores**: Muestrea el color de fondo y el color de letra del juego en tiempo real para integrar la traducción sin parches bruscos.
- **Cero basura en almacenamiento**: Las capturas se procesan 100% en memoria RAM (`ImageReader`) y se descartan al instante.
- **Anti Texto Fantasma**: Compara cambios de píxeles entre fotogramas para limpiar traducciones antiguas al instante y usa *debounce* (250ms) para diálogos tipo máquina de escribir.
- **Capa transparente al tacto**: `FLAG_NOT_TOUCHABLE` en la traducción para que nunca bloquee los controles del juego.

## Cómo compilar en Android Studio
1. Abre esta carpeta raíz en **Android Studio** (`File -> Open`).
2. Espera a que termine **Gradle Sync** (descargará ML Kit y dependencias automáticamente).
3. Ve a `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`.
