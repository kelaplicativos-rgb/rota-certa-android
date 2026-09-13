from pathlib import Path
import json


def ensure_replace(path: str, old: str, new: str, sentinel: str) -> None:
    p = Path(path)
    text = p.read_text()
    if sentinel in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}\n--- needle ---\n{old[:1200]}")
    p.write_text(text.replace(old, new, 1))


dynamic = "app/src/main/java/br/com/mapeiaia/rotacerta/trips/BlaBlaDynamicAccounts.kt"

ensure_replace(
    dynamic,
    '''                if (expectedFoundInAuthenticatedPage) {
                    identityConfirmedThisSync = true
                } else {
                    bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
                }
                if (identityConfirmedThisSync) persistPublicProfileEvidence(it)''',
    '''                if (expectedFoundInAuthenticatedPage) {
                    identityConfirmedThisSync = true
                } else {
                    bindIdentityFromLinks(it.profileLinks, it.visibleName)?.let { updated -> account = updated }
                }
                val observedSessionUuid0553 = when {
                    identityConfirmedThisSync && !account.profileUuid.isNullOrBlank() -> account.profileUuid
                    observedUuids.size == 1 -> observedUuids.single()
                    else -> null
                }
                val sessionHealth0553 = BlaBlaCarSessionKeeper0552.observeIdentity(
                    context = this,
                    account = account,
                    actualProfileUuid = observedSessionUuid0553,
                    operation = "SESSION_IDENTITY_SYNC",
                )
                identityConfirmedThisSync = sessionHealth0553.state == BlaBlaSessionState0552.VALID
                if (identityConfirmedThisSync) persistPublicProfileEvidence(it)''',
    'operation = "SESSION_IDENTITY_SYNC"',
)

ensure_replace(
    dynamic,
    '''            identityConfirmedThisSync = true
            ridesSnapshotIdentityAttempts0526 = 0''',
    '''            val sessionHealth0553 = BlaBlaCarSessionKeeper0552.observeIdentity(
                context = this,
                account = account,
                actualProfileUuid = resolution.authenticatedProfileUuid,
                operation = "RIDES_SNAPSHOT_IDENTITY",
            )
            if (sessionHealth0553.state != BlaBlaSessionState0552.VALID) {
                failRidesSnapshot0526(
                    status = BlaBlaRidesSnapshotStatus0526.FAILED_IDENTITY,
                    errorCode = "SESSION_" + sessionHealth0553.state.name,
                    authenticatedProfileUuid = resolution.authenticatedProfileUuid,
                )
                return@evaluateRequest
            }
            identityConfirmedThisSync = true
            ridesSnapshotIdentityAttempts0526 = 0''',
    'operation = "RIDES_SNAPSHOT_IDENTITY"',
)

ensure_replace(
    dynamic,
    '''            when {
                expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                expectedUuid == null && driverUuids.size == 1 -> {
                    val updated = registry.bindIdentity(account.id, driverUuids.single(), acceptedResult.detail.driverName)
                    if (updated != null) {
                        account = updated
                        identityConfirmedThisSync = true
                    }
                }
            }

            if (!identityConfirmedThisSync || account.verifiedDefinition() == null) {''',
    '''            when {
                expectedUuid != null && expectedUuid in driverUuids -> identityConfirmedThisSync = true
                expectedUuid == null && driverUuids.size == 1 -> {
                    val updated = registry.bindIdentity(account.id, driverUuids.single(), acceptedResult.detail.driverName)
                    if (updated != null) {
                        account = updated
                        identityConfirmedThisSync = true
                    }
                }
            }
            val observedDriverUuid0553 = when {
                identityConfirmedThisSync && !account.profileUuid.isNullOrBlank() -> account.profileUuid
                driverUuids.size == 1 -> driverUuids.single()
                else -> null
            }
            val tripIdentityHealth0553 = BlaBlaCarSessionKeeper0552.observeIdentity(
                context = this,
                account = account,
                actualProfileUuid = observedDriverUuid0553,
                operation = "TRIP_DETAIL_IDENTITY",
            )
            identityConfirmedThisSync =
                identityConfirmedThisSync && tripIdentityHealth0553.state == BlaBlaSessionState0552.VALID

            if (!identityConfirmedThisSync || account.verifiedDefinition() == null) {''',
    'operation = "TRIP_DETAIL_IDENTITY"',
)

ensure_replace(
    dynamic,
    '''    private fun bindIdentityFromLinks(links: List<String>, visibleName: String): BlaBlaDynamicAccount? {
        val found = BlaBlaCollectorIdentityModule.uuids(links)
        val currentUuid = account.profileUuid?.lowercase()
        return when {
            currentUuid != null && currentUuid in found -> {
                identityConfirmedThisSync = true
                registry.bindIdentity(account.id, currentUuid, visibleName)
            }
            currentUuid == null && found.size == 1 -> {
                identityConfirmedThisSync = true
                registry.bindIdentity(account.id, found.single(), visibleName)
            }
            else -> null
        }
    }''',
    '''    private fun bindIdentityFromLinks(links: List<String>, visibleName: String): BlaBlaDynamicAccount? {
        val found = BlaBlaCollectorIdentityModule.uuids(links)
        val currentUuid = account.profileUuid?.lowercase()
        val updated = when {
            currentUuid != null && currentUuid in found ->
                registry.bindIdentity(account.id, currentUuid, visibleName)
            currentUuid == null && found.size == 1 ->
                registry.bindIdentity(account.id, found.single(), visibleName)
            else -> null
        }
        val identityAccount0553 = updated ?: account
        val observedUuid0553 = when {
            updated != null -> identityAccount0553.profileUuid
            found.size == 1 -> found.single()
            else -> null
        }
        val health0553 = BlaBlaCarSessionKeeper0552.observeIdentity(
            context = this,
            account = identityAccount0553,
            actualProfileUuid = observedUuid0553,
            operation = "PROFILE_LINK_IDENTITY",
        )
        identityConfirmedThisSync = updated != null && health0553.state == BlaBlaSessionState0552.VALID
        return updated
    }''',
    'operation = "PROFILE_LINK_IDENTITY"',
)

ensure_replace(
    dynamic,
    '''        if (::webView.isInitialized) webView.destroy()
        syncCrashGuard?.close()''',
    '''        if (::webView.isInitialized) {
            val browserToDestroy0553 = webView
            if (Looper.myLooper() == Looper.getMainLooper()) {
                browserToDestroy0553.destroy()
            } else {
                Handler(Looper.getMainLooper()).post {
                    runCatching { browserToDestroy0553.destroy() }
                }
            }
        }
        syncCrashGuard?.close()''',
    'browserToDestroy0553',
)

build = "app/build.gradle.kts"
ensure_replace(
    build,
    'val releaseVersionCode = 5_844\nval releaseVersionName = "0.1.552"',
    'val releaseVersionCode = 5_845\nval releaseVersionName = "0.1.553"',
    'val releaseVersionName = "0.1.553"',
)

history_path = Path("app/src/main/assets/release_history.json")
history = json.loads(history_path.read_text())
history["releases"] = [r for r in history.get("releases", []) if r.get("version") != "0.1.553"]
history["releases"].insert(
    0,
    {
        "version": "0.1.553",
        "build": 5845,
        "commit": None,
        "branch": "agent/session-hardening-0.1.553",
        "generatedAt": None,
        "status": "EM_VALIDACAO",
        "implemented": [],
        "fixed": [
            "Saúde da sessão BlaBlaCar passa a operar fail-closed: somente UUID positivamente validado autoriza operação autenticada.",
            "LOGIN_REQUIRED, EXPIRED e PROFILE_MISMATCH permanecem bloqueados até nova confirmação positiva da identidade esperada.",
            "Confirmações de identidade do SESSION_IDENTITY, captura de Suas viagens e detalhe da viagem agora alimentam o SessionKeeper central.",
        ],
        "improved": [
            "Falhas de rede preservam cookies/WebStorage e não são promovidas a autenticação válida nem convertidas indevidamente em logout.",
            "Destruição da WebView é confinada à main thread para evitar encerramento inseguro do perfil persistente.",
            "A interface continua distinguindo sessão saudável, sessão preservada em revalidação e conta realmente desconectada.",
        ],
        "regressions": [],
        "resolvedRegressions": [],
        "modulesAffected": ["BlaBlaCar", "Contas externas", "Sessão multiperfil"],
        "detailsComplete": True,
    },
)
history_path.write_text(json.dumps(history, ensure_ascii=False, indent=2) + "\n")

# Fail closed if the materialized source does not contain every release-critical marker.
source = Path(dynamic).read_text()
required = [
    'operation = "SESSION_IDENTITY_SYNC"',
    'operation = "RIDES_SNAPSHOT_IDENTITY"',
    'operation = "TRIP_DETAIL_IDENTITY"',
    'operation = "PROFILE_LINK_IDENTITY"',
    'browserToDestroy0553',
]
missing = [marker for marker in required if marker not in source]
if missing:
    raise SystemExit("missing 0.1.553 source markers: " + ", ".join(missing))

print("0.1.553 session hardening materialized")
