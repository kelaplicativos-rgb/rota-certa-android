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
        Card(
            onClick = { openCandidateHistory(candidate) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val localProfile = candidate.localProfile
                Text(
                    (if (localProfile?.blocked == true) "🚫 " else "") + candidate.displayName.ifBlank { "Passageiro" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        candidate.whatsapp.ifBlank { "WhatsApp não informado" },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = candidate.whatsapp.isNotBlank(),
                        onClick = { openPassengerWhatsApp(context, candidate.whatsapp) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp_action),
                            contentDescription = "Abrir WhatsApp de ${candidate.displayName}",
                            tint = Color.Unspecified,
                        )
                    }
                }
                localProfile?.let { profile ->
                    val durableHistory = passengerStore.persistentHistory(profile.id)
                    Text(
                        "${durableHistory?.totalRides ?: passengerStore.rideHistory(profile.id).totalRides} concluída(s) • ${durableHistory?.totalOccurrences ?: 0} ocorrência(s)/reserva(s)",
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
                        OutlinedButton(
                            onClick = { blockCandidate = candidate },
                            modifier = Modifier.semantics {
                                contentDescription = if (candidate.localProfile.blocked) {
                                    "Liberar passageiro no carro"
                                } else {
                                    "Marcar passageiro como persona non grata"
                                }
                            },
                        ) { Text(if (candidate.localProfile.blocked) "✅" else "🚫") }
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

    blockCandidate?.let { candidate ->
        val currentlyBlocked = candidate.localProfile?.blocked == true
        AlertDialog(
            onDismissRequest = { blockCandidate = null },
            title = { Text(if (currentlyBlocked) "Liberar passageiro no carro?" else "Marcar como persona non grata?") },
            text = {
                Text(
                    if (currentlyBlocked) {
                        "Isso remove somente o bloqueio de persona non grata. O acesso à Agenda Pública continua sendo uma configuração separada."
                    } else {
                        "O passageiro será sinalizado por sua identidade canônica forte. O acesso à Agenda Pública não será alterado automaticamente."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    val current = canonicalProfile(candidate)
                    if (current != null) {
                        passengerStore.setBlocked(
                            current.id,
                            !currentlyBlocked,
                            if (!currentlyBlocked) "Persona non grata marcada pelo motorista" else "",
                        )
                        revision++
                        onChanged(if (currentlyBlocked) "Passageiro liberado para o carro." else "🚫 Passageiro marcado como persona non grata.")
                    }
                    blockCandidate = null
                }) { Text(if (currentlyBlocked) "Liberar" else "Confirmar 🚫") }
            },
            dismissButton = { TextButton(onClick = { blockCandidate = null }) { Text("Cancelar") } },
        )
    }

}

@Composable
internal fun PassengerHistoryPanel(
    history: PassengerPersistentHistory?,
    onBack: () -> Unit,
    onArchiveToggle: (PassengerProfile) -> Unit,
) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Histórico do passageiro", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
    if (history == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text("Histórico não encontrado.", modifier = Modifier.padding(12.dp))
        }
        return
    }

    val profile = history.profile
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    fun whenText(value: Long): String = java.time.Instant.ofEpochMilli(value)
        .atZone(java.time.ZoneId.systemDefault())
        .format(formatter)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text((if (profile.blocked) "🚫 " else "") + profile.displayName, style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(profile.whatsapp.ifBlank { "Telefone não informado" }, modifier = Modifier.weight(1f))
                IconButton(
                    enabled = profile.whatsapp.isNotBlank(),
                    onClick = { openPassengerWhatsApp(context, profile.whatsapp) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_whatsapp_action),
                        contentDescription = "Abrir WhatsApp do passageiro",
                        tint = Color.Unspecified,
                    )
                }
            }
            Text("${history.totalRides} viagem(ns) concluída(s)", style = MaterialTheme.typography.titleMedium)
            Text("${history.totalOccurrences} ocorrência(s)/reserva(s)", style = MaterialTheme.typography.bodyMedium)
            Text("Primeiro registro: ${whenText(history.firstSeenAtMillis)}", style = MaterialTheme.typography.bodySmall)
            Text("Último registro: ${whenText(history.lastSeenAtMillis)}", style = MaterialTheme.typography.bodySmall)
            if (profile.blocked) {
                Text(
                    "🚫 Persona non grata${profile.blockedReason.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { onArchiveToggle(profile) }) {
                Text(if (profile.archived) "Restaurar na lista" else "Arquivar da lista")
            }
        }
    }

    Text("Viagens concluídas", style = MaterialTheme.typography.titleMedium)
    if (history.completedRides.isEmpty()) {
        Text("Nenhuma viagem foi confirmada com ✅ ainda.", style = MaterialTheme.typography.bodySmall)
    } else {
        history.completedRides.sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis }.forEach { ride ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(whenText(ride.completedAtMillis ?: ride.updatedAtMillis), style = MaterialTheme.typography.titleSmall)
                    val route = listOf(ride.origin, ride.destination).filter(String::isNotBlank).joinToString(" → ")
                    if (route.isNotBlank()) Text(route)
                    val segment = listOf(ride.boarding, ride.dropoff).filter(String::isNotBlank).joinToString(" → ")
                    if (segment.isNotBlank() && segment != route) Text("Trecho: $segment", style = MaterialTheme.typography.bodySmall)
                    Text("${ride.seats} lugar(es) • ${ride.source.ifBlank { "Origem não informada" }}", style = MaterialTheme.typography.bodySmall)
                    ride.driverProfileUuid.takeIf(String::isNotBlank)?.let {
                        Text("Perfil: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    Text("Ocorrências e reservas", style = MaterialTheme.typography.titleMedium)
    history.rides.sortedByDescending(PassengerRideRecord::updatedAtMillis).forEach { ride ->
        val status = when (ride.status) {
            PassengerOccurrenceStatus.OBSERVED -> "Observado"
            PassengerOccurrenceStatus.CAPTURED -> "Capturado"
            PassengerOccurrenceStatus.RESERVED -> "Reservado"
            PassengerOccurrenceStatus.CANCELLED -> "Cancelado"
            PassengerOccurrenceStatus.COMPLETED -> "Concluído ✅"
        }
        Text("• $status • ${whenText(ride.updatedAtMillis)} • ${ride.source.ifBlank { "origem não informada" }}", style = MaterialTheme.typography.bodySmall)
    }

    Text("Alterações de identidade", style = MaterialTheme.typography.titleMedium)
    if (history.observations.isEmpty()) {
        Text("Nenhuma alteração observada.", style = MaterialTheme.typography.bodySmall)
    } else {
        history.observations.forEach { observation ->
            val details = listOf(
                observation.displayName.takeIf(String::isNotBlank),
                observation.whatsapp.takeIf(String::isNotBlank),
                observation.photoUrl.takeIf(String::isNotBlank)?.let { "foto registrada" },
                observation.source.takeIf(String::isNotBlank),
            ).filterNotNull().joinToString(" • ")
            Text("• ${whenText(observation.observedAtMillis)} — $details", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (profile.externalPassengerIds.isNotEmpty() || profile.onlineIdentityIds.isNotEmpty()) {
        HorizontalDivider()
        Text("Identificadores técnicos", style = MaterialTheme.typography.titleSmall)
        if (profile.externalPassengerIds.isNotEmpty()) {
            Text("BlaBlaCar UUID: ${profile.externalPassengerIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
        }
        if (profile.onlineIdentityIds.isNotEmpty()) {
            Text("Identidade online: ${profile.onlineIdentityIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun mergePassengerAdminCandidates(
    localProfiles: List<PassengerProfile>,
    collectedPassengers: List<BlaBlaCollectorPassenger>,
    remotePassengers: List<DriverPassengerAccess>,
): List<PassengerAdminCandidate> {
    val map = linkedMapOf<String, PassengerAdminCandidate>()
    val localByExternal = localProfiles.flatMap { profile -> profile.externalPassengerIds.map { it to profile } }.toMap()
    val localByOnline = localProfiles.flatMap { profile -> profile.onlineIdentityIds.map { it to profile } }.toMap()
    val phoneGroups = localProfiles
        .mapNotNull { profile -> passengerAdminContactKey(profile.whatsapp).takeIf(String::isNotBlank)?.let { it to profile } }
        .groupBy({ it.first }, { it.second })
    val uniqueLocalByPhone = phoneGroups.mapNotNull { (key, profiles) -> profiles.singleOrNull()?.let { key to it } }.toMap()

    fun merge(
        key: String,
        name: String,
        phone: String,
        local: PassengerProfile?,
        remote: DriverPassengerAccess?,
        externalPassengerId: String,
        source: String,
    ) {
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
            externalPassengerId = externalPassengerId.ifBlank { previous?.externalPassengerId.orEmpty() },
            source = listOf(previous?.source.orEmpty(), source).filter(String::isNotBlank).distinct().joinToString(" • "),
            lastActivityMillis = maxOf(previous?.lastActivityMillis ?: 0L, local?.updatedAtMillis ?: 0L),
        )
    }

    localProfiles.forEach { profile ->
        merge(
            key = "canonical:${profile.id}",
            name = profile.displayName,
            phone = profile.whatsapp,
            local = profile,
            remote = null,
            externalPassengerId = profile.externalPassengerIds.firstOrNull().orEmpty(),
            source = "Cadastro Rota Certa",
        )
    }

    collectedPassengers.forEachIndexed { index, passenger ->
        val externalId = stableExternalPassengerId(BlaBlaCollectorUrlModule.passengerIdentityKey(passenger.booking_href)).orEmpty()
        val phoneKey = passengerAdminContactKey(passenger.phone.orEmpty())
        val linked = localByExternal[externalId] ?: uniqueLocalByPhone[phoneKey]
        val key = when {
            linked != null -> "canonical:${linked.id}"
            externalId.isNotBlank() -> "external:$externalId"
            phoneKey.isNotBlank() -> "phone:$phoneKey"
            !passenger.booking_href.isNullOrBlank() -> "capture:${passenger.booking_href}"
            else -> "capture:$index:${normalizePassengerSearch(passenger.name)}"
        }
        merge(
            key = key,
            name = passenger.name,
            phone = passenger.phone.orEmpty(),
            local = linked,
            remote = null,
            externalPassengerId = externalId,
            source = "Captado na Timeline/BlaBlaCar",
        )
    }

    remotePassengers.forEachIndexed { index, access ->
        val onlineId = stableExternalPassengerId(access.id).orEmpty()
        val phoneKey = passengerAdminContactKey(access.passengerContact)
        val linked = localByOnline[onlineId] ?: uniqueLocalByPhone[phoneKey]
        val key = when {
            linked != null -> "canonical:${linked.id}"
            onlineId.isNotBlank() -> "online:$onlineId"
            phoneKey.isNotBlank() -> "phone:$phoneKey"
            else -> "remote:$index:${normalizePassengerSearch(access.displayName)}"
        }
        merge(
            key = key,
            name = access.displayName,
            phone = access.passengerContact,
            local = linked,
            remote = access,
            externalPassengerId = "",
            source = if (access.status == "PENDING") "Indicação aguardando aprovação" else "Acesso online",
        )
    }

    return map.values
        .filterNot { candidate ->
            candidate.localProfile?.archived == true &&
                candidate.remoteAccess == null &&
                !candidate.source.contains("Captado na Timeline/BlaBlaCar")
        }
        .sortedWith(
        compareBy<PassengerAdminCandidate> {
            when {
                it.remoteAccess?.status == "PENDING" -> 0
                it.localProfile?.blocked == true -> 1
                it.remoteAccess?.status == "ACTIVE" -> 2
                else -> 3
            }
        }.thenByDescending(PassengerAdminCandidate::lastActivityMillis)
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName },
    )
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
