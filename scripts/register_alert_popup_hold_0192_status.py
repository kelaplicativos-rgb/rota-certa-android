#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--head', required=True)
parser.add_argument('--run-id', required=True)
parser.add_argument('--pr-number', required=True)
parser.add_argument('--tests', required=True)
parser.add_argument('--apk-sha', required=True)
parser.add_argument('--apk-size', required=True)
parser.add_argument('--artifact-id', required=True)
parser.add_argument('--artifact-digest', required=True)
args = parser.parse_args()

root = Path(__file__).resolve().parents[1]
status_path = root / 'docs/PROJECT_STATUS.md'
decisions_path = root / 'docs/DECISIONS.md'

status_marker = '<!-- ALERT_POPUP_POST_PASS_HOLD_0192_CI_PENDING_DEVICE_START -->'
decision_marker = '<!-- DECISION_ALERT_POPUP_POST_PASS_HOLD_0192_START -->'

status = f'''{status_marker}
## 08/08/2026 — 0.1.192: pop-up de radar/alerta preserva os 3 s reais após a passagem

- **Branch:** `agent/fix-alert-popup-hold-0.1.192`; **PR:** #{args.pr_number}; **base:** `agent/proximity-alerts-no-direction-0.1.191`.
- **Commit funcional validado:** `{args.head}`; **workflow run:** `{args.run_id}`.
- **Versão:** `0.1.192`; **versionCode:** `5476`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido / evidência física:** na 0.1.191 o pop-up dos radares e alertas fechava rápido demais, apesar do contrato documentado de 3.000 ms.
- **Causa:** `DirectionalAlertOverlayController.showOrUpdate()` agendava o fechamento em 3.000 ms quando `visual.shouldClose=true`, porém a avaliação seguinte do motor podia retornar `visual=null` porque o alvo já estava `passed`; o serviço então chamava `directionalAlertOverlayChecklist5.hide()`, e `hide()` cancelava `pendingClose`, removendo a janela imediatamente.
- **Correção:** ausência normal de visual vinda do motor usa `hideFromEngineIdle()`. Se existe `pendingClose` pós-passagem, o overlay permanece até o callback de 3.000 ms. Fechamentos explícitos continuam usando `hide()` e permanecem imediatos.
- **Preservado:** botão `Fechar`, exclusão, alerta desligado, ausência prolongada de GPS, destruição do serviço e novo alvo continuam capazes de fechar/substituir imediatamente; radar continua com no máximo 1 fala por aproximação, alerta manual até 2; regra sem filtro de sentido da 0.1.191 permanece intacta.
- **Fronteira:** `DirectionalAlertPolicy`, `DirectionalProximityAlertEngine`, `DecisionEngine`, OCR, parser, rota, `RadarImport`, Manifest e permissões permaneceram byte a byte protegidos durante a transformação 0.1.192.
- **Testes finais:** `tests={args.tests}`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Artifact:** `rota-certa-0.1.192-alert-popup-hold-validated`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **APK:** `rota-certa-0.1.192-popup-radar-alerta-3s-validado-em-ci.apk`, `{args.apk_size}` bytes; assinatura APK v2 conferida.
- **SHA-256 do APK:** `{args.apk_sha}`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.192/rota-certa-0.1.192-candidate.apk`.
- **Pendência física:** cronometrar radar importado e alerta manual no aparelho real, confirmando permanência aproximada de 3 s após a passagem, fechamento manual imediato, ausência de reaparecimento na mesma passagem e rearme após sair da zona.
<!-- ALERT_POPUP_POST_PASS_HOLD_0192_CI_PENDING_DEVICE_END -->

'''

decision = f'''{decision_marker}
## 08/08/2026 — fechamento pós-passagem pertence ao overlay, não ao próximo snapshot do motor

**Decisão:** depois que um visual de radar/alerta entra em `shouldClose`, o tempo de 3.000 ms é propriedade do `DirectionalAlertOverlayController`. Um `visual=null` normal na avaliação seguinte não pode cancelar esse temporizador.

**Fechamento explícito:** desligar o recurso, perder GPS além da tolerância, excluir, tocar em `Fechar`, destruir o serviço ou substituir por novo visual continuam encerrando/cancelando imediatamente quando apropriado.

**Motivo:** o motor marca o alvo como passado e deixa de considerá-lo elegível na avaliação seguinte. Usar esse `null` como ordem de fechamento fazia `hide()` cancelar `pendingClose`, tornando o atraso de 3.000 ms apenas nominal e não observável no aparelho.

**Fronteira:** correção isolada no ciclo do overlay e na integração `onVisual`; não altera decisão de proximidade, direção, fala, farol, cards, OCR, rota, Casa/Alfinete, Manifest ou permissões.

**Evidência:** 0.1.192 / versionCode 5476; commit funcional `{args.head}`; workflow `{args.run_id}`; {args.tests} testes sem falhas; artifact `{args.artifact_id}`; APK SHA-256 `{args.apk_sha}`. Validação física do tempo ainda obrigatória.
<!-- DECISION_ALERT_POPUP_POST_PASS_HOLD_0192_END -->

'''

for path, marker, block in (
    (status_path, status_marker, status),
    (decisions_path, decision_marker, decision),
):
    text = path.read_text(encoding='utf-8')
    if marker in text:
        continue
    path.write_text(block + text, encoding='utf-8')

print('alert_popup_post_pass_hold_0192_status=registered')
