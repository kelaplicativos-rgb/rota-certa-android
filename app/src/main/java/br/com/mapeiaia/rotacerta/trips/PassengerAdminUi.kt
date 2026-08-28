package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import br.com.mapeiaia.rotacerta.R
import java.math.RoundingMode
import kotlinx.coroutines.launch

internal data class PassengerAdminCandidate(
    val key: String,
    val displayName: String,
    val whatsapp: String,
    val localProfile: PassengerProfile? = null,
    val remoteAccess: DriverPassengerAccess? = null,
    val externalPassengerId: String = "",
    val source: String = "",
    val lastActivityMillis: Long = 0L,
)

@Composable
fun PassengerAdminScreen(
    store: TripStore,
    onBack: () -> Unit,
    onChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val passengerStore = remember(context) { PassengerIdentityStore(context) }
    val passengerRepository = remember(context) { PassengerRepository(context) }
    val collectorStore = remember(context) { BlaBlaCollectorStateStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var remotePassengers by remember { mutableStateOf<List<DriverPassengerAccess>>(emptyList()) }
    var referralCreditCents by remember { mutableStateOf(0L) }
    var search by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newWhatsapp by remember { mutableStateOf("") }
    var selectedNewPassengerId by remember { mutableStateOf("") }
    var creditValue by remember { mutableStateOf("") }
    var temporaryPassword by remember { mutableStateOf<String?>(null) }
    var temporaryPasswordFor by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var historyProfileId by remember { mutableStateOf<String?>(null) }
    var blockCandidate by remember { mutableStateOf<PassengerAdminCandidate?>(null) }
    val settings = store.onlineSettings()

    val localProfiles = remember(revision, remotePassengers) { passengerStore.profiles() }
    val collectedTrips = remember(revision) {
        collectorStore.lastResponseRecoveringDynamicSessions()?.trips.orEmpty()
    }
    val collectedPassengers = remember(collectedTrips) {
        collectedTrips.flatMap { trip -> trip.passengers }.filter { it.name.isNotBlank() }
    }
    val collectedIdentityKey = remember(collectedTrips) {
        collectedTrips.flatMap { trip ->
            trip.passengers.map { passenger ->
                listOf(trip.profile_uuid, trip.trip_id.orEmpty(), passenger.booking_href.orEmpty(), passenger.name, passenger.phone.orEmpty()).joinToString("~")
            }
        }.joinToString("|")
    }
    LaunchedEffect(collectedIdentityKey) {
        var changed = false
        collectedTrips.forEach { trip ->
            trip.passengers.forEach { passenger ->
                val externalId = stableExternalPassengerId(BlaBlaCollectorUrlModule.passengerIdentityKey(passenger.booking_href))
                val observed = passengerStore.observeExternalPassenger(
                    displayName = passenger.name,
                    whatsapp = passenger.phone,
                    externalPassengerId = externalId,
                    reservationKey = externalPassengerReservationKey(trip.profile_uuid, passenger.booking_href),
                    externalTripId = trip.trip_id,
                    driverProfileUuid = trip.profile_uuid,
                )
                if (observed != null) changed = true
            }
        }
        if (changed) revision++
    }
    val canonicalSearchIds = remember(search, revision) {
        if (search.isBlank()) emptySet() else passengerRepository.search(search, 50).map(PassengerProfile::id).toSet()
    }
    val candidates = remember(localProfiles, collectedPassengers, remotePassengers, search, canonicalSearchIds) {
        mergePassengerAdminCandidates(localProfiles, collectedPassengers, remotePassengers)
            .filter { candidate ->
                val needle = search.trim()
                needle.isBlank() ||
                    candidate.localProfile?.id in canonicalSearchIds ||
                    candidate.displayName.contains(needle, ignoreCase = true) ||
                    candidate.whatsapp.contains(needle, ignoreCase = true)
            }
    }

    val newPassengerSuggestions = remember(newName, newWhatsapp, revision) {
        val query = newWhatsapp.takeIf { it.filter(Char::isDigit).length >= 4 } ?: newName
        passengerRepository.search(query, 6)
    }

    suspend fun reloadRemote() {
        if (!settings.configured) return
        runCatching { TripRemoteApi(settings).listDriverPassengers() }
            .onSuccess { response ->
                response.passengers.forEach { access ->
                    val current = passengerStore.resolveCanonicalPassenger(
                        onlineIdentityId = access.id,
                        whatsapp = access.passengerContact,
                    ) ?: access.displayName.trim().takeIf(String::isNotEmpty)?.let { name ->
                        passengerStore.createProfile(name, access.passengerContact)
                    }
                    if (current != null) {
                        val refreshed = passengerStore.saveProfile(
                            current.copy(
                                displayName = access.displayName.trim().ifBlank { current.displayName },
                                whatsapp = access.passengerContact.trim().ifBlank { current.whatsapp },
                                publicAccessStatus = access.status,
                                referredByContact = access.referredByContact,
                                creditBalanceCents = access.creditBalanceCents,
                                creditEarnedCents = access.creditEarnedCents,
                                creditSpentCents = access.creditSpentCents,
                            ),
                        )
                        stableExternalPassengerId(access.id)?.let { passengerStore.linkOnlineIdentityId(refreshed.id, it) }
                    }
                }
                remotePassengers = response.passengers
                referralCreditCents = response.referralCreditCents
                creditValue = formatCreditInput(response.referralCreditCents)
            }
            .onFailure { onChanged("Não foi possível carregar acessos dos passageiros: ${it.message ?: "erro de conexão"}") }
    }

    LaunchedEffect(settings.driverUsername, settings.driverToken, revision) {
        reloadRemote()
    }

    val selectedHistory = historyProfileId?.let(passengerStore::persistentHistory)
    if (historyProfileId != null) {
        PassengerHistoryPanel(
            history = selectedHistory,
            onBack = { historyProfileId = null },
            onArchiveToggle = { profile ->
                passengerStore.setArchived(profile.id, !profile.archived)
                revision++
                onChanged(
                    if (profile.archived) "Passageiro restaurado na lista; histórico preservado."
                    else "Passageiro arquivado da lista; histórico, UUIDs, bloqueios e viagens foram preservados.",
                )
                historyProfileId = null
            },
        )
        return
    }

    fun canonicalProfile(candidate: PassengerAdminCandidate): PassengerProfile? {
        val resolved = passengerStore.resolveCanonicalPassenger(
            passengerId = candidate.localProfile?.id,
            externalPassengerId = candidate.externalPassengerId,
            onlineIdentityId = candidate.remoteAccess?.id,
            whatsapp = candidate.whatsapp,
        ) ?: candidate.displayName.trim().takeIf(String::isNotEmpty)?.let { name ->
            passengerStore.createProfile(name, candidate.whatsapp)
        } ?: return null
        val restored = if (resolved.archived) passengerStore.saveProfile(resolved.copy(archived = false)) else resolved
        stableExternalPassengerId(candidate.externalPassengerId)?.let { passengerStore.linkExternalPassengerId(restored.id, it) }
        stableExternalPassengerId(candidate.remoteAccess?.id)?.let { passengerStore.linkOnlineIdentityId(restored.id, it) }
        return passengerStore.profile(restored.id) ?: restored
    }

    fun openCandidateHistory(candidate: PassengerAdminCandidate) {
        val profile = canonicalProfile(candidate)
        if (profile == null) onChanged("Não foi possível criar a identidade canônica deste passageiro.")
        else historyProfileId = profile.id
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("👥 Passageiros", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
    Text(
        "Administre quem pode entrar na Agenda Pública, senhas temporárias, indicações e créditos.",
        style = MaterialTheme.typography.bodySmall,
    )

    if (!settings.configured) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Configure a Integração online antes de liberar acessos. Os cadastros locais continuam disponíveis.",
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Programa de indicações", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = creditValue,
                onValueChange = { creditValue = it.take(16) },
                label = { Text("Crédito por indicação concluída (R$)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("Valor atual: ${formatCreditMoney(referralCreditCents)}", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = settings.configured && !loading,
                onClick = {
                    val cents = parseCreditInput(creditValue)
                    if (cents == null) {
                        onChanged("Informe um valor de crédito válido.")
                    } else {
                        loading = true
                        scope.launch {
                            runCatching { TripRemoteApi(settings).updateReferralCredit(cents) }
                                .onSuccess {
                                    referralCreditCents = it.referralCreditCents
                                    creditValue = formatCreditInput(it.referralCreditCents)
                                    onChanged("Crédito por indicação atualizado para ${formatCreditMoney(it.referralCreditCents)}.")
                                }
                                .onFailure { onChanged("Não foi possível atualizar o crédito: ${it.message ?: "erro de conexão"}") }
                            loading = false
                        }
                    }
                },
            ) { Text("Salvar valor dos créditos") }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Cadastrar convidado", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it.take(120)
                    selectedNewPassengerId = ""
                },
                label = { Text("Nome") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newWhatsapp,
                onValueChange = {
                    newWhatsapp = it.take(40)
                    selectedNewPassengerId = ""
                },
                label = { Text("WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (newPassengerSuggestions.isNotEmpty() && selectedNewPassengerId.isBlank()) {
                Text("Passageiros já cadastrados", style = MaterialTheme.typography.bodySmall)
                newPassengerSuggestions.forEach { existing ->
                    OutlinedButton(
                        onClick = {
                            selectedNewPassengerId = existing.id
                            newName = existing.displayName
                            newWhatsapp = existing.whatsapp
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Usar ${existing.displayName}${existing.whatsapp.takeIf(String::isNotBlank)?.let { " • ${maskPassengerAdminContact(it)}" }.orEmpty()}")
                    }
                }
            }
            if (selectedNewPassengerId.isNotBlank()) {
                Text("✓ Passageiro canônico selecionado", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                val name = newName.trim()
                val phone = newWhatsapp.trim()
                if (name.isBlank() || passengerAdminContactKey(phone).isBlank()) {
                    onChanged("Informe nome e WhatsApp do convidado.")
                } else {
                    val selected = selectedNewPassengerId.takeIf(String::isNotBlank)?.let(passengerStore::profile)
                    val exactMatches = passengerStore.exactContactMatches(phone)
                    val target = selected ?: exactMatches.singleOrNull()
                    if (selected == null && exactMatches.size > 1) {
                        onChanged("Há mais de um cadastro com esse WhatsApp. Selecione manualmente o passageiro correto; nenhum foi unido automaticamente.")
                    } else {
                        if (target == null) passengerStore.createProfile(name, phone)
                        else passengerStore.saveProfile(target.copy(displayName = name, whatsapp = phone, archived = false))
                        newName = ""
                        newWhatsapp = ""
                        selectedNewPassengerId = ""
                        revision++
                        onChanged(if (target == null) "Novo passageiro cadastrado. Agora você pode liberar o acesso." else "Cadastro canônico reutilizado. Agora você pode liberar o acesso.")
                    }
                }
            }) { Text(if (selectedNewPassengerId.isBlank()) "Cadastrar novo" else "Usar cadastro selecionado") }
        }
    }

    temporaryPassword?.let { password ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Senha temporária gerada", style = MaterialTheme.typography.titleMedium)
                Text(temporaryPasswordFor)
                Text(password, style = MaterialTheme.typography.headlineMedium)
                Text("Envie esta senha ao passageiro. No portal ele poderá trocar por uma senha própria.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(password))
                        onChanged("Senha temporária copiada.")
                    }) { Text("Copiar senha") }
                    OutlinedButton(onClick = {
                        temporaryPassword = null
                        temporaryPasswordFor = ""
                    }) { Text("Fechar") }
                }
            }
        }
    }

    OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        label = { Text("Buscar por nome ou WhatsApp") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    if (candidates.isEmpty()) {
        Text("Nenhum passageiro encontrado.")
    }

    candidates.forEach { candidate ->
        val access = candidate.remoteAccess
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val localProfile = candidate.localProfile
                Text(
                    (if (localProfile?.blocked == true) "🚫 " else "") + candidate.displayName.ifBlank { "Passageiro" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(candidate.whatsapp.ifBlank { "WhatsApp não informado" })
                localProfile?.let { profile ->
                    val durableHistory = passengerStore.persistentHistory(profile.id)
                    Text(
                        "${durableHistory?.totalRides ?: passengerStore.rideHistory(profile.id).totalRides} viagem(ns) • ${profile.externalPassengerIds.size} UUID(s) externo(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (profile.blocked) Text("🚫 Persona non grata no seu carro", color = MaterialTheme.colorScheme.error)
                }
                Text(passengerAccessLabel(access), style = MaterialTheme.typography.bodySmall)
                if (candidate.source.isNotBlank()) Text(candidate.source, style = MaterialTheme.typography.bodySmall)
                access?.referredByContact?.takeIf(String::isNotBlank)?.let {
                    Text("Indicado por: ${maskPassengerAdminContact(it)}", style = MaterialTheme.typography.bodySmall)
                }
                if (access != null) {
                    Text(
                        "Créditos: ${formatCreditMoney(access.creditBalanceCents)} • ganhos ${formatCreditMoney(access.creditEarnedCents)} • usados ${formatCreditMoney(access.creditSpentCents)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
                if (candidate.localProfile != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { historyProfileId = candidate.localProfile.id }) { Text("Histórico") }
                        OutlinedButton(onClick = {
                            val current = candidate.localProfile
                            passengerStore.setBlocked(
                                current.id,
                                !current.blocked,
                                if (!current.blocked) "Persona non grata marcada pelo motorista" else "",
                            )
                            revision++
                            onChanged(if (current.blocked) "Passageiro liberado para o carro." else "🚫 Passageiro marcado como persona non grata.")
                        }) { Text(if (candidate.localProfile.blocked) "Liberar no carro" else "🚫 Bloquear no carro") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (access?.status) {
                        "ACTIVE" -> {
                            OutlinedButton(
                                enabled = settings.configured && !loading,
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        runCatching { TripRemoteApi(settings).setPassengerAccessBlocked(candidate.whatsapp, true) }
                                            .onSuccess {
                                                revision++
                                                onChanged("Acesso de ${candidate.displayName} bloqueado.")
                                            }
                                            .onFailure { onChanged("Falha ao bloquear acesso: ${it.message ?: "erro de conexão"}") }
                                        loading = false
                                    }
                                },
                            ) { Text("Bloquear acesso") }
                            OutlinedButton(
                                enabled = settings.configured && !loading,
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        runCatching { TripRemoteApi(settings).resetPassengerPassword(candidate.whatsapp) }
                                            .onSuccess {
                                                temporaryPassword = it.temporaryPassword
                                                temporaryPasswordFor = candidate.displayName
                                                onChanged("Nova senha temporária gerada.")
                                            }
                                            .onFailure { onChanged("Falha ao redefinir senha: ${it.message ?: "erro de conexão"}") }
                                        loading = false
                                    }
                                },
                            ) { Text("Nova senha") }
                        }
                        "BLOCKED" -> {
                            Button(
                                enabled = settings.configured && !loading,
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        runCatching { TripRemoteApi(settings).setPassengerAccessBlocked(candidate.whatsapp, false) }
                                            .onSuccess {
                                                revision++
                                                onChanged("Acesso de ${candidate.displayName} liberado novamente.")
                                            }
                                            .onFailure { onChanged("Falha ao desbloquear: ${it.message ?: "erro de conexão"}") }
                                        loading = false
                                    }
                                },
                            ) { Text("Desbloquear") }
                        }
                        else -> {
                            Button(
                                enabled = settings.configured && passengerAdminContactKey(candidate.whatsapp).isNotBlank() && !loading,
                                onClick = {
                                    loading = true
                                    scope.launch {
                                        runCatching {
                                            TripRemoteApi(settings).invitePassenger(
                                                displayName = candidate.displayName,
                                                passengerContact = candidate.whatsapp,
                                                referredByContact = access?.referredByContact.orEmpty(),
                                            )
                                        }.onSuccess {
                                            temporaryPassword = it.temporaryPassword
                                            temporaryPasswordFor = candidate.displayName
                                            val local = candidate.localProfile
                                            if (local == null && candidate.displayName.isNotBlank()) {
                                                passengerStore.createProfile(candidate.displayName, candidate.whatsapp)
                                            }
                                            revision++
                                            onChanged("Acesso liberado. Envie a senha temporária ao passageiro.")
                                        }.onFailure { onChanged("Falha ao liberar acesso: ${it.message ?: "erro de conexão"}") }
                                        loading = false
                                    }
                                },
                            ) { Text(if (access?.status == "PENDING") "Aprovar e liberar" else "Liberar acesso") }
                        }
                    }
                }
            }
        }
    }

    historyProfileId?.let { profileId ->
        val history = passengerStore.persistentHistory(profileId)
        AlertDialog(
            onDismissRequest = { historyProfileId = null },
            title = { Text("Histórico do passageiro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (history == null) {
                        Text("Histórico não encontrado.")
                    } else {
                        val profile = history.profile
                        Text((if (profile.blocked) "🚫 " else "") + profile.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(profile.whatsapp.ifBlank { "Telefone não informado" })
                        Text("${history.totalRides} viagem(ns) registrada(s)")
                        if (profile.externalPassengerIds.isNotEmpty()) {
                            Text("UUID(s): " + profile.externalPassengerIds.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                        history.observations.take(15).forEach { observation ->
                            val whenText = java.time.Instant.ofEpochMilli(observation.observedAtMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            val details = listOf(
                                observation.displayName.takeIf(String::isNotBlank),
                                observation.whatsapp.takeIf(String::isNotBlank),
                                observation.photoUrl.takeIf(String::isNotBlank)?.let { "foto registrada" },
                            ).filterNotNull().joinToString(" • ")
                            Text("• $whenText — $details", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyProfileId = null }) { Text("Fechar") } },
        )
    }
}

internal fun mergePassengerAdminCandidates(
    localProfiles: List<PassengerProfile>,
    collectedPassengers: List<BlaBlaCollectorPassenger>,
    remotePassengers: List<DriverPassengerAccess>,
): List<PassengerAdminCandidate> {
    val map = linkedMapOf<String, PassengerAdminCandidate>()
    fun merge(name: String, phone: String, local: PassengerProfile?, remote: DriverPassengerAccess?, source: String) {
        val key = passengerAdminContactKey(phone)
        if (key.isBlank()) return
        val previous = map[key]
        map[key] = PassengerAdminCandidate(
            key = key,
            displayName = remote?.displayName?.takeIf(String::isNotBlank)
                ?: local?.displayName?.takeIf(String::isNotBlank)
                ?: previous?.displayName?.takeIf(String::isNotBlank)
                ?: name.trim(),
            whatsapp = remote?.passengerContact?.takeIf(String::isNotBlank)
                ?: local?.whatsapp?.takeIf(String::isNotBlank)
                ?: previous?.whatsapp?.takeIf(String::isNotBlank)
                ?: phone.trim(),
            localProfile = local ?: previous?.localProfile,
            remoteAccess = remote ?: previous?.remoteAccess,
            source = listOf(previous?.source.orEmpty(), source).filter(String::isNotBlank).distinct().joinToString(" • "),
        )
    }
    localProfiles.forEach { merge(it.displayName, it.whatsapp, it, null, "Cadastro Rota Certa") }
    collectedPassengers.forEach { merge(it.name, it.phone.orEmpty(), null, null, "Captado na Timeline/BlaBlaCar") }
    remotePassengers.forEach { merge(it.displayName, it.passengerContact, null, it, if (it.status == "PENDING") "Indicação aguardando aprovação" else "Acesso online") }
    return map.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
}

internal fun passengerAdminContactKey(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.length < 10) return ""
    return if (digits.startsWith("55") && digits.length in 12..13) digits.drop(2) else digits.takeLast(11)
}

internal fun maskPassengerAdminContact(raw: String): String {
    val digits = passengerAdminContactKey(raw)
    if (digits.length < 10) return raw
    return if (digits.length == 11) "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.takeLast(4)}"
    else "(${digits.take(2)}) ${digits.substring(2, 6)}-${digits.takeLast(4)}"
}

internal fun passengerAccessLabel(access: DriverPassengerAccess?): String = when (access?.status) {
    "ACTIVE" -> "✅ Agenda liberada"
    "BLOCKED" -> "🚫 Acesso bloqueado"
    "PENDING" -> "⏳ Aguardando sua aprovação"
    else -> "🔒 Sem acesso à Agenda Pública"
}

internal fun parseCreditInput(raw: String): Long? = runCatching {
    val normalized = raw.trim().replace(".", "").replace(",", ".")
    normalized.toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()?.takeIf { it in 0L..1_000_000L }

internal fun formatCreditInput(cents: Long): String =
    java.math.BigDecimal.valueOf(cents.coerceAtLeast(0L), 2).setScale(2).toPlainString().replace(".", ",")

internal fun formatCreditMoney(cents: Long): String =
    "R$ " + formatCreditInput(cents)
