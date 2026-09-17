#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
APP = ROOT / "app"
MAIN = APP / "src/main/java/br/com/mapeiaia/rotacerta"
TEST = APP / "src/test/java/br/com/mapeiaia/rotacerta"
SERVICE = MAIN / "LiveRideAccessibilityService.kt"
PARSER = MAIN / "RideTextParser.kt"
GRADLE = APP / "build.gradle.kts"
MARKER = "farol_unified_visual_0_1_168"


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        fail(f"{label}: esperado 1 ocorrência, encontrado {count}")
    return text.replace(old, new, 1)


def find_matching(text: str, opening: int, opener: str, closer: str) -> int:
    depth = 0
    quote: str | None = None
    escaped = False
    for index in range(opening, len(text)):
        char = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
            continue
        if char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                return index
    fail(f"delimitador {opener}{closer} sem fechamento")


def enclosing_function(text: str, marker: str) -> tuple[int, int, int]:
    marker_at = text.find(marker)
    if marker_at < 0:
        fail(f"marcador de função não encontrado: {marker}")
    candidates = list(
        re.finditer(
            r"(?m)^\s*(?:(?:private|internal|public|protected|override|final|open)\s+)*(?:suspend\s+)?fun\s+[A-Za-z_][A-Za-z0-9_]*\s*\(",
            text[:marker_at],
        )
    )
    if not candidates:
        fail(f"função não encontrada antes de {marker}")
    start = candidates[-1].start()
    body_open = text.find("{", candidates[-1].end())
    if body_open < 0 or body_open > marker_at:
        fail(f"abertura da função não encontrada para {marker}")
    body_close = find_matching(text, body_open, "{", "}")
    return start, body_open, body_close


KOTLIN = r'''package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale

/**
 * Núcleo visual puro da 0.1.168.
 *
 * Ele não autoriza verde/vermelho sozinho. Sua função é entregar ao parser apenas
 * um bloco coerente de card, estabilizar assinaturas visuais e impedir que um
 * fragmento de rua seja enviado ao serviço de rotas.
 */
object FarolUnifiedVisual0168 {
    const val CONTRACT_MARKER: String = "farol_unified_visual_0_1_168"

    private val whitespace = Regex("\\s+")
    private val volatileCountdown = Regex("(?i)\\b\\d{1,3}\\s*(?:seg(?:undos?)?|s|min(?:utos?)?|h)\\b")
    private val volatileMoney = Regex("(?i)R\\$\\s*\\d+(?:[.,]\\d{1,2})?")
    private val volatileDistance = Regex("(?i)~?\\s*\\d+(?:[.,]\\d+)?\\s*km\\b")
    private val resourceId = Regex("(?i)\\b[A-Za-z0-9_.]+:id/[A-Za-z0-9_\\-]+\\b")
    private val streetStart = Regex(
        "(?i)^(?:rua|r\\.?|avenida|av\\.?|alameda|travessa|estrada|rodovia|praça|praca|largo|viela|marginal)\\b",
    )
    private val cardStart = Regex(
        "(?i)^(?:pedido de viagem|nova (?:solicitação|solicitacao)|solicitação de viagem|solicitacao de viagem|corrida disponível|corrida disponivel|oferta de corrida)\\b",
    )
    private val cardAction = Regex(
        "(?i)\\b(?:aceitar(?: por)?|recusar|pular|ofereça sua tarifa|ofereca sua tarifa|confirmar corrida|iniciar viagem)\\b",
    )
    private val rideSignal = Regex(
        "(?i)\\b(?:pedido de viagem|corrida|viagem|embarque|destino|passageiro|uberx|99|preço justo|preco justo)\\b",
    )
    private val locationSignal = Regex(
        "(?i)\\b(?:cidade|jardim|vila|centro|bairro|parque|residencial|industrial|são paulo|sao paulo|santo andré|santo andre|sp|mg|rj|brasil)\\b",
    )
    private val namedPlaceSignal = Regex(
        "(?i)\\b(?:hotel|shopping|hospital|aeroporto|terminal|estação|estacao|condomínio|condominio|mercado|atacadista|restaurante|escola|faculdade|igreja|casa|empresa|posto)\\b",
    )

    fun fromVisionText(result: com.google.mlkit.vision.text.Text): String {
        val blocks = result.textBlocks.sortedWith(
            compareBy(
                { it.boundingBox?.top ?: Int.MAX_VALUE },
                { it.boundingBox?.left ?: Int.MAX_VALUE },
            ),
        )
        if (blocks.isEmpty()) return result.text
        return blocks.joinToString("\n\n") { block ->
            block.lines
                .sortedWith(
                    compareBy(
                        { it.boundingBox?.top ?: Int.MAX_VALUE },
                        { it.boundingBox?.left ?: Int.MAX_VALUE },
                    ),
                )
                .joinToString("\n") { it.text.trim() }
        }
    }

    fun normalizeForAnalysis(raw: String): String {
        if (raw.isBlank()) return ""
        val cleaned = raw
            .replace('\u00A0', ' ')
            .replace(resourceId, " ")
            .replace(Regex("(?i)\\s+(Pedido de viagem|Nova solicitação|Nova solicitacao|Solicitação de viagem|Solicitacao de viagem|Corrida disponível|Corrida disponivel)\\s+"), "\n$1 ")
            .replace(Regex("(?i)\\s+(Aceitar por|Aceitar|Pular|Recusar|Ofereça sua tarifa|Ofereca sua tarifa)\\s+"), "\n$1 ")
            .lines()
            .joinToString("\n") { whitespace.replace(it.trim(), " ") }
            .trim()
        if (cleaned.isBlank()) return ""

        val cards = splitCards(cleaned)
        val selected = if (cards.size <= 1) cleaned else cards.maxWithOrNull(
            compareBy<String> { scoreCard(it) }.thenByDescending { -it.length },
        ) ?: cleaned

        return selected
            .lines()
            .filterNot { line -> isClearlyTruncatedStreet(line.trim()) }
            .joinToString("\n")
            .trim()
    }

    fun semanticHash(raw: String): Int = semanticSignature(raw).hashCode()

    fun semanticSignature(raw: String): String = fold(
        normalizeForAnalysis(raw)
            .replace(volatileCountdown, " ")
            .replace(volatileMoney, " ")
            .replace(volatileDistance, " ")
            .replace(whitespace, " ")
            .trim(),
    )

    fun isClearlyTruncatedStreet(value: String): Boolean {
        val text = whitespace.replace(value.trim(), " ")
        if (!streetStart.containsMatchIn(text)) return false
        if (Regex("\\d").containsMatchIn(text)) return false
        val commaCount = text.count { it == ',' }
        val locationCount = locationSignal.findAll(text).count()
        val hasStateSuffix = Regex("(?i)(?:-|,)\\s*[A-Z]{2}\\b").containsMatchIn(text)
        return commaCount < 2 && locationCount < 2 && !hasStateSuffix
    }

    fun isNamedPlaceWithLocation(value: String): Boolean {
        val text = whitespace.replace(value.trim(), " ")
        if (text.length !in 8..220 || streetStart.containsMatchIn(text)) return false
        val opening = text.indexOf('(')
        val closing = text.lastIndexOf(')')
        val parentheticalLocation = opening > 1 && closing > opening + 3 &&
            locationSignal.containsMatchIn(text.substring(opening + 1, closing))
        val namedWithLocation = namedPlaceSignal.containsMatchIn(text) && locationSignal.containsMatchIn(text)
        val wordCount = Regex("[\\p{L}]{2,}").findAll(text).count()
        return wordCount >= 3 && (parentheticalLocation || namedWithLocation)
    }

    private fun splitCards(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = mutableListOf<String>()
        text.lines().forEach { line ->
            if (cardStart.containsMatchIn(line.trim()) && current.isNotEmpty()) {
                result += current.joinToString("\n").trim()
                current.clear()
            }
            current += line
        }
        if (current.isNotEmpty()) result += current.joinToString("\n").trim()
        return result.filter { it.isNotBlank() }
    }

    private fun scoreCard(card: String): Int {
        var score = 0
        if (cardAction.containsMatchIn(card)) score += 5
        if (rideSignal.containsMatchIn(card)) score += 4
        score += Regex("(?i)\\b(?:rua|avenida|av\\.?|alameda|travessa|estrada|rodovia)\\b").findAll(card).count() * 3
        score += Regex("\\d{1,5}").findAll(card).count().coerceAtMost(4)
        score += locationSignal.findAll(card).count().coerceAtMost(4)
        if (card.length > 2_000) score -= 5
        return score
    }

    private fun fold(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}
'''

TEST_KOTLIN = r'''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolUnifiedVisual0168Test {
    @Test
    fun truncatedStreetNeverBecomesRouteDestination() {
        assertTrue(FarolUnifiedVisual0168.isClearlyTruncatedStreet("Rua Joaquim"))
        assertFalse(FarolUnifiedVisual0168.isClearlyTruncatedStreet("Rua Joaquim Meira de Siqueira, 260, São Paulo - SP"))
    }

    @Test
    fun namedPickupInsideConfirmedCardIsAccepted() {
        assertTrue(FarolUnifiedVisual0168.isNamedPlaceWithLocation("Casa Vip (Cidade São Mateus)"))
        assertTrue(FarolUnifiedVisual0168.isNamedPlaceWithLocation("G M Hotel (Jardim Três Marias, São Paulo - SP)"))
        assertFalse(FarolUnifiedVisual0168.isNamedPlaceWithLocation("Dagmar 4.81 (271)"))
    }

    @Test
    fun countdownDoesNotCreateANewSemanticCard() {
        val first = "Pedido de viagem 38 seg. Rua A, 10 Rua B, 20 Aceitar por R$ 36"
        val second = "Pedido de viagem 51 seg. Rua A, 10 Rua B, 20 Aceitar por R$ 45"
        assertEquals(
            FarolUnifiedVisual0168.semanticSignature(first),
            FarolUnifiedVisual0168.semanticSignature(second),
        )
    }

    @Test
    fun listCardsAreNotMixed() {
        val text = """
            Pedido de viagem
            Hotel Alfa (Centro, São Paulo - SP)
            Rua A, 10, São Paulo - SP
            Aceitar por R$ 30
            Pular
            Pedido de viagem
            Hotel Beta (Vila Mariana, São Paulo - SP)
            Rua B, 20, São Paulo - SP
            Aceitar por R$ 40
            Pular
        """.trimIndent()
        val selected = FarolUnifiedVisual0168.normalizeForAnalysis(text)
        assertTrue(selected.contains("Pedido de viagem"))
        assertFalse(selected.contains("Rua A, 10") && selected.contains("Rua B, 20"))
    }

    @Test
    fun incompleteOcrLineIsRemovedBeforeParser() {
        val normalized = FarolUnifiedVisual0168.normalizeForAnalysis(
            "Nova solicitação\nRua Joaquim\nAceitar",
        )
        assertFalse(normalized.lines().any { it.trim() == "Rua Joaquim" })
    }
}
'''

CONTRACT_TEST = r'''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolUnifiedVisualCriticalPath0168Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun visualNormalizationIsInTheLiveProcessingPath() {
        assertTrue(service.contains("FarolUnifiedVisual0168.normalizeForAnalysis"))
        assertTrue(service.contains("FarolUnifiedVisual0168.semanticHash"))
    }

    @Test
    fun firstOcrFrameIsNotArtificiallyDelayed() {
        val marker = service.indexOf("OCR_FALLBACK_SCHEDULED")
        assertTrue(marker >= 0)
        val window = service.substring(marker, (marker + 5_000).coerceAtMost(service.length))
        assertFalse(window.contains("postDelayed("))
    }
}
'''

# Idempotence: a completed 0.1.168 tree is accepted without rewriting.
if GRADLE.exists() and MARKER in SERVICE.read_text(encoding="utf-8"):
    if 'versionName = "0.1.168"' not in GRADLE.read_text(encoding="utf-8"):
        fail("marcador 0.1.168 existe, mas a versão não corresponde")
    print("Rota Certa 0.1.168 já aplicada")
    raise SystemExit(0)

for required in (SERVICE, PARSER, GRADLE):
    if not required.exists():
        fail(f"arquivo obrigatório ausente: {required}")

MAIN.mkdir(parents=True, exist_ok=True)
TEST.mkdir(parents=True, exist_ok=True)
(MAIN / "FarolUnifiedVisual0168.kt").write_text(KOTLIN, encoding="utf-8")
(TEST / "FarolUnifiedVisual0168Test.kt").write_text(TEST_KOTLIN, encoding="utf-8")
(TEST / "FarolUnifiedVisualCriticalPath0168Test.kt").write_text(CONTRACT_TEST, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, 'versionName = "0.1.167"', 'versionName = "0.1.168"', "versionName")
gradle = replace_once(gradle, "versionCode = 5280", "versionCode = 5290", "versionCode")
GRADLE.write_text(gradle, encoding="utf-8")

service = SERVICE.read_text(encoding="utf-8")

# Normalize and segment the text at the single live processing entry point.
func_start, body_open, body_close = enclosing_function(service, "BUBBLE_PROCESS_ENTER")
signature = service[func_start:body_open]
params = list(re.finditer(r"\b([A-Za-z_][A-Za-z0-9_]*text[A-Za-z0-9_]*)\s*:\s*String\b", signature, re.I))
if not params:
    fail("parâmetro textual do processamento ao vivo não encontrado")
selected_param = next((m for m in params if m.group(1).lower() == "text"), params[-1])
original_name = selected_param.group(1)
raw_name = f"{original_name}Raw0168"
absolute_start = func_start + selected_param.start(1)
absolute_end = func_start + selected_param.end(1)
service = service[:absolute_start] + raw_name + service[absolute_end:]
body_open += len(raw_name) - len(original_name)
injection = (
    f"\n        val {original_name} = FarolUnifiedVisual0168.normalizeForAnalysis({raw_name})"
    f" // {MARKER}\n"
)
service = service[: body_open + 1] + injection + service[body_open + 1 :]

# Stable semantic hash: timers, prices and the countdown cannot reopen the same card.
service, semantic_replacements = re.subn(
    r"\b([A-Za-z_][A-Za-z0-9_]*(?:text|Text))\.hashCode\(\)",
    r"FarolUnifiedVisual0168.semanticHash(\1)",
    service,
)
if semantic_replacements < 1:
    fail("nenhum hash textual do caminho ao vivo foi estabilizado")

# Preserve ML Kit spatial blocks instead of flattening the entire screen into one sentence.
success_listener = re.search(r"addOnSuccessListener\s*\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*->", service)
if success_listener:
    result_name = success_listener.group(1)
    target = f"{result_name}.text"
    if target in service[success_listener.start() : success_listener.start() + 8_000]:
        after = service[success_listener.start() :]
        after = after.replace(target, f"FarolUnifiedVisual0168.fromVisionText({result_name})", 1)
        service = service[: success_listener.start()] + after

# The first visual frame is posted immediately. Later events may be conflated, but cannot
# postpone the frame that contains a short-lived popup.
ocr_start, ocr_open, ocr_close = enclosing_function(service, "OCR_FALLBACK_SCHEDULED")
ocr_body = service[ocr_open : ocr_close + 1]
post_at = ocr_body.find(".postDelayed(")
if post_at < 0:
    fail("postDelayed do fallback OCR não encontrado")
absolute_post = ocr_open + post_at
paren_open = service.find("(", absolute_post)
paren_close = find_matching(service, paren_open, "(", ")")
args = service[paren_open + 1 : paren_close]
depth_round = depth_curly = depth_square = 0
quote = None
escaped = False
comma_at = -1
for index, char in enumerate(args):
    if quote is not None:
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == quote:
            quote = None
        continue
    if char in ('"', "'"):
        quote = char
    elif char == "(": depth_round += 1
    elif char == ")": depth_round -= 1
    elif char == "{": depth_curly += 1
    elif char == "}": depth_curly -= 1
    elif char == "[": depth_square += 1
    elif char == "]": depth_square -= 1
    elif char == "," and depth_round == depth_curly == depth_square == 0:
        comma_at = index
if comma_at < 0:
    fail("argumento de atraso do postDelayed OCR não encontrado")
first_argument = args[:comma_at].rstrip()
method_start = service.rfind(".postDelayed", absolute_post, paren_open)
service = service[:method_start] + ".post(" + first_argument + ")" + service[paren_close + 1 :]

SERVICE.write_text(service, encoding="utf-8")

# Tighten the parser at its existing address-candidate predicate. Named POIs with real
# locality are accepted; a street fragment is rejected before any route request.
parser = PARSER.read_text(encoding="utf-8")
functions = []
for match in re.finditer(
    r"(?m)^\s*(?:(?:private|internal|public|protected)\s+)*fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*:\s*Boolean\s*\{",
    parser,
):
    param = re.search(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*:\s*String\b", match.group(2))
    if not param:
        continue
    opening = parser.find("{", match.start(), match.end() + 1)
    closing = find_matching(parser, opening, "{", "}")
    body = parser[opening : closing + 1].lower()
    name = match.group(1).lower()
    score = 0
    for token in ("address", "endereco", "endereço", "location", "local", "candidate", "valid"):
        if token in name: score += 5
    for token in ("rua", "avenida", "logradouro", "cep", "bairro"):
        if token in body: score += 2
    functions.append((score, opening, param.group(1), match.group(1)))
if not functions:
    fail("predicado de endereço do parser não encontrado")
score, opening, parameter, function_name = max(functions, key=lambda item: item[0])
if score < 2:
    fail(f"predicado de endereço ambíguo: {function_name} score={score}")
parser_injection = (
    f"\n        if (FarolUnifiedVisual0168.isClearlyTruncatedStreet({parameter})) return false"
    f" // {MARKER}\n"
    f"        if (FarolUnifiedVisual0168.isNamedPlaceWithLocation({parameter})) return true\n"
)
parser = parser[: opening + 1] + parser_injection + parser[opening + 1 :]
PARSER.write_text(parser, encoding="utf-8")

print("Rota Certa 0.1.168 aplicada: visão híbrida, card coerente e destino seguro")
