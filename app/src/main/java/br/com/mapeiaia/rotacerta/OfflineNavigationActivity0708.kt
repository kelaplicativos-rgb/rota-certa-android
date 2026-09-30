package br.com.mapeiaia.rotacerta

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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                OfflineNavigationScreen0708()
            }
        }
    }
}

@Composable
private fun OfflineNavigationScreen0708() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { OfflineMapStore0708(context) }
    val scope = rememberCoroutineScope()
    var maps by remember { mutableStateOf(store.listMaps()) }
    var status by remember {
        mutableStateOf(
            if (maps.isEmpty()) "Nenhum mapa regional importado."
            else maps.size.toString() + " mapa(s) regional(is) disponível(is).",
        )
    }

    fun refresh() {
        maps = store.listMaps()
    }

    val picker = rememberLauncherForActivityResult(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Navegação offline", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Esta área prepara os mapas regionais que ficarão dentro do próprio Rota Certa. Você pode enviar os arquivos .mwm depois, sem precisar acessar Android/data.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Mapas offline", fontWeight = FontWeight.Bold)
                Text(
                    if (maps.isEmpty()) {
                        "Aguardando mapas regionais. O aplicativo continua funcionando normalmente com a autoridade atual."
                    } else {
                        maps.size.toString() + " mapa(s) importado(s), " +
                            formatOfflineBytes0708(maps.sumOf { it.sizeBytes }) + " no total."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Importar mapas (.mwm)")
                }
                Text(
                    "Os arquivos são copiados para o armazenamento privado do Rota Certa. O arquivo original não é apagado.",
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
                Text("Motor offline", fontWeight = FontWeight.Bold)
                Text(
                    if (maps.isEmpty()) {
                        "Base preparada. O motor de rota não será autorizado sem mapas regionais."
                    } else {
                        "Mapas regionais presentes. A próxima etapa pode ligar busca, coordenadas, rota e navegação offline sobre estes arquivos."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Segurança: esta fase não altera o FAROL nem substitui o cálculo atual. Só a importação e o catálogo local de mapas foram habilitados.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
