#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "<!-- FAROL_TRACE_LAB_PHASE1_STATUS_START -->"
DECISION_MARKER = "<!-- FAROL_TRACE_LAB_PHASE1_DECISION_START -->"

STATUS_BLOCK = """<!-- FAROL_TRACE_LAB_PHASE1_STATUS_START -->
## 06/08/2026 — fase 1 — laboratório determinístico do farol

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base:** `main`.
- **Versão confirmada:** `0.1.187` (`versionCode` 5471), pacote `br.com.mapeiaia.rotacerta`.
- **Pedido:** iniciar a correção estrutural criando primeiro uma reprodução determinística dos erros do relatório, sem alterar ainda o comportamento de produção da bolinha.
- **Fonte:** `rota-certa-relatorio-depuracao (29).txt`, exportado em 06/08/2026 10:08 no Samsung SM-S911B/Android 16.
- **Implementação:** laboratório Python independente, fixture sanitizada, oráculo de estado e workflow próprio. Endereços e textos pessoais não foram incorporados.
- **Cobertura reproduzida:** 2.473 eventos; 1.440 eventos externos com raiz antiga do inDrive; 455 eventos repetidos; 562 limpezas; resultado de rota atrasado; raiz nula; raiz de outro pacote; seis sinais de falha; desaparecimento de card.
- **Invariantes:** portão de pacote antes da raiz; geração antiga não pinta; verde/vermelho exigem destino e distância; cinza/amarelo não retêm km; falha termina fechada; limpeza idempotente; card desaparecido elimina decisão.
- **Validação local:** sete testes aprovados; zero violações; zero divergências do oráculo; 454 eventos confluídos; 561 limpezas redundantes sem redesenho; apenas dez mudanças visuais no replay completo.
- **Arquivos:** `tools/farol_trace_lab.py`, `tests/fixtures/farol_trace_20260806_sanitized.json`, `tests/test_farol_trace_lab.py`, `.github/workflows/farol-trace-lab.yml`, `docs/FAROL_TRACE_REPLAY_LAB.md`.
- **Fronteira protegida:** nenhum arquivo Kotlin, Manifest, permissão, `DecisionEngine`, parser, rota, OCR, radar, alerta ou overlay foi alterado nesta fase.
- **Pendência:** conectar o oráculo aos contratos Kotlin e ao caminho real de acessibilidade somente após autorização explícita para a fase seguinte.
<!-- FAROL_TRACE_LAB_PHASE1_STATUS_END -->

"""

DECISION_BLOCK = """<!-- FAROL_TRACE_LAB_PHASE1_DECISION_START -->
## 06/08/2026 — toda correção do farol deve passar pelo replay do relatório

- O relatório sanitizado é uma fixture de regressão, não um log contínuo de produção.
- O portão de pacote é a primeira decisão e deve ocorrer antes de qualquer consulta à raiz.
- Pacote, janela, geração, assinatura do card e destino formam a identidade mínima de uma leitura.
- Resultado atrasado, raiz nula ou raiz de outro pacote falham fechado e nunca recuperam distância anterior.
- Repetição da mesma tela e limpeza redundante não podem gerar processamento ou redesenho ilimitado.
- O laboratório permanece independente do Android nesta fase; a próxima fase ligará o mesmo contrato ao Kotlin sem permitir que o oráculo passe por mera inspeção textual.
<!-- FAROL_TRACE_LAB_PHASE1_DECISION_END -->

"""


def prepend_once(path: Path, marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8")
    if marker not in current:
        path.write_text(block + current, encoding="utf-8")


repo = Path(__file__).resolve().parents[1]
prepend_once(repo / "docs/PROJECT_STATUS.md", STATUS_MARKER, STATUS_BLOCK)
prepend_once(repo / "docs/DECISIONS.md", DECISION_MARKER, DECISION_BLOCK)
