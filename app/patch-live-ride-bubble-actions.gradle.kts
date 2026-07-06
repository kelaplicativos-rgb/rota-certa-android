val patchLiveRideBubbleActions by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("saveCurrentDecisionAddressFromBubble" !in text) {
            text = text.replace(
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
"""            addView(actionMenuItem("📍  Salvar este local") {
                hideActionMenu()
                saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            })
            addView(actionMenuItem("🏠  Salvar GPS como casa") {
                hideActionMenu()
                saveCurrentDecisionAddressFromBubble(saveAsHome = true)
            })
            addView(actionMenuItem("📌  Salvar GPS como alfinete") {
                hideActionMenu()
                saveCurrentDecisionAddressFromBubble(saveAsHome = false)
            })
            addView(actionMenuItem("🔔  Criar alerta de proximidade") {
""",
            )

            text = text.replace(
"""    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
"""    private fun saveCurrentDecisionAddressFromBubble(saveAsHome: Boolean) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar Casa/Alfinete.")
                recordDiagnostic(
                    stage = "bubble_save_decision_address_no_gps",
                    color = currentRadarColor,
                    reason = "Nao foi possivel captar GPS para salvar Casa/Alfinete pela bolinha.",
                )
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            val address = resolved.addressLine.ifBlank { formatBubbleCoordinate(coordinate) }
            val settings = repository.settings.first()
            val updated = if (saveAsHome) {
                settings.copy(homeAddress = address, homeCoordinate = coordinate)
            } else {
                settings.copy(alternativeAddress = address, alternativeCoordinate = coordinate)
            }
            repository.saveSettings(updated)
            currentSettings = updated
            val label = if (saveAsHome) "Casa" else "Alfinete"
            toast("${dollar}label salvo pelo GPS atual.")
            recordDiagnostic(
                stage = if (saveAsHome) "bubble_save_home" else "bubble_save_alternative",
                color = currentRadarColor,
                reason = "${dollar}label salvo pela bolinha para decisao por km do destino final.",
            )
        }
    }

    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
""",
            )
        }

        text = text.replace(
"""            x = bubbleParams.x + dp(76)
            y = bubbleParams.y
""",
"""            x = overlayMenuX(bubbleParams)
            y = overlayMenuY(bubbleParams)
""",
        )

        text = text.replace(
"""        params.x = bubbleParams.x + dp(76)
        params.y = bubbleParams.y
""",
"""        params.x = overlayMenuX(bubbleParams)
        params.y = overlayMenuY(bubbleParams)
""",
        )

        if ("private fun overlayMenuX(" !in text) {
            text = text.replace(
"""    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
"""    private fun overlayMenuX(bubbleParams: WindowManager.LayoutParams): Int {
        val menuWidth = dp(260)
        val gap = dp(10)
        val rightX = bubbleParams.x + dp(66) + gap
        val screenWidth = resources.displayMetrics.widthPixels
        return if (rightX + menuWidth <= screenWidth) {
            rightX
        } else {
            (bubbleParams.x - menuWidth - gap).coerceAtLeast(0)
        }
    }

    private fun overlayMenuY(bubbleParams: WindowManager.LayoutParams): Int =
        bubbleParams.y.coerceAtLeast(0)

    private fun actionMenuItem(label: String, action: () -> Unit): TextView =
""",
            )
        }

        if ("private fun formatBubbleCoordinate(" !in text) {
            text = text.replace(
"""    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
""",
"""    private fun formatBubbleCoordinate(coordinate: Coordinate): String =
        String.format(Locale("pt", "BR"), "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
""",
            )
        }

        if (text != original) {
            file.writeText(text)
        }
    }
}

patchLiveRideBubbleActions.configure {
    mustRunAfter("patchLiveRideOverlayStability")
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(patchLiveRideBubbleActions)
}
