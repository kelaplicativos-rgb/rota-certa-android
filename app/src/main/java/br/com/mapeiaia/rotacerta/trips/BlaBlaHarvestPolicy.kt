package br.com.mapeiaia.rotacerta.trips

/**
 * Automatic harvest is intentionally focused on trip/passenger evidence.
 * Published-seat lookup through Editar -> Lugares is not a physical-capacity authority
 * and is kept out of the normal synchronization path for speed and determinism.
 * Manual external seat synchronization remains a separate explicit flow.
 */
internal object BlaBlaHarvestPolicy {
    const val AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = false
}
