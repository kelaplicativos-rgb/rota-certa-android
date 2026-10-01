# Rota Certa — Estado do projeto

## 30/07/2026 — 0.1.166 (5270) — farol universal para todos os aplicativos selecionados

- **Branch:** `agent/fix-99-ocr-0.1.166`
- **Commit de código e workflow validado:** `1b8422f1387c358eab2996164c04cb59cf4f99d9`
- **PR:** #34, rascunho e sem merge na `main`
- **Pedido:** corrigir as falhas reais da 99 e do Uber usando uma tecnologia mais robusta, sem limitar o farol aos três aplicativos conhecidos; qualquer pacote selecionado pelo usuário deve usar o mesmo motor.
- **Situação anterior:** 99 e Uber estavam selecionados, mas nenhuma tentativa chegou à rota. O inDrive havia conseguido decisões em sessão anterior, descartando Google Maps, Casa/Alfinetes e o motor de decisão como causa geral.
- **Problema e causa — 99:** a acessibilidade entregava texto vazio ou incompleto e o fallback chegava a `OCR_REQUEST_EVALUATE`, porém o método exigia que o texto incompleto já parecesse um card antes de autorizar o screenshot. O OCR necessário para recuperar os endereços era bloqueado pela própria pré-condição.
- **Problema e causa — Uber:** a captura OCR chegou a iniciar na janela real `1759`, mas um evento transitório com pacote nulo usou o `event.windowId` `1766`, abriu uma nova sessão e invalidou a captura antes do resultado.
- **Correção aplicada:** nova política pura `FarolSelectedAppInputPolicy0166`. A autorização usa exclusivamente o conjunto persistido de pacotes escolhidos pelo usuário e a raiz estrita atual. A janela estável vem de `rootInActiveWindow.windowId` quando a raiz pertence ao pacote selecionado. OCR continua pontual, orientado a eventos, sem loop ou polling, e só entra quando o parser ainda não encontrou os dois endereços.
- **Universalidade comprovada:** teste com pacote fictício `com.parceiro.corridas.driver` selecionado; nenhum nome de 99, Uber ou inDrive existe na nova política de produção. Pacote não selecionado permanece bloqueado.
- **Arquivos principais alterados:**
  - `scripts/fix_farol_selected_apps_0166.py`
  - `scripts/fix_farol_selected_apps_0166_rerun.py`
  - `.github/workflows/build-rota-certa-0.1.166.yml`
  - `.github/validation/rota-certa-0.1.166.txt`
  - código materializado: `LiveRideAccessibilityService.kt`, `FarolSelectedAppInputPolicy0166.kt` e `FarolSelectedAppInputPolicy0166Test.kt`
- **Limite protegido:** `DecisionEngine`, `RideTextParser`, `GoogleMapsService`, `ManualTechnicalReportBuilder`, `FarolDiagnosticSummary0165`, Manifest, permissões, componentes, Casa/Alfinetes e Coletor permaneceram inalterados. O Coletor continua ausente.
- **Testes executados:** scripts idempotentes; fronteira exata de arquivos; preservação da janela raiz diante de overlay; pacote arbitrário selecionado; bloqueio de pacote não selecionado; bloqueio de OCR quando o parser já está ativo; suíte unitária e de contrato; Android Lint; `clean assembleDebug`; inspeção de Manifest e DEX; pacote, versão, versionCode e assinatura APK v2.
- **Workflow final:** `Build Rota Certa 0.1.166`, run `30586897527`, job `91020295162`, todos os passos concluídos com sucesso.
- **Artifact final:** `rota-certa-0.1.166-universal-selected-app-farol-validated`, ID `8776986529`, retenção até 28/10/2026.
- **SHA-256 do APK:** `b4dbe016dd27e2915e2f16335df6cd8da40828779cc201f6e238547d56a94635`.
- **SHA-256 do ZIP do artifact:** `2d911fee20549cefd7073335aaebea9e64ae3df5afec75b79a8825337661851f`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendência prática:** instalar o APK e testar cards reais da 99, Uber e de ao menos outro aplicativo selecionado. O build comprova a regra universal e a integridade do APK, mas a confirmação visual de OCR e mudança verde/vermelho ainda depende do aparelho real.

## 30/07/2026 — 0.1.165 (5260) — linha do tempo autoritativa por tentativas

- **Branch:** `codex/diagnostic-attempt-timeline-0.1.165`
- **Commit de código:** `0515c349f59bf8fc5c0f5d5bb76cf4f83d2106a4`
- **Commit final validado pelo workflow:** `b4710587a1ecd704dd6092fb95ead3d88ac9f161`
- **PR:** #33, rascunho e sem merge na `main`
- **Pedido:** analisar o relatório real da versão 0.1.164 e corrigir somente o módulo que apresentou falha, sem alterar módulos definidos como OK.
- **Situação anterior:** o resumo autoritativo dizia que havia endereços, mas nenhuma rota, embora a trilha completa registrasse uma decisão verde válida.
- **Problema e causa:** o resumo 0.1.164 selecionava o último estado global e podia ser substituído por leitura incompleta posterior. Os padrões de alguns campos também não tratavam corretamente os delimitadores `|` e `;`.
- **Evidência funcional do aparelho:** a rota para `Rua Manoel Joaquim Ginjo, 35a` recebeu HTTP 200, retornou `5,363 km` e pintou verde. A tentativa posterior para `Rua Peramirim, 157` recebeu `4,161 km`, mas o estado foi limpo por `com.android.systemui` aproximadamente 31 ms antes da resposta, portanto o resultado não foi aplicado.
- **Correção aplicada:** reconstrução de tentativas independentes por mudança real de destino, considerando somente avaliações ativas com embarque e destino. O relatório agora separa a última decisão válida da última tentativa reconhecida e informa HTTP, distância, limpeza anterior e intervalo em milissegundos.
- **Arquivos do escopo:**
  - `scripts/fix_diagnostic_attempt_timeline_0165.py`
  - `scripts/fix_diagnostic_attempt_timeline_0165_rerun.py`
  - `.github/workflows/build-rota-certa-0.1.165.yml`
  - `.github/validation/rota-certa-0.1.165.txt`
  - código materializado: `FarolDiagnosticSummary0165.kt`, teste e integração mínima no `ManualTechnicalReportBuilder.kt`
- **Limite protegido:** hashes inalterados de `LiveRideAccessibilityService`, `DecisionEngine`, `RideTextParser`, `GoogleMapsService` e `FarolDiagnosticSummary0164`; Manifest e permissões inalterados; nenhum módulo funcional definido como OK foi alterado.
- **Testes executados:** aplicação idempotente, limite exato de arquivos, casos de decisão válida seguida de ruído, leitura incompleta posterior, resposta após limpeza, região inativa, testes unitários e de contrato, Android Lint, `clean assembleDebug`, Manifest, DEX, pacote, versão e assinatura APK v2.
- **Workflow final:** `Build Rota Certa 0.1.165`, run `30579419913`, job `90995655120`, todos os passos concluídos com sucesso.
- **Artifact final:** `rota-certa-0.1.165-diagnostic-attempt-timeline-validated`, ID `8774158117`, retenção até 28/10/2026.
- **SHA-256 do APK:** `ad762c731545b01b7f37d7f5b9eefe7042da820708befbaa3f4235fb904c9702`.
- **SHA-256 do ZIP final:** `7010c8a742e844fb406bb4b5a02aeba1b8bdecf7a096106f226a454708cab39c`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **Pendência prática:** instalar o APK e exportar um novo relatório. O farol não foi alterado; se o card continuar visível quando `com.android.systemui` assumir a raiz, essa situação deverá ser confirmada em nova captura antes de qualquer mudança funcional.

## 30/07/2026 — 0.1.164 (5250) — resumo autoritativo do diagnóstico

- **Branch:** `codex/diagnostic-session-summary-0.1.164`
- **Commit de código validado:** `6af1cfe9b4017ac127183b7fb53c801af192d5d6`
- **Commit do workflow final validado:** `69f79729727e21c99423666a370b6cae37c2910c`
- **PR:** #32, rascunho e sem merge na `main`
- **Pedido:** analisar o relatório real da versão 0.1.163 e localizar por que o farol permaneceu amarelo sem calcular a rota.
- **Problema confirmado:** o inDrive foi reconhecido e os dois endereços foram extraídos, porém Casa e Alfinetes estavam desativados. Pela regra atual, não existe alvo para comparar o destino e a execução termina antes de `BUBBLE_ROUTE_REQUESTED`.
- **Defeito adicional encontrado:** a seção legada do relatório declarou que nenhuma sessão de leitura havia sido registrada e exibiu estado persistido antigo, apesar de o gravador de voo conter sessões e endereços válidos da execução atual.
- **Causa:** o resumo legado lia campos independentes do `SharedPreferences`; ele não reconstruía a tentativa atual a partir da trilha do gravador.
- **Correção aplicada:** novo resumo autoritativo derivado somente dos eventos da sessão atual. Ele informa card, endereços, região ativa, solicitação de rota, cache, Google Maps e decisão, preservando o relatório completo abaixo.
- **Arquivos do escopo:**
  - `scripts/fix_diagnostic_session_summary_0164.py`
  - `scripts/fix_diagnostic_session_summary_0164_rerun.py`
  - `.github/workflows/build-rota-certa-0.1.164.yml`
  - `.github/validation/rota-certa-0.1.164.txt`
  - código materializado: `FarolDiagnosticSummary0164.kt`, teste e integração mínima no `ManualTechnicalReportBuilder.kt`
- **Limite protegido:** hashes inalterados de `LiveRideAccessibilityService`, `DecisionEngine`, `RideTextParser` e `GoogleMapsService`; sem alteração em Casa/Alfinetes, cores, raio, overlay, Manifest, permissões, atividades ou serviços.
- **Testes executados:** aplicação idempotente, limite exato de arquivos, testes unitários e de contrato, Android Lint, `clean assembleDebug`, inspeção de Manifest e DEX, pacote, versão e assinatura APK v2.
- **Workflow final:** `Build Rota Certa 0.1.164`, run `30575223919`, job `90981695281`, todos os passos concluídos com sucesso.
- **Artifact final:** `rota-certa-0.1.164-diagnostic-session-summary-validated`, ID `8772551211`, retenção até 28/10/2026.
- **SHA-256 do APK:** `9a76ad94d4b84edad27ede3d6154c225c81217dbd29b11f74c18108e0e23960e`.
- **SHA-256 do ZIP do artifact:** `436ef82c87f6fdf7d693e52c5e044f0679deedcba24cb444b07020173b4f978f`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **Risco/pendência prática:** para o farol calcular verde ou vermelho, o usuário deve ativar Casa ou ao menos um Alfinete com coordenada válida. A versão 0.1.164 não altera essa regra funcional.

## 30/07/2026 — 0.1.163 (5240) — gravador de voo do farol

- **Branch:** `codex/farol-flight-recorder-0.1.163`
- **Commit validado:** `1a6e6544b72d894db886305427dfee56364d0993`
- **PR:** #31, rascunho
- **Workflow:** run `30569838311`, job `90963452922`, concluído com sucesso
- **Artifact:** `rota-certa-0.1.163-farol-flight-recorder-validated`, ID `8770445998`
- **SHA-256 do APK:** `1e82eb1cef67467862d0c979343be35c8645df039402787d08386307f491086b`
- **Resultado prático:** o primeiro relatório real provou que a leitura e o parser funcionaram e que a interrupção ocorreu antes da rota por ausência de região ativa.
