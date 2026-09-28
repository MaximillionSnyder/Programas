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
| `recorrido_dev.mp4` | Card 10 "Recorrido" en auto-demo (8 s, un ciclo de 3.4 s repetido) |
| `recorrido_montage.png` | Un ciclo completo de la card Recorrido a 10 fps (rejilla 6x6) |
| `recorrido_cmp.png` | Dispositivo vs video de referencia, a 0.4 / 0.7 / 1.0 / 1.4 / 2.2 s |

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

## Verificación de la card "Recorrido"

`recorrido_dev.mp4` se grabó con `--ei variant 10 --ez auto true` (un solo ciclo de
3.4 s: 2.4 s de animación + 1 s de pausa). Sobre 239 fotogramas a 30 fps:

```
periodo medido: 102 fotogramas = 3.40 s exactos (ciclos en f90 y f192)
```

Se midió la cobertura de cubos (píxeles no-fondo con r<180, para excluir los textos)
dentro de la card fotograma a fotograma, y se comparó con la del video de referencia:

```
t(s)   dispositivo   video
0.2      0.028       0.108
0.3      0.052       0.164
0.4      0.098       0.272
0.5      0.198       0.311
0.6      0.259       0.371
0.7      0.303       0.444   <- pico en ambos
0.9      0.268       0.353
1.1      0.201       0.209
1.3      0.117       0.097
1.5      0.060       0.053   <- asentado en ambos
```

Los valores absolutos del dispositivo salen algo más bajos por el sesgo de color del
`screenrecord` (cubos oscuros que no pasan el umbral `r<180`), pero los puntos de
inflexión coinciden: pico en 0.7 s, mitad en 1.1 s, asentado en 1.5 s. El render
offline de la misma matemática sobre el mismo video da ±3% en todos los puntos.

```bash
# un ciclo a 30 fps: buscar el frame con la card vacia y contar 102
ffmpeg -v error -i recorrido_dev.mp4 -vf "fps=30,crop=1098:863:60:692" -f rawvideo -pix_fmt rgb24 - > rec.raw
```
