#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("uso: apply_farol_route_priority_0195.py <source-repository>")

root = Path(sys.argv[1]).resolve()


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: esperado exatamente 1 trecho, encontrado {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def insert_before_in_function_once(
    path: Path,
    function_marker: str,
    snapshot_pattern: re.Pattern[str],
    heavy_evaluation_pattern: re.Pattern[str],
    addition: str,
    label: str,
) -> None:
    text = path.read_text(encoding="utf-8")
    if "BUBBLE_FAST_DESTINATION_DUPLICATE_SKIPPED_0195" in text:
        raise SystemExit(f"{label}: marcador 0.1.195 já existe antes da transformação")
    if "universalLastActiveReadAtElapsedMillis0187" not in text:
        raise SystemExit(f"{label}: relógio monotônico 0.1.187 ausente na fonte materializada")

    function_count = text.count(function_marker)
    if function_count != 1:
        raise SystemExit(f"{label}: esperado exatamente 1 processRideText, encontrado {function_count}")
    function_start = text.index(function_marker)
    next_private = text.find("\n    private ", function_start + len(function_marker))
    function_end = next_private if next_private >= 0 else len(text)
    region = text[function_start:function_end]

    snapshot_matches = list(snapshot_pattern.finditer(region))
    heavy_matches = list(heavy_evaluation_pattern.finditer(region))
    if len(snapshot_matches) != 1:
        raise SystemExit(
            f"{label}: declaração semântica de snapshot esperada 1 vez dentro de processRideText, "
            f"encontrada {len(snapshot_matches)}"
        )
    if len(heavy_matches) != 1:
        raise SystemExit(
            f"{label}: avaliação pesada semântica esperada 1 vez dentro de processRideText, "
            f"encontrada {len(heavy_matches)}"
        )

    snapshot_match = snapshot_matches[0]
    heavy_match = heavy_matches[0]
    if snapshot_match.start() >= heavy_match.start():
        raise SystemExit(f"{label}: snapshot não precede a avaliação pesada")

    # Insere no último ponto seguro: imediatamente antes da avaliação pesada real.
    # Assim, qualquer guarda materializada pelas versões cumulativas anteriores continua
    # executando antes do fast gate, sem depender da forma textual do RHS do snapshot.
    absolute_anchor = function_start + heavy_match.start()
    path.write_text(text[:absolute_anchor] + addition + text[absolute_anchor:], encoding="utf-8")


def create_once(path: Path, content: str, label: str) -> None:
    if path.exists():
        raise SystemExit(f"{label}: arquivo já existe: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


gradle = root / "app/build.gradle.kts"
replace_once(gradle, "versionCode = 5478", "versionCode = 5479", "versionCode 0.1.195")
replace_once(gradle, 'versionName = "0.1.194"', 'versionName = "0.1.195"', "versionName 0.1.195")

service = root / "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
function_marker = "    private suspend fun processRideText(\n"
snapshot_pattern = re.compile(
    r"(?m)^[ \t]*val[ \t]+snapshotTextChecklist13[ \t]*=",
)
heavy_evaluation_pattern = re.compile(
    r"(?ms)^[ \t]*val[ \t]+evaluationChecklist13[ \t]*=[ \t]*"
    r"withContext\([ \t\r\n]*Dispatchers\.Default[ \t\r\n]*\)[ \t\r\n]*\{[ \t\r\n]*"
    r"SimpleSavedAppFarolPolicy\.evaluate[ \t\r\n]*\(",
)
fast_gate_block = '''        val routeActive0195 = universalRouteJob?.isActive == true
        val stableDecision0195 = currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red
        if (
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = selectedPackageChecklist13,
                activePackageName = universalActiveRidePackageName,
                activeAddressSignature = universalActiveAddressSignature,
                visibleText = snapshotTextChecklist13,
                routeActive = routeActive0195,
                stableDecision = stableDecision0195,
            )
        ) {
            // Todas as guardas materializadas das versões anteriores já executaram antes daqui.
            // O destino completo já foi validado; ruído de preço/tempo/layout não repete a
            // avaliação pesada nem interfere na rota já em andamento.
            universalLastActiveReadAtElapsedMillis0187 = android.os.SystemClock.elapsedRealtime()
            UnifiedDebugEventStore.record(
                "BUBBLE_FAST_DESTINATION_DUPLICATE_SKIPPED_0195",
                selectedPackageChecklist13,
                "routeActive=$routeActive0195; stableDecision=$stableDecision0195; textHash=${snapshotTextChecklist13.hashCode()}; signatureHash=${universalActiveAddressSignature?.hashCode() ?: 0}",
            )
            return
        }
'''
insert_before_in_function_once(
    service,
    function_marker,
    snapshot_pattern,
    heavy_evaluation_pattern,
    fast_gate_block,
    "fast gate após guardas materializados e antes da análise pesada 0.1.195",
)

helper = root / "app/src/main/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195.kt"
create_once(
    helper,
    '''package br.com.mapeiaia.rotacerta

import java.text.Normalizer

/**
 * Atalho estritamente conservador para eventos repetidos do mesmo destino já confirmado.
 *
 * Ele nunca confirma um destino novo. Só pode evitar repetir o gate pesado quando:
 * 1) existe rota em andamento ou decisão verde/vermelha estável;
 * 2) o pacote atual é exatamente o mesmo pacote da decisão;
 * 3) existe uma assinatura de destino previamente produzida pelo fluxo autorizado; e
 * 4) o destino completo dessa assinatura continua presente no texto visível atual.
 *
 * Se qualquer evidência faltar, retorna false e o fluxo completo continua normalmente.
 */
object FarolDestinationFastGate0195 {
    const val MARKER = "FAROL_CONFIRMED_DESTINATION_FAST_GATE_0195"

    fun shouldSkipHeavyAnalysis(
        packageName: String,
        activePackageName: String?,
        activeAddressSignature: String?,
        visibleText: String,
        routeActive: Boolean,
        stableDecision: Boolean,
    ): Boolean {
        if (!routeActive && !stableDecision) return false
        if (packageName.isBlank() || activePackageName != packageName) return false
        if (visibleText.isBlank()) return false

        val signature = activeAddressSignature?.trim().orEmpty()
        val separator = signature.indexOf('|')
        if (separator <= 0 || separator >= signature.lastIndex) return false
        if (signature.substring(0, separator) != packageName) return false

        val destinationIdentity = normalize(signature.substring(separator + 1))
        // Evita usar fragmentos curtos/ambíguos como chave de atalho.
        if (destinationIdentity.length < 8 || destinationIdentity.count { it == ' ' } < 1) return false

        val normalizedVisibleText = normalize(visibleText)
        if (normalizedVisibleText.isBlank()) return false
        return normalizedVisibleText.contains(destinationIdentity)
    }

    internal fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (raw in decomposed) {
            val markType = Character.getType(raw)
            if (
                markType == Character.NON_SPACING_MARK.toInt() ||
                markType == Character.COMBINING_SPACING_MARK.toInt() ||
                markType == Character.ENCLOSING_MARK.toInt()
            ) continue
            val ch = raw.lowercaseChar()
            if (ch.isLetterOrDigit()) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                out.append(ch)
                pendingSpace = false
            } else if (out.isNotEmpty()) {
                pendingSpace = true
            }
        }
        return out.toString()
    }
}
''',
    "helper de deduplicação por destino confirmado 0.1.195",
)

test = root / "app/src/test/java/br/com/mapeiaia/rotacerta/FarolDestinationFastGate0195Test.kt"
create_once(
    test,
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolDestinationFastGate0195Test {
    private val pkg = "sinet.startup.indriver"
    private val signature = "$pkg|rua dos cearenses 38 parque suburbano itapevi sp"

    @Test
    fun sameConfirmedDestinationWithFareChangeSkipsWhileRouteIsActive() {
        val text = "Pedido de viagem R$ 39,50 Rua Quinze de Novembro, 110 " +
            "Rua dos Cearenses, 38 (Parque Suburbano, Itapevi - SP) Ofereça sua tarifa R$ 47"

        assertTrue(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = text,
                routeActive = true,
                stableDecision = false,
            ),
        )
    }

    @Test
    fun sameConfirmedDestinationSkipsAfterStableDecision() {
        val text = "5 min. Rua dos Cearenses, 38 (Parque Suburbano, Itapevi - SP) R$ 44"

        assertTrue(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = text,
                routeActive = false,
                stableDecision = true,
            ),
        )
    }

    @Test
    fun accentAndPunctuationDifferencesStillMatchExactDestinationIdentity() {
        val accentSignature = "$pkg|avenida são joão 120 república são paulo sp"
        val text = "Destino: Avenida São João, 120 (República, São Paulo - SP)"

        assertTrue(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = accentSignature,
                visibleText = text,
                routeActive = true,
                stableDecision = false,
            ),
        )
    }

    @Test
    fun changedDestinationNeverSkipsHeavyAnalysis() {
        val text = "Pedido de viagem Rua Nova, 20 Rua Destino Diferente, 900 (Centro, Osasco - SP)"

        assertFalse(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = text,
                routeActive = true,
                stableDecision = false,
            ),
        )
    }

    @Test
    fun closedCardNeverSkipsHeavyAnalysis() {
        assertFalse(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = "Você está online. Procurando viagens próximas.",
                routeActive = false,
                stableDecision = true,
            ),
        )
    }

    @Test
    fun differentPackageNeverReusesAnotherAppsDestination() {
        val text = "Rua dos Cearenses, 38 (Parque Suburbano, Itapevi - SP)"

        assertFalse(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = "com.app99.driver",
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = text,
                routeActive = true,
                stableDecision = false,
            ),
        )
    }

    @Test
    fun withoutRouteOrStableDecisionFullAnalysisAlwaysRuns() {
        val text = "Rua dos Cearenses, 38 (Parque Suburbano, Itapevi - SP)"

        assertFalse(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = signature,
                visibleText = text,
                routeActive = false,
                stableDecision = false,
            ),
        )
    }

    @Test
    fun shortAmbiguousIdentityCannotActivateFastGate() {
        val text = "Centro"

        assertFalse(
            FarolDestinationFastGate0195.shouldSkipHeavyAnalysis(
                packageName = pkg,
                activePackageName = pkg,
                activeAddressSignature = "$pkg|centro",
                visibleText = text,
                routeActive = true,
                stableDecision = false,
            ),
        )
    }
}
''',
    "testes do fast gate 0.1.195",
)

print("apply_farol_route_priority_0195=passed")
