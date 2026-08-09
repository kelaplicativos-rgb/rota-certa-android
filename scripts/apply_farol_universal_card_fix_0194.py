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
replace_once(
    parser,
    '''        val wrappedLocalityContinuation = danglingAddressPrefix &&
            (value.contains(',') || value.contains('(')) &&
            normalized.length in 3..100
''',
    '''        val previousOpenParenthesis0194 = previous.count { it == '(' } > previous.count { it == ')' }
        val independentCurrentPlace0194 = isRecognizedNamedPlace(value) ||
            (isRecognizedContextualPlace0194(value) &&
                (isCompleteNumberedAddress(previous) || isRecognizedContextualPlace0194(previous)))
        if (
            independentCurrentPlace0194 &&
            !previousOpenParenthesis0194 &&
            !danglingAddressPrefix &&
            !previous.endsWith(',') &&
            !previous.endsWith('-') &&
            !previous.endsWith('–') &&
            !previous.endsWith('—')
        ) return false

        val wrappedLocalityContinuation = danglingAddressPrefix &&
            (value.contains(',') || value.contains('(')) &&
            normalized.length in 3..100
''',
    "separação de segundo local independente 0.1.194",
)

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

    @Test
    fun realWrappedLocalityContinuationRemainsOneAddress0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "Rua Erundina (Jardim Rodolfo\\nPirani, Sao Paulo - SP)",
        )
        assertEquals(1, addresses.size)
        assertEquals("Rua Erundina (Jardim Rodolfo Pirani, Sao Paulo - SP)", addresses.single())
    }

    @Test
    fun twoContextualPlacesOnSeparateLinesRemainDistinct0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "281, Jardim Nove de Julho\\nCCB Jardim Nove de Julho",
        )
        assertEquals(2, addresses.size)
    }
'''
insert_before_last_brace(parser_test, parser_tests, "testes do parser 0.1.194")

gate_test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolRealDevice0188Test.kt"
gate_tests = '''    @Test
    fun realInDrivePoiInsideOneCoherentCardIsAuthorized0194() {
        val packageName = "sinet.startup.indriver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "card",
                    text = "R. Carlos Vivaldi, 197 (Cidade Sao Mateus, Sao Paulo - SP, 03965-030)\\n" +
                        "Parque do Carmo (Jardim Nossa Senhora do Carmo, Sao Paulo - SP)",
                    source = FarolEvidenceSource0188.Accessibility,
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
    fun contextual99LocationsInsideOneOcrCardAreAuthorized0194() {
        val packageName = "com.app99.driver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "ocr-card",
                    text = "281, Jardim Nove de Julho\\nCCB Jardim Nove de Julho",
                    source = FarolEvidenceSource0188.Ocr,
                ),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals("CCB Jardim Nove de Julho", decision.authorization?.destination)
    }

    @Test
    fun unknownSelectedPackageUsesSameContextualPlaceParser0194() {
        val packageName = "org.example.future.driver"
        val decision = FarolRealDeviceGate0188.evaluate(
            packageName,
            setOf(packageName),
            listOf(
                block(
                    id = "future-card",
                    text = "Rua Origem Universal, 10\\nCentro Empresarial Jardim Novo",
                    source = FarolEvidenceSource0188.Ocr,
                ),
            ),
        )
        assertTrue(decision.authorized)
        assertEquals("Centro Empresarial Jardim Novo", decision.authorization?.destination)
    }
'''
insert_before_last_brace(gate_test, gate_tests, "testes do gate 0.1.194")

print("apply_farol_universal_card_fix_0194=passed")