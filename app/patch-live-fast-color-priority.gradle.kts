val patchLiveFastColorPriority by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("popup.candidate ignored reason=rota_certa_foreground" !in text) {
            text = text.replace(
"""        if (!serviceReady) return
        val windowPackageName = currentWindowPackageName()
""",
"""        if (!serviceReady) return
        if (allowPopupCandidate && isRotaCertaForegroundCandidate(text)) {
            traceEvent("popup.candidate ignored reason=rota_certa_foreground raw_length=${'$'}{text.length}")
            return
        }
        val windowPackageName = currentWindowPackageName()
""",
            )
        }

        if ("private fun isRotaCertaForegroundCandidate" !in text) {
            text = text.replace(
"""    private fun resolveRidePackageForText(
""",
"""    private fun isRotaCertaForegroundCandidate(text: String): Boolean {
        val packageName = currentWindowPackageName()
        if (packageName != this.packageName) return false
        val normalized = text.lowercase(Locale.ROOT)
        return normalized.contains("rota certa") ||
            normalized.contains("diagnostico") ||
            normalized.contains("ferramentas") ||
            normalized.contains("config")
    }

    private fun resolveRidePackageForText(
""",
            )
        }

        text = text.replace(
            "val destinationCoordinate = fields.destination?.let { geocodeBest(it, region, settings) }",
            "val destinationCoordinate = fields.destination?.let { geocodeFast(it, region, settings) }",
        )
        text = text.replace(
            "val homeCoordinate = settings.homeCoordinate ?: geocodeBest(settings.homeAddress, region, settings)",
            "val homeCoordinate = settings.homeCoordinate ?: geocodeFast(settings.homeAddress, region, settings)",
        )
        text = text.replace(
            "val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeBest(settings.alternativeAddress, region, settings)",
            "val alternativeCoordinate = settings.alternativeCoordinate ?: geocodeFast(settings.alternativeAddress, region, settings)",
        )

        text = text.replace(
"""            val homeDistanceKm = routeDistanceKm(destinationCoordinate, homeCoordinate, settings)
            val alternativeDistanceKm = routeDistanceKm(destinationCoordinate, alternativeCoordinate, settings)
            traceEvent("route.distance home=${'$'}{homeDistanceKm?.let(::formatDiagnosticKm) ?: "null"} alternative=${'$'}{alternativeDistanceKm?.let(::formatDiagnosticKm) ?: "null"}")
""",
"""            val homeDistanceKm: Double? = null
            val alternativeDistanceKm: Double? = null
            traceEvent("route.distance skipped live_fast=true home=approx alternative=approx")
""",
        )

        if ("private suspend fun geocodeFast(" !in text) {
            text = text.replace(
"""    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? =
        googleMapsService.geocode(query, region, settings.googleMapsApiKey)
            ?: geocodingService.geocode(query, region)
""",
"""    private suspend fun geocodeFast(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? {
        if (query.isBlank()) return null
        return geocodingService.geocode(query, region)
            ?: googleMapsService.geocode(query, region, settings.googleMapsApiKey)
    }

    private suspend fun geocodeBest(query: String, region: DeviceRegion, settings: AppSettings): Coordinate? =
        googleMapsService.geocode(query, region, settings.googleMapsApiKey)
            ?: geocodingService.geocode(query, region)
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchLiveFastColorPriority.configure {
    mustRunAfter("patchFastPopupAnalysis")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveFastColorPriority)
}
