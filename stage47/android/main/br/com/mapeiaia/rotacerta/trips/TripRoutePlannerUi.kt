package br.com.mapeiaia.rotacerta.trips

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import br.com.mapeiaia.rotacerta.BuildConfig
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TripRoutePlannerControl(
    stopNames: List<String>,
    departureAtMillis: Long?,
    onPlan: (TripRoutePlan) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val canCalculate = stopNames.size >= 2 &&
        stopNames.none(String::isBlank) &&
        departureAtMillis != null &&
        BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()

    OutlinedButton(
        enabled = canCalculate && !busy,
        onClick = {
            val departure = departureAtMillis ?: return@OutlinedButton
            busy = true
            message = "Calculando rota real de carro e horários…"
            scope.launch {
                runCatching {
                    TripRoutePlanner().plan(
                        stopNames = stopNames,
                        departureAtMillis = departure,
                        apiKey = BuildConfig.GOOGLE_MAPS_API_KEY,
                    )
                }.onSuccess { plan ->
                    onPlan(plan)
                    val km = String.format(Locale("pt", "BR"), "%.1f", plan.totalDistanceMeters / 1000.0)
                    val hours = plan.totalDurationSeconds / 3600
                    val minutes = (plan.totalDurationSeconds % 3600) / 60
                    message = "Rota Google confirmada: $km km • ${hours}h ${minutes}min • ${plan.legs.size} trecho(s)."
                }.onFailure {
                    message = "Não foi possível calcular a rota: ${it.message ?: "erro desconhecido"}"
                }
                busy = false
            }
        },
    ) {
        Text(if (busy) "Calculando…" else "Calcular rota e horários")
    }
    if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
        Text("Google Maps não configurado nesta instalação; a viagem pode ser salva sem ETA automático.")
    } else {
        message?.let { Text(it) }
    }
}
