// Rota Certa 0.1.125
// Impede que a leitura universal misture varios pedidos visiveis do inDrive.
// Quando existe uma lista comprovada, analisa apenas o primeiro card completo.

fun patchPrimaryVisibleCardScope125(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt nao encontrado para o escopo do card primario 0.1.125.")
    var text = file.readText()
    val dollar = "$"

    if ("primary_visible_card_scope_0_1_125" !in text) {
        val oldBlock = """        val snapshotText = text.trim()
        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
"""
        if (oldBlock !in text) {
            throw GradleException("Inicio da leitura universal nao encontrado para isolar o primeiro card visivel.")
        }
        val newBlock = """        val fullSnapshotText = text.trim()
        val primaryCardSelection = PrimaryVisibleRideCardSelector.select(fullSnapshotText)
        val snapshotText = primaryCardSelection.selectedText
        if (primaryCardSelection.cardCount > 1) {
            traceEvent(
                "universal.card.scope selected_index=${dollar}{primaryCardSelection.selectedIndex} cards=${dollar}{primaryCardSelection.cardCount} passenger=${dollar}{primaryCardSelection.passengerName.orEmpty()} reason=${dollar}{primaryCardSelection.reason}",
            )
        } // primary_visible_card_scope_0_1_125
        val trigger = UniversalAddressTrigger.evaluate(snapshotText)
"""
        text = text.replaceFirst(oldBlock, newBlock)
    }

    listOf(
        "PrimaryVisibleRideCardSelector.select(fullSnapshotText)",
        "primary_visible_card_scope_0_1_125",
        "universal.card.scope selected_index=",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Escopo do card primario 0.1.125 sem marcador: $marker")
    }

    val processStart = text.indexOf("    private suspend fun processRideText(")
    val triggerIndex = text.indexOf("val trigger = UniversalAddressTrigger.evaluate(snapshotText)", processStart)
    val selectorIndex = text.indexOf("PrimaryVisibleRideCardSelector.select(fullSnapshotText)", processStart)
    val passengerIndex = text.indexOf("RidePassengerIdentityPolicy.evaluate(snapshotText)", processStart)
    if (processStart < 0 || selectorIndex < processStart || triggerIndex < selectorIndex || passengerIndex < triggerIndex) {
        throw GradleException("Ordem do escopo do card primario invalida no codigo gerado.")
    }

    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchPrimaryVisibleCardScope125(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
