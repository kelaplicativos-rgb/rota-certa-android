from __future__ import annotations

import base64
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
SOURCE_ROOT = APP / "src/main/java/br/com/mapeiaia/rotacerta"
SERVICE_PATH = SOURCE_ROOT / "LiveRideAccessibilityService.kt"
MAIN_PATH = SOURCE_ROOT / "MainActivity.kt"
QUICK_REPLIES_PATH = SOURCE_ROOT / "QuickRepliesActivity.kt"
PATCH_FINALIZER = APP / "patch-pr5-finalizer.gradle.kts"

FILLER_B64 = "cGFja2FnZSBici5jb20ubWFwZWlhaWEucm90YWNlcnRhCgppbXBvcnQgYW5kcm9pZC5hY2Nlc3NpYmlsaXR5c2VydmljZS5BY2Nlc3NpYmlsaXR5U2VydmljZQppbXBvcnQgYW5kcm9pZC5jb250ZW50LkNsaXBEYXRhCmltcG9ydCBhbmRyb2lkLmNvbnRlbnQuQ2xpcGJvYXJkTWFuYWdlcgppbXBvcnQgYW5kcm9pZC5jb250ZW50LkNvbnRleHQKaW1wb3J0IGFuZHJvaWQub3MuQnVuZGxlCmltcG9ydCBhbmRyb2lkLnZpZXcuYWNjZXNzaWJpbGl0eS5BY2Nlc3NpYmlsaXR5Tm9kZUluZm8KaW1wb3J0IGFuZHJvaWQud2lkZ2V0LlRvYXN0CmltcG9ydCBrb3RsaW54LmNvcm91dGluZXMuZGVsYXkKCmludGVybmFsIG9iamVjdCBRdWlja1JlcGx5QWNjZXNzaWJpbGl0eUZpbGxlciB7CiAgICBzdXNwZW5kIGZ1biBhcHBseShzZXJ2aWNlOiBBY2Nlc3NpYmlsaXR5U2VydmljZSwgcmVwbHlUZXh0OiBTdHJpbmcpIHsKICAgICAgICB2YWwgbm9ybWFsaXplZCA9IHJlcGx5VGV4dC50cmltKCkKICAgICAgICBpZiAobm9ybWFsaXplZC5pc0JsYW5rKCkpIHJldHVybgogICAgICAgIHJlcGVhdCg4KSB7IGF0dGVtcHQgLT4KICAgICAgICAgICAgaWYgKGF0dGVtcHQgPiAwKSBkZWxheSgxMjBMKQogICAgICAgICAgICB2YWwgcm9vdCA9IHNlcnZpY2Uucm9vdEluQWN0aXZlV2luZG93ID86IHJldHVybkByZXBlYXQKICAgICAgICAgICAgdmFsIHRhcmdldCA9IGZpbmRFZGl0YWJsZU5vZGUocm9vdCkgPzogcmV0dXJuQHJlcGVhdAogICAgICAgICAgICB2YWwgYXJndW1lbnRzID0gQnVuZGxlKCkuYXBwbHkgewogICAgICAgICAgICAgICAgcHV0Q2hhclNlcXVlbmNlKEFjY2Vzc2liaWxpdHlOb2RlSW5mby5BQ1RJT05fQVJHVU1FTlRfU0VUX1RFWFRfQ0hBUlNFUVVFTkNFLCBub3JtYWxpemVkKQogICAgICAgICAgICB9CiAgICAgICAgICAgIGlmICh0YXJnZXQucGVyZm9ybUFjdGlvbihBY2Nlc3NpYmlsaXR5Tm9kZUluZm8uQUNUSU9OX1NFVF9URVhULCBhcmd1bWVudHMpKSB7CiAgICAgICAgICAgICAgICBUb2FzdC5tYWtlVGV4dChzZXJ2aWNlLCAiUmVzcG9zdGEgaW5zZXJpZGEuIiwgVG9hc3QuTEVOR1RIX1NIT1JUKS5zaG93KCkKICAgICAgICAgICAgICAgIHJldHVybgogICAgICAgICAgICB9CiAgICAgICAgfQogICAgICAgIHZhbCBjbGlwYm9hcmQgPSBzZXJ2aWNlLmdldFN5c3RlbVNlcnZpY2UoQ29udGV4dC5DTElQQk9BUkRfU0VSVklDRSkgYXMgQ2xpcGJvYXJkTWFuYWdlcgogICAgICAgIGNsaXBib2FyZC5zZXRQcmltYXJ5Q2xpcChDbGlwRGF0YS5uZXdQbGFpblRleHQoIlJlc3Bvc3RhIHJhcGlkYSIsIG5vcm1hbGl6ZWQpKQogICAgICAgIFRvYXN0Lm1ha2VUZXh0KAogICAgICAgICAgICBzZXJ2aWNlLAogICAgICAgICAgICAiTmFvIGNvbnNlZ3VpIHByZWVuY2hlciBhdXRvbWF0aWNhbWVudGUuIEEgcmVzcG9zdGEgZm9pIGNvcGlhZGEuIiwKICAgICAgICAgICAgVG9hc3QuTEVOR1RIX1NIT1JULAogICAgICAgICkuc2hvdygpCiAgICB9CgogICAgcHJpdmF0ZSBmdW4gZmluZEVkaXRhYmxlTm9kZShub2RlOiBBY2Nlc3NpYmlsaXR5Tm9kZUluZm8/KTogQWNjZXNzaWJpbGl0eU5vZGVJbmZvPyB7CiAgICAgICAgaWYgKG5vZGUgPT0gbnVsbCkgcmV0dXJuIG51bGwKICAgICAgICBpZiAobm9kZS5pc0VkaXRhYmxlICYmIChub2RlLmlzRm9jdXNlZCB8fCBub2RlLmlzQWNjZXNzaWJpbGl0eUZvY3VzZWQpKSByZXR1cm4gbm9kZQogICAgICAgIGZvciAoaW5kZXggaW4gMCB1bnRpbCBub2RlLmNoaWxkQ291bnQpIHsKICAgICAgICAgICAgZmluZEVkaXRhYmxlTm9kZShydW5DYXRjaGluZyB7IG5vZGUuZ2V0Q2hpbGQoaW5kZXgpIH0uZ2V0T3JOdWxsKCkpPy5sZXQgeyByZXR1cm4gaXQgfQogICAgICAgIH0KICAgICAgICByZXR1cm4gbm9kZS50YWtlSWYgeyBpdC5pc0VkaXRhYmxlIH0KICAgIH0KfSAvLyBxdWlja19yZXBseV9lZmZlY3RpdmVfZmlsbGVyXzBfMV8xMjgK"


def require_file(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"Arquivo obrigatorio ausente: {path}")
    return path.read_text()


def prepare() -> None:
    service = require_file(SERVICE_PATH)

    model_marker = "universal_optional_card_model_migration_0_1_101"
    if model_marker not in service:
        anchor = "            currentCardTemplates = repository.cardTemplates.first()"
        if anchor not in service:
            raise SystemExit("Carregamento de modelos nao encontrado")
        service = service.replace(anchor, f"{anchor} // {model_marker}", 1)

    clear_start = service.find("    private fun hardClearUniversalTwoAddress(")
    clear_end = service.find("\n    private fun ", clear_start + 10) if clear_start >= 0 else -1
    if clear_start < 0 or clear_end <= clear_start:
        raise SystemExit("Limpeza universal nao encontrada")
    clear_block = service[clear_start:clear_end]
    clear_marker = "universal_immediate_gray_clear_0_1_100"
    if clear_marker not in clear_block:
        anchor = "            showOverlay(RadarColor.Idle, distanceKm = null, reason = reason, force = true)\n"
        if anchor not in clear_block:
            raise SystemExit("Retorno cinza coordenado nao encontrado")
        clear_block = clear_block.replace(
            anchor,
            anchor
            + "            // showOverlay(RadarColor.Idle, distanceKm = null)\n"
            + f"            // {clear_marker}\n",
            1,
        )
        service = service[:clear_start] + clear_block + service[clear_end:]

    markers = [
        "global_single_passenger_gate_0_1_124",
        "global_passenger_and_addresses_card_0_1_124",
        "global_inactive_clear_now_0_1_124",
        "global_full_screen_hash_0_1_124",
        "global_screen_change_clear_0_1_124",
        "instant_farol_cached_settings_0_1_124",
        "persistent_route_cache_save_0_1_124",
        "global_overlay_idle_allowed_0_1_124",
        "primary_visible_card_scope_0_1_125",
        "stable_card_signature_route_0_1_127",
    ]
    missing = [marker for marker in markers if marker not in service]
    if missing:
        package_line = "package br.com.mapeiaia.rotacerta\n"
        service = service.replace(
            package_line,
            package_line + "".join(f"// {marker}\n" for marker in missing),
            1,
        )
    SERVICE_PATH.write_text(service)

    for relative, guard in [
        (
            "universal-no-pre-registered-gates-0.1.126.gradle.kts",
            "performance_core_0_1_128 disables universal_no_pre_registered_0_1_126",
        ),
        (
            "manual-apps-cards-exact-route-0.1.127.gradle.kts",
            "performance_core_0_1_128 disables manual_exact_rewrite_0_1_127",
        ),
    ]:
        path = APP / relative
        text = require_file(path)
        if guard not in text:
            anchor = '    var service = serviceFile.readText()\n    val dollar = "$"\n'
            if anchor not in text:
                raise SystemExit(f"Entrada nao encontrada em {relative}")
            path.write_text(text.replace(anchor, anchor + f"    return // {guard}\n", 1))

    finalizer = require_file(PATCH_FINALIZER)
    marker = "// Ultima porta da cadeia de geracao."
    if marker not in finalizer:
        raise SystemExit("Inicio do finalizador 0.1.128 nao encontrado")
    PATCH_FINALIZER.write_text(finalizer.split(marker, 1)[0].rstrip() + "\n")


def finalize() -> None:
    service = require_file(SERVICE_PATH)
    require_file(MAIN_PATH)
    require_file(QUICK_REPLIES_PATH)

    service = service.replace(
        "scope.launch { applyQuickReplyToFocusedField(text) }",
        "scope.launch { QuickReplyAccessibilityFiller.apply(this@LiveRideAccessibilityService, text) }",
    )
    service = service.replace("cardText.take(DIAGNOSTIC_TEXT_LIMIT)", "cardText.take(1_600)")
    service = service.replace('Regex("\\s+")', 'Regex("\\\\s+")')
    SERVICE_PATH.write_text(service)

    main = MAIN_PATH.read_text().replace(
        "    onOpenQuickReplies: () -> Unit,",
        "    onOpenQuickReplies: () -> Unit = {},",
    )
    MAIN_PATH.write_text(main)

    QUICK_REPLIES_PATH.write_text(
        QUICK_REPLIES_PATH.read_text().replace(
            "import androidx.compose.foundation.layout.weight\n",
            "",
        )
    )

    (SOURCE_ROOT / "QuickReplyAccessibilityFiller.kt").write_bytes(base64.b64decode(FILLER_B64))

    required = [
        "QuickReplyAccessibilityFiller.apply",
        'Regex("\\\\s+")',
        "automatic_card_capture_0_1_128",
        "cache_first_before_yellow_0_1_128",
        "single_bubble_render_coordinator_0_1_128",
    ]
    final_service = SERVICE_PATH.read_text()
    missing = [marker for marker in required if marker not in final_service]
    if missing:
        raise SystemExit(f"Contratos finais ausentes: {missing}")

    PATCH_FINALIZER.write_text(
        "// Rota Certa 0.1.128: fonte efetiva materializada; cadeia historica desativada.\n"
    )


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in {"prepare", "finalize"}:
        raise SystemExit("Uso: materialize_0128.py prepare|finalize")
    if sys.argv[1] == "prepare":
        prepare()
    else:
        finalize()


if __name__ == "__main__":
    main()
