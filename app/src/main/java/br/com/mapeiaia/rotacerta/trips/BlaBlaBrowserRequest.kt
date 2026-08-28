package br.com.mapeiaia.rotacerta.trips

/**
 * One browser request = one responsibility.
 *
 * Only requests that were observed/documented in the real BlaBlaCar browser flow
 * are listed here. Unknown interactions are deliberately not guessed.
 */
internal enum class BlaBlaBrowserRequest(
    val assetName: String,
    val mutatesRemoteState: Boolean = false,
) {
    SESSION_IDENTITY("session_identity.js"),
    DRIVER_PROFILE("driver_profile.js"),
    DRIVER_REVIEWS("driver_reviews.js"),

    RIDE_LIST("ride_list.js"),
    TRIP_OPEN("trip_open.js", mutatesRemoteState = true),
    TRIP_DETAIL("trip_detail.js"),
    TRIP_ITINERARY("trip_itinerary.js"),
    TRIP_EDIT("trip_edit.js"),

    PASSENGER_ROSTER("passenger_roster.js"),
    PASSENGER_OPEN("passenger_open.js", mutatesRemoteState = true),
    PASSENGER_IDENTITY("passenger_identity.js"),
    PASSENGER_CONTACT("passenger_contact.js"),
    PASSENGER_FARE("passenger_fare.js"),
    PASSENGER_SEGMENT("passenger_segment.js"),
    PASSENGER_ADDRESSES("passenger_addresses.js"),

    SEAT_OPTIONS("seat_options.js"),
    SEAT_CHANGE("seat_change.js", mutatesRemoteState = true),
    SEAT_SAVE("seat_save.js", mutatesRemoteState = true),

    PUBLIC_SEARCH_FORM("public_search_form.js", mutatesRemoteState = true),
    PUBLIC_SEARCH_RESULTS("public_search_results.js"),
    PUBLIC_RESULT_OPEN("public_result_open.js", mutatesRemoteState = true),
    PUBLIC_DRIVER_PROFILE_OPEN("public_driver_profile_open.js", mutatesRemoteState = true),
    PUBLIC_DRIVER_PROFILE("public_driver_profile.js"),
    PUBLIC_DRIVER_REVIEWS("public_driver_reviews.js"),

    MESSAGE_PASSENGER_OPEN("message_passenger_open.js", mutatesRemoteState = true),
    MESSAGE_THREAD("message_thread.js"),

    ARCHIVED_RIDE_LIST("archived_ride_list.js"),
    ARCHIVED_RIDE_OPEN("archived_ride_open.js", mutatesRemoteState = true),

    PAGE_STATE("page_state.js"),
    DOM_SNAPSHOT("dom_snapshot.js"),
}

internal data class BlaBlaBrowserExecutionContext(
    val accountId: String,
    val expectedProfileUuid: String = "",
    val syncGeneration: Long = 0L,
    val navigationGeneration: Long = 0L,
    val cardKey: String = "",
    val tripId: String = "",
    val passengerKey: String = "",
    val url: String = "",
)

internal data class BlaBlaBrowserRequestToken(
    val generation: Long,
    val request: BlaBlaBrowserRequest,
    val context: BlaBlaBrowserExecutionContext,
    val reason: String = "",
)
