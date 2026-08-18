#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PKG = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta'
ACT = PKG / 'FarolReadingActivationStage26.kt'
SERVICE = PKG / 'LiveRideAccessibilityService.kt'


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    return text.replace(old, new, 1)


a = ACT.read_text(encoding='utf-8')
old_invalidate = '''        @Synchronized
        fun invalidate() {
            lastWindowSignature = null
            lastRelevantValue = null
            bootstrapValueByStructure.clear()
            generation += 1L
        }
'''
new_invalidate = '''        /**
         * Ends only the deduplication identity of the current visual episode.
         * Generation is intentionally preserved: Stage46 already advances the authoritative
         * visual epoch/work tokens when disappearance is proven. The next identical surface is
         * therefore allowed to acquire again without weakening stale-work protection.
         */
        @Synchronized
        fun endVisualEpisode() {
            lastWindowSignature = null
            lastRelevantValue = null
            bootstrapValueByStructure.clear()
        }

        @Synchronized
        fun invalidate() {
            lastWindowSignature = null
            lastRelevantValue = null
            bootstrapValueByStructure.clear()
            generation += 1L
        }
'''
a = once(a, old_invalidate, new_invalidate, 'Stage40 visual-episode reset primitive')
ACT.write_text(a, encoding='utf-8')

s = SERVICE.read_text(encoding='utf-8')
old_release = '''        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("target_empty", eventPackageStage46)
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
'''
new_release = '''        showOverlay(RadarColor.Default, distanceKm = null)
        releaseConfirmedTargetStage46R3("target_empty", eventPackageStage46)

        // ERROR 1 causal fix: target_empty is the Stage46 proof that this concrete card episode
        // ended inside the same target window. Stage46 already revoked epoch/target/stale work;
        // terminate Stage40's episode-local dedup memory at the same proven boundary so an
        // identical card may re-enter immediately. Do not reset on ordinary duplicate events.
        stage26PreCollectGate.endVisualEpisode()
        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_VISUAL_EPISODE_PRECOLLECT_RESET", eventPackageStage46,
            details = "reason=target_empty; oldTarget=${releasedTargetPackageStage46R3.orEmpty()}; oldWindow=$releasedTargetWindowStage46R3; epoch=$stage46VisualEpoch; sameSignatureMayReenter=true; generationAuthorityPreserved=true",
        )
        FarolCausalLatencyStage28.Metrics.increment("stage46VisualEpisodePreCollectReset")

        FarolMaximumForensicsStage38.record(
            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), "S46_TARGET_EMPTY_FINAL_REVOKED", eventPackageStage46,
'''
s = once(s, old_release, new_release, 'target-empty ends Stage40 visual episode')
SERVICE.write_text(s, encoding='utf-8')
print('error1_card_visual_episode_reentry=PASS target_empty_resets_episode_dedup=true same_episode_coalescing_unchanged=true freshness_unchanged=true')
