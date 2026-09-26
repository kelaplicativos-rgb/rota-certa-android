package br.com.mapeiaia.rotacerta

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical Rota Certa identity boundary.
 *
 * The current local-only installation is provisioned once as a provisional tenant
 * that deliberately keeps the legacy (unscoped) storage keys. This preserves every
 * existing setting/trip without copying or deleting data. When real authentication
 * exists, that provisional tenant can be claimed by the canonical server tenant;
 * additional tenants receive deterministic isolated storage namespaces.
 *
 * IMPORTANT: switching to an additional tenant is intentionally not exposed yet.
 * Stage47 still has auxiliary stores being audited. Activating a second tenant before
 * every persistent store is scoped would risk cross-tenant leakage, so this layer
 * fails closed until that coverage is complete.
 */
@Serializable
data class RotaCertaTenantIdentity(
    val tenantId: String,
    val userId: String = "",
    val displayName: String = "",
    val localeTag: String = "",
    val currencyCode: String = "",
    val storageNamespace: String = "",
    val provisionalLocal: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

/** Pure storage policy so every repository can use the same tenant boundary. */
data class TenantStorageScope(
    val tenantId: String,
    val namespace: String,
) {
    val usesLegacyKeys: Boolean
        get() = namespace.isBlank()

    fun key(base: String): String =
        if (usesLegacyKeys) base else "${base}__tenant_$namespace"

    fun keyAlias(base: String): String =
        if (usesLegacyKeys) base else "$base.$namespace"

    fun scopedIdPrefix(): String =
        if (usesLegacyKeys) "" else "$namespace-"
}

object TenantStoragePolicy {
    fun namespaceFor(tenantIdRaw: String): String {
        val tenantId = tenantIdRaw.trim()
        require(tenantId.isNotEmpty()) { "tenantId must not be blank" }
        return MessageDigest.getInstance("SHA-256")
            .digest(tenantId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun scopeFor(identity: RotaCertaTenantIdentity): TenantStorageScope =
        TenantStorageScope(identity.tenantId, identity.storageNamespace)
}

class RotaCertaTenantRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Returns the active tenant, provisioning one local tenant exactly once when
     * this pre-authentication build has never had a Rota Certa identity before.
     */
    fun activeTenant(): RotaCertaTenantIdentity = synchronized(LOCK) {
        val current = listInternal()
        val activeId = prefs.getString(KEY_ACTIVE_TENANT_ID, null)
        current.firstOrNull { it.tenantId == activeId }?.let { return@synchronized it }

        current.firstOrNull()?.let { first ->
            prefs.edit().putString(KEY_ACTIVE_TENANT_ID, first.tenantId).apply()
            return@synchronized first
        }

        val provisional = RotaCertaTenantIdentity(
            tenantId = UUID.randomUUID().toString(),
            storageNamespace = "",
            provisionalLocal = true,
        )
        saveInternal(listOf(provisional), provisional.tenantId)
        provisional
    }

    fun activeScope(): TenantStorageScope = TenantStoragePolicy.scopeFor(activeTenant())

    fun list(): List<RotaCertaTenantIdentity> = synchronized(LOCK) { listInternal() }

    /**
     * Registers a canonical tenant without inventing authentication. Callers must
     * only pass identities already proven by the future server/auth layer.
     *
     * claimCurrentLegacyStorage=true is reserved for the first successful account
     * claim on an installation upgraded from the pre-tenant app. It reassigns the
     * existing legacy keys to that canonical identity without copying secrets.
     *
     * Additional tenants may be registered for future use but are NOT activated in
     * this foundation build. Runtime tenant switching stays disabled until every
     * persistent store, including Stage47 auxiliary stores, is tenant-scoped.
     */
    fun registerCanonicalTenant(
        tenantIdRaw: String,
        userIdRaw: String = "",
        displayNameRaw: String = "",
        localeTagRaw: String = "",
        currencyCodeRaw: String = "",
        claimCurrentLegacyStorage: Boolean = false,
    ): RotaCertaTenantIdentity = synchronized(LOCK) {
        val tenantId = tenantIdRaw.trim()
        require(tenantId.isNotEmpty()) { "tenantId must not be blank" }
        val currencyCode = currencyCodeRaw.trim().uppercase(Locale.ROOT)
        require(currencyCode.isEmpty() || currencyCode.matches(Regex("[A-Z]{3}"))) {
            "currencyCode must be a 3-letter ISO code"
        }

        val current = listInternal().toMutableList()
        val existing = current.firstOrNull { it.tenantId == tenantId }
        val activeId = prefs.getString(KEY_ACTIVE_TENANT_ID, null)
        val active = current.firstOrNull { it.tenantId == activeId }
        val claimLegacy = claimCurrentLegacyStorage &&
            active?.provisionalLocal == true &&
            active.storageNamespace.isBlank() &&
            current.none { it.tenantId == tenantId && it.storageNamespace.isNotBlank() }

        val createdAt = existing?.createdAtMillis ?: active?.takeIf { claimLegacy }?.createdAtMillis ?: System.currentTimeMillis()
        val namespace = when {
            existing != null -> existing.storageNamespace
            claimLegacy -> ""
            else -> TenantStoragePolicy.namespaceFor(tenantId)
        }
        val canonical = RotaCertaTenantIdentity(
            tenantId = tenantId,
            userId = userIdRaw.trim(),
            displayName = displayNameRaw.trim(),
            localeTag = localeTagRaw.trim(),
            currencyCode = currencyCode,
            storageNamespace = namespace,
            provisionalLocal = false,
            createdAtMillis = createdAt,
        )

        if (claimLegacy && active != null) current.removeAll { it.tenantId == active.tenantId }
        current.removeAll { it.tenantId == tenantId }
        current += canonical

        val nextActiveId = when {
            claimLegacy -> tenantId
            activeId == tenantId -> tenantId
            else -> activeId
        }
        saveInternal(current, nextActiveId)
        canonical
    }

    private fun listInternal(): List<RotaCertaTenantIdentity> = runCatching {
        json.decodeFromString<List<RotaCertaTenantIdentity>>(prefs.getString(KEY_TENANTS, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun saveInternal(tenants: List<RotaCertaTenantIdentity>, activeTenantId: String?) {
        val editor = prefs.edit().putString(KEY_TENANTS, json.encodeToString(tenants.distinctBy { it.tenantId }))
        if (activeTenantId.isNullOrBlank()) editor.remove(KEY_ACTIVE_TENANT_ID)
        else editor.putString(KEY_ACTIVE_TENANT_ID, activeTenantId)
        editor.apply()
    }

    companion object {
        private const val PREFS = "rota_certa_tenant_registry_v1"
        private const val KEY_TENANTS = "tenants"
        private const val KEY_ACTIVE_TENANT_ID = "active_tenant_id"
        private val LOCK = Any()
    }
}
