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

status_marker = '<!-- PROXIMITY_NO_DIRECTION_0191_CI_PENDING_DEVICE_START -->'
decision_marker = '<!-- DECISION_PROXIMITY_NO_DIRECTION_0191_START -->'

status = f'''{status_marker}
## 07/08/2026 — 0.1.191: radares e alertas avisam por aproximação, sem filtro de sentido

- **Branch:** `agent/proximity-alerts-no-direction-0.1.191`; **PR:** #{args.pr_number}; **base:** `agent/alert-popup-lifecycle-0.1.190`.
- **Commit funcional validado:** `{args.head}`; **workflow run:** `{args.run_id}`.
- **Versão:** `0.1.191`; **versionCode:** `5475`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** retirar a exigência de o veículo estar no mesmo sentido do alerta/radar, pois heading e direção cadastrada estavam atrasando ou bloqueando avisos; alertar enquanto a distância confirma aproximação, independentemente do sentido.
- **Situação anterior / causa:** `DirectionalProximityAlertEngine` exigia heading utilizável, `isTargetAhead(...)` e, para radar importado, `radarDirectionMatches(...)`. A passagem também dependia de o alvo ficar geometricamente atrás do heading. Leituras de rumo ausentes, instáveis ou divergentes podiam impedir o aviso mesmo com distância diminuindo.
- **Correção:** elegibilidade de radar e alerta passa a usar GPS recente/preciso + distância dentro do limite + tendência de aproximação. Heading, azimute e direção cadastrada do radar deixam de autorizar ou bloquear o aviso. A passagem é reconhecida quando a distância cresce de forma consistente depois do mínimo observado.
- **Preservado:** radar fala no máximo 1 vez por aproximação; alerta manual até 2; reset após sair da zona; `Fechar` silencia o ponto apenas na passagem atual; pop-up continua por 3 s após ultrapassar; farol, OCR, rota, Manifest e permissões não foram alterados.
- **Testes finais:** `tests={args.tests}`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Artifact:** `rota-certa-0.1.191-proximity-no-direction-validated`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **APK:** `rota-certa-0.1.191-alertas-sem-filtro-sentido-validado-em-ci.apk`, `{args.apk_size}` bytes; assinatura APK v2 conferida.
- **SHA-256 do APK:** `{args.apk_sha}`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.191/rota-certa-0.1.191-candidate.apk`.
- **Pendência física:** testar radar importado e alerta manual em aproximações nos dois sentidos, heading instável/ausente, trânsito lento, passagem do ponto, fechamento manual e nova aproximação futura; confirmar aviso oportuno sem duplicação.
<!-- PROXIMITY_NO_DIRECTION_0191_CI_PENDING_DEVICE_END -->

'''

decision = f'''{decision_marker}
## 07/08/2026 — proximidade tem prioridade sobre sentido em radares e alertas

**Decisão:** radares importados e alertas manuais não podem depender do heading do aparelho, de `isTargetAhead` nem da direção cadastrada do radar para avisar. O aviso é autorizado por GPS recente/preciso, distância dentro do limite e tendência de aproximação.

**Passagem:** depois do menor valor observado, crescimento consistente da distância identifica que o ponto foi ultrapassado. O pop-up mantém os 3.000 ms pós-passagem da 0.1.190 e o fechamento manual continua silenciando somente a passagem atual até sair da zona de reset.

**Motivo:** heading/azimute pode chegar atrasado, oscilar ou ficar indisponível mesmo quando a posição e a redução de distância são suficientes para concluir que o veículo se aproxima. Para segurança do aviso, falso negativo por sentido é pior do que avisar um ponto próximo vindo pelo sentido oposto.

**Fronteira:** esta mudança pertence somente ao subsistema de proximidade. Não altera `DecisionEngine`, leitura de cards, OCR, rota, Casa/Alfinete, cores do farol, Manifest ou permissões.

**Evidência:** 0.1.191 / versionCode 5475; commit funcional `{args.head}`; workflow `{args.run_id}`; {args.tests} testes sem falhas; artifact `{args.artifact_id}`; APK SHA-256 `{args.apk_sha}`. Validação física ainda obrigatória.
<!-- DECISION_PROXIMITY_NO_DIRECTION_0191_END -->

'''

for path, marker, block in (
    (status_path, status_marker, status),
    (decisions_path, decision_marker, decision),
):
    text = path.read_text(encoding='utf-8')
    if marker in text:
        continue
    path.write_text(block + text, encoding='utf-8')

print('proximity_alerts_0191_status=registered')
