from pathlib import Path

ROOT = Path.cwd().resolve()
MARKER = "contextual_shortcut_menu_0_1_183"


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
        raise RuntimeError(
            f"{relative}: expected exactly one occurrence, found {count}: {old[:180]!r}"
        )
    write(relative, text.replace(old, new, 1))


replace_once(
    "app/build.gradle.kts",
    '        versionCode = 5430\n        versionName = "0.1.182"',
    '        versionCode = 5440\n        versionName = "0.1.183"',
)

service = "app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt"
replace_once(
    service,
    '''    private fun executeShortcutQuickTap0180(entry0180: ResolvedShortcutGridEntry0179) {
        dispatchShortcutGesture0180(
            entry0180,
            ShortcutDirectTapPolicy0182.actionForTap(entry0180.quickAction0180),
            "single_tap_direct_0182",
        )
    }
''',
    '''    private fun executeShortcutQuickTap0180(entry0180: ResolvedShortcutGridEntry0179) {
        showShortcutActionMenu0183(entry0180.spec)
    }
''',
)

service_text = read(service)
start_token = "    private fun showShortcutModulePopup0181(spec: BubbleShortcutSpec) {\n"
end_token = "\n    private fun hideShortcutModulePopup0181() {"
start = service_text.find(start_token)
if start < 0:
    raise RuntimeError("LiveRideAccessibilityService.kt: inherited shortcut popup not found")
end = service_text.find(end_token, start)
if end < 0:
    raise RuntimeError("LiveRideAccessibilityService.kt: inherited shortcut popup end not found")

new_menu = '''    private fun showShortcutActionMenu0183(spec: BubbleShortcutSpec) {
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
                text = "Escolha o que deseja fazer agora."
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(8), 0, dp(10))
            })
        }
        popup.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideShortcutModulePopup0181()
                true
            } else {
                false
            }
        }

        ShortcutContextMenuPolicy0183.quickActionLabel(spec.id, spec.doubleTapAction)?.let { label ->
            popup.addView(Button(this).apply {
                text = label
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    executeShortcutContextAction0183(spec)
                }
            })
        }

        if (spec.id == "clear_clipboard") {
            popup.addView(Button(this).apply {
                text = "Limpar área de transferência"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    clearClipboardFromBubble()
                }
            })
            popup.addView(Button(this).apply {
                text = "Limpar cache do Rota Certa"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    clearOwnCache0183()
                }
            })
            popup.addView(Button(this).apply {
                text = "Abrir módulo Limpar"
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    openShortcutModule0171(spec)
                }
            })
        } else {
            popup.addView(Button(this).apply {
                text = ShortcutContextMenuPolicy0183.primaryActionLabel(spec.id)
                setOnClickListener {
                    hideShortcutModulePopup0181()
                    executeShortcutModule(spec)
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
                "SHORTCUT_CONTEXT_MENU_0183",
                universalResolvedForegroundPackage(),
                "id=${spec.id}",
            )
        }
    }

    private fun executeShortcutContextAction0183(spec: BubbleShortcutSpec) {
        when (spec.id) {
            "alerts" -> saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)
            "saved_places" -> saveCurrentPlaceFromBubble(SavedPlaceType.Place)
            else -> executeShortcutDoubleTap(spec)
        }
    }

    private fun clearOwnCache0183() {
        scope.launch(Dispatchers.IO) {
            val cleared = runCatching {
                cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                toast(
                    if (cleared) "Cache do Rota Certa limpo" else "Não foi possível limpar o cache",
                )
            }
        }
    }
'''
service_text = service_text[:start] + new_menu + service_text[end:]
write(service, service_text)

write(
    "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt",
    '''package br.com.mapeiaia.rotacerta

object ShortcutContextMenuPolicy0183 {
    const val CONTRACT_MARKER = "SHORTCUT_CONTEXT_MENU_0183"

    fun quickActionLabel(
        shortcutId: String,
        quickAction: BubbleShortcutQuickAction?,
    ): String? = when (quickAction) {
        BubbleShortcutQuickAction.CopyAllVisibleText -> "Copiar texto desta tela"
        BubbleShortcutQuickAction.CreateQuickReply -> "Criar resposta rápida"
        BubbleShortcutQuickAction.CreateRadarAtCurrentLocation -> "Criar radar neste local"
        BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation -> "Criar alerta aqui"
        BubbleShortcutQuickAction.CreateNamedSavedPlaceAtCurrentLocation -> "Salvar localização atual"
        BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation ->
            "Usar localização atual como destino"
        BubbleShortcutQuickAction.CaptureCurrentAppAndScreen -> "Capturar aplicativo e tela agora"
        BubbleShortcutQuickAction.OpenPrimaryQuickLink -> "Abrir link principal"
        BubbleShortcutQuickAction.ClearApplicationCache -> "Limpar cache do Rota Certa"
        null -> null
    }.takeUnless { shortcutId == "clear_clipboard" }

    fun primaryActionLabel(shortcutId: String): String = when (shortcutId) {
        "route" -> "Abrir módulo Rota"
        "destination" -> "Abrir módulo Destino"
        "alerts" -> "Abrir módulo Alertas"
        "saved_places" -> "Abrir módulo Locais"
        "radars" -> "Abrir módulo Radares"
        "appearance" -> "Abrir Aparência"
        "backup" -> "Abrir Backup"
        "quick_replies" -> "Abrir Respostas"
        "manual_capture" -> "Abrir aplicativos e cards"
        "collector" -> "Abrir Coletor"
        "diagnostic" -> "Exportar diagnóstico"
        "stop_app" -> "Encerrar Rota Certa"
        "quick_links" -> "Abrir módulo Links rápidos"
        "links" -> "Abrir Links"
        "finance" -> "Abrir Financeiro"
        "whatsapp" -> "Abrir WhatsApp"
        "copy_trip_confirmation" -> "Copiar confirmação da viagem"
        "passenger_value" -> "Capturar valor do passageiro"
        else -> "Executar ação"
    }
}
''',
)

replace_once(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutGridCustomizationContract0179Test.kt",
    '''    fun singleTapDispatchesTheSelectedShortcutWithoutGestureDelay() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertTrue(service.contains("onShortcut = ::executeShortcutQuickTap0180"))
        assertTrue(service.contains("ShortcutDirectTapPolicy0182.actionForTap"))
    }''',
    '''    fun singleTapOpensTheSelectedShortcutContextMenuWithoutGestureDelay() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertTrue(service.contains("onShortcut = ::executeShortcutQuickTap0180"))
        assertTrue(service.contains("showShortcutActionMenu0183(entry0180.spec)"))
    }''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutPerEntryMenuContract0180Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutPerEntryMenuContract0180Test {
    private val overlay = File("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt").readText()
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun shortcutTapStillHasNoTripleTapWindowOrHoldClassification() {
        assertTrue(overlay.contains("SHORTCUT_DIRECT_TAP_0182"))
        assertTrue(overlay.contains("singleAction()"))
        assertFalse(overlay.contains("tapCount0180"))
        assertFalse(overlay.contains("SHORTCUT_TRIPLE_TAP_WINDOW_MILLIS"))
        assertFalse(overlay.contains("heldMillis >= ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS"))
    }

    @Test
    fun eachShortcutTapOpensItsOwnContextMenu() {
        assertTrue(service.contains("showShortcutActionMenu0183(entry0180.spec)"))
        assertTrue(service.contains("SHORTCUT_CONTEXT_MENU_0183"))
        assertTrue(service.contains("Escolha o que deseja fazer agora."))
        assertTrue(service.contains("event.actionMasked == MotionEvent.ACTION_OUTSIDE"))
        assertTrue(service.contains("Abrir módulo Limpar"))
        assertTrue(service.contains("openShortcutModule0171(spec)"))
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutInPlaceContract0181Test.kt",
    '''package br.com.mapeiaia.rotacerta

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutInPlaceContract0181Test {
    private val service = File("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").readText()

    @Test
    fun alertAndSavedPlaceQuickActionsKeepTheirRealOverlayEditors() {
        assertTrue(service.contains("executeShortcutContextAction0183"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.ProximityAlert)"))
        assertTrue(service.contains("saveCurrentPlaceFromBubble(SavedPlaceType.Place)"))
    }

    @Test
    fun contextualMenuOffersQuickActionAndPrimaryModuleAction() {
        assertTrue(service.contains("ShortcutContextMenuPolicy0183.quickActionLabel"))
        assertTrue(service.contains("ShortcutContextMenuPolicy0183.primaryActionLabel"))
        assertTrue(service.contains("executeShortcutContextAction0183(spec)"))
        assertTrue(service.contains("executeShortcutModule(spec)"))
    }
}
''',
)

write(
    "app/src/test/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183Test.kt",
    '''package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutContextMenuPolicy0183Test {
    @Test
    fun importantQuickActionsUseExplicitUserFacingLabels() {
        assertEquals(
            "Criar alerta aqui",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "alerts",
                BubbleShortcutQuickAction.CreateNamedAlertAtCurrentLocation,
            ),
        )
        assertEquals(
            "Criar radar neste local",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "radars",
                BubbleShortcutQuickAction.CreateRadarAtCurrentLocation,
            ),
        )
        assertEquals(
            "Usar localização atual como destino",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "destination",
                BubbleShortcutQuickAction.DefineDestinationAtCurrentLocation,
            ),
        )
        assertEquals(
            "Capturar aplicativo e tela agora",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "manual_capture",
                BubbleShortcutQuickAction.CaptureCurrentAppAndScreen,
            ),
        )
        assertEquals(
            "Abrir link principal",
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "quick_links",
                BubbleShortcutQuickAction.OpenPrimaryQuickLink,
            ),
        )
    }

    @Test
    fun clearUsesItsDedicatedActionsInsteadOfGenericQuickAction() {
        assertNull(
            ShortcutContextMenuPolicy0183.quickActionLabel(
                "clear_clipboard",
                BubbleShortcutQuickAction.ClearApplicationCache,
            ),
        )
    }

    @Test
    fun modulesHaveSpecificOpenLabels() {
        assertEquals("Abrir módulo Alertas", ShortcutContextMenuPolicy0183.primaryActionLabel("alerts"))
        assertEquals("Abrir módulo Radares", ShortcutContextMenuPolicy0183.primaryActionLabel("radars"))
        assertEquals("Abrir módulo Destino", ShortcutContextMenuPolicy0183.primaryActionLabel("destination"))
        assertEquals("Abrir aplicativos e cards", ShortcutContextMenuPolicy0183.primaryActionLabel("manual_capture"))
        assertEquals("Abrir módulo Links rápidos", ShortcutContextMenuPolicy0183.primaryActionLabel("quick_links"))
    }
}
''',
)

policy_path = "app/src/main/java/br/com/mapeiaia/rotacerta/ShortcutContextMenuPolicy0183.kt"
policy_text = read(policy_path)
if MARKER not in policy_text:
    write(policy_path, policy_text + f"\n// {MARKER}\n")

print("0.1.183 contextual shortcut menu applied")
