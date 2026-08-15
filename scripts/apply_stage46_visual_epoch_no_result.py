#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'
PATCH_ROOT = Path(__file__).resolve().parents[1]
HELPER = PATCH_ROOT / 'stage46/FarolVisualEpochNoResultStage46.kt'

if not HELPER.exists():
    raise SystemExit('missing Stage46 helper')
(PKG / HELPER.name).write_text(HELPER.read_text(encoding='utf-8'), encoding='utf-8')


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


s = SERVICE.read_text(encoding='utf-8')

s = once(
    s,
    '    private val stage36BindingWorkToken = LinkedHashMap<String, FarolRuntimeAuthorityStage36.WorkToken>()\n',
    '    private val stage36BindingWorkToken = LinkedHashMap<String, FarolRuntimeAuthorityStage36.WorkToken>()\n'
    '    private val stage46BindingSurfaceToken = LinkedHashMap<String, FarolVisualEpochNoResultStage46.SurfaceToken>()\n',
    'Stage46 binding surface map',
)

s = once(
    s,
    '        val visualWindowIdStage19 = stage19ActiveWindowId ?: runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0\n',
    '        val visualWindowIdStage19 = stage19ActiveWindowId ?: runCatching { rootInActiveWindow?.windowId }.getOrNull() ?: 0\n'
    '        val surfaceTokenStage46 = FarolVisualEpochNoResultStage46.captureSurface(\n'
    '            currentRootPackageName(), eventPackageStage19, visualWindowIdStage19,\n'
    '        )\n'
    '        FarolMaximumForensicsStage38.record(\n'
    '            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_SURFACE_CAPTURED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '            details = "surfacePackage=${surfaceTokenStage46.packageName.orEmpty()}; surfaceWindow=${surfaceTokenStage46.windowId}; root=${currentRootPackageName().orEmpty()}",\n'
    '        )\n',
    'Stage46 OCR surface capture',
)

ocr_call = 'isStage36WorkFresh(workTokenStage36)'
ocr_count = s.count(ocr_call)
if ocr_count < 4:
    raise SystemExit(f'Stage46 expected >=4 Stage36 OCR freshness calls, got {ocr_count}')
s = s.replace(ocr_call, 'isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)')

s = once(
    s,
    '    private fun requestUniversalScreenshotStage19(\n',
    '''    private fun isStage46OcrWorkFresh(
        tokenStage36: FarolRuntimeAuthorityStage36.WorkToken,
        surfaceStage46: FarolVisualEpochNoResultStage46.SurfaceToken,
    ): Boolean {
        val runtimeFreshStage46 = isStage36WorkFresh(tokenStage36)
        val surfaceFreshStage46 = FarolVisualEpochNoResultStage46.surfaceFresh(surfaceStage46, currentRootPackageName())
        if (runtimeFreshStage46 && !surfaceFreshStage46) {
            FarolMaximumForensicsStage38.record(
                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_STALE_OCR_SURFACE_DROPPED", currentRootPackageName(),
                details = "captured=${surfaceStage46.packageName.orEmpty()}; current=${currentRootPackageName().orEmpty()}; window=${surfaceStage46.windowId}",
            )
        }
        return runtimeFreshStage46 && surfaceFreshStage46
    }

    private fun requestUniversalScreenshotStage19(
''',
    'Stage46 OCR fresh helper',
)

old_fragments = '''                                val fragmentsStage19 = structuredStage19.blocks.take(120).mapIndexedNotNull { indexStage19, blockStage19 ->
                                    blockStage19.text.takeIf(String::isNotBlank)?.let {
                                        FarolSpatialFragment0189(
                                            id = "stage19-ocr:$serialStage19/$indexStage19",
                                            text = it,
                                            left = blockStage19.left,
                                            top = blockStage19.top,
                                            right = blockStage19.right,
                                            bottom = blockStage19.bottom,
                                        )
                                    }
                                }
'''
new_fragments = '''                                val fragmentsStage19 = structuredStage19.blocks.take(120).mapIndexedNotNull { indexStage19, blockStage19 ->
                                    val textStage46 = blockStage19.text.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
                                    if (FarolVisualEpochNoResultStage46.shouldDropSelfOverlayDecimal(
                                            textStage46,
                                            blockStage19.left, blockStage19.top, blockStage19.right, blockStage19.bottom,
                                            bitmapStage19?.width ?: 0, bitmapStage19?.height ?: 0,
                                        )
                                    ) {
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_SELF_OVERLAY_OCR_FRAGMENT_DROPPED", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "text=${textStage46.take(80)}; bounds=${blockStage19.left},${blockStage19.top},${blockStage19.right},${blockStage19.bottom}",
                                        )
                                        null
                                    } else {
                                        FarolSpatialFragment0189(
                                            id = "stage19-ocr:$serialStage19/$indexStage19",
                                            text = textStage46,
                                            left = blockStage19.left,
                                            top = blockStage19.top,
                                            right = blockStage19.right,
                                            bottom = blockStage19.bottom,
                                        )
                                    }
                                }
'''
s = once(s, old_fragments, new_fragments, 'Stage46 pre-cluster self overlay filter')

s = once(
    s,
    '                                        val reconstructionStage45 = FarolOcrMultilineAddressStage45.reconstruct(groupStage19.text)\n',
    '                                        val sanitizedStage46 = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(groupStage19.text)\n'
    '                                        if (sanitizedStage46.changed) {\n'
    '                                            FarolMaximumForensicsStage38.record(\n'
    '                                                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_OCR_RECONSTRUCTION_DECONTAMINATED", eventPackageStage19, cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",\n'
    '                                                details = "group=${groupStage19.id}; decimals=${sanitizedStage46.removedStandaloneDecimals}; closures=${sanitizedStage46.syntheticClosures}; before=${groupStage19.text.replace("\\n", " | ").take(900)}; after=${sanitizedStage46.text.replace("\\n", " | ").take(900)}",\n'
    '                                            )\n'
    '                                        }\n'
    '                                        val reconstructionStage45 = FarolOcrMultilineAddressStage45.reconstruct(sanitizedStage46.text)\n',
    'Stage46 decontamination before Stage45',
)

s = once(
    s,
    '                                val evaluationStage19 = withContext(Dispatchers.Default) {\n                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)\n                                }\n',
    '                                var evaluationStage19 = withContext(Dispatchers.Default) {\n                                    FarolUniversalVisualPipelineStage19.evaluate(blocksStage19)\n                                }\n',
    'Stage46 mutable OCR evaluation',
)

recovery_anchor = '''                                if (!isStage46OcrWorkFresh(workTokenStage36, surfaceTokenStage46)) {
                                    FarolVisualIdentityStage23.Metrics.increment("ocrStaleAfterEvaluate")
'''
recovery = '''                                if (evaluationStage19 == null) {
                                    val pairBandsStage46 = FarolVisualEpochNoResultStage46.buildLocalAddressPairBands(
                                        fragmentsStage19.map { fragmentStage46 ->
                                            FarolVisualEpochNoResultStage46.Fragment(
                                                fragmentStage46.id, fragmentStage46.text,
                                                fragmentStage46.left, fragmentStage46.top, fragmentStage46.right, fragmentStage46.bottom,
                                            )
                                        },
                                    )
                                    FarolMaximumForensicsStage38.record(
                                        SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_RECOVERY_ATTEMPT", eventPackageStage19,
                                        cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                        details = "pairBands=${pairBandsStage46.size}; fragments=${fragmentsStage19.size}",
                                    )
                                    for (bandStage46 in pairBandsStage46) {
                                        val sanitizedBandStage46 = FarolVisualEpochNoResultStage46.sanitizeForReconstruction(bandStage46.text)
                                        val rebuiltBandStage46 = FarolOcrMultilineAddressStage45.reconstructClusterText(sanitizedBandStage46.text)
                                        val blockStage46 = FarolUniversalVisualPipelineStage19.VisualBlock(
                                            id = "stage46-pair:$serialStage19:${bandStage46.index}",
                                            metadataPackageName = eventPackageStage19,
                                            windowId = visualWindowIdStage19,
                                            windowLayer = Int.MAX_VALUE,
                                            depth = 1,
                                            text = rebuiltBandStage46,
                                            source = FarolUniversalVisualPipelineStage19.Source.Ocr,
                                            left = bandStage46.left,
                                            top = bandStage46.top,
                                            right = bandStage46.right,
                                            bottom = bandStage46.bottom,
                                        )
                                        val candidateStage46 = withContext(Dispatchers.Default) {
                                            FarolCausalCorrectionStage21.evaluate(listOf(blockStage46))
                                        }
                                        val semanticStage46 = candidateStage46?.let(FarolCausalCorrectionStage21::validateEvaluation)
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_PAIR_EVALUATED", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "band=${bandStage46.index}; accepted=${semanticStage46?.accepted == true}; reason=${semanticStage46?.reason.orEmpty()}; pickup=${candidateStage46?.pickup.orEmpty().take(500)}; destination=${candidateStage46?.destination.orEmpty().take(500)}",
                                        )
                                        if (candidateStage46 != null && semanticStage46?.accepted == true) {
                                            evaluationStage19 = candidateStage46
                                            FarolMaximumForensicsStage38.record(
                                                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_RECOVERED", eventPackageStage19,
                                                cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                                details = "band=${bandStage46.index}; signature=${candidateStage46.addressSignature}; destination=${candidateStage46.destination.take(700)}",
                                            )
                                            break
                                        }
                                    }
                                    if (evaluationStage19 == null && pairBandsStage46.isNotEmpty()) {
                                        FarolMaximumForensicsStage38.record(
                                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_NO_RESULT_WITH_ADDRESS_EVIDENCE", eventPackageStage19,
                                            cycleId = cycleIdStage20, operationId = "ocr-$serialStage19",
                                            details = "pairBands=${pairBandsStage46.size}; failClosed=true",
                                        )
                                    }
                                }

'''
s = once(s, recovery_anchor, recovery + recovery_anchor, 'Stage46 bounded no-result recovery')

s = once(
    s,
    '        val tokenStage36 = stage36RuntimeAuthority.captureDestinationToken(bindingStage26.addressSignature) ?: return\n'
    '        stage36BindingWorkToken[stage26BindingKey(bindingStage26)] = tokenStage36\n',
    '        val tokenStage36 = stage36RuntimeAuthority.captureDestinationToken(bindingStage26.addressSignature) ?: return\n'
    '        val keyStage46 = stage26BindingKey(bindingStage26)\n'
    '        stage36BindingWorkToken[keyStage46] = tokenStage36\n'
    '        if (stage46BindingSurfaceToken.size >= 12) {\n'
    '            val firstSurfaceStage46 = stage46BindingSurfaceToken.keys.firstOrNull()\n'
    '            if (firstSurfaceStage46 != null) stage46BindingSurfaceToken.remove(firstSurfaceStage46)\n'
    '        }\n'
    '        stage46BindingSurfaceToken[keyStage46] = FarolVisualEpochNoResultStage46.captureSurface(\n'
    '            currentRootPackageName(), null, stage19ActiveWindowId ?: 0,\n'
    '        )\n',
    'Stage46 route surface binding',
)

s = once(
    s,
    '        val tokenStage36 = stage36BindingWorkToken[stage26BindingKey(bindingStage26)] ?: return false\n'
    '        return stage36RuntimeAuthority.isFresh(tokenStage36)\n',
    '        val keyStage46 = stage26BindingKey(bindingStage26)\n'
    '        val tokenStage36 = stage36BindingWorkToken[keyStage46] ?: return false\n'
    '        val surfaceStage46 = stage46BindingSurfaceToken[keyStage46] ?: return false\n'
    '        val runtimeFreshStage46 = stage36RuntimeAuthority.isFresh(tokenStage36)\n'
    '        val surfaceFreshStage46 = FarolVisualEpochNoResultStage46.surfaceFresh(surfaceStage46, currentRootPackageName())\n'
    '        if (runtimeFreshStage46 && !surfaceFreshStage46) {\n'
    '            FarolMaximumForensicsStage38.record(\n'
    '                SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_STALE_ROUTE_SURFACE_DROPPED", currentRootPackageName(),\n'
    '                details = "captured=${surfaceStage46.packageName.orEmpty()}; current=${currentRootPackageName().orEmpty()}; binding=${bindingStage26.addressSignature}",\n'
    '            )\n'
    '        }\n'
    '        return runtimeFreshStage46 && surfaceFreshStage46\n',
    'Stage46 route/paint surface freshness',
)

SERVICE.write_text(s, encoding='utf-8')
print(f'stage46_visual_epoch_no_result=PASS ocr_freshness_checkpoints={ocr_count}')
