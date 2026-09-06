#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "<!-- FAROL_PHASE3_VALIDATED_STATUS_START -->"
DECISION_MARKER = "<!-- FAROL_PHASE3_VALIDATED_DECISION_START -->"

STATUS_BLOCK = """<!-- FAROL_PHASE3_VALIDATED_STATUS_START -->
## 06/08/2026 — fase 3 validada — rejeição de snapshot não apaga decisão válida

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
- **Commit funcional validado:** `941e280c580037d484eaab34145fdd7d6e37d29b`; workflow independente do replay validado no head `846f0163f4d93e7037996bd86e6a32dfb338a4ee`.
- **Versão:** `0.1.187`; **versionCode:** 5471; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Fonte da regressão:** `rota-certa-relatorio-depuracao (30).txt`, exportado em 06/08/2026 12:40 no Samsung SM-S911B/Android 16.
- **Situação anterior:** a fase 2 rejeitava corretamente raízes, pacotes e janelas incoerentes, porém toda rejeição chamava a limpeza visual. Uma decisão verde de 1,788 km foi apagada cerca de 109 ms depois por evento transitório do SystemUI/Rota Certa, sem prova de desaparecimento do card.
- **Causa:** o caminho crítico confundia “esta leitura não é confiável” com “o card confirmado desapareceu”. A rejeição de snapshot invalidava sessão e geração e chamava `hardClearUniversalTwoAddress`, transformando ruído transitório em amarelo.
- **Correção:** `FarolRejectedSnapshotPolicy0187Phase3` separa efeitos. Eventos transitórios, raiz ausente, pacote divergente ou evento sem pacote são descartados sem qualquer efeito visual. `event_root_window_mismatch` invalida somente trabalho assíncrono e buffers de leitura, preservando cor, destino e distância já confirmados. O bloco de rejeição não chama `hardClearUniversalTwoAddress` nem `showOverlay`.
- **Transições positivas preservadas:** tela coerente do aplicativo selecionado sem card válido continua limpando para amarelo; transição externa real e confirmada continua limpando para cinza; novo destino confirmado pode substituir a decisão anterior.
- **Arquivos principais:** `FarolRuntimeSafety0187.kt`, `LiveRideAccessibilityService.kt`, `FarolRuntimeSafety0187Test.kt`, `FarolRuntimeFix0187ContractTest.kt`, `tests/test_farol_trace_phase3.py`, patch e injetor da fase 3 e workflow do laboratório sem dependência de Marketplace Actions.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine`, rota Google, raio, Casa/Alfinetes, parser universal, confirmação 0.1.185, OCR como fonte auxiliar, radares e alertas não foram alterados.
- **Testes Android:** tests=354; failures=0; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Replay:** `Farol deterministic trace lab`, run `31120432823`, aprovado; valida verde preservado na leitura rejeitada, amarelo somente após desaparecimento coerente e cinza somente após pacote externo confirmado. O replay histórico estrito também permaneceu verde.
- **Workflow funcional:** `Build Rota Certa 0.1.187`, run `31118222427`, concluído com sucesso em testes, Lint, compilação, artifact, publicação e verificação do download permanente.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8974060308`, digest `cf7b6facbd474656e42c65ff12857de223e755212ed98ffbafdab152774d3f1b`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31118222427/artifacts/8974060308
- **Link permanente do APK:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; pacote/versão/versionCode conferidos; assinatura APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`.
- **SHA-256 do APK:** `0f7b9f8f8e8b592a7165746a9c8c2132c002ddd25cea88acc44cc0a536ff0332`.
- **Infraestrutura:** execuções redundantes posteriores falharam em `Set up job` por `Service Unavailable` ao baixar Marketplace Actions, antes do checkout e sem executar código; não contradizem o build funcional aprovado. O laboratório foi tornado independente dessas Actions e concluiu com sucesso.
- **Pendência real:** instalar este APK no Samsung SM-S911B/Android 16 e repetir decisão verde/vermelha com SystemUI, bolinha, teclado, retorno ao feed, troca de card e saída real do aplicativo; confirmar ausência de pisca e que eventos rejeitados registram preservação sem `BUBBLE_CLEAR_REQUEST` decorrente da rejeição.
<!-- FAROL_PHASE3_VALIDATED_STATUS_END -->

"""

DECISION_BLOCK = """<!-- FAROL_PHASE3_VALIDATED_DECISION_START -->
## 06/08/2026 — rejeitar uma leitura não é prova para apagar o farol

- **Descartar sem efeito:** SystemUI, teclado, overlay do Rota Certa, evento sem pacote, raiz ausente, raiz divergente e demais snapshots incoerentes não alteram cor, destino, quilômetros ou decisão já confirmada.
- **Invalidar leitura mantendo visual:** incompatibilidade entre janela do evento selecionado e janela da raiz cancela sessão, OCR, análise e rota em andamento e avança a geração, mas mantém o último visual confirmado até surgir evidência coerente.
- **Amarelo:** somente uma leitura coerente do aplicativo selecionado que prove ausência, fechamento ou troca do card pode limpar decisão e distância para o estado de espera.
- **Cinza:** somente uma transição externa real e confirmada para aplicativo passivo ou não selecionado pode limpar para inativo.
- **Ordem:** nenhuma rejeição de snapshot pode chamar `hardClearUniversalTwoAddress` ou `showOverlay`; primeiro descartar/inutilizar o trabalho, depois aguardar evidência positiva.
- **Concorrência:** resultado pertencente à sessão, janela ou geração invalidada continua proibido de pintar, mesmo que o visual anterior tenha sido preservado.
- **Núcleo universal:** adaptadores continuam sem autoridade para decidir cor; confirmação de destino final, rota real e `DecisionEngine` permanecem os únicos caminhos para verde/vermelho.
<!-- FAROL_PHASE3_VALIDATED_DECISION_END -->

"""


def prepend_once(path: Path, marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8")
    if marker not in current:
        path.write_text(block + current, encoding="utf-8")


repo = Path(__file__).resolve().parents[1]
prepend_once(repo / "docs/PROJECT_STATUS.md", STATUS_MARKER, STATUS_BLOCK)
prepend_once(repo / "docs/DECISIONS.md", DECISION_MARKER, DECISION_BLOCK)
