#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"


def replace_once(old: str, new: str) -> None:
    text = ACTIVITY.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"expected TripsActivity.kt anchor not found: {old[:120]!r}")
    ACTIVITY.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.text.input.PasswordVisualTransformation",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.text.input.PasswordVisualTransformation",
)

replace_once(
    """private fun OnlineSettingsEditor(
    initial: TripOnlineSettings,
    onSave: (TripOnlineSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var api by remember { mutableStateOf(initial.apiBaseUrl) }""",
    """private fun OnlineSettingsEditor(
    initial: TripOnlineSettings,
    onSave: (TripOnlineSettings) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val linkedProfiles = remember(context) { BlaBlaDynamicAccountRegistry(context).list() }
    var api by remember { mutableStateOf(initial.apiBaseUrl) }""",
)

replace_once(
    """    Text(\"Integração online\", style = MaterialTheme.typography.titleLarge)
    Text(\"Sem essas credenciais, nenhuma informação é enviada para servidor algum. O módulo local continua operacional.\")
    OutlinedTextField(api, { api = it.trim() }, label = { Text(\"API HTTPS\") }, modifier = Modifier.fillMaxWidth())""",
    """    Text(\"Integração online\", style = MaterialTheme.typography.titleLarge)
    Text(\"Sem essas credenciais, nenhuma informação é enviada para servidor algum. O módulo local continua operacional.\")
    Text(\"Conta do motorista\", style = MaterialTheme.typography.titleMedium)
    Text(
        \"Existe uma única conta de motorista. A chave privada, o token público e o link da agenda pertencem ao motorista e valem para todos os perfis vinculados.\",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(api, { api = it.trim() }, label = { Text(\"API HTTPS\") }, modifier = Modifier.fillMaxWidth())""",
)

replace_once(
    """    OutlinedTextField(
        calendarToken,
        { calendarToken = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
        label = { Text(\"Token público da agenda de viagens\") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    registrationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }""",
    """    OutlinedTextField(
        calendarToken,
        { calendarToken = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
        label = { Text(\"Token público da agenda de viagens\") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(\"Perfis vinculados\", style = MaterialTheme.typography.titleMedium)
    Text(
        \"Um motorista pode usar vários perfis. Cada perfil mantém sua identidade, sua posição e suas viagens de forma independente.\",
        style = MaterialTheme.typography.bodySmall,
    )
    if (linkedProfiles.isEmpty()) {
        Text(\"Nenhum perfil BlaBlaCar vinculado neste aparelho.\", style = MaterialTheme.typography.bodySmall)
    } else {
        linkedProfiles.forEach { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(profile.displayLabel, style = MaterialTheme.typography.titleSmall)
                    val identityStatus = when {
                        !profile.profileName.isNullOrBlank() && !profile.profileUuid.isNullOrBlank() -> \"${profile.profileName} • identidade vinculada\"
                        !profile.profileUuid.isNullOrBlank() -> \"Identidade vinculada\"
                        else -> \"Perfil aguardando confirmação de identidade\"
                    }
                    Text(identityStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    Text(
        \"Vincular outro perfil não cria outro motorista e não gera outra chave privada ou outro token da agenda.\",
        style = MaterialTheme.typography.bodySmall,
    )
    registrationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }""",
)

text = ACTIVITY.read_text(encoding="utf-8")
required = [
    "Conta do motorista",
    "Perfis vinculados",
    "BlaBlaDynamicAccountRegistry(context).list()",
    "Cada perfil mantém sua identidade, sua posição e suas viagens de forma independente.",
    "não gera outra chave privada ou outro token da agenda",
]
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit(f"multi-profile driver UI materialization incomplete: {missing}")

print("Agenda 0.1.296: one-driver/multi-profile online settings UI materialized")
