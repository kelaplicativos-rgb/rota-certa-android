from pathlib import Path

ROOT = Path.cwd().resolve()


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise FileNotFoundError(f"Missing required source file: {relative}")
    return path.read_text(encoding="utf-8")


def write(relative: str, content: str) -> None:
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(relative: str, old: str, new: str) -> None:
    text = read(relative)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{relative}: expected one occurrence, found {count}: {old[:160]!r}")
    write(relative, text.replace(old, new, 1))


service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"

replace_once(
    service,
    "    private var savedPlacePopupView: LinearLayout? = null\n",
    "    private var savedPlacePopupView: LinearLayout? = null\n    private var shortcutModulePopupView0181: LinearLayout? = null\n",
)

old_save = '''    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar este local.")
                recordDiagnostic(
                    stage = "bubble_save_place_no_gps",
                    color = currentRadarColor,
                    reason = "Nao foi possivel captar GPS para salvar o local.",
                )
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            if (type == SavedPlaceType.Place) {
                showSavePlacePopup(coordinate, resolved.addressLine)
                return@launch
            }

            val createdAt = System.currentTimeMillis()
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = "Alerta de proximidade",
                type = SavedPlaceType.ProximityAlert,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = currentSettings.proximityAlertDistanceMeters,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            openSavedPlaceEditor(place)
            toast("Alerta criado. Informe o nome.")
            recordDiagnostic(
                stage = "bubble_save_proximity_alert",
                color = currentRadarColor,
                reason = "Alerta de proximidade salvo pela bolinha a ${place.alertDistanceMeters ?: 200} metros.",
            )
        }
    }
'''
new_save = '''    private fun saveCurrentPlaceFromBubble(type: SavedPlaceType) {
        scope.launch {
            val coordinate = locationService.currentCoordinate()
            if (coordinate == null) {
                toast("Autorize a localizacao para salvar este local.")
                recordDiagnostic(
                    stage = "bubble_save_place_no_gps",
                    color = currentRadarColor,
                    reason = "Nao foi possivel captar GPS para salvar o local.",
                )
                return@launch
            }

            val resolved = gpsAddressResolver.resolve(coordinate)
            showSavePlacePopup(coordinate, resolved.addressLine, type)
        }
    }
'''
replace_once(service, old_save, new_save)

replace_once(
    service,
    "    private fun showSavePlacePopup(coordinate: Coordinate, resolvedAddress: String) {\n",
    "    private fun showSavePlacePopup(\n        coordinate: Coordinate,\n        resolvedAddress: String,\n        type: SavedPlaceType,\n    ) {\n",
)
replace_once(
    service,
    '''        val address = SavedPlacePopupPolicy0181.displayAddress(resolvedAddress, fallbackAddress)
        val nameInput = EditText(this).apply {
''',
    '''        val address = SavedPlacePopupPolicy0181.displayAddress(resolvedAddress, fallbackAddress)
        val isAlert = type == SavedPlaceType.ProximityAlert
        val nameInput = EditText(this).apply {
''',
)
replace_once(
    service,
    '''                text = "Nome do local"
''',
    '''                text = if (isAlert) "Nome do alerta" else "Nome do local"
''',
)
replace_once(
    service,
    '''                text = "Digite um nome ou salve vazio para usar Local salvo."
''',
    '''                text = if (isAlert) {
                    "Digite o nome que sera falado ou salve vazio para usar Alerta de proximidade."
                } else {
                    "Digite um nome ou salve vazio para usar Local salvo."
                }
''',
)
replace_once(
    service,
    '''                    name = SavedPlacePopupPolicy0181.savedName(nameInput.text?.toString().orEmpty()),
                    type = SavedPlaceType.Place,
                    address = address,
                    coordinate = coordinate,
                    alertDistanceMeters = null,
''',
    '''                    name = SavedPlacePopupPolicy0181.savedName(
                        nameInput.text?.toString().orEmpty(),
                        type,
                    ),
                    type = type,
                    address = address,
                    coordinate = coordinate,
                    alertDistanceMeters = if (isAlert) currentSettings.proximityAlertDistanceMeters else null,
''',
)
replace_once(
    service,
    '''                    toast("Local salvo.")
                    recordDiagnostic(
                        stage = "bubble_save_place",
                        color = currentRadarColor,
                        reason = "Local salvo pela bolinha sem sair da tela atual.",
                    )
''',
    '''                    toast(if (isAlert) "Alerta salvo." else "Local salvo.")
                    recordDiagnostic(
                        stage = if (isAlert) "bubble_save_proximity_alert" else "bubble_save_place",
                        color = currentRadarColor,
                        reason = if (isAlert) {
                            "Alerta de proximidade salvo pela bolinha sem sair da tela atual."
                        } else {
                            "Local salvo pela bolinha sem sair da tela atual."
                        },
                    )
''',
)

replace_once(
    service,
    '''        when (action0180) {
            ShortcutGestureAction0180.PRIMARY_ACTION -> executeShortcutModule(entry0180.spec)
            ShortcutGestureAction0180.OPEN_MODULE -> openShortcutModule0171(entry0180.spec)
            ShortcutGestureAction0180.NONE -> Unit
        }
    }

    private fun openShortcutEntryCustomization0180''',
    '''        when (action0180) {
            ShortcutGestureAction0180.PRIMARY_ACTION -> dispatchShortcutPrimaryInPlace0181(entry0180.spec)
            ShortcutGestureAction0180.OPEN_MODULE -> showShortcutModulePopup0181(entry0180.spec)
            ShortcutGestureAction0180.NONE -> Unit
        }
    }

    private fun dispatchShortcutPrimaryInPlace0181(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            in ShortcutInPlacePolicy0181.overlayFirstIds -> showShortcutModulePopup0181(spec)
            else -> executeShortcutModule(spec)
        }
    }

    private fun showShortcutModulePopup0181(spec: BubbleShortcutSpec) {
        hideActionMenu()
        hideSavedPlacePopup()
        hideShortcutModulePopup0181()
        val manager = windowManager ?: return
        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(250, 32, 32, 32))
                setStroke(dp(1), Color.argb(230, 255, 255, 255))
            }
            setPadding(dp(18), dp(16), dp(18), dp(14))
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "${spec.emoji}  ${spec.displayLabel}"
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = ShortcutInPlacePolicy0181.description(spec.id)
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(8), 0, dp(10))
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = ShortcutInPlacePolicy0181.statusLine(
                    shortcutId = spec.id,
                    radarColor = currentRadarColor.diagnosticLabel,
                    distanceKm = currentDistanceKm,
                    radarCount = currentImportedRadars.size,
                )
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(10))
            })
        }

        if (spec.doubleTapAction != null) {
            popup.addView(Button(this).apply {
                text = "Executar acao rapida"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    executeShortcutDoubleTap(spec)
                }
            })
        }
        if (spec.id == "saved_places" || spec.id == "alerts") {
            popup.addView(Button(this).apply {
                text = if (spec.id == "alerts") "Criar alerta aqui" else "Salvar este local"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    saveCurrentPlaceFromBubble(
                        if (spec.id == "alerts") SavedPlaceType.ProximityAlert else SavedPlaceType.Place,
                    )
                }
            })
        }
        popup.addView(Button(this).apply {
            text = "Fechar"
            setOnClickListener { hideShortcutModulePopup0181() }
        })

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            dp(336).coerceAtMost(metrics.widthPixels - dp(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        if (runCatching { manager.addView(popup, params) }.isSuccess) {
            shortcutModulePopupView0181 = popup
            UnifiedDebugEventStore.record(
                "SHORTCUT_IN_PLACE_POPUP_0181",
                universalResolvedForegroundPackage(),
                "id=${spec.id}",
            )
        }
    }

    private fun hideShortcutModulePopup0181() {
        val popup = shortcutModulePopupView0181 ?: return
        runCatching { windowManager?.removeView(popup) }
        shortcutModulePopupView0181 = null
    }

    private fun openShortcutEntryCustomization0180''',
)

replace_once(
    service,
    "    private fun removeOverlay() {\n        hideSavedPlacePopup()\n",
    "    private fun removeOverlay() {\n        hideShortcutModulePopup0181()\n        hideSavedPlacePopup()\n",
)

policy = "app/src/main/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181.kt"
replace_once(
    policy,
    '''object SavedPlacePopupPolicy0181 {
    const val DEFAULT_NAME = "Local salvo"

    fun savedName(input: String): String = input.trim().ifBlank { DEFAULT_NAME }
''',
    '''object SavedPlacePopupPolicy0181 {
    const val DEFAULT_NAME = "Local salvo"
    const val DEFAULT_ALERT_NAME = "Alerta de proximidade"

    fun savedName(input: String, type: SavedPlaceType): String = input.trim().ifBlank {
        if (type == SavedPlaceType.ProximityAlert) DEFAULT_ALERT_NAME else DEFAULT_NAME
    }
''',
)

write(
    "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutInPlacePolicy0181.kt",
    '''package br.com.mapeiaia.rotacerta

import java.util.Locale

object ShortcutInPlacePolicy0181 {
    val overlayFirstIds = setOf(
        "route",
        "destination",
        "alerts",
        "saved_places",
        "radars",
        "appearance",
        "backup",
        "finance",
        "diagnostic",
        "quick_replies",
        "manual_capture",
        "stop_app",
        "links",
    )

    fun description(shortcutId: String): String = when (shortcutId) {
        "route" -> "Mostra o estado atual do farol sem abrir a Home."
        "destination" -> "Mostra a acao rapida de destino sem trocar de aplicativo."
        "alerts" -> "Crie um alerta no ponto atual em um pop-up sobre esta tela."
        "saved_places" -> "Salve o local atual com endereco completo sem sair desta tela."
        "radars" -> "Mostra quantos radares estao carregados sem abrir outro modulo."
        "appearance" -> "Consulta rapida da bolinha; ajustes completos continuam protegidos na Home."
        "backup" -> "Acesso seguro ao modulo sem troca automatica de tela."
        "finance" -> "Acesso seguro ao modulo sem troca automatica de tela."
        "diagnostic" -> "Acesso seguro ao diagnostico sem troca automatica de tela."
        "quick_replies" -> "Acesso seguro as respostas sem troca automatica de tela."
        "manual_capture" -> "Acesso seguro a captura sem troca automatica de tela."
        "stop_app" -> "Acesso seguro ao encerramento sem executar por engano."
        "links" -> "Acesso seguro aos links sem abrir outro aplicativo automaticamente."
        else -> "Acao da grade executada sobre a tela atual."
    }

    fun statusLine(
        shortcutId: String,
        radarColor: String,
        distanceKm: Double?,
        radarCount: Int,
    ): String = when (shortcutId) {
        "route" -> buildString {
            append("Farol: ")
            append(radarColor)
            distanceKm?.let {
                append(" • ")
                append(String.format(Locale("pt", "BR"), "%.1f km", it))
            }
        }
        "radars" -> "Radares carregados: $radarCount"
        else -> "Voce continua no aplicativo e na tela que estava usando."
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlacePolicy0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlacePolicy0181Test {
    @Test
    fun internalModulesUseOverlayFirst() {
        assertTrue("saved_places" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("alerts" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("route" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("radars" in ShortcutInPlacePolicy0181.overlayFirstIds)
        assertTrue("finance" in ShortcutInPlacePolicy0181.overlayFirstIds)
    }

    @Test
    fun radarStatusShowsCount() {
        assertEquals(
            "Radares carregados: 42",
            ShortcutInPlacePolicy0181.statusLine("radars", "cinza", null, 42),
        )
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlaceContract0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlaceContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun configuredModuleGestureUsesOverlayInsteadOfMainActivity() {
        assertTrue(service.contains("OPEN_MODULE -> showShortcutModulePopup0181(entry0180.spec)"))
        assertTrue(service.contains("dispatchShortcutPrimaryInPlace0181"))
        assertTrue(service.contains("SHORTCUT_IN_PLACE_POPUP_0181"))
        assertTrue(service.contains("Voce continua no aplicativo e na tela que estava usando."))

        val popupStart = service.indexOf("private fun showShortcutModulePopup0181")
        val popupEnd = service.indexOf("private fun hideShortcutModulePopup0181", popupStart)
        val popupCode = service.substring(popupStart, popupEnd)
        assertFalse(popupCode.contains("startActivity("))
        assertFalse(popupCode.contains("openShortcutModule0171("))
    }

    @Test
    fun localAndAlertBothUseFocusableNameAndAddressPopup() {
        assertTrue(service.contains("showSavePlacePopup(coordinate, resolved.addressLine, type)"))
        assertTrue(service.contains("text = if (isAlert) \"Nome do alerta\" else \"Nome do local\""))
        assertTrue(service.contains("Endereco completo"))
        assertTrue(service.contains("type = type"))
    }
}
''',
)

popup_test = "app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181Test.kt"
write(
    popup_test,
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlacePopupPolicy0181Test {
    @Test
    fun blankPlaceNameUsesLocalSalvo() {
        assertEquals(
            "Local salvo",
            SavedPlacePopupPolicy0181.savedName("   ", SavedPlaceType.Place),
        )
    }

    @Test
    fun blankAlertNameUsesAlertDefault() {
        assertEquals(
            "Alerta de proximidade",
            SavedPlacePopupPolicy0181.savedName("", SavedPlaceType.ProximityAlert),
        )
    }

    @Test
    fun typedNameIsTrimmedAndPreserved() {
        assertEquals(
            "Casa da Ana",
            SavedPlacePopupPolicy0181.savedName("  Casa da Ana  ", SavedPlaceType.Place),
        )
    }

    @Test
    fun fullResolvedAddressIsPreferred() {
        assertEquals(
            "Rod. Fernao Dias, 850 - Jardim Fernandao, Pouso Alegre - MG, 37550-000",
            SavedPlacePopupPolicy0181.displayAddress(
                "  Rod. Fernao Dias, 850 - Jardim Fernandao, Pouso Alegre - MG, 37550-000  ",
                "-22.00000, -45.00000",
            ),
        )
    }
}
''',
)
