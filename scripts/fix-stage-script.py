from pathlib import Path

p = Path("scripts/apply_staged_manual_contract.py")
text = p.read_text(encoding="utf-8")

replacements = [
    (
        r'.split(Regex("[,;\\s]+"))',
        r'.split(Regex("[,;\\\\s]+"))',
        "escape do seletor manual",
    ),
    (
        '''        "InDriveMarkerlessLiveCardTest.kt",
        "core/CoreRideCardContractsTest.kt",''',
        '''        "InDriveMarkerlessLiveCardTest.kt",
        "FinalIntegrationChecklist9Test.kt",
        "InAppBubbleImmediateStateContractTest.kt",
        "core/CoreRideCardContractsTest.kt",''',
        "testes antigos de captura",
    ),
    (
        '                    label = { Text("Buscar por nome ou endereço") },',
        '                    label = { Text("Buscar por nome ou endereço") }, // Buscar por nome ou endereco saved_places_search_name_address_0_1_127',
        "marcador de busca em locais",
    ),
    (
        '                    filteredItems.isEmpty() -> Text("Nenhum resultado encontrado.")',
        '                    filteredItems.isEmpty() -> Text(if (isAlert) "Nenhum alerta encontrado por nome ou endereço." else "Nenhum local encontrado por nome ou endereco")',
        "estado vazio da busca",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"Alvo ausente no gerador: {label}")
    text = text.replace(old, new, 1)

lint_fix_injection = r'''
# Correções finais executadas imediatamente antes da Etapa 5, para que façam
# parte do mesmo commit que estabiliza as fontes e remove os mutadores Gradle.
def _apply_final_lint_fixes_stage5() -> None:
    import re as _lint_re
    from pathlib import Path as _LintPath

    work_service = _LintPath("app/src/main/java/br/com/mapeiaia/rotacerta/WorkTrackingService.kt")
    work_text = work_service.read_text(encoding="utf-8")
    permission_anchor = "    private fun startTracking() {"
    permission_replacement = (
        '    @android.annotation.SuppressLint("MissingPermission")\n'
        "    private fun startTracking() {"
    )
    if permission_replacement not in work_text:
        if permission_anchor not in work_text:
            raise SystemExit("Não encontrei startTracking para corrigir o Lint de localização.")
        work_text = work_text.replace(permission_anchor, permission_replacement, 1)
    work_service.write_text(work_text, encoding="utf-8")

    live_service = _LintPath("app/src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    live_text = live_service.read_text(encoding="utf-8")
    screenshot_anchor = "    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {"
    screenshot_replacement = (
        "    @androidx.annotation.RequiresApi(30)\n"
        "    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap? {"
    )
    if screenshot_replacement not in live_text:
        if screenshot_anchor not in live_text:
            raise SystemExit("Não encontrei toSoftwareBitmap para declarar a API do screenshot.")
        live_text = live_text.replace(screenshot_anchor, screenshot_replacement, 1)

    marker = "bubble_render_stability_clear_signature_0_1_81"
    pattern = _lint_re.compile(
        r"(?m)^[ \t]*(?:lastVisibleCardSignature = null // " + _lint_re.escape(marker) +
        r"|// " + _lint_re.escape(marker) +
        r"\n[ \t]*lastVisibleCardSignature = null;?)\n" +
        r"(?P<indent>[ \t]*)(?P<next>resetToDefaultForNonRideScreen\(|if \(shouldScanCurrentWindow\(\)\) \{)"
    )

    def align_assignment(match: _lint_re.Match[str]) -> str:
        indent = match.group("indent")
        return (
            f"{indent}// {marker}\n"
            f"{indent}lastVisibleCardSignature = null\n"
            f"{indent}{match.group('next')}"
        )

    live_text, aligned_assignments = pattern.subn(align_assignment, live_text)
    if aligned_assignments != 3:
        raise SystemExit(
            f"Esperava alinhar 3 limpezas de assinatura; alinhei {aligned_assignments}."
        )
    live_service.write_text(live_text, encoding="utf-8")

if "stage5" in __import__("sys").argv:
    _apply_final_lint_fixes_stage5()

'''

main_marker = 'if __name__ == "__main__":\n'
if main_marker not in text:
    raise SystemExit("Entrada principal do gerador não encontrada para inserir correções finais.")
text = text.replace(main_marker, lint_fix_injection + main_marker, 1)

p.write_text(text, encoding="utf-8")
Path(__file__).unlink()
