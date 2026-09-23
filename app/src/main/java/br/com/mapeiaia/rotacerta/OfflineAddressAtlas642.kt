package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Stage642 — atlas local aprendido.
 *
 * Todo endereço que já foi resolvido com sucesso passa a existir localmente no aparelho sem TTL.
 * As chaves são SHA-256 do texto normalizado: o endereço legível não é persistido neste atlas.
 * Assim, depois da primeira resolução, futuras decisões do Farol não dependem de rede.
 *
 * Isto não é um navegador completo nem substitui um pacote OSM de ruas ainda não vistas. É a
 * camada offline-first que elimina chamadas repetidas e permite operação sem internet para o
 * histórico já conhecido do motorista.
 */
class OfflineAddressAtlas642(context: Context) {
    companion object {
        const val CONTRACT_MARKER = "FAROL_OFFLINE_ADDRESS_ATLAS_STAGE642"
        const val NO_REMOTE_LOOKUP_MARKER = "ATLAS_LOOKUP_ZERO_NETWORK_STAGE642"
        const val LEARNS_SUCCESSFUL_GEOCODES = "SUCCESSFUL_GEOCODE_BECOMES_OFFLINE_FOREVER_STAGE642"
        private const val PREFS = "farol_offline_address_atlas_0642"
        private const val PREFIX = "a|"
        private const val MAX_ENTRIES = 8_192
        private const val PRUNE_EVERY = 64
    }

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var writes = 0

    fun lookup(address: String): Coordinate? {
        val canonical = canonical(address)
        if (canonical.isBlank()) return null
        return decode(prefs.getString(key(canonical), null))
    }

    fun learn(address: String, coordinate: Coordinate) {
        if (!valid(coordinate)) return
        val canonical = canonical(address)
        if (canonical.isBlank()) return
        prefs.edit().putString(
            key(canonical),
            "${System.currentTimeMillis()}|${coordinate.latitude}|${coordinate.longitude}",
        ).apply()
        pruneEventually()
    }

    fun learnAll(addresses: Iterable<String>, coordinate: Coordinate) {
        if (!valid(coordinate)) return
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        addresses.map(::canonical)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { normalized ->
                editor.putString(key(normalized), "$now|${coordinate.latitude}|${coordinate.longitude}")
            }
        editor.apply()
        pruneEventually()
    }

    internal fun canonicalForTest(value: String): String = canonical(value)

    private fun decode(raw: String?): Coordinate? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 3) return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        val longitude = parts[2].toDoubleOrNull() ?: return null
        val coordinate = Coordinate(latitude, longitude)
        return coordinate.takeIf(::valid)
    }

    @Synchronized
    private fun pruneEventually() {
        writes += 1
        if (writes < PRUNE_EVERY) return
        writes = 0
        val entries = prefs.all.mapNotNull { (key, raw) ->
            if (!key.startsWith(PREFIX)) return@mapNotNull null
            val timestamp = (raw as? String)?.substringBefore('|')?.toLongOrNull() ?: return@mapNotNull null
            key to timestamp
        }
        if (entries.size <= MAX_ENTRIES) return
        val editor = prefs.edit()
        entries.sortedByDescending { it.second }
            .drop(MAX_ENTRIES)
            .forEach { (key, _) -> editor.remove(key) }
        editor.apply()
    }

    private fun valid(coordinate: Coordinate): Boolean =
        coordinate.latitude.isFinite() &&
            coordinate.longitude.isFinite() &&
            coordinate.latitude in -90.0..90.0 &&
            coordinate.longitude in -180.0..180.0

    private fun key(canonical: String): String = PREFIX + sha256(canonical)

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
