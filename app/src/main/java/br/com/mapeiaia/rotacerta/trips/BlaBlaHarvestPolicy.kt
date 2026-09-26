package br.com.mapeiaia.rotacerta.trips

/**
 * Automatic harvest includes the documented Editar -> Lugares read because
 * published_seats is the BlaBlaCar available-seat pool used by the public Agenda.
 * The read remains isolated from remote writes: SEAT_CHANGE/SEAT_SAVE stay in
 * the explicit mutation controller.
 */
internal object BlaBlaHarvestPolicy {
    const val AUTOMATIC_PUBLISHED_SEAT_LOOKUP: Boolean = true
    const val AUTOMATIC_PAGE_SETTLE_MS: Long = 250L
}

/** Compatibility facade; the collector URL module is the only policy authority. */
internal object BlaBlaHarvestNavigationIdentity {
    fun same(left: String?, right: String?): Boolean =
        BlaBlaCollectorUrlModule.sameNavigation(left, right)

    fun isEditOrOptionsHref(value: String?): Boolean =
        BlaBlaCollectorUrlModule.isEditOrOptions(value)
}
