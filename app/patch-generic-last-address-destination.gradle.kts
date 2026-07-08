val genericLastAddressDestination by tasks.registering {
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

        if (text != original) file.writeText(text)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(genericLastAddressDestination)
}
