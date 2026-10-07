package br.com.mapeiaia.rotacerta

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class OfflineNavigationActivity0708 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OfflineNavigationScreen0709(
                    focusDestination0710 = intent.getBooleanExtra(EXTRA_FOCUS_DESTINATION_0710, false),
                )
            }
        }
    }

    companion object {
        const val EXTRA_FOCUS_DESTINATION_0710 = "gps_offline_focus_destination_0710"
        const val GRID_LAUNCH_MARKER_0710 = "GPS_OFFLINE_GRID_LAUNCH_0710"
    }
}

@Composable
private fun OfflineNavigationScreen0709(
    focusDestination0710: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { OfflineMapStore0708(context) }
    val directDownloader0750 = remember { OrganicMapsDirectMapDownloader0750(context) }
    val locationService0750 = remember { DeviceLocationService(context) }
    val scope = rememberCoroutineScope()
    var maps by remember { mutableStateOf(store.listMaps()) }
    var directDownloadSnapshot0750 by remember {
        mutableStateOf(OrganicMapsDirectMapDownloader0750.Snapshot(ready = false, message = "Preparando catálogo oficial do Organic Maps..."))
    }
    var directDownloadStatus0750 by remember { mutableStateOf("") }
    var status by remember {
        mutableStateOf(
            if (maps.isEmpty()) "Nenhum mapa regional importado."
            else maps.size.toString() + " mapa(s) regional(is) disponível(is).",
        )
    }
    var navigationStatus by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var destinationName by remember { mutableStateOf("") }
    var organicMapsAvailable by remember { mutableStateOf(OrganicMapsOfflineBridge0709.isAvailable(context)) }
    val destinationFocus0710 = remember { FocusRequester() }

    LaunchedEffect(focusDestination0710) {
        if (focusDestination0710) destinationFocus0710.requestFocus()
    }

    LaunchedEffect(directDownloader0750) {
        directDownloader0750.attach { snapshot0750 ->
            directDownloadSnapshot0750 = snapshot0750
            if (snapshot0750.downloadedRegionalMaps > 0) {
                maps = store.listMaps()
            }
        }
    }

    DisposableEffect(directDownloader0750) {
        onDispose { directDownloader0750.detach() }
    }

    fun refresh() {
        maps = store.listMaps()
        organicMapsAvailable = OrganicMapsOfflineBridge0709.isAvailable(context)
    }

    val mapPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) {
            status = "Seleção cancelada."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            status = "Importando " + uris.size + " arquivo(s)..."
            val results = withContext(Dispatchers.IO) { uris.map(store::import) }
            refresh()
            val accepted = results.count { it.accepted }
            val rejected = results.size - accepted
            status = "Importação concluída: " + accepted + " mapa(s)" +
                if (rejected > 0) ", " + rejected + " ignorado(s)." else "."
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
        }
    }

    val pointPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            navigationStatus = "Seleção de ponto cancelada."
            return@rememberLauncherForActivityResult
        }
        val picked = OrganicMapsOfflineBridge0709.extractPickedCoordinate(result.data)
        if (picked == null) {
            navigationStatus = "O Organic Maps retornou sem coordenada válida."
            return@rememberLauncherForActivityResult
        }
        val (coordinate, name) = picked
        destinationText = coordinate.wireValue()
        if (!name.isNullOrBlank()) destinationName = name
        navigationStatus = "Destino recebido: " + coordinate.wireValue()
    }

    fun launchPrepared(intent: Intent, successMessage: String) {
        val prepared = OrganicMapsOfflineBridge0709.prepareIntent(context, intent)
        if (prepared == null) {
            navigationStatus = "Organic Maps não está instalado ou não responde a esta ação."
            return
        }
        runCatching {
            context.startActivity(prepared)
            navigationStatus = successMessage
        }.onFailure { error ->
            navigationStatus = error.message ?: "Falha ao abrir o Organic Maps."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("GPS Offline", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Digite o endereço do destino. Com os mapas da região já baixados no Organic Maps, a busca e a orientação podem funcionar sem internet. Coordenadas podem iniciar a navegação diretamente.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Ponte Organic Maps", fontWeight = FontWeight.Bold)
                Text(
                    if (organicMapsAvailable) {
                        "Disponível no aparelho. A navegação pode usar os mapas que já estão baixados no Organic Maps mesmo sem internet."
                    } else {
                        "Organic Maps não detectado. A importação de .mwm do Rota Certa continua disponível, mas esta ponte não pode navegar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = destinationText,
                    onValueChange = { destinationText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(destinationFocus0710),
                    label = { Text("Para onde você vai?") },
                    supportingText = {
                        Text("Ex.: Rua Vicente Lopes, 8, São Paulo - SP ou -23.550520,-46.633308")
                    },
                )
                OutlinedTextField(
                    value = destinationName,
                    onValueChange = { destinationName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do destino (opcional)") },
                    singleLine = true,
                )

                Button(
                    onClick = {
                        val query = destinationText.trim()
                        if (query.isBlank()) {
                            navigationStatus = "Digite primeiro o endereço que deseja pesquisar."
                        } else {
                            val result = OrganicMapsOfflineBridge0709.launch(
                                context,
                                OrganicMapsOfflineBridge0709.buildSearchUri(query),
                            )
                            navigationStatus = result.reason
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = organicMapsAvailable,
                ) {
                    Text("Buscar endereço no GPS offline")
                }

                OutlinedButton(
                    onClick = {
                        val request = OrganicMapsOfflineBridge0709.prepareIntent(
                            context,
                            OrganicMapsOfflineBridge0709.buildPickPointIntent("Rota Certa"),
                        )
                        if (request == null) {
                            navigationStatus = "Organic Maps não está disponível para escolher o ponto."
                        } else {
                            pointPicker.launch(request)
                            navigationStatus = "Escolha o destino no mapa e confirme."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = organicMapsAvailable,
                ) {
                    Text("Escolher coordenada no mapa")
                }

                Button(
                    onClick = {
                        val coordinate = OrganicMapsOfflineBridge0709.parseCoordinate(destinationText)
                        if (coordinate == null) {
                            navigationStatus = "Para endereço escrito, toque em Buscar endereço no GPS offline e escolha o resultado. Para navegação direta, use uma coordenada ou Escolher coordenada no mapa."
                        } else {
                            val result = OrganicMapsOfflineBridge0709.launch(
                                context,
                                OrganicMapsOfflineBridge0709.buildNavigationUri(
                                    destination = coordinate,
                                    destinationName = destinationName.ifBlank { "Destino Rota Certa" },
                                ),
                            )
                            navigationStatus = if (result.launched) {
                                "Navegação offline enviada ao Organic Maps (" + result.packageName + ")."
                            } else {
                                result.reason
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = organicMapsAvailable,
                ) {
                    Text("Navegar offline agora")
                }

                OutlinedButton(
                    onClick = {
                        val result = OrganicMapsOfflineBridge0709.openMain(context)
                        navigationStatus = result.reason
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = organicMapsAvailable,
                ) {
                    Text("Abrir Organic Maps")
                }

                if (!organicMapsAvailable) {
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(OrganicMapsOfflineBridge0709.downloadPageIntent()) }
                                .onFailure { navigationStatus = it.message ?: "Não foi possível abrir a página de instalação." }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Instalar Organic Maps")
                    }
                }

                if (navigationStatus.isNotBlank()) {
                    Text(navigationStatus, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Mapas do próprio Rota Certa", fontWeight = FontWeight.Bold)
                Text(
                    "Download direto pela fonte oficial do Organic Maps, usando o downloader nativo incorporado. Não abre navegador e não exige procurar arquivo .mwm.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    directDownloadSnapshot0750.message.ifBlank {
                        directDownloadSnapshot0750.downloadedRegionalMaps.toString() + " mapa(s) regional(is) disponível(is) no motor offline."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                directDownloadSnapshot0750.totalBytes?.let { bytes0750 ->
                    Text(
                        "Tamanho previsto do pacote selecionado: " + formatOfflineBytes0708(bytes0750),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            directDownloadStatus0750 = "Localizando o pacote Brasil no catálogo oficial..."
                            val result0750 = directDownloader0750.startBrazilDownload()
                            directDownloadStatus0750 = result0750.message
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = directDownloadSnapshot0750.ready && !directDownloadSnapshot0750.isBusy,
                ) {
                    Text("⬇️ Baixar mapa do Brasil")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            directDownloadStatus0750 = "Identificando sua região pelo GPS..."
                            val coordinate0750 = locationService0750.currentCoordinate()
                            if (coordinate0750 == null) {
                                directDownloadStatus0750 = "Não consegui obter a posição atual. Você pode usar o botão Brasil ou liberar a localização do Rota Certa."
                            } else {
                                val result0750 = directDownloader0750.startCurrentRegionDownload(coordinate0750)
                                directDownloadStatus0750 = result0750.message
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = directDownloadSnapshot0750.ready && !directDownloadSnapshot0750.isBusy,
                ) {
                    Text("⬇️ Baixar mapa da minha região")
                }
                if (directDownloadSnapshot0750.isBusy) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val result0750 = directDownloader0750.cancelActiveDownload()
                                directDownloadStatus0750 = result0750.message
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancelar download")
                    }
                }
                if (directDownloadStatus0750.isNotBlank()) {
                    Text(
                        directDownloadStatus0750,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (maps.isEmpty()) {
                        "Você ainda pode importar manualmente arquivos .mwm, mas isso agora é apenas uma alternativa."
                    } else {
                        maps.size.toString() + " arquivo(s) .mwm no armazenamento privado, " +
                            formatOfflineBytes0708(maps.sumOf { it.sizeBytes }) + " no total."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { mapPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importar .mwm manualmente")
                }
                Text(
                    "O download direto e a importação manual usam o armazenamento privado compatível com o motor Organic Maps incorporado.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Autoridade do FAROL", fontWeight = FontWeight.Bold)
                Text(
                    "Com mapa regional instalado, o FAROL tenta primeiro a rota rodoviária do Organic Maps incorporado. Se o motor offline não responder com segurança dentro do orçamento, o Google continua como fallback.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Haversine continua privado e nunca vira quilometragem pública. A cor e o KM respeitam o binding do destino atual para impedir resultado de endereço antigo ou parcial.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Créditos e dados de mapa", fontWeight = FontWeight.Bold)
                Text(
                    "Integração compatível com o Organic Maps Project. Dados de mapa: © OpenStreetMap e Organic Maps.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://organicmaps.app"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) {
                        Text("Organic Maps")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://openstreetmap.org/copyright"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) {
                        Text("OpenStreetMap")
                    }
                }
            }
        }

        if (maps.isNotEmpty()) {
            Text("Arquivos disponíveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            maps.forEach { map ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(map.name, fontWeight = FontWeight.Bold)
                        Text(formatOfflineBytes0708(map.sizeBytes), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val removed = withContext(Dispatchers.IO) { store.remove(map.name) }
                                    refresh()
                                    status = if (removed) {
                                        "Mapa removido: " + map.name
                                    } else {
                                        "Não foi possível remover " + map.name + "."
                                    }
                                }
                            },
                        ) {
                            Text("Remover")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { (context as? ComponentActivity)?.finish() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Voltar")
        }
    }
}

private fun formatOfflineBytes0708(bytes: Long): String {
    if (bytes < 1024L) return bytes.toString() + " B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return DecimalFormat("0.0").format(value) + " " + units[unit.coerceAtLeast(0)]
}
