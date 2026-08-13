#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
PATCH_ROOT = Path(__file__).resolve().parents[1]

def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)

def insert_before(text: str, anchor: str, addition: str, label: str) -> str:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 anchor, got {count}')
    return text.replace(anchor, addition + anchor, 1)


helper = PATCH_ROOT / 'stage38/FarolMaximumForensicsStage38.kt'
if not helper.exists():
    raise SystemExit('missing Stage38 helper')
(PKG / 'FarolMaximumForensicsStage38.kt').write_text(helper.read_text())

# ---------------------------------------------------------------------------
# Universal parser: diagnostic mirror only. Production findAddresses is untouched.
# ---------------------------------------------------------------------------
parser = PKG / 'UniversalScreenAddressParser.kt'
p = parser.read_text()
forensic_parser = r'''
    /** Stage38 diagnostic mirror. It never participates in the production result. */
    fun forensicExplainStage38(text: String): List<String> {
        if (text.isBlank()) return listOf("parser_input_blank=true")
        val steps = ArrayList<String>(96)
        val lines = text.lines().flatMap(::splitAddressSegments).filter { it.length >= 4 }
        steps += "input_len=${text.length}; input_hash=${text.hashCode()}; segments=${lines.size}"
        val candidates = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val raw = normalizeLine(lines[index])
            val current = cleanAddressSegment(lines[index])
            val noiseReason = when {
                fileRegex.containsMatchIn(current) -> "file_noise"
                calendarRegex.containsMatchIn(current) -> "calendar_noise"
                uiNoiseRegex.containsMatchIn(current) -> "ui_noise"
                transactionNoiseRegex.containsMatchIn(current) -> "transaction_noise"
                else -> "none"
            }
            val recognized = isRecognizedAddress(current)
            val potentialNamed = isPotentialNamedPlacePrefix(current)
            val potentialStreet = isPotentialStreetPrefix(current)
            steps += "segment=$index; raw=${raw.take(500)}; cleaned=${current.take(500)}; noise=$noiseReason; recognized=$recognized; potentialNamed=$potentialNamed; potentialStreetPrefix=$potentialStreet"
            val canStart = recognized || potentialNamed || potentialStreet
            if (!canStart) {
                val structureReason = when {
                    current.length < 5 -> "too_short"
                    noiseReason != "none" -> noiseReason
                    streetStartRegex.containsMatchIn(current) || parenthesizedStreetRegex.containsMatchIn(current) -> "street_lead_but_no_meaningful_name"
                    namedPlaceStartRegex.containsMatchIn(current) -> "named_place_without_required_locality_or_meaningful_words"
                    else -> "no_recognized_address_lead"
                }
                steps += "segment=$index; decision=REJECT_START; reason=$structureReason"
                index += 1
                continue
            }

            val startedFromDanglingStreetPrefix = potentialStreet
            val parts = mutableListOf(current)
            var nextIndex = index + 1
            while (nextIndex < lines.size && parts.size < 3) {
                val preserveNamedPlaceContinuation = isPotentialNamedPlacePrefix(parts.first()) || isPotentialStreetPrefix(parts.first())
                val next = if (preserveNamedPlaceContinuation) normalizeLine(lines[nextIndex]) else cleanAddressSegment(lines[nextIndex])
                val continuation = looksLikeContinuation(next, parts.last())
                steps += "segment=$index; continuation_index=$nextIndex; previous=${parts.last().take(350)}; next=${next.take(350)}; accepted=$continuation"
                if (!continuation) break
                parts += next
                nextIndex += 1
            }
            val joinedRaw = parts.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\.{2,}$"), "")
                .trim(' ', ',', '-', '–', '—')
            val joined = if (startedFromDanglingStreetPrefix) {
                val openingParenthesis = joinedRaw.indexOf('(')
                if (openingParenthesis >= 0) joinedRaw.substring(openingParenthesis + 1).trim(' ', ',', '-', '–', '—', ')') else joinedRaw
            } else joinedRaw
            val finalRecognized = joined.length >= 5 && isRecognizedAddress(joined)
            val numbered = if (finalRecognized) isCompleteNumberedAddress(joined) else false
            steps += "segment=$index; joined=${joined.take(700)}; finalRecognized=$finalRecognized; completeNumbered=$numbered; parts=${parts.size}"
            if (finalRecognized) {
                candidates += joined
                steps += "segment=$index; decision=ADDRESS_ACCEPT; value=${joined.take(700)}"
            } else {
                steps += "segment=$index; decision=ADDRESS_REJECT_AFTER_JOIN; reason=recognition_failed_after_join"
            }
            index = nextIndex
        }
        val distinct = candidates.distinctBy(::canonical)
        steps += "parser_final_count=${distinct.size}; parser_final=${distinct.joinToString(" || ").take(1200)}"
        return steps
    }

'''
p = insert_before(p, '    private fun isPotentialStreetPrefix(value: String): Boolean {\n', forensic_parser, 'parser forensic mirror')
parser.write_text(p)


print('stage38_parser=PASS')
