# Morph Demo — Card → Gráfica en Jetpack Compose

Demo que compara **dos técnicas** para transformar una card de información en una
gráfica **sin que la card cambie de tamaño**: el borde queda fijo y solo se animan
las piezas de dentro.

| Card | Técnica | Archivo |
| --- | --- | --- |
| 1 | Cuadrados interpolados a mano (`animateFloatAsState` + `lerp`) | `morph/RectMorphCard.kt` |
| 2 | `SharedTransitionLayout` + `Modifier.sharedElement` | `morph/SharedMorphCard.kt` |

En la Card 1 los 4 segmentos de la barra de progreso **se separan y crecen** hasta ser
4 barras, el track se adelgaza hasta ser la línea base y el panel del hero se estrecha
hasta ser la canaleta del eje Y. Son las mismas piezas todo el tiempo: por eso se siente
una transformación y no un crossfade.

En la Card 2 no se calcula geometría: se escriben dos layouts reales y se marca con la
misma key qué piezas viajan entre ambos estados.

Toca cada card para alternarla.

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
