package br.com.mapeiaia.rotacerta.trips

/** Single authority for UUID evidence exposed by BlaBlaCar collector pages. */
internal object BlaBlaCollectorIdentityModule {
    private val uuidRegex = Regex(
        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
    )

    fun uuids(links: List<String>): Set<String> = links.flatMap { href ->
        uuidRegex.findAll(href).map { match -> match.value.lowercase() }.toList()
    }.toSet()

    /**
     * Once the authenticated profile page verified the account, a UUID that is
     * visible only inside a passenger row is not driver identity evidence.
     */
    fun trustedDriverProfileLinks(
        expectedUuid: String?,
        authenticatedProfileSessionVerified: Boolean,
        observedLinks: List<String>,
    ): List<String> {
        val expected = expectedUuid?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        val distinct = observedLinks.map { link -> link.trim() }.filter(String::isNotEmpty).distinct()
        if (!authenticatedProfileSessionVerified || expected == null) return distinct
        return distinct.filter { link -> expected in uuids(listOf(link)) }
    }
}
