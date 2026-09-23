package com.example.morphdemo.morph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Alto fijo de la card: el contenedor no cambia entre el bloque de cubos y la grafica. */
val CubeCardHeight: Dp = 268.dp

private val CubeBodyHeight: Dp = 168.dp

// --- Geometria normalizada del cuerpo ------------------------------------------------

/** Estado DATO: los cubos apilados en un bloque compacto, abajo a la izquierda. */
private const val PILE_W = 0.40f
private const val PILE_BASELINE = 0.94f
private const val PILE_MAX_H = 0.26f
private const val PILE_MIN_H = 0.025f

/** Estado GRAFICA: los mismos cubos repartidos a lo ancho. */
private const val CHART_BASELINE = 0.88f
private const val CHART_MAX_H = 0.74f
private const val CHART_MIN_H = 0.04f

/** Cuanto del recorrido total se dedica al escalonado entre cubos. */
private const val STAGGER = 0.45f

/** Altura del saltito que da cada cubo mientras viaja (normalizada). */
private const val HOP = 0.085f

/** Un cubo, ya resuelto en pixeles. */
private data class Cube(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/**
 * Dibuja un cubo en perspectiva isometrica: cara superior mas clara, cara lateral mas
 * oscura y cara frontal del color base. Es lo que hace que las piezas se lean como cubos
 * y no como barras planas.
 */
private fun DrawScope.drawCube(cube: Cube, depth: Float, color: Color) {
    val dx = depth
    val dy = -depth * 0.55f
    val radius = (cube.w * 0.16f).coerceAtMost(6f)

    // Cara superior.
    drawPath(
        path = Path().apply {
            moveTo(cube.x, cube.y)
            lineTo(cube.x + dx, cube.y + dy)
            lineTo(cube.x + cube.w + dx, cube.y + dy)
            lineTo(cube.x + cube.w, cube.y)
            close()
        },
        color = lerp(color, Color.White, 0.34f),
    )

    // Cara lateral derecha.
    drawPath(
        path = Path().apply {
            moveTo(cube.x + cube.w, cube.y)
            lineTo(cube.x + cube.w + dx, cube.y + dy)
            lineTo(cube.x + cube.w + dx, cube.y + cube.h + dy)
            lineTo(cube.x + cube.w, cube.y + cube.h)
            close()
        },
        color = lerp(color, Color.Black, 0.32f),
    )

    // Cara frontal.
    drawRoundRect(
        color = color,
        topLeft = Offset(cube.x, cube.y),
        size = Size(cube.w, cube.h),
        cornerRadius = CornerRadius(radius, radius),
    )
}

/**
 * Card con cubos animados: primero el dato, y al tocar los cubos salen del bloque y se
 * despliegan hasta formar la grafica.
 *
 * El movimiento es **escalonado**: cada cubo arranca un poco despues que el anterior, asi
 * que el bloque se deshace en una ola de izquierda a derecha en vez de saltar entero de
 * golpe. Cada cubo ademas da un saltito y se hincha a mitad de camino, de modo que se lee
 * como un cubo que gira en el aire y no como una barra que se estira.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeMorphCard(
    title: String,
    trailing: String,
    headlineLabel: String,
    headlineValue: String,
    headlineUnit: String?,
    details: String,
    maxLabel: String,
    minLabel: String,
    series: List<Float>,
    cubeColor: Color,
    modifier: Modifier = Modifier,
    autoToggleMs: Long? = null,
) {
    var showChart by remember { mutableStateOf(false) }

    if (autoToggleMs != null) {
        LaunchedEffect(autoToggleMs) {
            while (true) {
                delay(autoToggleMs)
                showChart = !showChart
            }
        }
    }

    // Se guarda el State, no el Float: asi el Canvas lo lee en la fase de dibujo y no
    // hay que recomponer la tarjeta entera en cada fotograma.
    val progress: State<Float> = animateFloatAsState(
        targetValue = if (showChart) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "cubeMorph",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val infoAlpha = (1f - t / 0.28f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.60f) / 0.40f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(CubeCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = muted,
                )
                Text(text = trailing, fontSize = 11.sp, color = muted)
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(CubeBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val pileCell = size.width * PILE_W / count
                    val chartCell = size.width / count

                    series.forEachIndexed { index, fraction ->
                        // Progreso propio de este cubo: arranca escalonado.
                        val start = (index.toFloat() / count) * STAGGER
                        val span = 1f - STAGGER
                        val raw = ((tNow - start) / span).coerceIn(0f, 1f)
                        val local = FastOutSlowInEasing.transform(raw)

                        // Bloque compacto -> posicion en la grafica.
                        val pileW = pileCell * 0.78f
                        val pileH = size.height * (PILE_MIN_H + fraction * PILE_MAX_H)
                        val pileX = index * pileCell
                        val pileY = size.height * PILE_BASELINE - pileH

                        val chartW = chartCell * 0.60f
                        val chartH = size.height * (CHART_MIN_H + fraction * CHART_MAX_H)
                        val chartX = index * chartCell
                        val chartY = size.height * CHART_BASELINE - chartH

                        val x = pileX + (chartX - pileX) * local
                        val w = pileW + (chartW - pileW) * local
                        val h = pileH + (chartH - pileH) * local
                        var y = pileY + (chartY - pileY) * local

                        // Saltito: sube y baja una vez durante el viaje.
                        y -= sin(local * PI.toFloat()) * size.height * HOP

                        // El cubo se hincha a mitad de camino: parece que gira en el aire.
                        val baseDepth = w * 0.30f
                        val depth = baseDepth * (1f + 0.85f * sin(local * PI.toFloat()))

                        drawCube(
                            cube = Cube(x = x, y = y, w = w, h = h),
                            depth = depth,
                            color = cubeColor,
                        )
                    }
                }

                // --- Estado DATO ------------------------------------------------
                if (infoAlpha > 0.01f) {
                    Text(
                        text = headlineLabel,
                        modifier = Modifier.offset(x = 0.dp, y = 0.dp).alpha(infoAlpha),
                        fontSize = 11.sp,
                        color = muted,
                        maxLines = 1,
                    )
                    Row(
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.10f).alpha(infoAlpha),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = headlineValue,
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = onSurface,
                            maxLines = 1,
                        )
                        if (headlineUnit != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = headlineUnit,
                                modifier = Modifier.padding(bottom = 5.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = muted,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = details,
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.46f).alpha(infoAlpha),
                        fontSize = 11.sp,
                        color = muted,
                        maxLines = 1,
                    )
                }

                // --- Estado GRAFICA ---------------------------------------------
                if (chartAlpha > 0.01f) {
                    Text(
                        text = maxLabel,
                        modifier = Modifier.offset(x = 0.dp, y = 0.dp).alpha(chartAlpha),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = muted,
                        maxLines = 1,
                    )
                    Text(
                        text = minLabel,
                        modifier = Modifier
                            .offset(x = 0.dp, y = ch * (CHART_BASELINE + 0.03f))
                            .alpha(chartAlpha),
                        fontSize = 10.sp,
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
