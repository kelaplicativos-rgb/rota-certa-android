# Rota Certa — Estado do projeto

## 30/07/2026 — 0.1.164 (5250) — resumo autoritativo do diagnóstico

- **Branch:** `codex/diagnostic-session-summary-0.1.164`
- **Commit atual:** será atualizado ao concluir a validação
- **PR:** pendente de abertura
- **Pedido:** analisar o relatório real da versão 0.1.163 e localizar por que o farol permaneceu amarelo sem calcular a rota.
- **Problema confirmado:** o inDrive foi reconhecido e os dois endereços foram extraídos, porém Casa e Alfinetes estavam desativados. Pela regra atual, não existe alvo para comparar o destino e a execução termina antes de `BUBBLE_ROUTE_REQUESTED`.
- **Defeito adicional encontrado:** a seção legada do relatório declarou que nenhuma sessão de leitura havia sido registrada e exibiu estado persistido antigo, apesar de o gravador de voo conter sessões e endereços válidos da execução atual.
- **Causa:** o resumo legado lê campos independentes do `SharedPreferences`; ele não reconstrói a tentativa atual a partir da trilha do gravador.
- **Correção aplicada:** novo resumo autoritativo derivado somente dos eventos da sessão atual. Ele informa card, endereços, região ativa, solicitação de rota, cache, Google Maps e decisão, preservando o relatório completo abaixo.
- **Arquivos do escopo:**
  - `scripts/fix_diagnostic_session_summary_0164.py`
  - `scripts/fix_diagnostic_session_summary_0164_rerun.py`
  - `.github/workflows/build-rota-certa-0.1.164.yml`
  - `.github/validation/rota-certa-0.1.164.txt`
  - código materializado: `FarolDiagnosticSummary0164.kt`, teste e integração mínima no `ManualTechnicalReportBuilder.kt`
- **Limite protegido:** sem alteração em `LiveRideAccessibilityService`, `DecisionEngine`, `RideTextParser`, `GoogleMapsService`, Casa/Alfinetes, cores, raio, overlay, Manifest, permissões, atividades ou serviços.
- **Testes planejados:** idempotência, limite exato de arquivos, testes unitários e de contrato, Android Lint, `assembleDebug`, Manifest, DEX, pacote, versão e assinatura.
- **Workflow:** pendente.
- **Artifact:** pendente.
- **SHA-256 do APK:** pendente.
- **Risco/pendência prática:** para o farol calcular verde ou vermelho, o usuário deve ativar Casa ou ao menos um Alfinete com coordenada válida. A versão 0.1.164 não altera essa regra funcional.

## 30/07/2026 — 0.1.163 (5240) — gravador de voo do farol

- **Branch:** `codex/farol-flight-recorder-0.1.163`
- **Commit validado:** `1a6e6544b72d894db886305427dfee56364d0993`
- **PR:** #31, rascunho
- **Workflow:** run `30569838311`, job `90963452922`, concluído com sucesso
- **Artifact:** `rota-certa-0.1.163-farol-flight-recorder-validated`, ID `8770445998`
- **SHA-256 do APK:** `1e82eb1cef67467862d0c979343be35c8645df039402787d08386307f491086b`
- **Resultado prático:** o primeiro relatório real provou que a leitura e o parser funcionaram e que a interrupção ocorreu antes da rota por ausência de região ativa.
