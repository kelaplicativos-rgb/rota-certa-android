package br.com.mapeiaia.rotacerta

import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.search.SearchEngine

object OrganicMapsEmbeddedProbe0711 {
    const val CONTRACT_MARKER = "ORGANIC_MAPS_EMBEDDED_SDK_0711"

    fun apiTypesPresent(): Boolean =
        OrganicMaps::class.java.name.isNotBlank() && SearchEngine.INSTANCE != null
}
