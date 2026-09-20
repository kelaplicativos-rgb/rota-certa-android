package br.com.mapeiaia.rotacerta.trips

import java.text.Normalizer

internal data class BlaBlaItineraryMerge0598(
    val stops: List<String>,
    val authoritative: Boolean,
    val source: String,
)

internal object BlaBlaItineraryRecovery0598 {
    private fun display(raw: String): String =
        raw.trim().take(240).substringBefore(',').trim()

    internal fun key(raw: String): String = Normalizer
        .normalize(display(raw), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    internal fun bounded(
        origin: String,
        destination: String,
        rawStops: List<String>,
    ): List<String> {
        val ordered = mutableListOf<String>()
        fun add(raw: String) {
            val label = display(raw)
            val normalized = key(label)
            if (normalized.isBlank()) return
            if (ordered.lastOrNull()?.let(::key) == normalized) return
            ordered += label
        }
        add(origin)
        rawStops.forEach(::add)
        add(destination)
        val originKey = key(origin)
        val destinationKey = key(destination)
        if (originKey.isBlank() || destinationKey.isBlank() || originKey == destinationKey) return emptyList()
        if (ordered.size < 2 || key(ordered.first()) != originKey || key(ordered.last()) != destinationKey) return emptyList()
        if (ordered.drop(1).dropLast(1).any { key(it) == originKey || key(it) == destinationKey }) return emptyList()
        return ordered
    }

    private fun isOrderedSubsequence(needle: List<String>, haystack: List<String>): Boolean {
        if (needle.isEmpty()) return true
        val haystackKeys = haystack.map(::key)
        var cursor = 0
        for (raw in needle) {
            val wanted = key(raw)
            var found = false
            while (cursor < haystackKeys.size) {
                if (haystackKeys[cursor] == wanted) {
                    found = true
                    cursor++
                    break
                }
                cursor++
            }
            if (!found) return false
        }
        return true
    }

    internal fun compatibleRicher(
        origin: String,
        destination: String,
        current: List<String>,
        candidate: List<String>,
    ): List<String> {
        val base = bounded(origin, destination, current)
        val next = bounded(origin, destination, candidate)
        if (next.size < 2) return base
        if (base.size < 2) return next
        return when {
            next.size > base.size && isOrderedSubsequence(base, next) -> next
            base.size >= next.size && isOrderedSubsequence(next, base) -> base
            else -> base
        }
    }

    /**
     * Recovers a route only when passenger segment constraints imply one unique
     * topological order. Any ambiguity or cycle fails closed and returns empty.
     */
    internal fun recoverFromPassengerSegments(
        origin: String,
        destination: String,
        passengers: List<BlaBlaCollectorPassenger>,
    ): List<String> {
        val originLabel = display(origin)
        val destinationLabel = display(destination)
        val originKey = key(originLabel)
        val destinationKey = key(destinationLabel)
        if (originKey.isBlank() || destinationKey.isBlank() || originKey == destinationKey) return emptyList()

        val labels = linkedMapOf<String, String>()
        labels[originKey] = originLabel
        labels[destinationKey] = destinationLabel

        data class Edge(val from: String, val to: String)
        val observedEdges = linkedSetOf<Edge>()

        passengers.forEach { passenger ->
            val fromLabel = display(passenger.boarding.orEmpty())
            val toLabel = display(passenger.dropoff.orEmpty())
            val from = key(fromLabel)
            val to = key(toLabel)
            if (from.isBlank() || to.isBlank() || from == to) return@forEach
            if (to == originKey || from == destinationKey) return emptyList()
            labels.putIfAbsent(from, fromLabel)
            labels.putIfAbsent(to, toLabel)
            observedEdges += Edge(from, to)
        }

        if (labels.size < 3 || observedEdges.isEmpty()) return emptyList()

        val edges = linkedSetOf<Edge>()
        edges += observedEdges
        labels.keys.filter { it != originKey }.forEach { edges += Edge(originKey, it) }
        labels.keys.filter { it != destinationKey }.forEach { edges += Edge(it, destinationKey) }

        val outgoing = labels.keys.associateWith { linkedSetOf<String>() }.toMutableMap()
        val indegree = labels.keys.associateWith { 0 }.toMutableMap()
        edges.forEach { edge ->
            if (edge.from == edge.to) return@forEach
            if (outgoing.getValue(edge.from).add(edge.to)) {
                indegree[edge.to] = indegree.getValue(edge.to) + 1
            }
        }

        val remaining = labels.keys.toMutableSet()
        val ordered = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val available = remaining.filter { indegree.getValue(it) == 0 }
            if (available.size != 1) return emptyList()
            val next = available.single()
            ordered += next
            remaining.remove(next)
            outgoing.getValue(next).forEach { child ->
                indegree[child] = indegree.getValue(child) - 1
            }
        }

        if (ordered.firstOrNull() != originKey || ordered.lastOrNull() != destinationKey) return emptyList()
        return ordered.mapNotNull(labels::get).takeIf { it.size >= 3 }.orEmpty()
    }

    internal fun merge(
        origin: String,
        destination: String,
        currentStops: List<String>,
        currentAuthoritative: Boolean,
        dedicatedDomStops: List<String>,
        dedicatedDomAuthoritative: Boolean,
        dedicatedNetworkStops: List<String>,
        passengerStops: List<String>,
    ): BlaBlaItineraryMerge0598 {
        var stops = bounded(origin, destination, currentStops)
        var authoritative = currentAuthoritative
        var source = "trip_detail"

        val dedicatedDom = bounded(origin, destination, dedicatedDomStops)
        if (dedicatedDomAuthoritative && dedicatedDom.size >= 2) {
            stops = dedicatedDom
            authoritative = true
            source = "trip_itinerary_dom"
        } else {
            val mergedDom = compatibleRicher(origin, destination, stops, dedicatedDom)
            if (mergedDom.size > stops.size) {
                stops = mergedDom
                source = "trip_itinerary_dom_compatible"
            }
        }

        val networkMerged = compatibleRicher(origin, destination, stops, dedicatedNetworkStops)
        if (networkMerged.size > stops.size) {
            stops = networkMerged
            source = "trip_itinerary_network_compatible"
        }

        val passengerMerged = compatibleRicher(origin, destination, stops, passengerStops)
        if (passengerMerged.size > stops.size) {
            stops = passengerMerged
            source = "passenger_segment_unique_order"
        }

        return BlaBlaItineraryMerge0598(
            stops = stops,
            authoritative = authoritative,
            source = source,
        )
    }
}
