# Morph Demo — Card → Gráfica en Jetpack Compose

Demo que compara **dos técnicas** para transformar una card de información en una
gráfica **sin que la card cambie de tamaño**: el borde queda fijo y solo se animan
las piezas de dentro.

| Card | Técnica | Archivo |
| --- | --- | --- |
| 1 | Cuadrados interpolados a mano (`animateFloatAsState` + `lerp`) | `morph/RectMorphCard.kt` |
| 2 | `SharedTransitionLayout` + `Modifier.sharedElement` | `morph/SharedMorphCard.kt` |
| 3 | **Dato → gráfica**: primero el dato, al tocar los cuadrados se vuelven la gráfica | `morph/SeriesMorphCard.kt` |
| 4 | **Cubos animados**: cada pieza es un cubo 3D y salen en ola hasta la gráfica | `morph/CubeMorphCard.kt` |
| 5 | **Variante 1 — Lluvia**: el gráfico está oculto; los cubos caen y lo construyen | `morph/CubeRainCard.kt` |

En la Card 1 los 4 segmentos de la barra de progreso **se separan y crecen** hasta ser
4 barras, el track se adelgaza hasta ser la línea base y el panel del hero se estrecha
hasta ser la canaleta del eje Y. Son las mismas piezas todo el tiempo: por eso se siente
una transformación y no un crossfade.

En la Card 2 no se calcula geometría: se escriben dos layouts reales y se marca con la
misma key qué piezas viajan entre ambos estados.

Toca cada card para alternarla.

---

## Variantes: el gráfico oculto

El requisito cambió: **el gráfico no debe verse en el estado información**. Eso obliga a
reinventar de dónde salen los cubos, porque ya no pueden estar esperando a la vista.

### Variante 1 — Lluvia de cubos · `morph/CubeRainCard.kt`

**Estado información:** solo el dato. `Desnivel 9 m`, su detalle y una pista
(`Toca para ver el perfil`) que hace intencional el hueco de abajo. **Ningún cubo.**

**Al tocar:** cada cubo **cae desde fuera de la tarjeta** con aceleración de gravedad,
escalonado de izquierda a derecha, y **se aplasta contra la línea base** antes de quedarse
quieto. La gráfica se construye sola, de izquierda a derecha.

**El truco para que no se vea nada:** los cubos no se ocultan con opacidad ni con una
bandera. Viven **por encima del lienzo** (`startY = -altura - 6%`) y el `Canvas` lleva
`Modifier.clipToBounds()`, así que simplemente no existen en pantalla hasta que empiezan a
caer. Al volver, suben y desaparecen por arriba: el mismo camino al revés.

```kotlin
val fallT = (local / RAIN_FALL_END).coerceAtMost(1f)   // 0..0.72 del recorrido propio
val y = startY + (targetY - startY) * (fallT * fallT)  // gravedad: acelera al caer

// impacto: se comprime en vertical y se ensancha en horizontal
val impact = sin(settleT * PI)
val h = targetH * (1f - RAIN_SQUASH * impact)
val w = targetW * (1f + RAIN_WIDEN * impact)
```

Las volteretas salen de animar la profundidad mientras cae (`sin(fallT * π * 2)`), así que
el cubo gira dos veces en el aire.

**Verificación** sobre 483 fotogramas de `demo5.mp4`:

- En el estado información **no hay ni un píxel de gráfico** (`v1_dato.png`).
- En `v1_caida.png` se ve la cascada: los cubos entrando por arriba, escalonados.
- El centroide del texto `ALTURA` varía **0.05 px** en los 483 fotogramas: la card no se
  mueve.

---

## Cubos animados: la card que se despliega en ola

`CubeMorphCard` lleva la idea anterior un paso más allá. En vez de barras planas, **cada
pieza es un cubo en perspectiva isométrica**: cara superior más clara, cara lateral más
oscura y cara frontal del color base. Eso solo ya cambia por completo cómo se lee el
movimiento — dejan de ser barras que se estiran y pasan a ser bloques que viajan.

### Cómo se inventó el movimiento

Tres decisiones, y cada una resuelve un problema distinto:

**1. Escalonado (`STAGGER = 0.45`).** Cada cubo no arranca a la vez: el cubo `i` empieza
en `i / n * 0.45` del recorrido total. El bloque no salta entero de golpe, **se deshace
en una ola de izquierda a derecha**. Es lo que hace que se lea como una animación y no
como un cambio de estado.

**2. Saltito (`HOP`).** Cada cubo sube y baja una vez mientras viaja
(`-sin(local * π) * altura`). Sin esto el movimiento es un deslizamiento aburrido; con
esto los cubos **vuelan en arco**.

**3. El cubo se hincha a mitad de camino.** La profundidad se multiplica por
`1 + 0.85 * sin(local * π)`, así que el cubo se engorda justo cuando está en el aire y
adelgaza al aterrizar. Es el truco que vende la ilusión de que **gira sobre sí mismo**:
sin él, un cubo que solo se traslada parece una barra que se mueve.

```kotlin
val start = (index.toFloat() / count) * STAGGER
val local = FastOutSlowInEasing.transform(((t - start) / (1f - STAGGER)).coerceIn(0f, 1f))

y -= sin(local * PI.toFloat()) * size.height * HOP          // el arco
val depth = baseDepth * (1f + 0.85f * sin(local * PI.toFloat()))   // el giro
```

### Un detalle de rendimiento

El progreso se guarda como `State<Float>` y se lee **dentro del Canvas**, no en la
composición:

```kotlin
val progress: State<Float> = animateFloatAsState(...)
Canvas(modifier) {
    val tNow = progress.value      // se lee en la fase de dibujo
    ...
}
```

Así cada fotograma invalida solo el dibujo, no la tarjeta entera con sus textos. Con 28
cubos animados eso se nota.

### Verificación

Alto de la card sobre **250 fotogramas** de `demo4.mp4`. El borde de la card tiene muy
poco contraste y una sombra suave, así que medirlo por umbral da falsos positivos de
±3 px por ruido del encoder. La prueba fiable es una **referencia interna de alto
contraste**: el centroide del texto `ALTURA`.

```
centroide de 'ALTURA': min=107.45  max=107.51  variacion=0.06 px   (250 fotogramas)
```

La card no se mueve ni una décima de píxel. El contenedor es fijo.

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
| `cubos_dato.png` | La card de cubos en estado dato, junto a la versión plana |
| `cubos_grafica.png` | La misma card ya desplegada en gráfica |
| `cubos_morph.png` | **La ola**: los cubos salen escalonados y aterrizan, a 20 fps |
| `demo4.mp4` | Grabación de los cubos animados (12 s) |

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
