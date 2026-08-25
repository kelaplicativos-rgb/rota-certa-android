package br.com.mapeiaia.rotacerta.trips

/**
 * Automatic harvest is intentionally focused on trip/passenger evidence.
 * Published-seat lookup through Editar -> Lugares is not a physical-capacity authority
 * and is kept out of the normal synchronization path for speed and determinism.
 * Manual external seat synchronization remains a separate explicit flow.
 */
internal object BlaBlaHarvestPolicy {
    const val AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = false
    const val AUTOMATIC_PAGE_SETTLE_MS: Long = 250L
}

/** Compatibility facade; the collector URL module is the only policy authority. */
internal object BlaBlaHarvestNavigationIdentity {
    fun same(left: String?, right: String?): Boolean =
        BlaBlaCollectorUrlModule.sameNavigation(left, right)

    fun isEditOrOptionsHref(value: String?): Boolean =
        BlaBlaCollectorUrlModule.isEditOrOptions(value)
}
