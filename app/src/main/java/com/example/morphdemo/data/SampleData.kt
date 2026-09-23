package com.example.morphdemo.data

import androidx.compose.ui.graphics.Color

/**
 * Una categoria de gasto.
 *
 * @param fraction altura relativa de la barra en el estado "grafica" (0f..1f).
 */
data class Category(
    val name: String,
    val amountText: String,
    val fraction: Float,
    val color: Color,
)

object SampleData {
    const val TITLE = "Presupuesto mensual"
    const val AMOUNT = "S/ 1,240"
    const val SUBTITLE = "de S/ 2,000 - 62%"

    val categories = listOf(
        Category("Comida", "520", 0.55f, Color(0xFF6C5CE7)),
        Category("Transp.", "310", 0.80f, Color(0xFF00B894)),
        Category("Ocio", "190", 0.35f, Color(0xFFE17055)),
        Category("Otros", "220", 0.65f, Color(0xFF0984E3)),
    )

    // --- Series de una sesion de entrenamiento (caso FitLog) ------------------------

    /**
     * Perfil de altura normalizado (0f..1f), con la forma tipica de una salida real:
     * llano al principio, subida suave y un pico corto al final.
     */
    val elevationProfile = listOf(
        0.18f, 0.15f, 0.21f, 0.29f, 0.27f, 0.25f, 0.33f, 0.39f,
        0.37f, 0.35f, 0.34f, 0.33f, 0.35f, 0.37f, 0.39f, 0.41f,
        0.44f, 0.47f, 0.51f, 0.54f, 0.57f, 0.59f, 0.61f, 0.65f,
        0.71f, 0.78f, 1.00f, 0.52f,
    )

    /** Frecuencia cardiaca normalizada: sube, se mantiene y aprieta al final. */
    val heartRateProfile = listOf(
        0.30f, 0.34f, 0.38f, 0.45f, 0.52f, 0.58f, 0.62f, 0.66f,
        0.68f, 0.70f, 0.71f, 0.72f, 0.70f, 0.69f, 0.71f, 0.73f,
        0.75f, 0.78f, 0.80f, 0.82f, 0.84f, 0.86f, 0.88f, 0.90f,
        0.92f, 0.95f, 1.00f, 0.88f,
    )

    // Etiquetas de la card de altura, con los mismos numeros que la pantalla real.
    const val ALTITUDE_TRAILING = "300 puntos"
    const val ALTITUDE_LABEL = "Desnivel"
    const val ALTITUDE_VALUE = "9"
    const val ALTITUDE_UNIT = "m"
    const val ALTITUDE_DETAILS = "Max 96 m - Min 82 m"
    const val ALTITUDE_HINT = "Toca para ver el perfil"
    const val ALTITUDE_MAX = "Max 96 m"
    const val ALTITUDE_MIN = "Min 82 m"

    const val PULSE_TRAILING = "300 puntos"
    const val PULSE_LABEL = "FC media"
    const val PULSE_VALUE = "142"
    const val PULSE_UNIT = "ppm"
    const val PULSE_DETAILS = "Maxima 171 ppm"
    const val PULSE_MAX = "Max 171 ppm"
    const val PULSE_MIN = "Min 96 ppm"
}
