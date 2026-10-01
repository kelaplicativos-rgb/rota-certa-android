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

# ---------------------------------------------------------------------------
# Stage21: pure diagnostic replay of layer/anchor/candidate selection.
# No Android clock here so JVM tests remain deterministic.
# ---------------------------------------------------------------------------
stage21 = PKG / 'FarolCausalCorrectionStage21.kt'
s21 = stage21.read_text()
forensic_eval = r'''
    /**
     * Stage38 diagnostic replay. Runs only when requested by the service after the authoritative
     * evaluate() already returned, and therefore cannot authorize/reject/cache/route/paint.
     */
    fun forensicExplainEvaluationStage38(
        blocks: List<FarolUniversalVisualPipelineStage19.VisualBlock>,
    ): List<String> {
        val steps = ArrayList<String>(256)
        if (blocks.isEmpty()) return listOf("evaluation=NO_BLOCKS")
        data class Parsed38(val block: FarolUniversalVisualPipelineStage19.VisualBlock, val addresses: List<String>)
        fun visualTop38(block: FarolUniversalVisualPipelineStage19.VisualBlock): Int =
            block.top.takeUnless { it == Int.MAX_VALUE } ?: Int.MAX_VALUE

        steps += "blocks=${blocks.size}; sources=${blocks.groupingBy { it.source }.eachCount()}"
        val layers = blocks.asSequence()
            .filter { it.text.isNotBlank() && !it.syntheticRoot }
            .map { it.windowLayer }
            .distinct()
            .sortedDescending()
            .toList()
        steps += "layers=${layers.joinToString(",")}"

        for (layer in layers) {
            val layerBlocks = blocks.asSequence()
                .filter { it.windowLayer == layer && it.text.isNotBlank() && !it.syntheticRoot }
                .take(160)
                .toList()
            if (layerBlocks.isEmpty()) continue
            steps += "layer=$layer; blockCount=${layerBlocks.size}"
            val parsed = layerBlocks.map { block ->
                val normalized = WrappedAddressTextNormalizer.normalize(block.text)
                val rawAddresses = UniversalScreenAddressParser.findAddresses(normalized)
                val addresses = rawAddresses
                    .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
                    .map(FarolCausalLatencyStage28::trimNarrativeSuffix)
                    .filter(String::isNotBlank)
                    .distinctBy(::canonical)
                steps += "block=${block.id}; window=${block.windowId}; depth=${block.depth}; bounds=${block.left},${block.top},${block.right},${block.bottom}; text_len=${block.text.length}; parser_raw=${rawAddresses.size}; after_clean=${addresses.size}; addresses=${addresses.joinToString(" || ").take(1200)}"
                if (addresses.size < UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES) {
                    UniversalScreenAddressParser.forensicExplainStage38(normalized).take(120).forEach { parserStep ->
                        steps += "block=${block.id}; parser_rule=$parserStep"
                    }
                }
                Parsed38(block, addresses)
            }
            val addressBearing = parsed.filter { it.addresses.isNotEmpty() }
            if (addressBearing.isEmpty()) {
                steps += "layer=$layer; decision=CONTINUE; reason=no_address_bearing_block"
                continue
            }

            val anchor = if (addressBearing.any { visualTop38(it.block) != Int.MAX_VALUE }) {
                addressBearing.minWithOrNull(
                    compareBy<Parsed38> { visualTop38(it.block) }
                        .thenByDescending { it.block.depth }
                        .thenBy { it.block.text.length },
                )
            } else {
                val deepest = addressBearing.maxOf { it.block.depth }
                val specific = addressBearing.filter { it.block.depth == deepest }
                val lastDestinations = specific.map { canonical(it.addresses.last()) }.distinct()
                if (lastDestinations.size > 1) {
                    steps += "layer=$layer; decision=NULL; reason=conflicting_deepest_destinations; values=${lastDestinations.joinToString(" || ")}"
                    return steps
                }
                specific.minByOrNull { it.block.text.length }
            }
            if (anchor == null) {
                steps += "layer=$layer; decision=NULL; reason=no_anchor"
                return steps
            }
            steps += "layer=$layer; anchor=${anchor.block.id}; anchorWindow=${anchor.block.windowId}; anchorAddresses=${anchor.addresses.joinToString(" || ").take(1200)}"

            val candidates = parsed.filter { candidate ->
                val sameWindow = candidate.block.windowId == anchor.block.windowId
                val enough = candidate.addresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES
                val related = candidate.block.id == anchor.block.id ||
                    anchor.block.id.startsWith(candidate.block.id + "/") ||
                    contains(candidate.block, anchor.block)
                if (!sameWindow || !enough || !related) {
                    steps += "candidate=${candidate.block.id}; accepted=false; sameWindow=$sameWindow; enoughAddresses=$enough; relatedToAnchor=$related; addressCount=${candidate.addresses.size}"
                } else {
                    steps += "candidate=${candidate.block.id}; accepted=true; addressCount=${candidate.addresses.size}"
                }
                sameWindow && enough && related
            }
            if (candidates.isEmpty()) {
                steps += "layer=$layer; decision=NULL; reason=no_candidate_with_two_addresses_in_anchor_context"
                return steps
            }
            val bestDepth = candidates.maxOf { it.block.depth }
            val winner = candidates.filter { it.block.depth == bestDepth }
                .minWithOrNull(compareBy<Parsed38> { visualTop38(it.block) }.thenBy { it.block.text.length })
            if (winner == null) {
                steps += "layer=$layer; decision=NULL; reason=no_winner"
                return steps
            }
            val pickup = winner.addresses.first()
            val destination = winner.addresses.last()
            steps += "winner=${winner.block.id}; pickup=${pickup.take(700)}; destination=${destination.take(700)}"
            if (canonical(pickup) == canonical(destination)) {
                steps += "decision=NULL; reason=same_pickup_destination"
                return steps
            }
            val signature = DestinationAddressIdentityPolicy.signature("visual", destination)
            if (signature.isBlank()) {
                steps += "decision=NULL; reason=blank_address_signature"
                return steps
            }
            val stableBlockIdentity = "${winner.block.windowId}:${winner.addresses.joinToString("|") { canonical(it) }}"
            val evaluation = FarolUniversalVisualPipelineStage19.Evaluation(
                windowId = winner.block.windowId,
                blockId = stableBlockIdentity,
                source = winner.block.source,
                analysisText = winner.block.text.trim(),
                addresses = winner.addresses,
                pickup = pickup,
                destination = destination,
                addressSignature = signature,
                screenHash = "$stableBlockIdentity|$signature".hashCode(),
            )
            val validation = validateEvaluation(evaluation)
            steps += "semantic_validation=${validation.accepted}; reason=${validation.reason}; signature=$signature"
            steps += if (validation.accepted) "diagnostic_expected_candidate=true" else "diagnostic_expected_candidate=false"
            return steps
        }
        steps += "evaluation=NULL; reason=no_layer_produced_candidate"
        return steps
    }

'''
s21 = insert_before(s21, '    private fun contains(\n', forensic_eval, 'Stage21 forensic evaluation')
stage21.write_text(s21)


print('stage38_evaluation=PASS')
