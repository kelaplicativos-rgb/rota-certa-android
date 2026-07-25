// Contrato final da etapa 16.

fun readClassChecklist16(root: java.io.File, preferredName: String, marker: String): String {
    val preferred = java.io.File(root, preferredName)
    if (preferred.exists()) return preferred.readText()
    return root.listFiles()
        ?.firstOrNull { it.isFile && it.extension == "kt" && marker in runCatching { it.readText() }.getOrDefault("") }
        ?.readText()
        ?: throw GradleException("Classe ausente no contrato 16: $marker")
}

fun verifyDestinationIdentityChecklist16(root: java.io.File) {
    val parser = readClassChecklist16(root, "UniversalScreenAddressParser.kt", "object UniversalScreenAddressParser")
    val simple = readClassChecklist16(root, "SimpleSavedAppFarolPolicy.kt", "object SimpleSavedAppFarolPolicy")
    val stability = readClassChecklist16(root, "FarolDisplayStabilityPolicy.kt", "object FarolDisplayStabilityPolicy")
    val identity = readClassChecklist16(root, "DestinationAddressIdentityPolicy.kt", "object DestinationAddressIdentityPolicy")
    val combined = listOf(parser, simple, stability, identity).joinToString("\n")

    listOf(
        "clean_unmatched_address_wrappers_checklist_16",
        "joined_destination_cleanup_checklist_16",
        "destination_only_signature_checklist_16",
        "compatible_partial_destination_checklist_16",
        "sameDestinationSignatures",
        "explicitHouseNumber",
    ).forEach { marker ->
        if (marker !in combined) throw GradleException("Contrato 16 ausente: $marker")
    }

    val evaluateStart = simple.indexOf("    fun evaluate(")
    val fingerprintStart = simple.indexOf("    /** Mantido para compatibilidade", evaluateStart)
    if (evaluateStart < 0 || fingerprintStart <= evaluateStart) {
        throw GradleException("evaluate final ausente no contrato 16.")
    }
    val evaluate = simple.substring(evaluateStart, fingerprintStart)
    if ("trigger.addressSignature" in evaluate) {
        throw GradleException("Assinatura antiga com embarque ainda participa da estabilidade.")
    }
    if ("DestinationAddressIdentityPolicy.signature(normalizedPackage, destination)" !in evaluate) {
        throw GradleException("Assinatura final ainda nao e baseada somente no destino.")
    }
    if ("PARTIAL_ABSENCE_CONFIRM_MILLIS = 500L" !in stability) {
        throw GradleException("Confirmacao anti-pisca de 500 ms foi perdida.")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        verifyDestinationIdentityChecklist16(
            layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta").asFile,
        )
    }
}
