package br.com.mapeiaia.rotacerta.trips

internal data class BlaBlaSeatCollectionState(
    val tripIdPresent: Boolean,
    val editIdentityMatches: Boolean,
    val optionsIdentityMatches: Boolean,
    val publishedSeats: Int?,
)

/** Edit/options identity and published-seat decisions. No external write occurs here. */
internal object BlaBlaCollectorSeatModule {
    fun state(
        tripId: String?,
        editHref: String?,
        optionsHref: String?,
        publishedSeats: Int?,
    ): BlaBlaSeatCollectionState {
        val id = tripId?.trim().orEmpty()
        return BlaBlaSeatCollectionState(
            tripIdPresent = id.isNotBlank(),
            editIdentityMatches = id.isNotBlank() && BlaBlaHarvestAssociation.editPageMatches(id, editHref.orEmpty()),
            optionsIdentityMatches = id.isNotBlank() && BlaBlaHarvestAssociation.optionsPageMatches(id, optionsHref.orEmpty()),
            publishedSeats = publishedSeats,
        )
    }

    fun complete(state: BlaBlaSeatCollectionState): Boolean =
        state.tripIdPresent &&
            state.editIdentityMatches &&
            state.optionsIdentityMatches &&
            state.publishedSeats != null &&
            state.publishedSeats >= 0
}
