// Rota Certa 0.1.128
// Reconhece a variacao real do pop-up inDrive sobre a tela bloqueada.
//
// O contrato 0.1.86 ja bloqueia listas e exige card individual, acao primaria,
// valor e sinais do inDrive. O erro observado era mais especifico: o pop-up
// fornece dois enderecos completos, mas nao fornece linhas isoladas A/B.
// Portanto, dois enderecos reconhecidos tambem constituem dois extremos da rota.

val inDriveCardFamily128 by tasks.registering {
    val matcherFile = layout.projectDirectory.file(
        "src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt",
    )
    inputs.file(matcherFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = matcherFile.asFile
        if (!file.exists()) throw GradleException("RideCardTemplateMatcher.kt nao encontrado.")
        var source = file.readText()

        if ("indrive_locked_popup_two_addresses_0_1_128" !in source) {
            val functionStart = source.indexOf("    private fun isInDriveOpenedCardContract(")
            val functionEnd = if (functionStart >= 0) {
                source.indexOf("    private fun isInDriveIndividualContract(", functionStart)
            } else {
                -1
            }
            if (functionStart < 0 || functionEnd < 0) {
                throw GradleException("Contrato final de card aberto do inDrive nao encontrado.")
            }

            var functionRegion = source.substring(functionStart, functionEnd)
            val returnAnchor = "        return hasRouteStructure &&\n"
            if (returnAnchor !in functionRegion) {
                throw GradleException("Retorno final do contrato inDrive nao encontrado.")
            }
            functionRegion = functionRegion.replaceFirst(
                returnAnchor,
                "        val hasTwoAddressPopup128 = addressCount >= 2 // indrive_locked_popup_two_addresses_0_1_128\n" +
                    "        return (hasRouteStructure || hasTwoAddressPopup128) &&\n",
            )
            source = source.substring(0, functionStart) + functionRegion + source.substring(functionEnd)
        }

        listOf(
            "indrive_locked_popup_two_addresses_0_1_128",
            "private fun isInDriveOpenedCardContract",
            "if (isInDriveListingScreen(normalized, rawText)) return false",
            "hasPrimaryAction",
            "moneyCount >= 1",
            "return (hasRouteStructure || hasTwoAddressPopup128)",
        ).forEach { marker ->
            if (marker !in source) throw GradleException("Contrato inDrive 0.1.128 incompleto: $marker")
        }

        file.writeText(source)
    }
}

inDriveCardFamily128.configure {
    mustRunAfter("inDriveCardContractMatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    dependsOn(inDriveCardFamily128)
}
