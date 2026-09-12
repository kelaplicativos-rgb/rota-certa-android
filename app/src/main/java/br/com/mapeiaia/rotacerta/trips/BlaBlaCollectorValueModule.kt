package br.com.mapeiaia.rotacerta.trips

internal data class BlaBlaPassengerValueEvidence(
    val namePresent: Boolean,
    val routePresent: Boolean,
    val farePresent: Boolean,
    val htmlPresent: Boolean,
)

/** Required passenger/value evidence, isolated from WebView orchestration. */
internal object BlaBlaCollectorValueModule {
    fun complete(evidence: BlaBlaPassengerValueEvidence): Boolean =
        evidence.namePresent && evidence.routePresent && evidence.farePresent && evidence.htmlPresent

    fun missing(evidence: BlaBlaPassengerValueEvidence): Set<String> = buildSet {
        if (!evidence.namePresent) add("name")
        if (!evidence.routePresent) add("route")
        if (!evidence.farePresent) add("fare")
        if (!evidence.htmlPresent) add("html")
    }
}
