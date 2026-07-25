// Checklist 16 — identidade estável do destino contra OCR parcial e pontuação solta.

fun replaceFunctionChecklist16(source: String, signature: String, replacement: String): String {
    val start = source.indexOf(signature)
    if (start < 0) throw GradleException("Funcao ausente no checklist 16: $signature")
    val open = source.indexOf('{', start)
    if (open < 0) throw GradleException("Corpo ausente no checklist 16: $signature")
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
    throw GradleException("Fim da funcao ausente no checklist 16: $signature")
}

fun locateClassChecklist16(packageDir: java.io.File, preferredName: String, marker: String): java.io.File {
    val preferred = java.io.File(packageDir, preferredName)
    if (preferred.exists()) return preferred
    return packageDir.listFiles()
        ?.firstOrNull { it.isFile && it.extension == "kt" && marker in runCatching { it.readText() }.getOrDefault("") }
        ?: throw GradleException("Classe ausente no checklist 16: $marker")
}

fun patchAddressParserChecklist16(file: java.io.File) {
    val text = replaceFunctionChecklist16(
        file.readText(),
        "    private fun cleanAddressSegment(",
        """    private fun cleanAddressSegment(value: String): String {
        val withoutMarker = DestinationAddressIdentityPolicy.cleanParserSegment(value.replace(markerPrefix, ""))
        val starts = listOfNotNull(
            streetStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            localityStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
            poiStartRegex.find(withoutMarker)?.groups?.get(1)?.range?.first,
        )
        val start = starts.minOrNull()
        val extracted = if (start != null) withoutMarker.substring(start) else withoutMarker
        return DestinationAddressIdentityPolicy.cleanParserSegment(extracted)
    } // clean_unmatched_address_wrappers_checklist_16
""",
    )
    file.writeText(text)
}

fun patchSimpleFarolPolicyChecklist16(file: java.io.File) {
    val text = replaceFunctionChecklist16(
        file.readText(),
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
    )
    file.writeText(text)
}

fun patchDisplayStabilityChecklist16(file: java.io.File) {
    val text = replaceFunctionChecklist16(
        file.readText(),
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
    )
    file.writeText(text)
}

fun patchDestinationIdentityChecklist16(packageDir: java.io.File) {
    patchAddressParserChecklist16(
        locateClassChecklist16(packageDir, "UniversalScreenAddressParser.kt", "object UniversalScreenAddressParser"),
    )
    patchSimpleFarolPolicyChecklist16(
        locateClassChecklist16(packageDir, "SimpleSavedAppFarolPolicy.kt", "object SimpleSavedAppFarolPolicy"),
    )
    patchDisplayStabilityChecklist16(
        locateClassChecklist16(packageDir, "FarolDisplayStabilityPolicy.kt", "object FarolDisplayStabilityPolicy"),
    )
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchDestinationIdentityChecklist16(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
