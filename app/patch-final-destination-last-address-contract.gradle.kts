val finalDestinationLastAddressContract by tasks.registering {
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt")
    inputs.file(parserFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = parserFile.asFile
        var text = file.readText()
        val original = text

        text = text.replace(
"""        val addresses = findAddressCandidates(lines)
        val pickup = findAddressAfterMarker(lines, pickupMarkers) ?: addresses.firstOrNull()
        val destination = findAddressAfterMarker(lines, destinationMarkers) ?: addresses.asReversed().firstOrNull {
            !it.equals(pickup, ignoreCase = true)
        }
""",
"""        val addresses = findAddressCandidates(lines)
        val explicitDestination = findAddressAfterMarker(lines, destinationMarkers)
        val destination = explicitDestination ?: addresses.lastOrNull()
        val pickup = findAddressAfterMarker(lines, pickupMarkers)
            ?: addresses.firstOrNull { !it.equals(destination, ignoreCase = true) }
            ?: addresses.firstOrNull()
""",
        )

        text = text.replace(
"""    private fun resultWithCommonFields(fields: RideFields, lines: List<String>, scopedText: String, parserName: String): RideParseResult =
        RideParseResult(
            fields = fields.copy(
                fare = fields.fare ?: findFare(lines, scopedText),
                distance = fields.distance ?: distanceRegex.find(scopedText)?.value?.trim(),
                time = fields.time ?: timeRegex.find(scopedText)?.value?.trim(),
            ),
            parserName = parserName,
        )
""",
"""    private fun resultWithCommonFields(fields: RideFields, lines: List<String>, scopedText: String, parserName: String): RideParseResult {
        val addresses = findAddressCandidates(lines)
        val lastAddressDestination = addresses.lastOrNull()
        val firstAddressPickup = addresses.firstOrNull { !it.equals(lastAddressDestination, ignoreCase = true) }
            ?: addresses.firstOrNull()
        val fixedFields = if (addresses.size >= 2 && !lastAddressDestination.isNullOrBlank()) {
            fields.copy(
                pickup = fields.pickup ?: firstAddressPickup,
                destination = lastAddressDestination,
                fare = fields.fare ?: findFare(lines, scopedText),
                distance = fields.distance ?: distanceRegex.find(scopedText)?.value?.trim(),
                time = fields.time ?: timeRegex.find(scopedText)?.value?.trim(),
            )
        } else {
            fields.copy(
                fare = fields.fare ?: findFare(lines, scopedText),
                distance = fields.distance ?: distanceRegex.find(scopedText)?.value?.trim(),
                time = fields.time ?: timeRegex.find(scopedText)?.value?.trim(),
            )
        }
        return RideParseResult(
            fields = fixedFields,
            parserName = if (addresses.size >= 2) "${'$'}parserName-last-address-destination" else parserName,
        )
    }
""",
        )

        if (text != original) file.writeText(text)
    }
}

finalDestinationLastAddressContract.configure {
    mustRunAfter("genericLastAddressDestination")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(finalDestinationLastAddressContract)
}
