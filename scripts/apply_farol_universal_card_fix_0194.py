#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("uso: apply_farol_universal_card_fix_0194.py <source-repository>")

root = Path(sys.argv[1]).resolve()


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: esperado exatamente 1 trecho, encontrado {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def insert_before_last_brace(path: Path, addition: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    pos = text.rfind("\n}")
    if pos < 0:
        raise SystemExit(f"{label}: fechamento final da classe não encontrado")
    path.write_text(text[:pos] + "\n" + addition.rstrip() + "\n" + text[pos:], encoding="utf-8")


gradle = root / "app/build.gradle.kts"
replace_once(gradle, "versionCode = 5477", "versionCode = 5478", "versionCode 0.1.194")
replace_once(gradle, 'versionName = "0.1.193"', 'versionName = "0.1.194"', "versionName 0.1.194")

parser = root / "app/src/main/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParser.kt"
replace_once(
    parser,
    "object UniversalScreenAddressParser {\n",
    'object UniversalScreenAddressParser {\n    const val CONTEXTUAL_PLACE_MARKER_0194 = "UNIVERSAL_CONTEXTUAL_PLACE_0194"\n',
    "marcador do parser 0.1.194",
)
replace_once(
    parser,
    '    private val invalidStreetNameWords = setOf("de", "da", "do", "das", "dos", "n", "no", "numero", "número", "sn", "app", "aplicativo", "web", "google", "maps") // no_via_app_false_address_checklist_15\n',
    '    private val invalidStreetNameWords = setOf("de", "da", "do", "das", "dos", "n", "no", "numero", "número", "sn", "app", "aplicativo", "web", "google", "maps") // no_via_app_false_address_checklist_15\n'
    '    private val contextualPlaceGenericWords0194 = setOf(\n'
    '        "cidade", "bairro", "jardim", "vila", "distrito", "municipio", "município",\n'
    '        "satelite", "satélite", "centro", "sao", "são", "santo", "santa", "state", "of",\n'
    '    )\n',
    "vocabulário contextual 0.1.194",
)
replace_once(
    parser,
    "        return isRecognizedNamedPlace(value)\n",
    "        return isRecognizedNamedPlace(value) || isRecognizedContextualPlace0194(value)\n",
    "autorização contextual do parser 0.1.194",
)
contextual_fn = '''    /**
     * 0.1.194: reconhece locais que não começam por uma categoria fixa quando
     * o próprio texto carrega contexto geográfico forte. Isso cobre POIs e
     * estabelecimentos desconhecidos sem transformar texto livre em endereço.
     */
    private fun isRecognizedContextualPlace0194(value: String): Boolean {
        if (value.length < 5 || isNoise(value)) return false
        val hasStrongLocalitySignal =
            stateRegex.containsMatchIn(value) ||
                cepRegex.containsMatchIn(value) ||
                namedPlaceLocalityRegex.containsMatchIn(value)
        if (!hasStrongLocalitySignal) return false

        val meaningfulWords = canonical(value)
            .split(Regex("\\\\s+"))
            .filter { token ->
                token.length >= 3 &&
                    token.any { char -> char.isLetter() } &&
                    token !in contextualPlaceGenericWords0194
            }
        return meaningfulWords.size >= 2
    } // universal_contextual_place_0_1_194

'''
replace_once(
    parser,
    "    private fun isPotentialStreetPrefix(value: String): Boolean {\n",
    contextual_fn + "    private fun isPotentialStreetPrefix(value: String): Boolean {\n",
    "função contextual do parser 0.1.194",
)

gate = root / "app/src/main/java/br/com/mapeiaia/rotacerta/FarolRealDeviceGate0188.kt"
replace_once(
    gate,
    '    const val VISUAL_PRIORITY_MARKER = "FAROL_TOP_BLOCK_AUTHORITY_0189"\n',
    '    const val VISUAL_PRIORITY_MARKER = "FAROL_TOP_BLOCK_AUTHORITY_0189"\n'
    '    const val SPLIT_CARD_RECOVERY_MARKER_0194 = "FAROL_SPLIT_CARD_RECOVERY_0194"\n',
    "marcador do gate 0.1.194",
)
old_candidates = '''        val candidates = parsed.asSequence()
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
'''
new_candidates = '''        val directCandidates = parsed.asSequence()
            .filter { !it.passive && !it.block.syntheticRoot }
            .filter { it.block.windowLayer == bestLayer }
            .filter { it.addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES }
            .filter { candidate ->
                candidate.block.id == anchor.block.id ||
                    anchor.block.id.startsWith(candidate.block.id + "/") ||
                    contains(candidate.block, anchor.block)
            }
            .toList()

        fun hasValidGeometry0194(block: FarolCardBlock0188): Boolean =
            block.top != Int.MAX_VALUE &&
                block.bottom != Int.MAX_VALUE &&
                block.right > block.left &&
                block.bottom > block.top

        fun structurallyClose0194(first: FarolCardBlock0188, second: FarolCardBlock0188): Boolean {
            if (first.id == second.id) return true
            if (first.parentId != null && first.parentId == second.parentId) return true
            if (first.id.startsWith(second.id + "/") || second.id.startsWith(first.id + "/")) return true

            val firstParts = first.id.split('/')
            val secondParts = second.id.split('/')
            var common = 0
            val limit = minOf(firstParts.size, secondParts.size)
            while (common < limit && firstParts[common] == secondParts[common]) common += 1
            val firstDistance = firstParts.size - common
            val secondDistance = secondParts.size - common
            return common >= 4 && firstDistance <= 2 && secondDistance <= 2
        }

        fun spatiallyClose0194(first: FarolCardBlock0188, second: FarolCardBlock0188): Boolean {
            if (!hasValidGeometry0194(first) || !hasValidGeometry0194(second)) return false
            val verticalGap = when {
                first.bottom < second.top -> second.top - first.bottom
                second.bottom < first.top -> first.top - second.bottom
                else -> 0
            }
            val horizontalOverlap =
                (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0)
            val firstWidth = (first.right - first.left).coerceAtLeast(1)
            val secondWidth = (second.right - second.left).coerceAtLeast(1)
            val overlapRatio = horizontalOverlap.toDouble() / minOf(firstWidth, secondWidth).coerceAtLeast(1)
            val referenceHeight = maxOf(
                (first.bottom - first.top).coerceAtLeast(1),
                (second.bottom - second.top).coerceAtLeast(1),
            )
            val maxGap = (referenceHeight * 6).coerceIn(96, 420)
            return verticalGap <= maxGap && overlapRatio >= 0.08
        }

        fun recoverSplitUpperCard0194(): Parsed? {
            if (anchor.block.source != FarolEvidenceSource0188.Accessibility) return null
            if (!hasValidGeometry0194(anchor.block)) return null

            val pool = layerAddressBlocks
                .filter { it.block.source == FarolEvidenceSource0188.Accessibility }
                .filter { !it.passive && !it.block.syntheticRoot }
                .filter { it.addresses.isNotEmpty() }
                .filter { hasValidGeometry0194(it.block) }

            val anchorCandidate = pool.firstOrNull { it.block.id == anchor.block.id } ?: return null
            val component = arrayListOf(anchorCandidate)
            val remaining = pool.filterNot { it.block.id == anchor.block.id }.toMutableList()
            var cursor = 0
            while (cursor < component.size) {
                val current = component[cursor]
                cursor += 1
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (
                        structurallyClose0194(current.block, next.block) &&
                        spatiallyClose0194(current.block, next.block)
                    ) {
                        component += next
                        iterator.remove()
                    }
                }
            }

            if (component.size < 2) return null
            val ordered = component.sortedWith(
                compareBy<Parsed> { visualTop(it.block) }
                    .thenBy { it.block.left }
                    .thenByDescending { it.block.depth },
            )
            val mergedText = ordered
                .map { it.block.text.trim() }
                .filter(String::isNotBlank)
                .distinct()
                .joinToString("\\n")
            val recoveredAddresses = UniversalScreenAddressParser.findAddresses(
                WrappedAddressTextNormalizer.normalize(mergedText),
            ).map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                .filter(String::isNotBlank)
                .distinctBy { canonical(it) }
            if (recoveredAddresses.size < UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES) return null

            val recoveredBlock = FarolCardBlock0188(
                id = "${anchor.block.id}/recovered0194",
                parentId = anchor.block.parentId,
                packageName = anchor.block.packageName,
                windowId = anchor.block.windowId,
                windowLayer = anchor.block.windowLayer,
                depth = ordered.maxOf { it.block.depth },
                text = mergedText,
                source = anchor.block.source,
                left = ordered.minOf { it.block.left },
                top = ordered.minOf { it.block.top },
                right = ordered.maxOf { it.block.right },
                bottom = ordered.maxOf { it.block.bottom },
                syntheticRoot = false,
            )
            return Parsed(recoveredBlock, recoveredAddresses, passive = false)
        }

        val candidates = if (directCandidates.isNotEmpty()) {
            directCandidates
        } else {
            listOfNotNull(recoverSplitUpperCard0194())
        }

        if (candidates.isEmpty()) {
            return rejected("Bloco visual superior ainda não contém dois endereços confirmados.")
        }
'''
replace_once(gate, old_candidates, new_candidates, "recuperação de card dividido 0.1.194")

parser_test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParserTest.kt"
parser_tests = '''    @Test
    fun contextualPoiWithLocalityWithoutKnownPrefixIsRecognized0194() {
        assertTrue(UniversalScreenAddressParser.isRecognizedAddress("CCB Jardim Nove de Julho"))
    }

    @Test
    fun numericLocalityFragmentIsRecognized0194() {
        assertTrue(UniversalScreenAddressParser.isRecognizedAddress("281, Jardim Nove de Julho"))
    }

    @Test
    fun contextualNoiseWithoutLocalityIsRejected0194() {
        assertFalse(
            UniversalScreenAddressParser.isRecognizedAddress(
                "Status perfeito! Voce esta pronto para aceitar corridas agora mesmo",
            ),
        )
    }

    @Test
    fun realInDrivePoiDestinationYieldsTwoLocations0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "R. Carlos Vivaldi, 197 (Cidade Sao Mateus, Sao Paulo - SP, 03965-030)\\n" +
                "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
        )
        assertEquals(2, addresses.size)
        assertEquals(
            "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
            addresses.last(),
        )
    }
'''
insert_before_last_brace(parser_test, parser_tests, "testes do parser 0.1.194")

gate_test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt"
gate_tests = '''    @Test
    fun splitAddressFragmentsFromSameUpperCardRecoverAsOneCard0194() {
        val packageName = "sinet.startup.indriver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "a11y:6544/0/0/0/0/1/2",
                    parentId = "a11y:6544/0/0/0/0/1",
                    depth = 6,
                    text = "R. Carlos Vivaldi, 197 (Cidade Sao Mateus, Sao Paulo - SP, 03965-030)",
                    left = 90,
                    top = 420,
                    right = 960,
                    bottom = 490,
                ),
                block(
                    id = "a11y:6544/0/0/0/0/1/3",
                    parentId = "a11y:6544/0/0/0/0/1",
                    depth = 6,
                    text = "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
                    left = 92,
                    top = 505,
                    right = 970,
                    bottom = 575,
                ),
                block(
                    id = "a11y:6544/0/0/0/0/3",
                    parentId = "a11y:6544/0/0/0/0",
                    depth = 5,
                    text = "Rua Card Inferior, 20\\nAvenida Destino Inferior, 30",
                    left = 80,
                    top = 900,
                    right = 980,
                    bottom = 1150,
                ),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals(
            "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
            decision.authorization?.destination,
        )
    }

    @Test
    fun splitAddressFragmentsFromDifferentCardsNeverCombine0194() {
        val packageName = "com.app99.driver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "a11y:10/0/1/0",
                    parentId = "a11y:10/0/1",
                    depth = 4,
                    text = "281, Jardim Nove de Julho",
                    left = 80,
                    top = 200,
                    right = 900,
                    bottom = 260,
                ),
                block(
                    id = "a11y:10/0/2/0",
                    parentId = "a11y:10/0/2",
                    depth = 4,
                    text = "Rua Paulino Cupertino, 120",
                    left = 80,
                    top = 275,
                    right = 900,
                    bottom = 335,
                ),
            ),
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun unknownSelectedPackageUsesSameSplitCardRecovery0194() {
        val packageName = "org.example.future.driver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "a11y:42/0/0/4/8/1",
                    parentId = "a11y:42/0/0/4/8",
                    depth = 6,
                    text = "Rua Origem Universal, 10",
                    left = 100,
                    top = 300,
                    right = 920,
                    bottom = 365,
                ),
                block(
                    id = "a11y:42/0/0/4/8/2",
                    parentId = "a11y:42/0/0/4/8",
                    depth = 6,
                    text = "Terminal Central Jardim Novo",
                    left = 100,
                    top = 380,
                    right = 920,
                    bottom = 445,
                ),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals("Terminal Central Jardim Novo", decision.authorization?.destination)
    }
'''
insert_before_last_brace(gate_test, gate_tests, "testes do gate 0.1.194")

print("apply_farol_universal_card_fix_0194=passed")
