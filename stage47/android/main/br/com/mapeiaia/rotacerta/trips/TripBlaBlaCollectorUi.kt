package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun BlaBlaCollectorPanel(
    trips: List<Trip>,
    stateStore: BlaBlaCollectorStateStore,
    currentResponse: BlaBlaCollectorMonthResponse?,
    onResult: (BlaBlaCollectorMonthResponse) -> Unit,
    onChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialSettings = remember { stateStore.settings() }
    var profile1 by remember { mutableStateOf(stateStore.lastProfile1()) }
    var profile2 by remember { mutableStateOf(stateStore.lastProfile2()) }
    var month by remember { mutableStateOf(stateStore.lastMonth().ifBlank { YearMonth.now().toString() }) }
    var settings by remember { mutableStateOf(initialSettings) }
    var baseUrl by remember { mutableStateOf(initialSettings.baseUrl) }
    var token by remember { mutableStateOf(initialSettings.token) }
    var configOpen by remember { mutableStateOf(!initialSettings.configured) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    HorizontalDivider()
    Text("Perfis BlaBlaCar")
    OutlinedTextField(
        value = profile1,
        onValueChange = { profile1 = it.trim().take(36) },
        label = { Text("UUID perfil 1") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = profile2,
        onValueChange = { profile2 = it.trim().take(36) },
        label = { Text("UUID perfil 2 (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = month,
        onValueChange = { month = it.filter { ch -> ch.isDigit() || ch == '-' }.take(7) },
        label = { Text("Mês — AAAA-MM") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(8f))) {
        Button(
            enabled = !busy && settings.configured,
            onClick = {
                error = null
                val ids = listOf(profile1, profile2).map(String::trim).filter(String::isNotBlank)
                if (ids.isEmpty() || ids.any { !UUID_REGEX.matches(it) }) {
                    error = "Informe UUID válido para cada perfil."
                    return@Button
                }
                if (!MONTH_REGEX.matches(month)) {
                    error = "Informe o mês no formato AAAA-MM."
                    return@Button
                }
                val routes = BlaBlaCollectorScope.fromAgenda(trips, month)
                if (routes.isEmpty()) {
                    error = "Não há rota na Agenda para formar o escopo desta busca."
                    return@Button
                }
                stateStore.saveQuery(profile1, profile2, month)
                busy = true
                scope.launch {
                    runCatching {
                        BlaBlaCollectorApi(settings).search(
                            BlaBlaCollectorMonthRequest(
                                profiles = ids.map(::BlaBlaCollectorProfileRequest),
                                month = month,
                                routes = routes,
                                include_past = false,
                            ),
                        )
                    }.onSuccess { response ->
                        stateStore.saveResponse(response)
                        onResult(response)
                        onChanged("Linha do tempo BlaBlaCar atualizada: ${response.trips.size} viagem(ns).")
                    }.onFailure {
                        error = it.message ?: "Falha ao consultar o coletor."
                    }
                    busy = false
                }
            },
        ) { Text(if (busy) "Buscando…" else "Buscar") }
        OutlinedButton(onClick = { configOpen = !configOpen }) {
            Text(if (configOpen) "Fechar configuração" else "Configurar coletor")
        }
    }

    if (!settings.configured) {
        Text("Configure uma vez o endereço HTTPS do coletor para liberar a busca.")
    }
    error?.let { Text(it) }

    if (configOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(6f))) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it.trim() },
                label = { Text("URL HTTPS do coletor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token do coletor (se configurado)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            TextButton(onClick = {
                val updated = BlaBlaCollectorSettings(baseUrl.trimEnd('/'), token.trim())
                if (!updated.configured) {
                    error = "A URL do coletor deve começar com https://"
                } else {
                    stateStore.saveSettings(updated)
                    settings = updated
                    configOpen = false
                    error = null
                    onChanged("Configuração do coletor salva no aparelho.")
                }
            }) { Text("Salvar configuração") }
        }
    }

    currentResponse?.let { response ->
        val coverage = response.coverage
        val validated = coverage.validated_queries
        val requested = coverage.requested_queries
        val icon = if (response.status == "validated") "✅" else "⏳"
        Text("$icon ${response.month ?: ""} • ${response.trips.size} viagem(ns) • $validated/$requested consultas")
        if (!coverage.global_profile_month_complete) {
            Text("Escopo: rotas dinâmicas da Agenda. UUID valida o perfil; não presume rotas que não foram pesquisadas.")
        }
    }
}

private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
private val MONTH_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
