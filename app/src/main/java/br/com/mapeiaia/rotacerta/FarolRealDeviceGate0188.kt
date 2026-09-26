package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

enum class FarolEvidenceSource0188 { Accessibility, Ocr }

data class FarolCardBlock0188(
    val id: String,
    val parentId: String? = null,
    val packageName: String,
    val windowId: Int,
    val windowLayer: Int = 0,
    val depth: Int = 0,
    val text: String,
    val source: FarolEvidenceSource0188,
    val left: Int = 0,
    val top: Int = Int.MAX_VALUE,
    val right: Int = 0,
    val bottom: Int = Int.MAX_VALUE,
    val syntheticRoot: Boolean = false,
)

data class FarolRouteAuthorization0188(
    val packageName: String,
    val windowId: Int,
    val blockId: String,
    val source: FarolEvidenceSource0188,
    val analysisText: String,
    val addresses: List<String>,
    val pickup: String,
    val destination: String,
    val addressSignature: String,
    val screenHash: Int,
)

data class FarolRouteGateDecision0188(
    val authorization: FarolRouteAuthorization0188? = null,
    val reason: String,
) {
    val authorized: Boolean get() = authorization != null
}

/**
 * Porta universal fail-closed criada a partir da reprovação real da 0.1.187.
 *
 * 0.1.189 mantém o pacote como mera autorização de observação e acrescenta
 * autoridade visual monotônica: a janela mais alta vence e, dentro dela, o
 * bloco visual superior atual vence. Dois ou mais endereços dentro desse mesmo
 * bloco autorizam o último como destino final; blocos distintos nunca são
 * combinados para atingir o mínimo de endereços.
 */
object FarolRealDeviceGate0188 {
    const val CONTRACT_MARKER = "FAROL_REAL_DEVICE_GATE_0188"
    const val VISUAL_PRIORITY_MARKER = "FAROL_TOP_BLOCK_AUTHORITY_0189"

    private val passivePhrases = listOf(
        "reconhecimento facial",
        "facial recognition",
        "reconocimiento facial",
        "abra a boca",
        "open your mouth",
        "status perfeito",
        "voce esta pronto",
        "você está pronto",
        "you are ready",
        "you're ready",
        "carregando aguarde",
        "loading please wait",
        "verificacao de identidade",
        "verificação de identidade",
        "verify identity",
        "verifique sua identidade",
        "document verification",
        "verificacao de documento",
        "verificação de documento",
        "entrar na conta",
        "fazer login",
        "password",
        "senha",
        "go online",
        "conecte-se",
    ).map(::canonical)

    fun evaluate(
        selectedPackageName: String?,
        selectedPackages: Set<String>,
        blocks: List<FarolCardBlock0188>,
    ): FarolRouteGateDecision0188 {
        val selected = normalizePackage(selectedPackageName)
            ?: return rejected("Pacote atual ausente.")
        val allowed = selectedPackages.mapNotNull(::normalizePackage).toSet()
        if (selected !in allowed) return rejected("Pacote atual não foi selecionado pelo usuário.")
        if (blocks.isEmpty()) return rejected("Nenhum card ou modal atual foi segmentado.")

        val normalizedBlocks = blocks.asSequence()
            .filter { normalizePackage(it.packageName) == selected }
            .filter { it.text.isNotBlank() }
            .take(180)
            .toList()
        if (normalizedBlocks.isEmpty()) return rejected("Nenhum bloco pertence ao pacote selecionado.")

        data class Parsed(
            val block: FarolCardBlock0188,
            val addresses: List<String>,
            val passive: Boolean,
        )

        fun hasPassiveEvidence(text: String): Boolean {
            val value = canonical(text)
            return passivePhrases.any(value::contains)
        }

        val parsed = normalizedBlocks.map { block ->
            val addresses = UniversalScreenAddressParser.findAddresses(
                WrappedAddressTextNormalizer.normalize(block.text),
            ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                .filter(String::isNotBlank)
                .distinctBy { canonical(it) }
            Parsed(block, addresses, hasPassiveEvidence(block.text))
        }

        val addressBearing = parsed.filter {
            !it.passive && !it.block.syntheticRoot && it.addresses.isNotEmpty()
        }
        if (addressBearing.isEmpty()) {
            val passive = parsed.any { it.addresses.isNotEmpty() && it.passive }
            return rejected(
                if (passive) "Tela passiva, segurança ou status não pode autorizar rota."
                else "Nenhum endereço atual pertence a um card/modal coerente.",
            )
        }

        // Janela visualmente superior tem autoridade. Uma janela inferior nunca
        // pode continuar decidindo depois que uma superior do mesmo pacote surge.
        val bestLayer = addressBearing.maxOf { it.block.windowLayer }
        val layerAddressBlocks = addressBearing.filter { it.block.windowLayer == bestLayer }

        fun visualTop(block: FarolCardBlock0188): Int =
            block.top.takeUnless { it == Int.MAX_VALUE } ?: Int.MAX_VALUE

        val anchor = if (layerAddressBlocks.any { visualTop(it.block) != Int.MAX_VALUE }) {
            layerAddressBlocks.minWithOrNull(
                compareBy<Parsed> { visualTop(it.block) }
                    .thenByDescending { it.block.depth }
                    .thenBy { it.block.text.length },
            )
        } else {
            // Sem geometria confiável, mantemos fail-closed e usamos a estrutura
            // hierárquica para selecionar o bloco mais específico.
            val deepest = layerAddressBlocks.maxOf { it.block.depth }
            val specific = layerAddressBlocks.filter { it.block.depth == deepest }
            if (specific.map { canonical(it.addresses.last()) }.distinct().size > 1) {
                return rejected("Mais de um card coerente está visível sem geometria suficiente para priorizar.")
            }
            specific.minByOrNull { it.block.text.length }
        } ?: return rejected("Bloco visual atual ausente.")

        val candidates = parsed.asSequence()
            .filter { !it.passive && !it.block.syntheticRoot }
            .filter { it.block.windowLayer == bestLayer }
            .filter { it.addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES }
            .filter { candidate ->
                // O candidato precisa ser o próprio bloco superior ou um contêiner
                // hierárquico que o contenha. Assim um card inferior completo não
                // pode vencer um card superior ainda parcial.
                candidate.block.id == anchor.block.id ||
                    anchor.block.id.startsWith(candidate.block.id + "/") ||
                    contains(candidate.block, anchor.block)
            }
            .toList()

        if (candidates.isEmpty()) {
            return rejected("Bloco visual superior ainda não contém dois endereços confirmados.")
        }

        // Entre contêineres que representam o mesmo bloco superior, o mais
        // específico ganha. Isso evita usar o texto agregado de uma tela inteira.
        val bestDepth = candidates.maxOf { it.block.depth }
        val deepestCandidates = candidates.filter { it.block.depth == bestDepth }
        val winner = deepestCandidates.minWithOrNull(
            compareBy<Parsed> { visualTop(it.block) }.thenBy { it.block.text.length },
        ) ?: return rejected("Card coerente ausente.")

        val pickup = winner.addresses.first()
        val destination = winner.addresses.last()
        if (canonical(pickup) == canonical(destination)) {
            return rejected("Origem e destino resultaram no mesmo endereço.")
        }
        val signature = DestinationAddressIdentityPolicy.signature(selected, destination)
        if (signature.isBlank()) return rejected("Assinatura do destino final ficou vazia.")

        // O hash inclui a autoridade visual. Mesmo destino em outro bloco/janela
        // representa uma nova geração e invalida OCR/rota anteriores.
        val authorityIdentity = "$selected|${winner.block.windowId}|${winner.block.id}|$signature"
        return FarolRouteGateDecision0188(
            authorization = FarolRouteAuthorization0188(
                packageName = selected,
                windowId = winner.block.windowId,
                blockId = winner.block.id,
                source = winner.block.source,
                analysisText = winner.block.text.trim(),
                addresses = winner.addresses,
                pickup = pickup,
                destination = destination,
                addressSignature = signature,
                screenHash = authorityIdentity.hashCode(),
            ),
            reason = "Bloco visual superior confirmado; último endereço autorizado como destino final.",
        )
    }

    private fun contains(container: FarolCardBlock0188, child: FarolCardBlock0188): Boolean {
        if (container.top == Int.MAX_VALUE || child.top == Int.MAX_VALUE) return false
        if (container.right <= container.left || child.right <= child.left) return false
        return child.left >= container.left && child.right <= container.right &&
            child.top >= container.top && child.bottom <= container.bottom
    }

    private fun rejected(reason: String) = FarolRouteGateDecision0188(reason = reason)

    private fun normalizePackage(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)

    private fun canonical(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
