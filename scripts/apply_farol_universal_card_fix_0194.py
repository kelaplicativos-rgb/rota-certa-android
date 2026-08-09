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
    '''object UniversalScreenAddressParser {
    const val SECOND_PLACE_BOUNDARY_MARKER_0194 = "UNIVERSAL_SECOND_PLACE_BOUNDARY_0194"
    private val strongIndependentPoiStartRegex0194 = Regex(
        "^(?:shopping|terminal|estacao|estação|aeroporto|rodoviaria|rodoviária|hospital|mercado|restaurante|hotel|pousada|escola|faculdade|universidade|posto|poupatempo|igreja|cemiterio|cemitério|loja|lojas)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val ambiguousParkStartRegex0194 = Regex(
        "^parque(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
    private val nestedLocalityParenthesisRegex0194 = Regex(
        "\\(\\s*(?:cidade|bairro|jardim|vila|distrito|municipio|município|residencial|condominio|condomínio|loteamento|centro|sitio|sítio|fazenda)(?:\\b|(?=\\s))",
        RegexOption.IGNORE_CASE,
    )
''',
    "marcador e predicados do parser 0.1.194",
)
replace_once(
    parser,
    '''        val wrappedLocalityContinuation = danglingAddressPrefix &&
            (value.contains(',') || value.contains('(')) &&
            normalized.length in 3..100
''',
    '''        val previousOpenParenthesis0194 = previous.count { it == '(' } > previous.count { it == ')' }
        val standaloneNamedPlace0194 =
            strongIndependentPoiStartRegex0194.containsMatchIn(value) ||
                (
                    ambiguousParkStartRegex0194.containsMatchIn(value) &&
                        nestedLocalityParenthesisRegex0194.containsMatchIn(value)
                    )
        val independentNamedPlace0194 =
            standaloneNamedPlace0194 &&
                isRecognizedNamedPlace(value) &&
                isCompleteNumberedAddress(previous)
        if (
            independentNamedPlace0194 &&
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
    "limite seguro entre rua completa e segundo local 0.1.194",
)

parser_test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/UniversalScreenAddressParserTest.kt"
parser_tests = '''    @Test
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
    fun genericStreetThenTerminalRemainTwoLocations0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "Rua Origem Universal, 10 (Centro, Sao Paulo - SP)\\n" +
                "Terminal Central (Centro, Sao Paulo - SP)",
        )
        assertEquals(2, addresses.size)
        assertEquals("Terminal Central (Centro, Sao Paulo - SP)", addresses.last())
    }

    @Test
    fun parkNamedNeighborhoodAfterCompleteStreetRemainsOneAddress0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "Rua Exemplo, 123\\nParque Sao Jorge, Sao Paulo - SP",
        )
        assertEquals(1, addresses.size)
        assertEquals("Rua Exemplo, 123 Parque Sao Jorge, Sao Paulo - SP", addresses.single())
    }

    @Test
    fun parkNamedNeighborhoodWithCityParenthesisRemainsOneAddress0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "Rua Exemplo, 123\\nParque Sao Jorge (Sao Paulo - SP)",
        )
        assertEquals(1, addresses.size)
        assertEquals("Rua Exemplo, 123 Parque Sao Jorge (Sao Paulo - SP)", addresses.single())
    }

    @Test
    fun barePoiWithoutGeographicEvidenceStillFailsClosed0194() {
        assertFalse(UniversalScreenAddressParser.isRecognizedAddress("Parque do Carmo"))
    }

    @Test
    fun secondStreetBehaviorRemainsUnchanged0194() {
        val addresses = UniversalScreenAddressParser.findAddresses(
            "Rua Origem Universal, 10\\nRua Destino Universal, 20",
        )
        assertEquals(2, addresses.size)
        assertEquals("Rua Destino Universal, 20", addresses.last())
    }
'''
insert_before_last_brace(parser_test, parser_tests, "testes do parser 0.1.194")

print("apply_farol_universal_card_fix_0194=passed")
