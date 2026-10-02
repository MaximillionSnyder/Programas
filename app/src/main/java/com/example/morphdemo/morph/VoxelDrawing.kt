package com.example.morphdemo.morph

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Piezas del morph por voxeles, compartidas por las cards que usan esta tecnica.
 *
 * Estaba todo dentro de [VoxelMorphCard]; se extrajo aqui para que la card de running
 * ([RunVoxelCard]) use exactamente la misma mecanica: teselar cada pieza en voxeles,
 * interpolarla y dibujar cada voxel como cubo pseudo-3D mientras vuela.
 */

/** Rectangulo normalizado (0..1) dentro del area de contenido de la card. */
internal data class VRect(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Una pieza del morph, ya subdividida.
 *
 * @param cols columnas de voxeles, @param rows filas de voxeles.
 */
internal data class VoxelPiece(
    val from: VRect,
    val to: VRect,
    val fromColor: Color,
    val toColor: Color,
    val cols: Int,
    val rows: Int,
)

internal fun lerpVRect(a: VRect, b: VRect, t: Float) = VRect(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t,
    w = a.w + (b.w - a.w) * t,
    h = a.h + (b.h - a.h) * t,
)

/**
 * Hash determinista 0..1 a partir de un entero.
 *
 * Se usa para que cada voxel tenga su propio jitter y sentido de giro sin guardar estado:
 * el mismo indice da siempre el mismo valor, asi que la animacion es reproducible.
 */
internal fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return (x and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
}

/**
 * Dibuja un voxel.
 *
 * Si `flight` es ~0 el voxel esta en reposo y se pinta plano, del tamano exacto de su celda
 * (con medio pixel de mas para que no aparezcan lineas de costura entre voxeles vecinos).
 * Si esta volando se dibuja como cubo: cara frontal + tapa + costado, con el ancho de la
 * cara frontal encogido por el coseno del giro, que es lo que da la sensacion de volumen.
 */
internal fun DrawScope.drawVoxel(
    cx: Float,
    cy: Float,
    cellW: Float,
    cellH: Float,
    color: Color,
    accent: Color,
    flight: Float,
    lift: Float,
    spin: Float,
    scale: Float,
) {
    if (flight < 0.02f) {
        drawRect(
            color = color,
            topLeft = Offset(cx - cellW * 0.5f, cy - cellH * 0.5f),
            size = Size(cellW + 0.5f, cellH + 0.5f),
        )
        return
    }

    // El giro alrededor del eje Y se aproxima encogiendo la cara frontal por |cos| y
    // dibujando el costado con el ancho que asoma: |sin|.
    val angle = spin * flight * 1.5f
    val cosA = abs(cos(angle))
    val sinA = abs(sin(angle))

    val frontH = cellH * scale
    val frontW = cellW * scale * (0.30f + 0.70f * cosA)
    val depth = min(cellW, cellH) * 0.38f * (0.20f + 0.80f * sinA) * scale
    val sideSign = if (spin >= 0f) 1f else -1f

    val left = cx - frontW * 0.5f
    val top = cy - lift - frontH * 0.5f
    val right = left + frontW
    val bottom = top + frontH

    val front = color
    val topFace = lerp(color, Color.White, 0.30f)
    val sideFace = lerp(color, Color.Black, 0.28f)

    // Tapa: paralelogramo que se abre hacia arriba.
    val topPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right + depth * sideSign * 0.45f, top - depth)
        lineTo(left + depth * sideSign * 0.45f, top - depth)
        close()
    }
    drawPath(topPath, topFace)

    // Costado: paralelogramo que se abre hacia el lado opuesto al giro.
    val sidePath = Path().apply {
        moveTo(right, top)
        lineTo(right, bottom)
        lineTo(right - depth * sideSign * 0.45f, bottom - depth)
        lineTo(right - depth * sideSign * 0.45f, top - depth)
        close()
    }
    drawPath(sidePath, sideFace)

    // Cara frontal.
    drawRoundRect(
        color = front,
        topLeft = Offset(left, top),
        size = Size(frontW, frontH),
        cornerRadius = CornerRadius(min(frontW, frontH) * 0.12f),
    )

    // Borde emisivo: solo mientras el voxel esta en el aire.
    drawRoundRect(
        color = accent.copy(alpha = 0.55f * flight),
        topLeft = Offset(left, top),
        size = Size(frontW, frontH),
        cornerRadius = CornerRadius(min(frontW, frontH) * 0.12f),
        style = Stroke(width = 1.2.dp.toPx()),
    )
}
