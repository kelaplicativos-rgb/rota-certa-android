package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Contrato final e mínimo do farol:
 * 1. o aplicativo foi selecionado manualmente pelo usuário;
 * 2. a tela contém pelo menos dois endereços reconhecidos;
 * 3. o último endereço é o destino final.
 *
 * Nenhum passageiro, preço, frase, modelo visual ou contrato de card participa
 * do caminho crítico. Nenhum modelo visual participa da leitura ou da decisão.
 */
object SimpleSavedAppFarolPolicy {
    data class Evaluation(
        val packageName: String?,
        val addresses: List<String>,
        val pickup: String?,
        val destination: String?,
        val addressSignature: String,
        val screenHash: Int,
        val active: Boolean,
    )

    fun evaluate(
        packageName: String?,
        savedPackages: Set<String>,
        text: String,
    ): Evaluation {
        val normalizedPackage = normalize(packageName)
        val normalizedSaved = savedPackages.mapNotNull(::normalize).toSet()
        val trigger = UniversalAddressTrigger.evaluate(text)
        val cleanedAddresses = trigger.addresses
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy { value -> value.lowercase(Locale.ROOT) }
        val destination = cleanedAddresses.lastOrNull()
        val active = normalizedPackage != null &&
            normalizedPackage in normalizedSaved &&
            cleanedAddresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES &&
            !destination.isNullOrBlank()
        val addressSignature = if (active) {
            DestinationAddressIdentityPolicy.signature(normalizedPackage, destination)
        } else {
            ""
        }
        return Evaluation(
            packageName = normalizedPackage,
            addresses = cleanedAddresses,
            pickup = cleanedAddresses.firstOrNull()?.takeIf { active },
            destination = destination?.takeIf { active },
            addressSignature = addressSignature,
            screenHash = if (active) {
                FarolDisplayStabilityPolicy.stableScreenHash(normalizedPackage, addressSignature)
            } else {
                trigger.screenHash
            },
            active = active,
        )
    } // destination_only_signature_checklist_16
 // destination_only_signature_checklist_16


    /** Mantido para compatibilidade; não deve mais decidir limpeza pelo texto completo. */
    fun screenFingerprint(
        packageName: String?,
        text: String,
        windowId: Int,
    ): Int = listOf(
        normalize(packageName).orEmpty(),
        windowId.toString(),
        text.replace('\u00A0', ' ')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n"),
    ).joinToString("|").hashCode()

    fun changed(previous: Int?, current: Int): Boolean = previous != null && previous != current

    /** Permite ensinar um aplicativo pela captura, mas nunca o próprio app/sistema. */
    fun teachablePackage(packageName: String?, ownPackageName: String): String? {
        val normalized = normalize(packageName) ?: return null
        val own = normalize(ownPackageName)
        if (normalized == own) return null
        if (normalized == "android" || normalized == "com.android.systemui") return null
        if (normalized.startsWith("com.samsung.android.systemui")) return null
        if (normalized.contains("launcher") || normalized.contains("keyguard")) return null
        if (normalized.contains("inputmethod") || normalized.contains("keyboard")) return null
        return normalized
    }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}
