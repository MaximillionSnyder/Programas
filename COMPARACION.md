# Card -> Grafica: dos tecnicas de morph en Jetpack Compose

Demo que compara dos formas de transformar una card de informacion en una grafica
**sin que la card cambie de tamano**.

```
morphdemo/
  app/src/main/java/com/example/morphdemo/
    MainActivity.kt                    pantalla con las dos cards
    data/SampleData.kt                 categorias y textos de ejemplo
    theme/Theme.kt                     Material 3 claro/oscuro
    morph/RectMorphCard.kt             CARD 1 - cuadrados interpolados
    morph/SharedMorphCard.kt           CARD 2 - SharedTransitionLayout
```

## Compilar e instalar

```bash
export JAVA_HOME=/data/data/com.termux/files/home/buildtools/jdk-17.0.20.1+1
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/data/data/com.termux/files/home/buildtools/sdk
GRADLE=/data/data/com.termux/files/home/.gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0/bin/gradle
cd /data/data/com.termux/files/home/morphdemo
$GRADLE assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Stack: Gradle 9.5.0, AGP 9.3.0, Kotlin 2.4.10, Compose UI 1.11.4, Material3 1.4.0,
compileSdk 37, minSdk 26.

## El requisito clave: el contenedor no cambia de tamano

Las dos cards usan `Modifier.fillMaxWidth().height(MorphCardHeight)` con
`MorphCardHeight = 210.dp`. Ese alto fijo es lo que garantiza que el borde de la card
no se mueva ni un pixel: **lo unico que se anima son las piezas de dentro**.

El segundo ingrediente es que ambos estados se dibujan sobre el mismo rectangulo de
contenido (`BoxWithConstraints` en la Card 1, `AnimatedContent` + `fillMaxSize()` en la
Card 2). No hay dos pantallas que se intercambian: hay un area fija y piezas que viajan.

## Comparacion

| | Card 1 - Cuadrados animados | Card 2 - SharedTransitionLayout |
| --- | --- | --- |
| Archivo | `RectMorphCard.kt` (~247 lineas) | `SharedMorphCard.kt` (~263 lineas) |
| Idea | Un `Box` por pieza; interpolas tu la geometria | Escribes dos layouts reales y marcas que piezas son "la misma" |
| Como se anima | `animateFloatAsState` 0f -> 1f y `lerp` de cada campo | `Modifier.sharedElement(key)` + `animateBounds` interno |
| Que controlas | Todo: x, y, ancho, alto, radio, color, easing, timing | Solo la key; Compose calcula las bounds |
| Texto | Se desvanece en ventanas de `t` (`infoAlpha`, `chartAlpha`) | Crossfade normal de `AnimatedContent` |
| API | Estable | `@ExperimentalSharedTransitionApi` |
| Curva de aprendizaje | Baja, pero escribes la geometria a mano | Baja si el layout ya existe; rara si no |
| Se adapta a cambios de layout | No: si mueves un texto, reajustas las fracciones | Si: mueves el composable y el morph sigue |
| Ideal para | Morphin visual preciso, piezas que no son layouts normales | Refactor de una pantalla real donde los elementos ya existen |
| **Duración medida en dispositivo** | 409–525 ms, simétrica | 175–467 ms, **asimétrica y más rápida** |

## Card 1 - cuadrados interpolados

Cada pieza es un `Slot` en coordenadas normalizadas (0f..1f) del area de contenido, con
su version en estado INFO y su version en estado GRAFICA:

```kotlin
private data class Slot(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val radius: Float, val color: Color,
)

private fun lerpSlot(a: Slot, b: Slot, t: Float) = Slot(
    x = a.x + (b.x - a.x) * t,
    // ... y, w, h, radius igual ...
    color = lerp(a.color, b.color, t),
)
```

El estado se deriva de un unico `t`, asi que todo queda sincronizado:

```kotlin
val t by animateFloatAsState(
    targetValue = if (showChart) 1f else 0f,
    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
)
```

El mapeo INFO -> GRAFICA es lo que cuenta la historia:

| Pieza | Estado INFO | Estado GRAFICA |
| --- | --- | --- |
| `panel` | fondo suave del hero (100% ancho, 52% alto) | canaleta del eje Y (24% ancho, 100% alto) |
| `track` | track de la barra de progreso | linea base de la grafica |
| `bar0..bar3` | 4 segmentos del progreso en una fila | 4 barras de alturas distintas |

Los segmentos de color **se separan y crecen** hasta ser barras, el track se adelgaza
hasta ser el eje, y el panel se estrecha hasta ser la canaleta. Es la misma pieza todo el
tiempo: por eso se siente transformacion y no crossfade.

El orden de dibujo importa: en un `Box` los hijos posteriores van encima, por eso
`buildSlots()` agrega `panel`, luego `track` y al final las barras (para que las barras
queden por encima de la linea base).

## Card 2 - SharedTransitionLayout

No se calcula ninguna geometria. Se escriben los dos estados como layouts normales y se
les pone la misma key a las piezas que deben viajar:

```kotlin
SharedTransitionLayout(modifier = Modifier.fillMaxSize().padding(CardPadding)) {
    val shared = this
    AnimatedContent(targetState = showChart, modifier = Modifier.fillMaxSize()) { chart ->
        val visibility: AnimatedContentScope = this
        Box(Modifier.fillMaxSize()) {
            if (chart) shared.ChartState(visibility, amount, categories)
            else shared.InfoState(visibility, title, amount, subtitle, categories)
        }
    }
}
```

Y en cada pieza, la misma key en ambos estados:

```kotlin
// estado INFO: segmento del progreso
Box(
    Modifier.weight(1f).height(12.dp)
        .sharedElement(rememberSharedContentState("bar$index"), avs)
        .clip(RoundedCornerShape(6.dp))
        .background(category.color)
)

// estado GRAFICA: barra alta, otra posicion y otro tamano
Box(
    Modifier.weight(1f).fillMaxHeight(category.fraction)
        .sharedElement(rememberSharedContentState("bar$index"), avs)
        .clip(RoundedCornerShape(7.dp))
        .background(category.color)
)
```

Compose mide cada elemento en los dos layouts y anima las bounds entre ambos. La key
`"panel"` hace lo mismo con el panel del hero, que se convierte en la canaleta.

## Cual usar

- Si la grafica y la info **no comparten estructura** (las barras no existen en el estado
  informacion), la Card 1 es mas directa y da control total del ritmo.
- Si ya tienes la pantalla de info y quieres que **los elementos existentes** se
  reacomoden, la Card 2 evita reescribir la geometria y aguanta cambios de layout.
- Se pueden mezclar: `sharedElement` para lo que ya es layout y rects a mano para adornos.

## Llevarlo a AppGasto

`BudgetProgressCard` es el candidato natural: su `LinearProgressIndicator` se parte en
tantas barras como categorias tenga el gasto, el track pasa a ser la linea base, y el
porcentaje se va al eje Y. `BudgetChartStyle` (CIRCULAR / BAR / SPEEDOMETER / COMPACT) ya
te da el enum de estados destino; el morph seria la transicion entre ellos.

## Notas del entorno (Termux / Android aarch64)

Dos cosas que hay que respetar para compilar aqui:

1. **AGP 9 ya trae Kotlin integrado.** Aplicar `org.jetbrains.kotlin.android` es un error
   fatal: `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin
   support since AGP 9.0`. Solo se aplica `com.android.application` y
   `org.jetbrains.kotlin.plugin.compose`.
2. **El aapt2 de AGP es x86-64 y no corre en aarch64.** Se usa el aapt2 nativo del NDK
   que ya estaba en `buildtools/aapt2-native`:

   ```properties
   android.aapt2FromMavenOverride=/data/data/com.termux/files/home/buildtools/aapt2-native/aapt2
   ```

Verificado: `:app:compileDebugKotlin` y `assembleDebug` pasan, APK de 11.6 MB.
La geometria del morph se valido numericamente (todas las piezas dentro de 0..1, sin
solapamientos, barras apoyadas sobre la linea base).

## Verificación en dispositivo real

Probado en un POCO X6 Pro 5G (Android 17, 1220x2712, densidad 480) con la app instalada
por adb. Detalle completo y artefactos en [`README.md`](README.md) y
[`docs/verificacion/`](docs/verificacion/).

**El requisito central se cumple.** Alto de la card medido sobre 207 fotogramas:

| | Alto | Variación |
| --- | --- | --- |
| Card 1 | 590 – 592 px | 2 px (antialiasing) |
| Card 2 | 590 – 592 px | 2 px (antialiasing) |

**Duración real del morph**, muestreando el píxel de la barra más alta:

| | info → gráfica | gráfica → info |
| --- | --- | --- |
| Card 1 | 467 / 525 / 467 ms | 409 / 409 / 409 ms |
| Card 2 | 292 / 467 / 467 ms | 175 / 233 / 233 ms |

La diferencia entre ambas técnicas **no es solo de código: se nota en el ritmo**. La
Card 1 es simétrica y pausada; la Card 2 vuelve casi el doble de rápido, porque usa el
*spring* por defecto de `sharedElement`. Para igualarlas hay que darle a la Card 2 un
`boundsTransform` explícito con un `tween`.

> El video de la medición es de ~17 fps: ±58 ms de incertidumbre. Sirve para comparar
> las dos cards entre sí, no para certificar milisegundos exactos.
