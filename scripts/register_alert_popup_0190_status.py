#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUS = ROOT / "docs/PROJECT_STATUS.md"
DECISIONS = ROOT / "docs/DECISIONS.md"

STATUS_MARKER = "<!-- ALERT_POPUP_0190_CI_PENDING_DEVICE_START -->"
DECISION_MARKER = "<!-- DECISION_ALERT_POPUP_0190_START -->"

status_block = r'''<!-- ALERT_POPUP_0190_CI_PENDING_DEVICE_START -->
## 07/08/2026 — 0.1.190: pop-ups de radares/alertas permanecem 3 s após o ponto; CI aprovado

- **Branch:** `agent/alert-popup-lifecycle-0.1.190`; **PR:** #68; **base:** `agent/fix-farol-priority-latency-0.1.189`.
- **Commit funcional validado:** `c5805d89e0187d163c7e04a999554a95591700ca`; **workflow run:** `31191545985`; job `92909308180` concluído com sucesso.
- **Versão:** `0.1.190`; **versionCode:** `5474`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** os pop-ups de radares e alertas não devem desaparecer imediatamente ao ultrapassar o ponto; devem permanecer por cerca de 3 segundos e fechar sozinhos. Se o usuário tocar em `Fechar`, o mesmo ponto não deve reaparecer durante a passagem atual.
- **Situação anterior / causa:** `DirectionalAlertOverlayController` usava `PASSED_CLOSE_DELAY_MILLIS = 750L`, mantendo a indicação pós-passagem por somente 750 ms. A supressão manual até sair da zona já existia no motor e não precisava ser redesenhada.
- **Correção:** o atraso visual pós-passagem foi alterado para `3_000L`. O fechamento manual continua chamando `dismissUntilExit(visual.targetId)` no fluxo direcional e `dismissSavedPlaceUntilExit(alert.id)` no fluxo legado; depois de sair da zona de reset, uma aproximação futura pode avisar novamente.
- **Escopo:** alteração funcional isolada no ciclo visual do pop-up. `DecisionEngine`, rota, OCR, Manifest/permissões, `DirectionalAlertPolicy`, `DirectionalProximityAlertEngine` e `LiveRideAccessibilityService` permaneceram protegidos/inalterados pela validação de SHA-256 do build.
- **Primeiro run reprovado:** `31189837277`, job `92903581710`. A primeira causa real foi infraestrutura de materialização: transporte `.gz.b64` da 0.1.189 com CRC/length inválidos; a árvore 0.1.188 havia passado testes/Lint/assemble antes da falha. O validador 0.1.190 foi corrigido para usar `build_rota_certa_0189_parts.sh`, que reconstrói o patch a partir de seis partes com SHA-256 conferido.
- **Testes finais:** `tests=372`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/APK aprovada.
- **Artifact:** `rota-certa-0.1.190-alert-popup-lifecycle-validated`, ID `8999711497`, digest `sha256:042ea788583ca1c63d0502935206b241bb3d5cd380e1df2e59c0d7b81fb6914f`.
- **APK no artifact:** `rota-certa-0.1.190-alertas-popup-3s-validado-em-ci.apk`, `56.157.135` bytes.
- **Assinatura:** APK Signature Scheme v2 válida; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **SHA-256 do APK:** `8c32f2bcf07cc1109e4ac996db406bbbe520c5cd155fab9e703d37ef1b8e9d34`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.190/rota-certa-0.1.190-candidate.apk`.
- **Pendência física:** confirmar em aparelho real radar importado e alerta manual: aviso permanece aproximadamente 3 s depois de ultrapassar; `Fechar` impede reabertura do mesmo ponto na passagem atual; sair da zona e reaproximar rearma o ponto; fala, farol e desempenho permanecem normais.
<!-- ALERT_POPUP_0190_CI_PENDING_DEVICE_END -->

'''

decision_block = r'''<!-- DECISION_ALERT_POPUP_0190_START -->
## 07/08/2026 — ciclo visual de radares e alertas após ultrapassar o ponto

**Decisão:** o pop-up compartilhado de radar/alerta deve permanecer visível por `3.000 ms` depois que o motor indicar que o ponto foi ultrapassado, salvo fechamento manual. O botão `Fechar` silencia somente aquele alvo na aproximação/passagem atual; o alvo pode ser rearmado depois que o usuário sai da zona de reset e se aproxima novamente.

**Motivo:** `750 ms` era curto demais para leitura humana. A semântica `dismissUntilExit` já atendia à necessidade de não reabrir imediatamente o mesmo ponto e deve ser preservada, em vez de criar bloqueio permanente ou alterar o cadastro do radar/alerta.

**Fronteira:** esta decisão pertence ao controlador visual, não ao `DecisionEngine`, motor de rota, OCR, política direcional ou motor de proximidade. Não adicionar polling, timers globais contínuos ou estado persistente por ponto para cumprir esse comportamento.

**Evidência:** 0.1.190 / versionCode 5474; commit funcional `c5805d89e0187d163c7e04a999554a95591700ca`; PR #68; workflow `31191545985`; 372 testes sem falhas; Lint e `clean assembleDebug` aprovados; artifact `8999711497`; APK SHA-256 `8c32f2bcf07cc1109e4ac996db406bbbe520c5cd155fab9e703d37ef1b8e9d34`. Validação física ainda obrigatória.
<!-- DECISION_ALERT_POPUP_0190_END -->

'''


def prepend_once(path: Path, marker: str, block: str) -> bool:
    current = path.read_text(encoding="utf-8") if path.exists() else ""
    if marker in current:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(block + current, encoding="utf-8")
    return True

changed = []
if prepend_once(STATUS, STATUS_MARKER, status_block):
    changed.append(str(STATUS.relative_to(ROOT)))
if prepend_once(DECISIONS, DECISION_MARKER, decision_block):
    changed.append(str(DECISIONS.relative_to(ROOT)))

print("updated=" + (",".join(changed) if changed else "none"))
