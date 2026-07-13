// Corrige enderecos do inDrive quebrados pelo OCR/acessibilidade em varias linhas.
// Exemplos comuns: o nome da rua termina em "do/de" e continua na linha seguinte,
// ou o numero do endereco aparece sozinho no inicio da proxima linha.

val inDriveAddressWrapPatch by tasks.registering {
    val parserFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/RideTextParser.kt")
    inputs.file(parserFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = parserFile.asFile
        if (!file.exists()) return@doLast

        var text = file.readText()
        val original = text

        if ("indrive_address_wrap_0_1_85" !in text) {
            val oldContinuationBlock = """        val normalized = value.lowercase()
        val previous = previousLine.trim()
        val previousNormalized = previous.lowercase()
        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }

        if (looksLikeAddress(value) && !previousEndsWithStreetType) return false
"""
            val newContinuationBlock = """        val normalized = value.lowercase()
        val previous = previousLine.trim()
        val previousNormalized = previous.lowercase()
        val previousHasOpenParenthesis = previous.count { it == '(' } > previous.count { it == ')' }
        val previousEndsWithStreetType = streetTypeSuffixes.any { previousNormalized.endsWith(it) }
        val previousEndsWithConnector = Regex("\\b(?:da|de|do|das|dos|e)\\s*\\z", RegexOption.IGNORE_CASE)
            .containsMatchIn(previous)
        val previousStartsAsAddress = looksLikeAddress(previous)
        val previousHasStreetNumber = Regex("\\b\\d{1,6}\\b").containsMatchIn(previous)
        val valueStartsWithStreetNumber = Regex("^\\d{1,6}\\b").containsMatchIn(value)
        val joinsSplitStreetName = previousStartsAsAddress &&
            !previousHasStreetNumber &&
            (previousEndsWithConnector || valueStartsWithStreetNumber) // indrive_address_wrap_0_1_85

        if (joinsSplitStreetName) return true
        if (looksLikeAddress(value) && !previousEndsWithStreetType) return false
"""
            if (oldContinuationBlock !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o bloco de continuacao de endereco do RideTextParser.")
            }
            text = text.replace(oldContinuationBlock, newContinuationBlock)

            val oldAddressStartBlock = """        val firstLine = cleanAddressLine(rawFirstLine)
        if (!looksLikeAddress(firstLine)) return null

        val parts = mutableListOf(firstLine)
"""
            val newAddressStartBlock = """        val firstLine = cleanAddressLine(rawFirstLine)
        val hasOwnMapMarker = mapPointRegex.find(rawFirstLine) != null
        if (!hasOwnMapMarker && startIndex > 0) {
            val previousAddressLine = cleanAddressLine(lines[startIndex - 1])
            if (isAddressContinuation(firstLine, previousAddressLine)) return null // indrive_skip_consumed_wrap_0_1_85
        }
        if (!looksLikeAddress(firstLine)) return null

        val parts = mutableListOf(firstLine)
"""
            if (oldAddressStartBlock !in text) {
                throw org.gradle.api.GradleException("Nao encontrei o inicio de buildAddressBlock no RideTextParser.")
            }
            text = text.replace(oldAddressStartBlock, newAddressStartBlock)
        }

        if ("indrive_address_wrap_0_1_85" !in text || "indrive_skip_consumed_wrap_0_1_85" !in text) {
            throw org.gradle.api.GradleException("A correcao de endereco quebrado do inDrive nao foi instalada por completo.")
        }

        if (text != original) file.writeText(text)
    }
}

inDriveAddressWrapPatch.configure {
    mustRunAfter("inDriveCardContractMatch")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(inDriveAddressWrapPatch)
}
