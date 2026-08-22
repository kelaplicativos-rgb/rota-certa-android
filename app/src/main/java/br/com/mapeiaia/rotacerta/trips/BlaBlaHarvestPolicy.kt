package br.com.mapeiaia.rotacerta.trips

/**
 * Automatic harvest is intentionally focused on trip/passenger evidence.
 * Published-seat lookup through Editar -> Lugares is not a physical-capacity authority
 * and is kept out of the normal synchronization path for speed and determinism.
 * Manual external seat synchronization remains a separate explicit flow.
 *
 * MHTML is diagnostic/contingency evidence, not a prerequisite for the normal collector.
 * The automatic path should prefer structured DOM evidence and only archive MHTML when an
 * explicit diagnostic flow asks for it.
 */
internal object BlaBlaHarvestPolicy {
    const val AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = false
    const val AUTOMATIC_MHTML_ARCHIVE: Boolean = false
    const val AUTOMATIC_PAGE_SETTLE_MS: Long = 250L
}
