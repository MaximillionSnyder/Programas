# Artefactos de verificación

Grabación de la app corriendo en un **POCO X6 Pro 5G** (Android 17, 1220x2712,
densidad 480), instalada por adb. La app se lanzó en **modo auto-demo** para que las
cards se alternaran solas (ver la sección correspondiente en el README raíz).

| Archivo | Qué es |
| --- | --- |
| `hi1_morph.png` | Card 1 a 30 fps: el morph completo, del estado info a la gráfica |
| `hi2_morph.png` | Card 2 a 30 fps: el mismo morph, visiblemente más rápido |
| `montage_coarse.png` | Rejilla de 12 s a 1 fps: alternancia completa de ambas cards |
| `demo2.mp4` | Grabación original (12 s, 1220x2712) |
| `serie_inicial.png` | Las dos cards de "Dato → gráfica" en estado gráfica |
| `serie_morph.png` | La transición dato → gráfica de la card Altura, a 20 fps |
| `serie_pulso.png` | La card de Pulso alternando, a 1 fps |
| `demo3.mp4` | Grabación de las cards nuevas (12 s) |

## Cómo se generaron

```bash
# lanzar en modo auto-demo
adb shell am force-stop com.example.morphdemo
adb shell am start -n com.example.morphdemo/.MainActivity --ez auto true

# grabar
adb shell screenrecord --time-limit 12 --bit-rate 8000000 /sdcard/demo2.mp4
adb pull /sdcard/demo2.mp4

# montaje de una card a 30 fps (recorte: x, alto, x0, y0)
ffmpeg -i demo2.mp4 -ss 2.85 -t 0.7 \
  -vf "crop=1220:670:0:630,fps=30,scale=420:-1,tile=7x3" -frames:v 1 hi1_morph.png
ffmpeg -i demo2.mp4 -ss 2.85 -t 0.7 \
  -vf "crop=1220:670:0:1490,fps=30,scale=420:-1,tile=7x3" -frames:v 1 hi2_morph.png

# rejilla de toda la grabación
ffmpeg -i demo2.mp4 -vf "crop=1220:1250:0:450,fps=1,scale=360:-1,tile=4x3" -frames:v 1 montage_coarse.png
```

## Cómo se midió

**Alto de la card.** Se extrajo una columna de píxeles dentro de la card pero fuera del
contenido (`x=1150`) en RGB crudo, y en cada fotograma se buscó la primera y la última
fila más clara que el fondo. Resultado: alto constante de 590–592 px en los 207
fotogramas; los 2 px son antialiasing en las esquinas redondeadas.

**Duración del morph.** Se extrajo la columna de la barra más alta (`x=659`, la barra
verde) y en cada fotograma se localizó el primer píxel verde. Las ventanas en que esa
posición cambia son las transiciones; su duración es la del morph.

```bash
ffmpeg -i demo2.mp4 -vf "crop=2:670:659:630" -f rawvideo -pix_fmt rgb24 - > col1.raw
```

> El video es de ~17 fps (205 fotogramas en 11.97 s), así que hay ±58 ms de
> incertidumbre. Suficiente para comparar las dos cards entre sí.

## Resultado

| | info → gráfica | gráfica → info |
| --- | --- | --- |
| Card 1 (cuadrados) | 467 / 525 / 467 ms | 409 / 409 / 409 ms |
| Card 2 (SharedTransition) | 292 / 467 / 467 ms | 175 / 233 / 233 ms |

| | Alto de la card |
| --- | --- |
| Card 1 | 590 – 592 px |
| Card 2 | 590 – 592 px |

## Verificación de la card "Dato → gráfica"

Alto de la card sobre **99 fotogramas** de `demo3.mp4`, muestreando una columna dentro
de la card pero fuera del contenido (`x=1149`, y 680..1530):

```
borde superior : 30..31    variacion 1 px
borde inferior : 794..796  variacion 2 px
ALTO           : 765..767 px  variacion 2 px  -> ALTO CONSTANTE
```

Los 2 px son antialiasing. El contenedor no cambia entre el estado dato y el estado
gráfica, así que la lista no da saltos al tocar.

```bash
ffmpeg -v error -i demo3.mp4 -vf "crop=2:850:1149:680" -f rawvideo -pix_fmt rgb24 - > sedge.raw
```
