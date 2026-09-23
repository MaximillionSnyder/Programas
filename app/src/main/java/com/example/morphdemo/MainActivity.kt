package com.example.morphdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.SampleData
import com.example.morphdemo.morph.CubeBurstCard
import com.example.morphdemo.morph.CubeConvergeCard
import com.example.morphdemo.morph.CubeFlipCard
import com.example.morphdemo.morph.CubeMorphCard
import com.example.morphdemo.morph.CubeRainCard
import com.example.morphdemo.morph.CubeSlabCard
import com.example.morphdemo.morph.RectMorphCard
import com.example.morphdemo.morph.SeriesMorphCard
import com.example.morphdemo.morph.SharedMorphCard
import com.example.morphdemo.theme.MorphDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modo auto-demo, se activa con:  am start ... --ez auto true
        // Hace falta porque HyperOS bloquea inyectar eventos de entrada desde adb.
        val auto = intent.getBooleanExtra("auto", false)
        setContent {
            MorphDemoTheme {
                Screen(auto)
            }
        }
    }
}

@Composable
private fun Screen(auto: Boolean = false) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Card -> Grafica",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mismo tamano de card en ambos estados. Toca cada card para transformarla.",
            fontSize = 12.sp,
            color = muted,
        )

        Spacer(Modifier.height(28.dp))

        SectionHeader(
            number = "1",
            name = "Variante 5 - Convergen desde los lados",
            detail = "Los cubos entran por los bordes y se cierran desde el centro",
        )
        Spacer(Modifier.height(10.dp))
        CubeConvergeCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            hint = SampleData.ALTITUDE_HINT,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "2",
            name = "Variante 4 - El bloque que se resuelve",
            detail = "Cae un bloque macizo y despues se asienta en el perfil",
        )
        Spacer(Modifier.height(10.dp))
        CubeSlabCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            hint = SampleData.ALTITUDE_HINT,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "3",
            name = "Variante 3 - Se ponen de pie",
            detail = "Los cubos giran sobre su eje hasta quedar de frente",
        )
        Spacer(Modifier.height(10.dp))
        CubeFlipCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            hint = SampleData.ALTITUDE_HINT,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "4",
            name = "Variante 2 - El dato se deshace",
            detail = "Los cubos nacen del numero y vuelan en arco hasta la grafica",
        )
        Spacer(Modifier.height(10.dp))
        CubeBurstCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            hint = SampleData.ALTITUDE_HINT,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "5",
            name = "Variante 1 - Lluvia de cubos",
            detail = "El grafico esta oculto; al tocar, los cubos caen y lo forman",
        )
        Spacer(Modifier.height(10.dp))
        CubeRainCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            hint = SampleData.ALTITUDE_HINT,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "6",
            name = "Cubos animados (ola)",
            detail = "La version anterior: el bloque se veia y se desplegaba",
        )
        Spacer(Modifier.height(10.dp))
        CubeMorphCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            cubeColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2600L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "7",
            name = "Dato -> grafica (plano)",
            detail = "La misma idea con barras planas, para comparar",
        )
        Spacer(Modifier.height(10.dp))
        SeriesMorphCard(
            title = "Altura",
            trailing = SampleData.ALTITUDE_TRAILING,
            headlineLabel = SampleData.ALTITUDE_LABEL,
            headlineValue = SampleData.ALTITUDE_VALUE,
            headlineUnit = SampleData.ALTITUDE_UNIT,
            details = SampleData.ALTITUDE_DETAILS,
            maxLabel = SampleData.ALTITUDE_MAX,
            minLabel = SampleData.ALTITUDE_MIN,
            series = SampleData.elevationProfile,
            barColor = MaterialTheme.colorScheme.primary,
            autoToggleMs = if (auto) 2000L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "8",
            name = "Cuadrados animados",
            detail = "Rectangulos interpolados a mano con animateFloatAsState",
        )
        Spacer(Modifier.height(10.dp))
        RectMorphCard(
            title = SampleData.TITLE,
            amount = SampleData.AMOUNT,
            subtitle = SampleData.SUBTITLE,
            categories = SampleData.categories,
            autoToggleMs = if (auto) 2000L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "9",
            name = "SharedTransitionLayout",
            detail = "animateBounds sobre layouts reales, sin calcular geometria",
        )
        Spacer(Modifier.height(10.dp))
        SharedMorphCard(
            title = SampleData.TITLE,
            amount = SampleData.AMOUNT,
            subtitle = SampleData.SUBTITLE,
            categories = SampleData.categories,
            autoToggleMs = if (auto) 2000L else null,
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionHeader(number: String, name: String, detail: String) {
    val primary = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "$number - $name",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = onBackground,
        )
        Text(
            text = detail,
            fontSize = 11.sp,
            color = muted,
        )
        Spacer(Modifier.height(2.dp))
        Spacer(
            Modifier
                .height(2.dp)
                .fillMaxWidth(0.18f)
                .background(primary)
        )
    }
}
