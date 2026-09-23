package com.example.morphdemo.morph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.Category
import kotlinx.coroutines.delay

/** Alto fijo de la card: es la garantia de que el contenedor NO cambia de tamano. */
val MorphCardHeight: Dp = 210.dp

internal val CardPadding: Dp = 20.dp

// --- Geometria normalizada (0f..1f) del lienzo interno de la card -------------------

private const val SEG_GAP = 0.022f
private const val BASELINE_Y = 0.88f
private const val BAR_START_X = 0.30f
private const val BAR_STEP_X = 0.175f
private const val BAR_W = 0.16f
private const val BAR_MAX_H = 0.72f

/** Un rectangulo en coordenadas normalizadas respecto al contenido de la card. */
private data class Slot(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val radius: Float,
    val color: Color,
)

private fun lerpSlot(a: Slot, b: Slot, t: Float) = Slot(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t,
    w = a.w + (b.w - a.w) * t,
    h = a.h + (b.h - a.h) * t,
    radius = a.radius + (b.radius - a.radius) * t,
    color = lerp(a.color, b.color, t),
)

/**
 * Construye los pares (estado INFO -> estado GRAFICA).
 *
 * El orden importa: en un Box los hijos posteriores se dibujan encima.
 * Por eso el track va antes que los segmentos de color (las barras deben quedar por
 * encima de la linea base) y el panel va primero de todo.
 */
private fun buildSlots(
    categories: List<Category>,
    accent: Color,
    trackColor: Color,
    axisColor: Color,
): List<Pair<Slot, Slot>> {
    val n = categories.size
    val segW = (1f - SEG_GAP * (n - 1)) / n

    val slots = mutableListOf<Pair<Slot, Slot>>()

    // 1. Panel "hero" (fondo suave arriba) -> canaleta del eje Y a la izquierda.
    slots += Slot(0f, 0f, 1f, 0.52f, 18f, accent.copy(alpha = 0.10f)) to
        Slot(0f, 0f, 0.24f, 1f, 14f, accent.copy(alpha = 0.08f))

    // 2. Track de la barra de progreso -> linea base de la grafica.
    slots += Slot(0f, 0.86f, 1f, 0.07f, 6f, trackColor) to
        Slot(0.28f, BASELINE_Y, 0.72f, 0.012f, 2f, axisColor)

    // 3. Los cuadrados protagonistas: segmentos del progreso -> barras.
    categories.forEachIndexed { i, category ->
        val infoX = i * (segW + SEG_GAP)
        val chartH = category.fraction * BAR_MAX_H
        slots += Slot(infoX, 0.86f, segW, 0.07f, 6f, category.color) to
            Slot(
                x = BAR_START_X + i * BAR_STEP_X,
                y = BASELINE_Y - chartH,
                w = BAR_W,
                h = chartH,
                radius = 7f,
                color = category.color,
            )
    }

    return slots
}

/**
 * CARD A - morph por interpolacion manual de rectangulos.
 *
 * Todas las piezas son el MISMO Box en ambos estados: no hay crossfade de layouts.
 * Lo unico que cambia es el valor t (0 = info, 1 = grafica) y de el se derivan
 * posicion, tamano, radio de esquina y color de cada cuadrado.
 */
@Composable
fun RectMorphCard(
    title: String,
    amount: String,
    subtitle: String,
    categories: List<Category>,
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
        label = "rectMorph",
    )

    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val slots = remember(categories, accent, trackColor, axisColor) {
        buildSlots(categories, accent, trackColor, axisColor)
    }

    // Los textos se desvanecen en ventanas opuestas: la info se va pronto,
    // las etiquetas de la grafica entran al final.
    val infoAlpha = (1f - t / 0.30f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(MorphCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(CardPadding)
        ) {
            val cw = maxWidth
            val ch = maxHeight

            // --- Los cuadrados -------------------------------------------------
            slots.forEach { (from, to) ->
                val s = lerpSlot(from, to, t)
                Box(
                    modifier = Modifier
                        .offset(x = cw * s.x, y = ch * s.y)
                        .size(width = cw * s.w, height = ch * s.h)
                        .clip(RoundedCornerShape(s.radius.dp))
                        .background(s.color)
                )
            }

            // --- Texto del estado INFO ----------------------------------------
            if (infoAlpha > 0.01f) {
                MorphLabel(title, cw * 0.05f, ch * 0.055f, cw * 0.90f, infoAlpha, 10.sp, muted)
                MorphLabel(
                    amount, cw * 0.045f, ch * 0.14f, cw * 0.90f,
                    infoAlpha, 26.sp, onSurface, FontWeight.ExtraBold,
                )
                MorphLabel(subtitle, cw * 0.05f, ch * 0.40f, cw * 0.90f, infoAlpha, 10.sp, muted)
            }

            // --- Texto del estado GRAFICA --------------------------------------
            if (chartAlpha > 0.01f) {
                MorphLabel("Total", cw * 0.02f, ch * 0.02f, cw * 0.20f, chartAlpha, 8.sp, muted)
                MorphLabel(
                    amount, cw * 0.02f, ch * 0.08f, cw * 0.21f,
                    chartAlpha, 11.sp, onSurface, FontWeight.Bold,
                )

                categories.forEachIndexed { i, category ->
                    val barX = BAR_START_X + i * BAR_STEP_X
                    val chartH = category.fraction * BAR_MAX_H
                    val barY = BASELINE_Y - chartH

                    // Valor encima de cada barra.
                    MorphLabel(
                        category.amountText,
                        cw * barX, ch * (barY - 0.075f), cw * BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.SemiBold,
                        TextAlign.Center,
                    )
                    // Nombre de la categoria bajo la linea base.
                    MorphLabel(
                        category.name,
                        cw * barX, ch * 0.905f, cw * BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.Normal,
                        TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MorphLabel(
    text: String,
    x: Dp,
    y: Dp,
    width: Dp,
    alpha: Float,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = Modifier
            .offset(x = x, y = y)
            .width(width)
            .alpha(alpha),
        fontSize = fontSize,
        lineHeight = fontSize * 1.15f,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
    )
}
