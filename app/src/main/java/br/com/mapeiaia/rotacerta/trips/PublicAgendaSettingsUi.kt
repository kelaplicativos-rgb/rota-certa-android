package br.com.mapeiaia.rotacerta.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
private fun PublicProfileTextField(
    label: String,
    fieldKey: String,
    mode: PublicDriverProfileMode,
    manualValue: String,
    automaticValue: String,
    overrideFields: Set<String>,
    onManualValueChange: (String) -> Unit,
    onOverrideFieldsChange: (Set<String>) -> Unit,
    sanitize: (String) -> String = { it },
) {
    val overridden = fieldKey in overrideFields
    when (mode) {
        PublicDriverProfileMode.MANUAL -> OutlinedTextField(
            value = manualValue,
            onValueChange = { onManualValueChange(sanitize(it)) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        PublicDriverProfileMode.BLABLACAR -> {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(automaticValue.ifBlank { "Não disponível na última coleta confirmada." })
            Text("Automático da BlaBlaCar", style = MaterialTheme.typography.bodySmall)
        }
        PublicDriverProfileMode.HYBRID -> if (overridden) {
            OutlinedTextField(
                value = manualValue,
                onValueChange = { onManualValueChange(sanitize(it)) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Personalizado por você", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onOverrideFieldsChange(overrideFields - fieldKey) }) {
                Text("Voltar ao automático")
            }
        } else {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(automaticValue.ifBlank { "Não disponível na última coleta confirmada." })
            Text("Automático da BlaBlaCar", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onOverrideFieldsChange(overrideFields + fieldKey) }) {
                Text("Personalizar este campo")
            }
        }
    }
}

@Composable
internal fun OnlineSettingsEditor(
    initial: TripOnlineSettings,
    onSave: (TripOnlineSettings) -> Unit,
    onRotateLink: (expectedCurrent: String, replacement: String) -> Boolean,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val registry = remember(context) { BlaBlaDynamicAccountRegistry(context) }
    val profileStore = remember(context) { BlaBlaPublicProfileStore(context) }
    var linkedProfiles by remember { mutableStateOf(registry.list()) }
    var profileRefreshRevision by remember { mutableStateOf(0) }
    var profileSyncMessage by remember { mutableStateOf<String?>(null) }

    var api by remember { mutableStateOf(initial.apiBaseUrl) }
    var publicBase by remember { mutableStateOf(initial.publicBaseUrl) }
    var token by remember { mutableStateOf(initial.driverToken) }
    var calendarToken by remember { mutableStateOf(initial.publicCalendarToken) }
    var driverName by remember { mutableStateOf(initial.driverDisplayName) }
    var driverUsername by remember { mutableStateOf(initial.driverUsername) }
    var driverWhatsapp by remember { mutableStateOf(initial.driverWhatsapp) }
    var driverPhotoUrl by remember { mutableStateOf(initial.driverPhotoUrl) }
    var driverAbout by remember { mutableStateOf(initial.driverPublicAbout) }
    var driverRating by remember { mutableStateOf(initial.driverPublicRating) }
    var driverReviewCount by remember { mutableStateOf(initial.driverPublicReviewCount.toString()) }
    var driverBadge by remember { mutableStateOf(initial.driverPublicBadge) }
    var vehicleMakeModel by remember { mutableStateOf(initial.vehicleMakeModel) }
    var vehicleColor by remember { mutableStateOf(initial.vehicleColor) }
    var vehicleAmenities by remember { mutableStateOf(initial.vehicleAmenities) }
    var driverPreferences by remember { mutableStateOf(initial.driverPreferences) }
    var paymentInstructions by remember { mutableStateOf(initial.paymentInstructions) }
    var googleCalendarUrl by remember { mutableStateOf(initial.googleCalendarPublicUrl) }
    var publicProfileMode by remember { mutableStateOf(initial.publicProfileMode) }
    var selectedPublicProfileAccountId by remember { mutableStateOf(initial.selectedPublicProfileAccountId) }
    var publicProfileOverrideFields by remember { mutableStateOf(initial.publicProfileOverrideFields) }
    var registrationMessage by remember { mutableStateOf<String?>(null) }
    var linkActionMessage by remember { mutableStateOf<String?>(null) }
    var confirmRegenerateLink by remember { mutableStateOf(false) }
    var linkRotationInFlight by remember { mutableStateOf(false) }
    val registrationScope = rememberCoroutineScope()

    val selectedProfile = linkedProfiles.firstOrNull { it.id == selectedPublicProfileAccountId }
    val automaticSnapshot = remember(selectedPublicProfileAccountId, profileRefreshRevision, linkedProfiles) {
        selectedPublicProfileAccountId.takeIf(String::isNotBlank)?.let(profileStore::read)
    }
    val profileSelectionValid = publicProfileMode == PublicDriverProfileMode.MANUAL ||
        !selectedProfile?.profileUuid.isNullOrBlank()

    val profileSyncLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        linkedProfiles = registry.list()
        profileRefreshRevision++
        profileSyncMessage = if (result.resultCode == android.app.Activity.RESULT_OK) {
            "Perfil sincronizado. Os dados confirmados foram atualizados."
        } else {
            "Sincronização encerrada sem substituir os últimos dados confirmados."
        }
    }

    fun buildSettings() = TripOnlineSettings(
        apiBaseUrl = api.trimEnd('/'),
        publicBaseUrl = publicBase.trimEnd('/'),
        driverToken = token.trim(),
        publicCalendarToken = calendarToken.trim(),
        driverDisplayName = driverName.trim(),
        driverUsername = DriverIdentityRules.normalizeUsername(driverUsername),
        driverWhatsapp = driverWhatsapp.trim(),
        driverPhotoUrl = driverPhotoUrl.trim(),
        driverPublicAbout = driverAbout.trim(),
        driverPublicRating = driverRating.trim(),
        driverPublicReviewCount = driverReviewCount.toIntOrNull() ?: 0,
        driverPublicBadge = driverBadge.trim(),
        vehicleMakeModel = vehicleMakeModel.trim(),
        vehicleColor = vehicleColor.trim(),
        vehicleAmenities = vehicleAmenities.trim(),
        driverPreferences = driverPreferences.trim(),
        paymentInstructions = paymentInstructions.trim(),
        googleCalendarPublicUrl = googleCalendarUrl.trim(),
        publicProfileMode = publicProfileMode,
        selectedPublicProfileAccountId = selectedPublicProfileAccountId,
        publicProfileOverrideFields = publicProfileOverrideFields.intersect(PublicDriverProfileFields.profileControlled),
    )

    Text("Integração online", style = MaterialTheme.typography.titleLarge)
    Text("O link da agenda pertence ao motorista e permanece o mesmo ao alterar dados ou perfil.")
    OutlinedTextField(api, { api = it.trim() }, label = { Text("API HTTPS") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(publicBase, { publicBase = it.trim() }, label = { Text("URL pública") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        driverUsername,
        { driverUsername = DriverIdentityRules.normalizeUsername(it) },
        label = { Text("Nome de usuário no link") },
        modifier = Modifier.fillMaxWidth(),
        enabled = token.isBlank(),
    )

    HorizontalDivider()
    Text("Dados públicos do motorista", style = MaterialTheme.typography.titleMedium)
    Text("Fonte dos dados", style = MaterialTheme.typography.titleSmall)
    if (publicProfileMode == PublicDriverProfileMode.BLABLACAR) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("✓ Usar dados da BlaBlaCar") }
    } else {
        OutlinedButton(onClick = { publicProfileMode = PublicDriverProfileMode.BLABLACAR }, modifier = Modifier.fillMaxWidth()) {
            Text("Usar dados da BlaBlaCar")
        }
    }
    if (publicProfileMode == PublicDriverProfileMode.MANUAL) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("✓ Usar meus próprios dados") }
    } else {
        OutlinedButton(onClick = { publicProfileMode = PublicDriverProfileMode.MANUAL }, modifier = Modifier.fillMaxWidth()) {
            Text("Usar meus próprios dados")
        }
    }
    if (publicProfileMode == PublicDriverProfileMode.HYBRID) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("✓ BlaBlaCar + personalizar") }
    } else {
        OutlinedButton(onClick = { publicProfileMode = PublicDriverProfileMode.HYBRID }, modifier = Modifier.fillMaxWidth()) {
            Text("BlaBlaCar + personalizar")
        }
    }

    if (publicProfileMode != PublicDriverProfileMode.MANUAL) {
        Text("Perfil exibido na Agenda Pública", style = MaterialTheme.typography.titleSmall)
        if (linkedProfiles.isEmpty()) {
            Text("Nenhum perfil BlaBlaCar vinculado neste aparelho.")
        } else {
            linkedProfiles.forEach { profile ->
                OutlinedButton(
                    onClick = { selectedPublicProfileAccountId = profile.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val label = profile.profileName?.takeIf(String::isNotBlank) ?: profile.displayLabel
                    val suffix = if (profile.profileUuid.isNullOrBlank()) " • UUID pendente" else ""
                    Text(if (profile.id == selectedPublicProfileAccountId) "✓ $label$suffix" else "$label$suffix")
                }
            }
        }
        selectedProfile?.let { profile ->
            Button(
                enabled = !linkRotationInFlight,
                onClick = {
                    profileSyncMessage = "Sincronizando ${profile.displayLabel}…"
                    profileSyncLauncher.launch(BlaBlaDynamicSessionIntents.sync(context, profile))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sincronizar perfil selecionado") }
        }
        profileSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        when {
            selectedProfile == null -> Text("Selecione o perfil que será exibido.")
            selectedProfile.profileUuid.isNullOrBlank() -> Text("Sincronize para confirmar o UUID antes de publicar.")
            automaticSnapshot != null -> {
                val whenText = Instant.ofEpochMilli(automaticSnapshot.lastSyncedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                Text("✓ Identidade confirmada • última sincronização $whenText")
            }
            else -> Text("UUID confirmado, mas ainda sem dados públicos confirmados. Dados de outro perfil não serão usados.")
        }
    }

    PublicProfileTextField("Nome público", PublicDriverProfileFields.NAME, publicProfileMode, driverName,
        automaticSnapshot?.profileName.orEmpty(), publicProfileOverrideFields, { driverName = it.take(120) }, { publicProfileOverrideFields = it }, { it.take(120) })
    PublicProfileTextField("Foto pública — URL HTTPS", PublicDriverProfileFields.PHOTO, publicProfileMode, driverPhotoUrl,
        automaticSnapshot?.photoUrl.orEmpty(), publicProfileOverrideFields, { driverPhotoUrl = it.trim().take(500) }, { publicProfileOverrideFields = it }, { it.trim().take(500) })
    PublicProfileTextField("Apresentação", PublicDriverProfileFields.ABOUT, publicProfileMode, driverAbout,
        automaticSnapshot?.about.orEmpty(), publicProfileOverrideFields, { driverAbout = it.take(320) }, { publicProfileOverrideFields = it }, { it.take(320) })
    PublicProfileTextField("Nota pública", PublicDriverProfileFields.RATING, publicProfileMode, driverRating,
        automaticSnapshot?.rating.orEmpty(), publicProfileOverrideFields, { driverRating = it.take(12) }, { publicProfileOverrideFields = it }, { it.take(12) })
    PublicProfileTextField("Quantidade de avaliações", PublicDriverProfileFields.REVIEW_COUNT, publicProfileMode, driverReviewCount,
        automaticSnapshot?.reviewCount?.toString().orEmpty(), publicProfileOverrideFields,
        { driverReviewCount = it.filter(Char::isDigit).take(7) }, { publicProfileOverrideFields = it }, { it.filter(Char::isDigit).take(7) })
    PublicProfileTextField("Selo ou destaque", PublicDriverProfileFields.BADGE, publicProfileMode, driverBadge,
        automaticSnapshot?.badge.orEmpty(), publicProfileOverrideFields, { driverBadge = it.take(80) }, { publicProfileOverrideFields = it }, { it.take(80) })
    PublicProfileTextField("Veículo — marca/modelo", PublicDriverProfileFields.VEHICLE, publicProfileMode, vehicleMakeModel,
        automaticSnapshot?.vehicleMakeModel.orEmpty(), publicProfileOverrideFields, { vehicleMakeModel = it.take(120) }, { publicProfileOverrideFields = it }, { it.take(120) })
    PublicProfileTextField("Cor do veículo", PublicDriverProfileFields.VEHICLE_COLOR, publicProfileMode, vehicleColor,
        automaticSnapshot?.vehicleColor.orEmpty(), publicProfileOverrideFields, { vehicleColor = it.take(60) }, { publicProfileOverrideFields = it }, { it.take(60) })
    PublicProfileTextField("Comodidades", PublicDriverProfileFields.AMENITIES, publicProfileMode, vehicleAmenities,
        automaticSnapshot?.amenities.orEmpty(), publicProfileOverrideFields, { vehicleAmenities = it.take(240) }, { publicProfileOverrideFields = it }, { it.take(240) })
    PublicProfileTextField("Preferências", PublicDriverProfileFields.PREFERENCES, publicProfileMode, driverPreferences,
        automaticSnapshot?.preferences.orEmpty(), publicProfileOverrideFields, { driverPreferences = it.take(240) }, { publicProfileOverrideFields = it }, { it.take(240) })

    HorizontalDivider()
    Text("Dados próprios do Rota Certa", style = MaterialTheme.typography.titleMedium)
    Text("WhatsApp e pagamento nunca são sobrescritos pela sincronização BlaBlaCar.")
    OutlinedTextField(
        driverWhatsapp,
        { driverWhatsapp = it.filter { ch -> ch.isDigit() || ch == '+' || ch == '(' || ch == ')' || ch == '-' || ch == ' ' }.take(24) },
        label = { Text("WhatsApp do motorista — opcional") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(paymentInstructions, { paymentInstructions = it.take(240) }, label = { Text("Pagamento") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(googleCalendarUrl, { googleCalendarUrl = it.trim() }, label = { Text("Link público do Google Agenda — opcional") }, modifier = Modifier.fillMaxWidth())

    HorizontalDivider()
    Text("Credenciais e link público", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(token, { token = it }, label = { Text("Chave privada do motorista") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        calendarToken,
        { calendarToken = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
        label = { Text("Token público da agenda de viagens — fixo") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        enabled = token.isBlank(),
    )
    Text("O token não muda ao salvar dados. A troca só acontece pela ação separada Gerar novo link.")

    registrationMessage?.let { Text(it) }
    if (token.isBlank()) {
        Button(
            enabled = api.startsWith("https://") && publicBase.startsWith("https://") && driverName.isNotBlank(),
            onClick = {
                val normalizedUsername = DriverIdentityRules.normalizeUsername(driverUsername.ifBlank { driverName })
                if (!DriverIdentityRules.isValidUsername(normalizedUsername)) {
                    registrationMessage = "Escolha um nome de usuário com pelo menos 3 caracteres."
                } else {
                    driverUsername = normalizedUsername
                    registrationScope.launch {
                        val candidate = buildSettings().copy(driverUsername = normalizedUsername)
                        runCatching { TripRemoteApi(candidate).registerDriver(driverName.trim(), normalizedUsername) }
                            .onSuccess { response ->
                                driverName = response.displayName
                                driverUsername = response.username
                                token = response.driverToken
                                calendarToken = response.publicAgendaToken
                                registrationMessage = "Link exclusivo gerado: ${response.publicAgendaUrl}"
                            }
                            .onFailure { registrationMessage = "Não foi possível gerar o link: ${it.message}" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Gerar meu link exclusivo") }
    } else {
        val currentSettings = buildSettings()
        currentSettings.publicAgendaUrl?.let { agendaUrl ->
            Text("Seu link da agenda", style = MaterialTheme.typography.titleMedium)
            Text(agendaUrl)
            Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(agendaUrl))) }
                    .onSuccess { linkActionMessage = "Abrindo sua agenda…" }
                    .onFailure { linkActionMessage = "Não foi possível abrir o link neste aparelho." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Abrir agenda") }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Link da Agenda Rota Certa", agendaUrl))
                linkActionMessage = if (clipboard != null) "Link copiado." else "Não foi possível copiar o link."
            }, modifier = Modifier.fillMaxWidth()) { Text("Copiar link") }
            linkActionMessage?.let { Text(it) }

            OutlinedButton(
                enabled = !linkRotationInFlight,
                onClick = { confirmRegenerateLink = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gerar novo link") }

            if (confirmRegenerateLink) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Atenção", style = MaterialTheme.typography.titleMedium)
                        Text("Gerar um novo link invalida imediatamente o link atual. Faça isso somente se realmente quiser substituir o endereço da agenda.")
                        Button(
                            enabled = !linkRotationInFlight,
                            onClick = {
                                registrationScope.launch {
                                    linkRotationInFlight = true
                                    val expectedCurrent = currentSettings.publicCalendarToken
                                    val rotationId = UUID.randomUUID().toString()
                                    runCatching { TripRemoteApi(currentSettings).regeneratePublicAgenda(expectedCurrent, rotationId) }
                                        .onSuccess { response ->
                                            if (onRotateLink(expectedCurrent, response.publicAgendaToken)) {
                                                calendarToken = response.publicAgendaToken
                                                confirmRegenerateLink = false
                                                onSave(currentSettings.copy(
                                                    publicCalendarToken = response.publicAgendaToken,
                                                    driverDisplayName = response.displayName.ifBlank { currentSettings.driverDisplayName },
                                                    driverUsername = response.username.ifBlank { currentSettings.driverUsername },
                                                ))
                                            } else {
                                                linkActionMessage = "Conflito local detectado. Reabra esta tela antes de tentar novamente."
                                            }
                                        }
                                        .onFailure { linkActionMessage = "Não foi possível gerar um novo link: ${it.message ?: "erro de conexão"}" }
                                    linkRotationInFlight = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Confirmar novo link") }
                        TextButton(enabled = !linkRotationInFlight, onClick = { confirmRegenerateLink = false }) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    if (!profileSelectionValid) Text("Antes de salvar, confirme o UUID do perfil BlaBlaCar selecionado.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = profileSelectionValid && !linkRotationInFlight, onClick = { onSave(buildSettings()) }) { Text("Salvar") }
        TextButton(enabled = !linkRotationInFlight, onClick = onCancel) { Text("Voltar") }
    }
}
