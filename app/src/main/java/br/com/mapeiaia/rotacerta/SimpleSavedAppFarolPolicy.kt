package br.com.mapeiaia.rotacerta

import java.util.Locale

/**
 * Contrato final e mínimo do farol:
 * 1. o aplicativo foi ensinado/salvo pelo usuário;
 * 2. a tela contém pelo menos dois endereços reconhecidos;
 * 3. o último endereço é o destino final.
 *
 * Nenhum passageiro, preço, frase, modelo visual ou contrato de card participa
 * do caminho crítico. Modelos continuam apenas como apoio para a galeria.
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
        val active = normalizedPackage != null &&
            normalizedPackage in normalizedSaved &&
            trigger.addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES &&
            !trigger.destination.isNullOrBlank()
        val addressSignature = if (active) {
            normalizedPackage + "|" + trigger.addressSignature
        } else {
            ""
        }
        return Evaluation(
            packageName = normalizedPackage,
            addresses = trigger.addresses,
            pickup = trigger.pickup,
            destination = trigger.destination,
            addressSignature = addressSignature,
            // Preço, cronômetro, nome, mapa e outras informações variáveis não
            // podem transformar o mesmo destino em uma tela nova e fazer piscar.
            screenHash = if (active) {
                FarolDisplayStabilityPolicy.stableScreenHash(normalizedPackage, addressSignature)
            } else {
                trigger.screenHash
            },
            active = active,
        )
    }

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
