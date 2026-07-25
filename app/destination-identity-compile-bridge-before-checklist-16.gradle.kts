// Ponte registrada antes do checklist 15.
// Como doFirst executa em ordem inversa, esta correção roda depois do finalizador
// histórico e imediatamente antes do compilador Kotlin.

fun replaceFunctionBridge16(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao ausente na ponte 16: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente na ponte 16: $signature")
    var depth = 0
    var index = open
    while (index < source.length) {
        when (source[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return source.substring(0, start) + replacement + source.substring(index + 1)
            }
        }
        index += 1
    }
    throw GradleException("Fim da funcao ausente na ponte 16: $signature")
}

fun patchDestinationIdentityBridge16(packageDir: java.io.File) {
    val parserFile = java.io.File(packageDir, "UniversalScreenAddressParser.kt")
    val simpleFile = java.io.File(packageDir, "SimpleSavedAppFarolPolicy.kt")
    val stabilityFile = java.io.File(packageDir, "FarolDisplayStabilityPolicy.kt")

    var parser = parserFile.readText()
    parser = replaceFunctionBridge16(
        parser,
        "    private fun cleanAddressSegment(",
        """    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = DestinationAddressIdentityPolicy.cleanDisplayAddress(value.replace(markerPrefix, ""))
        val starts = listOfNotNull(
            streetStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            localityStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            poiStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
        )
        val start = starts.minOrNull()
        val extracted = if (start != null) withoutMarker.substring(start) else withoutMarker
        return DestinationAddressIdentityPolicy.cleanDisplayAddress(extracted)
    } // clean_unmatched_address_wrappers_checklist_16
""",
    )
    if ("joined_destination_cleanup_checklist_16" !in parser) {
        parser = parser.replaceFirst(
            """                val joined = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim(' ', ',', '-', '–', '—')
""",
            """                val joined = DestinationAddressIdentityPolicy.cleanDisplayAddress(
                    parts.joinToString(" "),
                ) // joined_destination_cleanup_checklist_16
""",
        )
    }
    parserFile.writeText(parser)

    simpleFile.writeText(
        replaceFunctionBridge16(
            simpleFile.readText(),
            "    fun evaluate(",
            """    fun evaluate(
        packageName: String?,
        savedPackages: Set<String>,
        text: String,
    ): Evaluation {
        val normalizedPackage = normalize(packageName)
        val normalizedSaved = savedPackages.mapNotNull(::normalize).toSet()
        val trigger = UniversalAddressTrigger.evaluate(text)
        val cleanedAddresses = trigger.addresses
            .map(DestinationAddressIdentityPolicy::cleanDisplayAddress)
            .filter(String::isNotBlank)
            .distinctBy { value -> value.lowercase(Locale.ROOT) }
        val destination = cleanedAddresses.lastOrNull()
        val active = normalizedPackage != null &&
            normalizedPackage in normalizedSaved &&
            cleanedAddresses.size >= UniversalAddressTrigger.MINIMUM_VISIBLE_ADDRESSES &&
            !destination.isNullOrBlank()
        val addressSignature = if (active) {
            DestinationAddressIdentityPolicy.signature(normalizedPackage, destination)
        } else {
            ""
        }
        return Evaluation(
            packageName = normalizedPackage,
            addresses = cleanedAddresses,
            pickup = cleanedAddresses.firstOrNull()?.takeIf { active },
            destination = destination?.takeIf { active },
            addressSignature = addressSignature,
            screenHash = if (active) {
                FarolDisplayStabilityPolicy.stableScreenHash(normalizedPackage, addressSignature)
            } else {
                trigger.screenHash
            },
            active = active,
        )
    } // destination_only_signature_checklist_16
""",
        ),
    )

    stabilityFile.writeText(
        replaceFunctionBridge16(
            stabilityFile.readText(),
            "    fun decide(",
            """    fun decide(
        previousPackageName: String?,
        previousWindowId: Int?,
        activeAddressSignature: String?,
        currentPackageName: String?,
        currentWindowId: Int?,
        currentAddressSignature: String?,
        hasTwoAddresses: Boolean,
        eventType: Int,
    ): Action {
        @Suppress("UNUSED_VARIABLE") val ignoredWindowIds = previousWindowId to currentWindowId
        @Suppress("UNUSED_VARIABLE") val ignoredVariableEvent = eventType
        val packageChanged = previousPackageName != null && currentPackageName != null &&
            previousPackageName != currentPackageName
        if (hasTwoAddresses) {
            if (packageChanged) return Action.ClearThenProcess
            if (activeAddressSignature.isNullOrBlank() || currentAddressSignature.isNullOrBlank()) {
                return Action.ProcessCurrent
            }
            return if (DestinationAddressIdentityPolicy.sameDestinationSignatures(
                    activeAddressSignature,
                    currentAddressSignature,
                )
            ) {
                Action.KeepCurrent
            } else {
                Action.ClearThenProcess
            }
        }
        if (packageChanged) return Action.ClearImmediately
        if (activeAddressSignature != null) return Action.ConfirmAbsence
        return Action.KeepCurrent
    } // compatible_partial_destination_checklist_16
""",
        ),
    )
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    doFirst {
        patchDestinationIdentityBridge16(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
