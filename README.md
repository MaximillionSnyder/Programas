# Morph Demo — Card → Gráfica en Jetpack Compose

Demo que compara **dos técnicas** para transformar una card de información en una
gráfica **sin que la card cambie de tamaño**: el borde queda fijo y solo se animan
las piezas de dentro.

| Card | Técnica | Archivo |
| --- | --- | --- |
| 1 | Cuadrados interpolados a mano (`animateFloatAsState` + `lerp`) | `morph/RectMorphCard.kt` |
| 2 | `SharedTransitionLayout` + `Modifier.sharedElement` | `morph/SharedMorphCard.kt` |
| 3 | **Dato → gráfica**: primero el dato, al tocar los cuadrados se vuelven la gráfica | `morph/SeriesMorphCard.kt` |

En la Card 1 los 4 segmentos de la barra de progreso **se separan y crecen** hasta ser
4 barras, el track se adelgaza hasta ser la línea base y el panel del hero se estrecha
hasta ser la canaleta del eje Y. Son las mismas piezas todo el tiempo: por eso se siente
una transformación y no un crossfade.

En la Card 2 no se calcula geometría: se escriben dos layouts reales y se marca con la
misma key qué piezas viajan entre ambos estados.

Toca cada card para alternarla.

---

## Dato → gráfica (el patrón que se quiere llevar a FitLog)

`SeriesMorphCard` invierte el orden: **primero se lee el dato, y la gráfica aparece solo
al tocar**. Es el caso de las tarjetas de entrenamiento, donde hoy la gráfica ocupa la
tarjeta y los números viven en una sección aparte, debajo.

**Estado dato:** el valor grande (`Desnivel 9 m`), su detalle (`Max 96 m - Min 82 m`) y
una **miniatura del propio perfil** abajo a la izquierda.

**Estado gráfica:** esa miniatura **se despliega** —las barras se reparten a lo ancho y
crecen a su altura real— y quedan las etiquetas de máximo y mínimo.

Son las mismas barras en los dos estados: lo único que cambia es dónde están y cuánto
miden. La miniatura no es un adorno, es la gráfica en pequeño, así que el ojo ya sabe qué
va a aparecer.

```kotlin
SeriesMorphCard(
    title = "Altura",
    trailing = "300 puntos",
    headlineLabel = "Desnivel",
    headlineValue = "9",
    headlineUnit = "m",
    details = "Max 96 m - Min 82 m",
    maxLabel = "Max 96 m",
    minLabel = "Min 82 m",
    series = SampleData.elevationProfile,   // 28 valores normalizados 0f..1f
    barColor = MaterialTheme.colorScheme.primary,
)
```

**Por qué la serie se muestrea a ~28 barras.** Un GPX trae cientos de puntos; animar
cientos de rectángulos es tirar frames. Se agrupa la serie en ~28 cubos y cada uno toma
la altura de su tramo. El perfil se lee igual y la animación va sobrada.

**Alto fijo de 268 dp.** Igual que en las otras dos cards: el borde no se mueve, así que
la lista no da saltos al alternar. Verificado sobre 99 fotogramas: 765–767 px de alto,
con 2 px de variación por antialiasing.

---

## ⚠️ Modo auto-demo — usar SIEMPRE para verificar en dispositivo

> **Esto es lo que hay que recordar.** Para grabar o verificar la animación desde una PC
> o desde Termux, **hay que lanzar la app con el extra `auto`**. Sin él no se puede
> automatizar nada.

```bash
adb shell am force-stop com.example.morphdemo
adb shell am start -n com.example.morphdemo/.MainActivity --ez auto true
```

Con `auto = true` las dos cards **se alternan solas cada 2000 ms**, así que la animación
corre sin que nadie toque la pantalla. Las dos van sincronizadas, de modo que cada
fotograma muestra ambas técnicas en la misma fase y se pueden comparar lado a lado.

Sin el extra, la app funciona normal: se alternan solo al tocarlas.

### ¿Por qué hace falta?

Porque **HyperOS (Xiaomi) bloquea inyectar eventos de entrada desde adb**:

```
adb shell input tap 450 730
java.lang.SecurityException: Injecting input events requires the caller
to have the INJECT_EVENTS permission.
```

Lo mismo pasa con `input keyevent` y con `monkey`. Sin poder tocar la pantalla por adb,
la única forma de automatizar una grabación es que la propia app se accione sola. De ahí
el extra.

En un dispositivo sin esa restricción bastaría con `adb shell input tap`, y el modo
auto-demo sería innecesario.

### Cómo se implementa

`MainActivity` lee el extra y lo pasa a las dos cards; cada card lo acepta como
parámetro opcional y, si viene, arranca un bucle:

```kotlin
val auto = intent.getBooleanExtra("auto", false)
// ...
RectMorphCard(..., autoToggleMs = if (auto) 2000L else null)

// dentro de cada card:
if (autoToggleMs != null) {
    LaunchedEffect(autoToggleMs) {
        while (true) {
            delay(autoToggleMs)
            showChart = !showChart
        }
    }
}
```

Es un parámetro con valor por defecto `null`, así que **no cambia el comportamiento
normal** de la app.

---

## Verificación en dispositivo

Verificado en un **POCO X6 Pro 5G** (Android 17, 1220x2712, densidad 480) con la app
instalada por adb. Los artefactos están en [`docs/verificacion/`](docs/verificacion/).

### El requisito central: la card no cambia de tamaño

Medido sobre **207 fotogramas** de la grabación:

| | Alto de la card | Variación |
| --- | --- | --- |
| Card 1 | 590 – 592 px | 2 px |
| Card 2 | 590 – 592 px | 2 px |

Los 2 px son antialiasing en las esquinas redondeadas. **El contenedor no se mueve.**

### Duración real del morph

Medido muestreando el píxel de la barra más alta fotograma a fotograma:

| | info → gráfica | gráfica → info |
| --- | --- | --- |
| **Card 1** (cuadrados) | 467 / 525 / 467 ms | 409 / 409 / 409 ms |
| **Card 2** (SharedTransition) | 292 / 467 / 467 ms | **175 / 233 / 233 ms** |

**Hallazgo:** la Card 2 va casi el doble de rápido al volver, y es asimétrica (~467 ms
para ir a la gráfica, ~200 ms para regresar). La Card 1 es simétrica y pausada. Es el
*spring* por defecto de `sharedElement`, que nadie configuró. Para igualarlas hay que
darle a la Card 2 un `boundsTransform` explícito con un `tween`.

> **Límite de la medición:** el video es de ~17 fps, así que hay ±58 ms de incertidumbre.
> Sirve para comparar las dos cards entre sí, no para certificar milisegundos exactos.

### Artefactos

| Archivo | Qué es |
| --- | --- |
| `hi1_morph.png` | Card 1 a 30 fps: el morph completo, del estado info a la gráfica |
| `hi2_morph.png` | Card 2 a 30 fps: el mismo morph, visiblemente más rápido |
| `montage_coarse.png` | Rejilla de 12 s a 1 fps: alternancia completa de ambas cards |
| `demo2.mp4` | Grabación original de la app corriendo (12 s) |
| `serie_inicial.png` | Las dos cards nuevas en estado gráfica |
| `serie_morph.png` | La transición dato → gráfica a 20 fps |
| `serie_pulso.png` | La card de pulso alternando (1 fps) |
| `demo3.mp4` | Grabación de las cards nuevas (12 s) |

Para reproducir la grabación:

```bash
adb shell screenrecord --time-limit 12 /sdcard/demo.mp4
adb pull /sdcard/demo.mp4
ffmpeg -i demo.mp4 -vf "crop=1220:670:0:630,fps=30,scale=420:-1,tile=7x3" -frames:v 1 card1.png
```

---

## Detalle técnico

La comparación completa (tabla de decisión, fragmentos clave, cómo llevarlo a una app
real y notas del entorno Termux) está en **[COMPARACION.md](COMPARACION.md)**.

## Stack

Gradle 9.5.0 · AGP 9.3.0 · Kotlin 2.4.10 · Compose UI 1.11.4 · Material3 1.4.0
compileSdk 37 · minSdk 26

## Compilar

Necesitas un JDK 17 y el SDK de Android. Crea `local.properties` con la ruta de tu SDK:

```properties
sdk.dir=/ruta/a/tu/android-sdk
```

Luego:

```bash
./gradlew assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## APK lista para instalar

Descárgala desde la sección **[Releases](../../releases)** de este repo.

> Es una build **debug**, firmada con la clave de depuración estándar de Android.
> Android pedirá permitir "instalar apps desconocidas".
