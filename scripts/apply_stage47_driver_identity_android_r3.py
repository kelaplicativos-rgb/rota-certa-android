#!/usr/bin/env python3
from pathlib import Path
import sys

SOURCE = Path(sys.argv[1]).resolve()


def once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    print(f"stage47_driver_identity_anchor label={label!r} count={count} file={path.name}", flush=True)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: Path, start: str, end: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    start_count = text.count(start)
    end_count = text.count(end)
    print(f"stage47_driver_identity_anchor label={label!r} start_count={start_count} end_count={end_count} file={path.name}", flush=True)
    if start_count != 1 or end_count != 1:
        raise SystemExit(f"{label}: expected one start/end marker, got start={start_count} end={end_count}")
    begin = text.index(start)
    finish = text.index(end, begin)
    if finish <= begin:
        raise SystemExit(f"{label}: invalid function boundary order")
    path.write_text(text[:begin] + new + text[finish:], encoding="utf-8")

pkg = SOURCE / "app/src/main/java/br/com/mapeiaia/rotacerta/trips"
domain = pkg / "TripDomain.kt"
once(domain,
'''object TripFareEngine {
''',
r'''object DriverIdentityRules {
    fun normalizeUsername(value: String): String {
        val ascii = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return ascii.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32)
    }

    fun isValidUsername(value: String): Boolean =
        value.length in 3..32 && value.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
}

object TripFareEngine {
''', "driver identity rules")

store = pkg / "TripStore.kt"
once(store,
'''data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
) {
''',
'''data class TripOnlineSettings(
    val apiBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val driverToken: String = "",
    val publicCalendarToken: String = "",
    val driverDisplayName: String = "",
    val driverUsername: String = "",
    val googleCalendarPublicUrl: String = "",
) {
''', "driver settings fields")
once(store,
'''    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }
            ?.trimEnd('/')
            ?.let { base ->
                publicCalendarToken.takeIf { it.length >= 16 }
                    ?.let { token -> "$base/calendar/$token.ics" }
            }
''',
'''    val publicAgendaUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            val username = driverUsername.takeIf(DriverIdentityRules::isValidUsername) ?: return@let null
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token -> "$base/?motorista=$username&agenda=$token" }
        }

    val publicCalendarUrl: String?
        get() = publicBaseUrl.takeIf { it.startsWith("https://") }?.trimEnd('/')?.let { base ->
            publicCalendarToken.takeIf { it.length >= 16 }?.let { token ->
                if (DriverIdentityRules.isValidUsername(driverUsername)) "$base/calendar/$driverUsername/$token.ics" else "$base/calendar/$token.ics"
            }
        }

    val googleCalendarMirrorUrl: String?
        get() = googleCalendarPublicUrl.trim().takeIf { it.startsWith("https://") }
''', "driver agenda urls")

remote = pkg / "TripRemoteApi.kt"
once(remote,
'''@Serializable
data class RemoteBookingResponse(
''',
'''@Serializable
data class DriverRegistrationRequest(val displayName: String, val username: String)

@Serializable
data class DriverRegistrationResponse(
    val displayName: String,
    val username: String,
    val driverToken: String,
    val publicAgendaToken: String,
    val publicAgendaUrl: String,
    val calendarUrl: String,
)

@Serializable
data class RemoteBookingResponse(
''', "driver registration DTOs")
once(remote,
'''    suspend fun publish(trip: Trip): PublishedTripResponse = request(
''',
'''    suspend fun registerDriver(displayName: String, username: String): DriverRegistrationResponse = request(
        method = "POST",
        path = "/v1/drivers/register",
        body = json.encodeToString(DriverRegistrationRequest(displayName.trim(), username.trim())),
        requireDriverToken = false,
    )

    suspend fun publish(trip: Trip): PublishedTripResponse = request(
''', "driver registration api")
once(remote,
'''            if (requireDriverToken) setRequestProperty("X-Rota-Certa-Driver-Token", settings.driverToken)
''',
'''            if (requireDriverToken && settings.driverUsername.isNotBlank()) setRequestProperty("X-Rota-Certa-Driver-Username", settings.driverUsername)
            if (requireDriverToken) setRequestProperty("X-Rota-Certa-Driver-Token", settings.driverToken)
''', "driver username auth header")

calendar = pkg / "TripCalendar.kt"
replace_between(
    calendar,
    '''    fun sharePublicAgenda(context: Context, settings: TripOnlineSettings): Boolean {
''',
    '''    fun shareIcs(context: Context, trip: Trip, booking: Booking? = null) {
''',
    r'''    fun sharePublicAgenda(context: Context, settings: TripOnlineSettings): Boolean {
        val agendaUrl = settings.publicAgendaUrl ?: return false
        val calendarUrl = settings.publicCalendarUrl ?: return false
        val driver = settings.driverDisplayName.ifBlank { settings.driverUsername }
        shareText(
            context,
            "Compartilhar Agenda de Viagens",
            "Rota Certa — Agenda de $driver\nConsultar destinos, valores, vagas e reservar:\n$agendaUrl\n\nAssinar calendário (.ics):\n$calendarUrl\n\nO link não contém a chave privada do motorista nem dados de outros passageiros.",
        )
        return true
    }

    fun shareGoogleCalendarFallback(context: Context, settings: TripOnlineSettings): Boolean {
        val url = settings.googleCalendarMirrorUrl ?: return false
        val driver = settings.driverDisplayName.ifBlank { settings.driverUsername }
        shareText(context, "Compartilhar Google Agenda", "Rota Certa — calendário de viagens de $driver\n$url\n\nO Google Agenda é apenas um espelho. Reservas, cancelamentos e vagas em tempo real continuam na Agenda Pública do Rota Certa.")
        return true
    }

''',
    "named agenda share boundaries",
)

activity = pkg / "TripsActivity.kt"
once(activity,
'''                    val onlineSettings = store.onlineSettings()
                    if (onlineSettings.publicCalendarUrl != null) {
                        OutlinedButton(onClick = {
                            if (TripCalendarBridge.sharePublicAgenda(activity, onlineSettings)) {
                                message = "Link da Agenda Pública pronto para compartilhar."
                            }
                        }) { Text("Compartilhar agenda pública") }
                    }
''',
'''                    val onlineSettings = store.onlineSettings()
                    if (onlineSettings.publicAgendaUrl != null) {
                        OutlinedButton(onClick = {
                            if (TripCalendarBridge.sharePublicAgenda(activity, onlineSettings)) message = "Link exclusivo da Agenda Pública pronto para compartilhar."
                        }) { Text("Compartilhar minha agenda") }
                    }
                    if (onlineSettings.googleCalendarMirrorUrl != null) {
                        OutlinedButton(onClick = {
                            if (TripCalendarBridge.shareGoogleCalendarFallback(activity, onlineSettings)) message = "Link do Google Agenda pronto para compartilhar."
                        }) { Text("Compartilhar Google Agenda") }
                    }
''', "agenda share actions")
once(activity,
'''    var token by remember { mutableStateOf(initial.driverToken) }
    var calendarToken by remember { mutableStateOf(initial.publicCalendarToken) }
    Text("Integração online", style = MaterialTheme.typography.titleLarge)
''',
'''    var token by remember { mutableStateOf(initial.driverToken) }
    var calendarToken by remember { mutableStateOf(initial.publicCalendarToken) }
    var driverName by remember { mutableStateOf(initial.driverDisplayName) }
    var driverUsername by remember { mutableStateOf(initial.driverUsername) }
    var googleCalendarUrl by remember { mutableStateOf(initial.googleCalendarPublicUrl) }
    var registrationMessage by remember { mutableStateOf<String?>(null) }
    val registrationScope = rememberCoroutineScope()
    Text("Integração online", style = MaterialTheme.typography.titleLarge)
''', "identity settings state")
once(activity,
'''    OutlinedTextField(publicBase, { publicBase = it.trim() }, label = { Text("URL pública") }, modifier = Modifier.fillMaxWidth())
''',
'''    OutlinedTextField(publicBase, { publicBase = it.trim() }, label = { Text("URL pública") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverName, { driverName = it }, label = { Text("Nome público do motorista") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(driverUsername, { driverUsername = DriverIdentityRules.normalizeUsername(it) }, label = { Text("Nome de usuário no link") }, modifier = Modifier.fillMaxWidth(), enabled = token.isBlank())
    OutlinedTextField(googleCalendarUrl, { googleCalendarUrl = it.trim() }, label = { Text("Link público do Google Agenda — opcional") }, modifier = Modifier.fillMaxWidth())
''', "identity settings fields")
once(activity,
'''    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
''',
'''    registrationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (token.isBlank()) {
        Button(enabled = api.startsWith("https://") && publicBase.startsWith("https://") && driverName.isNotBlank(), onClick = {
            val normalizedUsername = DriverIdentityRules.normalizeUsername(driverUsername.ifBlank { driverName })
            if (!DriverIdentityRules.isValidUsername(normalizedUsername)) {
                registrationMessage = "Escolha um nome de usuário com pelo menos 3 caracteres."
            } else {
                driverUsername = normalizedUsername
                registrationScope.launch {
                    val candidate = TripOnlineSettings(apiBaseUrl = api.trimEnd('/'), publicBaseUrl = publicBase.trimEnd('/'), driverDisplayName = driverName.trim(), driverUsername = normalizedUsername, googleCalendarPublicUrl = googleCalendarUrl.trim())
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
        }) { Text("Gerar meu link exclusivo") }
    } else {
        val preview = TripOnlineSettings(apiBaseUrl = api.trimEnd('/'), publicBaseUrl = publicBase.trimEnd('/'), driverToken = token, publicCalendarToken = calendarToken, driverDisplayName = driverName.trim(), driverUsername = driverUsername, googleCalendarPublicUrl = googleCalendarUrl.trim()).publicAgendaUrl
        preview?.let { Text("Seu link: $it", style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
''', "link generator")
once(activity,
'''                    publicCalendarToken = calendarToken.trim(),
                ),
''',
'''                    publicCalendarToken = calendarToken.trim(),
                    driverDisplayName = driverName.trim(),
                    driverUsername = DriverIdentityRules.normalizeUsername(driverUsername),
                    googleCalendarPublicUrl = googleCalendarUrl.trim(),
                ),
''', "save identity settings")

print("stage47_driver_identity_android_r3=PASS per_driver_link_ui=true google_calendar_fallback=true")
