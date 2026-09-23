package com.example.morphdemo.morph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Alto fijo de la card: el contenedor no cambia entre el dato y la grafica. */
val SeriesCardHeight: Dp = 268.dp

private val BodyHeight: Dp = 168.dp

// --- Geometria normalizada del cuerpo de la card ------------------------------------

/** Miniatura del perfil en el estado "dato": abajo a la izquierda. */
private const val MINI_W = 0.42f
private const val MINI_H = 0.22f
private const val MINI_Y = 0.72f

/** Perfil completo en el estado "grafica". */
private const val CHART_BASELINE = 0.92f
private const val CHART_MAX_H = 0.80f
private const val CHART_MIN_H = 0.05f

/** Un rectangulo del perfil, en coordenadas normalizadas. */
private data class Bar(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float)

/**
 * Posicion de la barra [index] en cada estado.
 *
 * En el estado "dato" las barras forman una miniatura del propio perfil, abajo a la
 * izquierda. En el estado "grafica" se reparten a lo ancho y crecen a su altura real.
 * Son las mismas barras: lo unico que cambia es donde estan y cuanto miden.
 */
private fun barAt(index: Int, count: Int, fraction: Float, expanded: Boolean): Bar {
    val cell = (if (expanded) 1f else MINI_W) / count
    return if (expanded) {
        val h = CHART_MIN_H + fraction * (CHART_MAX_H - CHART_MIN_H)
        Bar(
            x = index * cell,
            y = CHART_BASELINE - h,
            w = cell * 0.68f,
            h = h,
            radius = 4f,
        )
    } else {
        val h = 0.02f + fraction * MINI_H
        Bar(
            x = index * cell,
            y = MINI_Y + MINI_H - h,
            w = cell * 0.66f,
            h = h,
            radius = 2f,
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * Card de serie temporal: primero el dato, y al tocar los cuadrados se convierten en
 * la grafica.
 *
 * Es el patron de FitLog: el desnivel o la frecuencia cardiaca se leen de un vistazo, y
 * la grafica que hoy vive debajo aparece solo cuando se pide. El alto es fijo, asi que
 * la lista no da saltos al alternar.
 *
 * @param series valores normalizados 0f..1f, uno por cuadrado.
 */
@Composable
fun SeriesMorphCard(
    title: String,
    trailing: String,
    headlineLabel: String,
    headlineValue: String,
    headlineUnit: String?,
    details: String,
    maxLabel: String,
    minLabel: String,
    series: List<Float>,
    barColor: Color,
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

    val t by animateFloatAsState(
        targetValue = if (showChart) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "seriesMorph",
    )

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // La info se va pronto; las etiquetas de la grafica entran al final.
    val infoAlpha = (1f - t / 0.30f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(SeriesCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Cabecera, igual en los dos estados.
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(BodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                // --- Los cuadrados: miniatura -> perfil -------------------------
                series.forEachIndexed { index, fraction ->
                    val from = barAt(index, series.size, fraction, expanded = false)
                    val to = barAt(index, series.size, fraction, expanded = true)
                    Box(
                        modifier = Modifier
                            .offset(x = cw * lerp(from.x, to.x, t), y = ch * lerp(from.y, to.y, t))
                            .size(
                                width = cw * lerp(from.w, to.w, t),
                                height = ch * lerp(from.h, to.h, t),
                            )
                            .clip(RoundedCornerShape(lerp(from.radius, to.radius, t).dp))
                            .background(barColor)
                    )
                }

                // --- Estado DATO ------------------------------------------------
                if (infoAlpha > 0.01f) {
                    Text(
                        text = headlineLabel,
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.0f).alpha(infoAlpha),
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
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.0f).alpha(chartAlpha),
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
