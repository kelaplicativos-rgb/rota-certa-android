package br.com.mapeiaia.rotacerta.trips

/**
 * Non-degrading passenger snapshot transition for Timeline rendering.
 *
 * A refresh may provide an immediate canonical seed while richer passenger metadata is
 * resolved in background. Once a coherent snapshot is already visible, that snapshot must
 * remain authoritative until the refreshed coherent snapshot is ready for an atomic swap.
 */
internal fun <T> preservePassengerTimelineSnapshotDuringRefresh0532(
    current: T?,
    immediateCanonical: T?,
): T? = current ?: immediateCanonical
