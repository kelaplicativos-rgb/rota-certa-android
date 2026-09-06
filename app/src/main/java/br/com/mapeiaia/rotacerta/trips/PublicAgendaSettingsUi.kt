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
import androidx.compose.runtime.LaunchedEffect
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
    val initialResolvedProfile = remember(context, initial) { PublicDriverProfileResolver(context).resolve(initial) }
    val migrateLegacyHybrid = initial.publicProfileMode == PublicDriverProfileMode.HYBRID
    var linkedProfiles by remember { mutableStateOf(registry.list()) }
    var profileRefreshRevision by remember { mutableStateOf(0) }
    var profileSyncMessage by remember { mutableStateOf<String?>(null) }

    var api by remember { mutableStateOf(initial.apiBaseUrl) }
    var publicBase by remember { mutableStateOf(initial.publicBaseUrl) }
    var token by remember { mutableStateOf(initial.driverToken) }
    var calendarToken by remember { mutableStateOf(initial.publicCalendarToken) }
    var driverName by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.displayName else initial.driverDisplayName) }
    var driverUsername by remember { mutableStateOf(initial.driverUsername) }
    var driverWhatsapp by remember { mutableStateOf(initial.driverWhatsapp) }
    var driverPhotoUrl by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.photoUrl else initial.driverPhotoUrl) }
    var driverAbout by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.about else initial.driverPublicAbout) }
    var driverRating by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.rating else initial.driverPublicRating) }
    var driverReviewCount by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.reviewCount?.toString().orEmpty() else initial.driverPublicReviewCount.toString()) }
    var driverBadge by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.badge else initial.driverPublicBadge) }
    var vehicleMakeModel by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.vehicleMakeModel else initial.vehicleMakeModel) }
    var vehicleColor by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.vehicleColor else initial.vehicleColor) }
    var vehicleAmenities by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.amenities else initial.vehicleAmenities) }
    var driverPreferences by remember { mutableStateOf(if (migrateLegacyHybrid) initialResolvedProfile.preferences else initial.driverPreferences) }
    var paymentInstructions by remember { mutableStateOf(initial.paymentInstructions) }
    var googleCalendarUrl by remember { mutableStateOf(initial.googleCalendarPublicUrl) }
    var publicProfileMode by remember { mutableStateOf(if (initial.publicProfileMode == PublicDriverProfileMode.BLABLACAR) PublicDriverProfileMode.BLABLACAR else PublicDriverProfileMode.MANUAL) }
    var selectedPublicProfileAccountId by remember { mutableStateOf(initial.selectedPublicProfileAccountId) }
    var publicProfileOverrideFields by remember { mutableStateOf(emptySet<String>()) }
    var registrationMessage by remember { mutableStateOf<String?>(null) }
    var linkActionMessage by remember { mutableStateOf<String?>(null) }
    var testerLinkStatus by remember { mutableStateOf<DriverTesterLinkResponse?>(null) }
    var testerLinkUrl by remember { mutableStateOf("") }
    var testerLinkMessage by remember { mutableStateOf<String?>(null) }
    var testerLinkInFlight by remember { mutableStateOf(false) }
    var confirmRegenerateLink by remember { mutableStateOf(false) }
    var linkRotationInFlight by remember { mutableStateOf(false) }
    var usernameChangeInFlight by remember { mutableStateOf(false) }
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
        val reviews = result.data?.getIntExtra("public_profile_review_count", 0) ?: 0
        profileSyncMessage = if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (reviews > 0) {
                "Dados do motorista atualizados • $reviews avaliação(ões) detalhada(s) capturada(s)."
            } else {
                "Dados do motorista atualizados. Nenhuma avaliação detalhada nova foi encontrada nesta leitura."
            }
        } else {
            "Leitura do perfil encerrada sem substituir os últimos dados confirmados."
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
        publicProfileOverrideFields = emptySet(),
    )

    LaunchedEffect(api, token, driverUsername) {
        if (token.isBlank() || !api.startsWith("https://") || driverUsername.isBlank()) {
            testerLinkStatus = null
            testerLinkUrl = ""
            return@LaunchedEffect
        }
        testerLinkInFlight = true
        runCatching { TripRemoteApi(buildSettings()).testerLinkStatus() }
            .onSuccess { testerLinkStatus = it }
            .onFailure { testerLinkMessage = "Não foi possível consultar o link de teste: ${it.message ?: "erro de conexão"}" }
        testerLinkInFlight = false
    }

    Text("Integração online", style = MaterialTheme.typography.titleLarge)
    Text("O link da agenda pertence ao motorista e permanece o mesmo ao alterar dados ou perfil.")
    OutlinedTextField(api, { api = it.trim() }, label = { Text("API HTTPS") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(publicBase, { publicBase = it.trim() }, label = { Text("URL pública") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        driverUsername,
        { driverUsername = DriverIdentityRules.normalizeUsername(it) },
        label = { Text("Nome de usuário no link") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !linkRotationInFlight && !usernameChangeInFlight,
    )
    val normalizedLinkUsername = DriverIdentityRules.normalizeUsername(driverUsername)
    val shortLinkPreview = publicBase
        .takeIf { it.startsWith("https://") }
        ?.trimEnd('/')
        ?.let { base ->
            normalizedLinkUsername
                .takeIf(DriverIdentityRules::isValidPublicUsername)
                ?.let { "$base/$it" }
        }
    Text("Escolha o nome que quiser para o endereço curto e altere quando precisar. Espaços e acentos são convertidos automaticamente para um link simples. O nome precisa estar disponível; somente endereços técnicos do Rota Certa não podem ser usados.", style = MaterialTheme.typography.bodySmall)
    shortLinkPreview?.let { Text("Seu endereço: $it", style = MaterialTheme.typography.bodySmall) }
    if (DriverIdentityRules.isReservedUsername(normalizedLinkUsername)) {
        Text("Esse endereço é usado internamente pelo Rota Certa. Escolha outro.", style = MaterialTheme.typography.bodySmall)
    }

    HorizontalDivider()
    Text("Dados públicos do motorista", style = MaterialTheme.typography.titleMedium)
    Text("Como o motorista aparecerá na Agenda Pública", style = MaterialTheme.typography.titleSmall)
    Text("Escolha uma única identidade pública. Isso não filtra as viagens: todos os cards de todas as contas BlaBlaCar conectadas entram automaticamente na Timeline e na Agenda Pública.")
    if (publicProfileMode == PublicDriverProfileMode.BLABLACAR) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("✓ Usar um perfil da BlaBlaCar") }
    } else {
        OutlinedButton(onClick = { publicProfileMode = PublicDriverProfileMode.BLABLACAR }, modifier = Modifier.fillMaxWidth()) {
            Text("Usar um perfil da BlaBlaCar")
        }
    }
    if (publicProfileMode == PublicDriverProfileMode.MANUAL) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("✓ Criar meu perfil personalizado") }
    } else {
        OutlinedButton(onClick = { publicProfileMode = PublicDriverProfileMode.MANUAL }, modifier = Modifier.fillMaxWidth()) {
            Text("Criar meu perfil personalizado")
        }
    }

    if (publicProfileMode != PublicDriverProfileMode.MANUAL) {
        Text("Perfil BlaBlaCar usado nos dados do motorista", style = MaterialTheme.typography.titleSmall)
        if (linkedProfiles.isEmpty()) {
            Text("Nenhum perfil BlaBlaCar vinculado neste aparelho.")
        } else {
            linkedProfiles.forEach { profile ->
                OutlinedButton(
                    onClick = { selectedPublicProfileAccountId = profile.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val label = BlaBlaDriverProfileNamePolicy.normalize(profile.profileName)
                        ?: profile.displayLabel
                    val suffix = if (profile.profileUuid.isNullOrBlank()) " • UUID pendente" else ""
                    Text(if (profile.id == selectedPublicProfileAccountId) "✓ $label$suffix" else "$label$suffix")
                }
            }
        }
        selectedProfile?.let { profile ->
            Button(
                enabled = !linkRotationInFlight,
                onClick = {
                    profileSyncMessage = "Buscando foto, nota, avaliações e demais dados de ${profile.displayLabel}…"
                    profileSyncLauncher.launch(BlaBlaDynamicSessionIntents.profile(context, profile))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Atualizar dados deste perfil") }
        }
        profileSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Esta escolha serve somente para foto, nome, nota, avaliações, selo e outros dados do motorista. As viagens não dependem desta seleção.",
            style = MaterialTheme.typography.bodySmall,
        )
        when {
            selectedProfile == null -> Text("Selecione o perfil que será exibido.")
            selectedProfile.profileUuid.isNullOrBlank() -> Text("Sincronize para confirmar o UUID antes de publicar.")
            automaticSnapshot != null -> {
                val whenText = Instant.ofEpochMilli(automaticSnapshot.lastSyncedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                Text("✓ Identidade confirmada • última sincronização $whenText")
                if (automaticSnapshot.reviews.isNotEmpty()) {
                    Text("${automaticSnapshot.reviews.size} avaliação(ões) detalhada(s) disponível(is) para a Agenda Pública.")
                }
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

    HorizontalDivider()
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
    Text("O token não muda ao salvar dados. A credencial interna não aparece no endereço curto. A ação de troca abaixo serve somente para revogar intencionalmente links técnicos antigos.")

    registrationMessage?.let { Text(it) }
    if (token.isBlank()) {
        Button(
            enabled = api.startsWith("https://") && publicBase.startsWith("https://") && driverName.isNotBlank(),
            onClick = {
                val normalizedUsername = DriverIdentityRules.normalizeUsername(driverUsername.ifBlank { driverName })
                if (!DriverIdentityRules.isValidPublicUsername(normalizedUsername)) {
                    registrationMessage = if (DriverIdentityRules.isReservedUsername(normalizedUsername)) {
                        "Esse endereço é usado internamente pelo Rota Certa. Escolha outro."
                    } else {
                        "Escolha um nome de usuário com pelo menos 3 caracteres."
                    }
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
            OutlinedButton(onClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, agendaUrl)
                }
                runCatching { context.startActivity(Intent.createChooser(shareIntent, "Compartilhar link da Agenda")) }
                    .onSuccess { linkActionMessage = "Abrindo opções de compartilhamento…" }
                    .onFailure { linkActionMessage = "Não foi possível compartilhar o link neste aparelho." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Compartilhar link") }
            linkActionMessage?.let { Text(it) }

            OutlinedButton(
                enabled = !linkRotationInFlight,
                onClick = { confirmRegenerateLink = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Trocar credencial interna") }

            if (confirmRegenerateLink) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Atenção", style = MaterialTheme.typography.titleMedium)
                        Text("O endereço curto continuará o mesmo. Esta ação troca a credencial interna e invalida links técnicos antigos que contenham ?agenda=... Use somente para revogação intencional.")
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
                                        .onFailure { linkActionMessage = "Não foi possível trocar a credencial interna: ${it.message ?: "erro de conexão"}" }
                                    linkRotationInFlight = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Confirmar troca de credencial") }
                        TextButton(enabled = !linkRotationInFlight, onClick = { confirmRegenerateLink = false }) { Text("Cancelar") }
                    }
                }
            }
        }

        HorizontalDivider()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🧪 Link exclusivo de teste", style = MaterialTheme.typography.titleMedium)
                Text("Abre a Agenda sem WhatsApp ou senha em uma sessão TESTER isolada. Reservas e cancelamentos desse link usam somente estado sombra e não alteram a operação real.")
                val activeTesterLink = testerLinkStatus?.active == true
                val testerExpiry = testerLinkStatus?.expiresAtMillis?.takeIf { it > 0L }?.let { millis ->
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(millis))
                }
                Text(
                    when {
                        testerLinkInFlight && testerLinkStatus == null -> "Consultando estado do link…"
                        activeTesterLink && testerExpiry != null -> "Link ativo • expira em $testerExpiry"
                        activeTesterLink -> "Link ativo"
                        (testerLinkStatus?.revokedAtMillis ?: 0L) > 0L -> "Link revogado"
                        else -> "Nenhum link de teste ativo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                testerLinkUrl.takeIf(String::isNotBlank)?.let { testUrl ->
                    Text(testUrl, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        enabled = !testerLinkInFlight,
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Link de teste Rota Certa", testUrl))
                            testerLinkMessage = if (clipboard != null) "Link de teste copiado." else "Não foi possível copiar o link de teste."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copiar link de teste") }
                    OutlinedButton(
                        enabled = !testerLinkInFlight,
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, testUrl)
                            }
                            runCatching { context.startActivity(Intent.createChooser(shareIntent, "Compartilhar link de teste")) }
                                .onSuccess { testerLinkMessage = "Abrindo opções de compartilhamento…" }
                                .onFailure { testerLinkMessage = "Não foi possível compartilhar o link de teste neste aparelho." }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Compartilhar link de teste") }
                }
                if (activeTesterLink && testerLinkUrl.isBlank()) {
                    Text("Por segurança, o segredo do link não é recuperado do servidor depois da geração. Gere um novo link para obter uma nova URL copiável; o link anterior será invalidado.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = !testerLinkInFlight,
                    onClick = {
                        registrationScope.launch {
                            testerLinkInFlight = true
                            runCatching { TripRemoteApi(buildSettings()).generateTesterLink() }
                                .onSuccess { response ->
                                    testerLinkStatus = response
                                    testerLinkUrl = response.testUrl
                                    testerLinkMessage = if (activeTesterLink) "Novo link gerado. O link e as sessões anteriores foram invalidados." else "Link de teste gerado com sucesso."
                                }
                                .onFailure { testerLinkMessage = "Não foi possível gerar o link de teste: ${it.message ?: "erro de conexão"}" }
                            testerLinkInFlight = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (activeTesterLink) "Gerar novo link de teste" else "Gerar link de teste") }
                if (activeTesterLink) {
                    OutlinedButton(
                        enabled = !testerLinkInFlight,
                        onClick = {
                            registrationScope.launch {
                                testerLinkInFlight = true
                                runCatching { TripRemoteApi(buildSettings()).revokeTesterLink() }
                                    .onSuccess { response ->
                                        testerLinkStatus = response
                                        testerLinkUrl = ""
                                        testerLinkMessage = "Link de teste revogado. Sessões TESTER associadas também deixam de ser válidas."
                                    }
                                    .onFailure { testerLinkMessage = "Não foi possível revogar o link de teste: ${it.message ?: "erro de conexão"}" }
                                testerLinkInFlight = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Revogar link de teste") }
                }
                testerLinkMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (!profileSelectionValid) Text("Antes de salvar, confirme o UUID do perfil BlaBlaCar selecionado.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = profileSelectionValid && !linkRotationInFlight && !usernameChangeInFlight,
            onClick = {
                val candidate = buildSettings()
                val normalizedUsername = candidate.driverUsername
                when {
                    !DriverIdentityRules.isValidPublicUsername(normalizedUsername) -> {
                        registrationMessage = if (DriverIdentityRules.isReservedUsername(normalizedUsername)) {
                            "Esse endereço é usado internamente pelo Rota Certa. Escolha outro."
                        } else {
                            "Escolha um nome de usuário com pelo menos 3 caracteres."
                        }
                    }
                    token.isNotBlank() && normalizedUsername != initial.driverUsername -> {
                        registrationScope.launch {
                            usernameChangeInFlight = true
                            val requestId = UUID.randomUUID().toString()
                            val authSettings = candidate.copy(driverUsername = initial.driverUsername)
                            runCatching {
                                TripRemoteApi(authSettings).changeDriverUsername(
                                    username = normalizedUsername,
                                    currentPublicAgendaToken = candidate.publicCalendarToken,
                                    requestId = requestId,
                                )
                            }.onSuccess { response ->
                                driverUsername = response.username
                                linkActionMessage = "Identificador atualizado sem alterar o token público. Endereços anteriores permanecem como aliases."
                                onSave(candidate.copy(driverUsername = response.username))
                            }.onFailure {
                                linkActionMessage = "Não foi possível alterar o identificador: ${it.message ?: "erro de conexão"}"
                            }
                            usernameChangeInFlight = false
                        }
                    }
                    else -> onSave(candidate)
                }
            },
        ) { Text(if (usernameChangeInFlight) "Salvando…" else "Salvar") }
        TextButton(enabled = !linkRotationInFlight && !usernameChangeInFlight, onClick = onCancel) { Text("Voltar") }
    }
}


@Composable
internal fun AgendaAppSettingsScreen0416(
    initial: TripOnlineSettings,
    onSave: (TripOnlineSettings) -> Unit,
) {
    val context = LocalContext.current
    val referenceStore = remember(context) { TripReferenceOriginStore(context) }
    var referenceOrigin by remember { mutableStateOf(referenceStore.read()) }
    var message by remember { mutableStateOf<String?>(null) }
    var vehicleMakeModel by remember(initial.vehicleMakeModel) { mutableStateOf(initial.vehicleMakeModel) }
    var vehicleColor by remember(initial.vehicleColor) { mutableStateOf(initial.vehicleColor) }
    var vehicleAmenities by remember(initial.vehicleAmenities) { mutableStateOf(initial.vehicleAmenities) }

    Text("Configurações", style = MaterialTheme.typography.titleLarge)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Dados do veículo", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = vehicleMakeModel,
                onValueChange = { vehicleMakeModel = it.take(120) },
                label = { Text("Marca/modelo") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vehicleColor,
                onValueChange = { vehicleColor = it.take(60) },
                label = { Text("Cor") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vehicleAmenities,
                onValueChange = { vehicleAmenities = it.take(240) },
                label = { Text("Comodidades") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            vehicleMakeModel = vehicleMakeModel.trim(),
                            vehicleColor = vehicleColor.trim(),
                            vehicleAmenities = vehicleAmenities.trim(),
                            publicProfileOverrideFields = initial.publicProfileOverrideFields +
                                setOf(
                                    PublicDriverProfileFields.VEHICLE,
                                    PublicDriverProfileFields.VEHICLE_COLOR,
                                    PublicDriverProfileFields.AMENITIES,
                                ),
                        ),
                    )
                    message = "Dados do veículo salvos."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar dados do veículo")
            }
        }
    }

    TripReferenceOriginSettingsCard0416(
        referenceOrigin = referenceOrigin,
        onReferenceChanged = { referenceOrigin = it },
        onChanged = { message = it },
    )

    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
