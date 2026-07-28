from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutOverlayController.kt'
MAIN = ROOT / 'app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# Materializa primeiro a proteção OCR 0.1.144.
gradle_before = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.143"' in gradle_before or 'versionName = "0.1.142"' in gradle_before or 'versionName = "0.1.141"' in gradle_before:
    subprocess.run(['python', str(ROOT / 'scripts/apply_ocr_authority_0144.py')], check=True)

overlay = OVERLAY.read_text(encoding='utf-8')
overlay = overlay.replace('import android.view.GestureDetector\n', '')
if 'import android.os.Handler\n' not in overlay:
    overlay = overlay.replace('import android.graphics.drawable.GradientDrawable\n', 'import android.graphics.drawable.GradientDrawable\nimport android.os.Handler\nimport android.os.Looper\n')
if 'import android.view.HapticFeedbackConstants\n' not in overlay:
    overlay = overlay.replace('import android.view.Gravity\n', 'import android.view.Gravity\nimport android.view.HapticFeedbackConstants\n')
if 'import android.view.ViewConfiguration\n' not in overlay:
    overlay = overlay.replace('import android.view.ViewGroup\n', 'import android.view.ViewGroup\nimport android.view.ViewConfiguration\n')

old_trace = '''                                trace("bubble.shortcut.double_tap id=${module.spec.id} action=$it")
                                onShortcutDoubleTap(module.spec)
'''
new_trace = '''                                trace("bubble.shortcut.long_press id=${module.spec.id} action=$it")
                                onShortcutDoubleTap(module.spec)
'''
if old_trace in overlay:
    overlay = overlay.replace(old_trace, new_trace, 1)

old_gesture = '''        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    singleAction()
                    return true
                }

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    val action = doubleAction ?: return false
                    action()
                    return true
                }
            },
        )
        setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
'''
new_gesture = '''        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var longPressTriggered = false
        val longPressAction = Runnable {
            longPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            (doubleAction ?: singleAction).invoke()
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    longPressTriggered = false
                    view.isPressed = true
                    handler.postDelayed(longPressAction, 1_500L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moved = kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    if (moved) {
                        handler.removeCallbacks(longPressAction)
                        view.isPressed = false
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressAction)
                    view.isPressed = false
                    if (!longPressTriggered) singleAction()
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    handler.removeCallbacks(longPressAction)
                    view.isPressed = false
                    true
                }
                else -> true
            }
        }
'''
if old_gesture not in overlay:
    raise SystemExit('gesture detector block not found')
overlay = overlay.replace(old_gesture, new_gesture, 1)

# Acessibilidade: explica o gesto e evita anunciar toque duplo.
overlay = overlay.replace('contentDescription = spec.label', 'contentDescription = "${spec.label}. Toque para abrir; mantenha pressionado por um segundo e meio para executar."', 1)
OVERLAY.write_text(overlay, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')
main = main.replace(
    'Chave Google Maps API: obrigatória para o farol verde/vermelho',
    'Google Maps: necessário para calcular verde/vermelho',
)
main = main.replace(
    'Configure GOOGLE_MAPS_API_KEY no local.properties ou no segredo do GitHub Actions.',
    'Este APK foi gerado sem a chave do Google Maps. Gere novamente pelo GitHub Actions com o segredo GOOGLE_MAPS_API_KEY.',
)
main = main.replace(
    'Dois toques executam a ação rápida.',
    'Mantenha pressionado por 1,5 segundo para executar a ação rápida.',
)
main = main.replace(
    'dois toques',
    'pressionar e segurar',
)
MAIN.write_text(main, encoding='utf-8')

gradle = GRADLE.read_text(encoding='utf-8')
if 'versionName = "0.1.144"' not in gradle:
    raise SystemExit('expected 0.1.144 version not found')
gradle = gradle.replace('versionName = "0.1.144"', 'versionName = "0.1.145"', 1)
if 'versionCode = 5050' in gradle:
    gradle = gradle.replace('versionCode = 5050', 'versionCode = 5060', 1)
else:
    gradle = gradle.replace('versionCode = appVersionCode', 'versionCode = 5060', 1)
GRADLE.write_text(gradle, encoding='utf-8')

print('Applied long-press shortcuts, OCR stability and UI correction 0.1.145')
