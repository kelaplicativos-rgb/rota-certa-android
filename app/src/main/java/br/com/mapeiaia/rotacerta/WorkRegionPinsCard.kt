package br.com.mapeiaia.rotacerta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WorkRegionPinsCard(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val resolver = remember { WorkRegionAddressResolver(context) }
    val scope = rememberCoroutineScope()
    val pins = remember(settings) { WorkRegionTargetPolicy.editablePins(settings) }
    var newAddress by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun addAddress() {
        val address = newAddress.trim()
        if (address.isBlank() || adding || pins.size >= WorkRegionTargetPolicy.MAX_PINS) return
        adding = true
        status = "Localizando o endereço..."
        scope.launch {
            val coordinate = resolver.resolve(address, settings.googleMapsApiKey)
            when {
                coordinate == null -> status = "Não consegui localizar este endereço. Inclua número, cidade e estado."
                WorkRegionTargetPolicy.containsEquivalent(settings, address, coordinate) -> {
                    status = "Este endereço já está na lista de alfinetes."
                }
                else -> {
                    val createdAt = System.currentTimeMillis()
                    val pin = WorkRegionPin(
                        id = "work-pin-$createdAt-${address.hashCode()}",
                        address = address,
                        coordinate = coordinate,
                        enabled = true,
                        createdAtMillis = createdAt,
                    )
                    onSettingsChange(WorkRegionTargetPolicy.addOrUpdate(settings, pin))
                    newAddress = ""
                    status = "Alfinete adicionado e ligado."
                    focusManager.clearFocus()
                }
            }
            adding = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Região de trabalho", fontWeight = FontWeight.Bold)
            Text(
                "O farol fica verde quando o destino final estiver dentro do raio da Casa ou de qualquer alfinete ligado.",
            )

            WorkRegionToggleRow(
                label = "Usar Casa",
                checked = settings.homeTargetEnabled,
                onCheckedChange = { enabled -> onSettingsChange(settings.copy(homeTargetEnabled = enabled)) },
            )
            WorkRegionToggleRow(
                label = "Usar alfinetes",
                checked = settings.alternativeTargetEnabled,
                onCheckedChange = { enabled -> onSettingsChange(settings.copy(alternativeTargetEnabled = enabled)) },
            )

            if (!settings.homeTargetEnabled && !settings.alternativeTargetEnabled) {
                Text("Casa e alfinetes estão desligados. O farol não poderá liberar verde.")
            }

            Text("Endereços dos alfinetes (${pins.size})", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = newAddress,
                onValueChange = { newAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Novo endereço: aeroporto, rodoviária, bairro...") },
                singleLine = true,
                enabled = !adding && pins.size < WorkRegionTargetPolicy.MAX_PINS,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addAddress() }),
            )
            Button(
                onClick = { addAddress() },
                enabled = newAddress.isNotBlank() && !adding && pins.size < WorkRegionTargetPolicy.MAX_PINS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (adding) "Localizando..." else "Adicionar alfinete")
            }
            if (status.isNotBlank()) Text(status)

            if (pins.isEmpty()) {
                Text("Nenhum endereço de alfinete cadastrado.")
            } else {
                pins.forEach { pin ->
                    WorkRegionPinEditor(
                        pin = pin,
                        settings = settings,
                        resolver = resolver,
                        onSettingsChange = onSettingsChange,
                    )
                }
            }

            Text(
                "Todos os alfinetes usam a mesma barra de distância. O cálculo de Casa e alfinetes é feito em uma única consulta de rota.",
            )
        }
    }
}

@Composable
private fun WorkRegionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WorkRegionPinEditor(
    pin: WorkRegionPin,
    settings: AppSettings,
    resolver: WorkRegionAddressResolver,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var draftAddress by remember(pin.id, pin.address) { mutableStateOf(pin.address) }
    var saving by remember(pin.id) { mutableStateOf(false) }
    var status by remember(pin.id) { mutableStateOf("") }

    fun saveAddress() {
        val address = draftAddress.trim()
        if (address.isBlank() || saving) return
        saving = true
        status = "Validando endereço..."
        scope.launch {
            val coordinate = resolver.resolve(address, settings.googleMapsApiKey)
            if (coordinate == null) {
                status = "Endereço não localizado. Confira número, cidade e estado."
            } else {
                onSettingsChange(
                    WorkRegionTargetPolicy.addOrUpdate(
                        settings,
                        pin.copy(address = address, coordinate = coordinate),
                    ),
                )
                status = "Endereço salvo."
                focusManager.clearFocus()
            }
            saving = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkRegionToggleRow(
                label = if (pin.enabled) "Alfinete ligado" else "Alfinete desligado",
                checked = pin.enabled,
                onCheckedChange = { enabled ->
                    onSettingsChange(WorkRegionTargetPolicy.setEnabled(settings, pin.id, enabled))
                },
            )
            OutlinedTextField(
                value = draftAddress,
                onValueChange = { draftAddress = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endereço do alfinete") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveAddress() }),
            )
            pin.coordinate?.let { coordinate ->
                Text("Coordenada validada: %.5f, %.5f".format(coordinate.latitude, coordinate.longitude))
            }
            if (status.isNotBlank()) Text(status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { saveAddress() },
                    enabled = draftAddress.isNotBlank() && !saving && draftAddress.trim() != pin.address.trim(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "Salvando..." else "Salvar")
                }
                OutlinedButton(
                    onClick = { onSettingsChange(WorkRegionTargetPolicy.remove(settings, pin.id)) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Excluir")
                }
            }
        }
    }
}
