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
        raise RuntimeError(f"{relative}: expected one occurrence, found {count}: {old[:140]!r}")
    write(relative, text.replace(old, new, 1))


replace_once(
    "app/build.gradle.kts",
    "versionCode = 5410",
    "versionCode = 5420",
)
replace_once(
    "app/build.gradle.kts",
    'versionName = "0.1.180"',
    'versionName = "0.1.181"',
)

service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service,
    "import android.view.WindowManager\nimport android.view.accessibility.AccessibilityEvent\n",
    "import android.view.WindowManager\nimport android.view.inputmethod.InputMethodManager\nimport android.view.accessibility.AccessibilityEvent\n",
)
replace_once(
    service,
    "import android.widget.LinearLayout\nimport android.widget.TextView\n",
    "import android.widget.Button\nimport android.widget.EditText\nimport android.widget.LinearLayout\nimport android.widget.TextView\n",
)
replace_once(
    service,
    "    private var overlayMenuView: LinearLayout? = null\n    private var overlayMenuParams: WindowManager.LayoutParams? = null\n",
    "    private var overlayMenuView: LinearLayout? = null\n    private var overlayMenuParams: WindowManager.LayoutParams? = null\n    private var savedPlacePopupView: LinearLayout? = null\n",
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
            val createdAt = System.currentTimeMillis()
            val isAlert = type == SavedPlaceType.ProximityAlert
            val place = SavedPlace(
                id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                name = if (isAlert) "Alerta de proximidade" else "Local salvo",
                type = type,
                address = resolved.addressLine,
                coordinate = coordinate,
                alertDistanceMeters = if (isAlert) currentSettings.proximityAlertDistanceMeters else null,
                createdAtMillis = createdAt,
            )
            repository.addSavedPlace(place)
            openSavedPlaceEditor(place)
            toast(if (isAlert) "Alerta criado. Informe o nome." else "Local salvo. Informe o nome.")
            recordDiagnostic(
                stage = if (isAlert) "bubble_save_proximity_alert" else "bubble_save_place",
                color = currentRadarColor,
                reason = if (isAlert) {
                    "Alerta de proximidade salvo pela bolinha a ${place.alertDistanceMeters ?: 200} metros."
                } else {
                    "Local salvo pela bolinha."
                },
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
replace_once(service, old_save, new_save)

popup_code = '''    private fun showSavePlacePopup(coordinate: Coordinate, resolvedAddress: String) {
        hideActionMenu()
        hideSavedPlacePopup()
        val manager = windowManager ?: return
        val fallbackAddress = String.format(
            Locale("pt", "BR"),
            "%.5f, %.5f",
            coordinate.latitude,
            coordinate.longitude,
        )
        val address = SavedPlacePopupPolicy0181.displayAddress(resolvedAddress, fallbackAddress)
        val nameInput = EditText(this).apply {
            hint = "Nome"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = "Nome do local"
        }
        val popup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.argb(250, 32, 32, 32))
                setStroke(dp(1), Color.argb(230, 255, 255, 255))
            }
            setPadding(dp(18), dp(16), dp(18), dp(14))
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Nome do local"
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Digite um nome ou salve vazio para usar Local salvo."
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(6), 0, dp(12))
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = "Endereco completo"
                textSize = 13f
                setTextColor(Color.LTGRAY)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@LiveRideAccessibilityService).apply {
                text = address
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, dp(4), 0, dp(10))
                contentDescription = "Endereco completo: $address"
            })
            addView(nameInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancelButton = Button(this).apply {
            text = "Cancelar"
            setOnClickListener { hideSavedPlacePopup() }
        }
        val saveButton = Button(this).apply {
            text = "Salvar"
            setOnClickListener {
                val createdAt = System.currentTimeMillis()
                val place = SavedPlace(
                    id = "place-$createdAt-${coordinate.latitude}-${coordinate.longitude}",
                    name = SavedPlacePopupPolicy0181.savedName(nameInput.text?.toString().orEmpty()),
                    type = SavedPlaceType.Place,
                    address = address,
                    coordinate = coordinate,
                    alertDistanceMeters = null,
                    createdAtMillis = createdAt,
                )
                hideSavedPlacePopup()
                scope.launch {
                    repository.addSavedPlace(place)
                    toast("Local salvo.")
                    recordDiagnostic(
                        stage = "bubble_save_place",
                        color = currentRadarColor,
                        reason = "Local salvo pela bolinha sem sair da tela atual.",
                    )
                }
            }
        }
        buttons.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        buttons.addView(
            saveButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        popup.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        val params = WindowManager.LayoutParams(
            dp(336),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        if (runCatching { manager.addView(popup, params) }.isSuccess) {
            savedPlacePopupView = popup
            nameInput.requestFocus()
            popup.post {
                val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                keyboard?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideSavedPlacePopup() {
        val popup = savedPlacePopupView ?: return
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        keyboard?.hideSoftInputFromWindow(popup.windowToken, 0)
        runCatching { windowManager?.removeView(popup) }
        savedPlacePopupView = null
    }

'''
replace_once(
    service,
    "    private fun collectVisibleTextForAction(): String {\n",
    popup_code + "    private fun collectVisibleTextForAction(): String {\n",
)
replace_once(
    service,
    "    private fun removeOverlay() {\n        hideActionMenu()\n",
    "    private fun removeOverlay() {\n        hideSavedPlacePopup()\n        hideActionMenu()\n",
)

write(
    "app/src/main/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181.kt",
    '''package br.com.mapeiaia.rotacerta

object SavedPlacePopupPolicy0181 {
    const val DEFAULT_NAME = "Local salvo"

    fun savedName(input: String): String = input.trim().ifBlank { DEFAULT_NAME }

    fun displayAddress(address: String, fallback: String): String =
        address.trim().ifBlank { fallback.trim() }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupPolicy0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedPlacePopupPolicy0181Test {
    @Test
    fun blankNameUsesLocalSalvo() {
        assertEquals("Local salvo", SavedPlacePopupPolicy0181.savedName("   "))
    }

    @Test
    fun typedNameIsTrimmedAndPreserved() {
        assertEquals("Casa da Ana", SavedPlacePopupPolicy0181.savedName("  Casa da Ana  "))
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

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/SavedPlacePopupContract0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacePopupContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun savePlaceUsesFocusableOverlayWithoutOpeningRotaCerta() {
        assertTrue(service.contains("private fun showSavePlacePopup"))
        assertTrue(service.contains("WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(service.contains("Endereco completo"))
        assertTrue(service.contains("Digite um nome ou salve vazio para usar Local salvo."))
        assertTrue(service.contains("text = \"Cancelar\""))
        assertTrue(service.contains("text = \"Salvar\""))
        assertTrue(service.contains("if (type == SavedPlaceType.Place)"))
        assertTrue(service.contains("showSavePlacePopup(coordinate, resolved.addressLine)"))

        val popupStart = service.indexOf("private fun showSavePlacePopup")
        val popupEnd = service.indexOf("private fun hideSavedPlacePopup", popupStart)
        val popupCode = service.substring(popupStart, popupEnd)
        assertFalse(popupCode.contains("startActivity("))
        assertFalse(popupCode.contains("FLAG_NOT_FOCUSABLE"))
    }

    @Test
    fun proximityAlertKeepsExistingEditorFlow() {
        assertTrue(service.contains("type = SavedPlaceType.ProximityAlert"))
        assertTrue(service.contains("openSavedPlaceEditor(place)"))
    }
}
''',
)
