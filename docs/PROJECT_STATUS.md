# Rota Certa — Estado do projeto

## 31/07/2026 — 0.1.170 (5310) — falha do despertar por notificação contida

- **Branch:** `agent/contain-notification-crash-0.1.170`; **PR:** #39, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f50710959058054f45962f44e77ee76934bc0666`.
- **Pedido/evidência do usuário:** vídeo `1000876918.mp4` e APK `rota-certa-0.1.169-uber-notification-wakeup-validado.apk` enviados após o teste real. O APK possui SHA-256 `0c11a2f7292516f0daf00cf10e30f52e7279f87200a209be78f647458a9d7915`, idêntico ao artifact validado da 0.1.169.
- **Situação anterior:** no Samsung SM-S911B/Android 16, o Android exibiu “Rota Certa fechou porque este app tem um bug”. Depois, o serviço `Rota Certa – leitura ao vivo` apareceu como não funcionando/desativado; limpar cache e reativar o serviço não comprovou estabilidade.
- **Causa e limite da análise:** o vídeo comprova encerramento do processo/serviço, mas não contém stack trace e não identifica honestamente uma linha única. A auditoria localizou uma fronteira nova da 0.1.169 sem contenção global: a chamada síncrona de `handleNotificationWakeup0169` e o `scope.launch` da confirmação podiam deixar uma exceção escapar para o processo de acessibilidade.
- **Correção aplicada:**
  - contenção `try/catch` fail-closed na entrada `TYPE_NOTIFICATION_STATE_CHANGED`;
  - contenção externa no trabalho assíncrono, preservando `CancellationException` normal;
  - novo `FarolNotificationFailureCircuit0170`, com pausa limitada de 60 segundos somente para o despertar por notificação após exceção;
  - cancelamento do token/trabalho, liberação de `screenshotInProgress` e limpeza imediata do estado visual transitório;
  - registro limitado do estágio e tipo da exceção, sem log contínuo e sem screenshots em ciclo;
  - parser, decisão, rota, Casa/Alfinetes, cores e exigência de card confirmado permanecem inalterados.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_notification_wakeup_crash_containment_0170.py`;
  - `.github/workflows/build-rota-certa-0.1.170.yml`;
  - `LiveRideAccessibilityService.kt`;
  - novo `FarolNotificationFailureCircuit0170.kt`;
  - novos testes `FarolNotificationFailureCircuit0170Test.kt` e `FarolNotificationCrashContainmentContract0170Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, `rota_certa_accessibility.xml`, permissões, `AccessibilityEventFloodGate.kt`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `FarolSelectedAppInputPolicy0166.kt`, `FarolRealtimeEventGate0167.kt`, `FarolUnifiedVisual0168.kt` e `FarolNotificationWakeup0169.kt` permaneceram byte a byte preservados em relação à árvore 0.1.169.
- **Testes executados:** aplicação completa 0.1.151–0.1.170; aplicação do patch duas vezes/idempotência; fronteira exata; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.170`, run `30627859183`, job `91147144553`, todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.170-accessibility-crash-contained-validated`, ID `8792223578`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.170`, versionCode `5310`.
- **APK:** `rota-certa-0.1.170-acessibilidade-falha-contida-validado.apk`, 55.911.275 bytes.
- **SHA-256 do APK:** `68ed1f6d7398aa36608a420c18e8f8d16bcd999693dfd9a29d8a9e9d6d785880`.
- **SHA-256 do ZIP do artifact:** `ed16988e02076653d61298c31459f653743d3c1b7c1511ec84c3d4372f13b4d1`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e riscos:** instalar a 0.1.170 no mesmo aparelho, reativar `Rota Certa – leitura ao vivo`, manter Modo Trabalho ligado e observar uma oferta real. Confirmar que o serviço permanece ativo, que o alerta de fechamento não retorna, que a bolinha fica amarela durante confirmação, libera verde/vermelho somente com card e rota válidos e limpa ao desaparecer. Se houver nova falha, exportar o relatório imediatamente; a contenção preservará o serviço e deverá registrar `NOTIFICATION_WAKE_FAILURE_CONTAINED_0170` com o estágio real.

## 31/07/2026 — 0.1.169 (5300) — despertar pontual de cards sobrepostos validado

- **Branch:** `agent/fix-uber-overlay-event-gap-0.1.169`; **PR:** #38, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f2ba7afe6bed450393ae6e9a479330644fa06239`.
- **Pedido do usuário:** analisar o vídeo `1000876623.mp4` e o relatório `rota-certa-relatorio-depuracao (16).txt`, localizar por que um card UberX ficava visível sobre a tela inicial enquanto a bolinha permanecia cinza e aplicar a correção completa, sem polling contínuo.
- **Situação anterior:** o card Uber aparecia sobre o launcher por vários segundos, mas o serviço não recebia evento de janela ou conteúdo durante esse intervalo. Sem evento, o pipeline não solicitava screenshot, não executava OCR, não confirmava o card, não calculava a rota e não atualizava a bolinha.
- **Causa comprovada:** o relatório registrou um intervalo de aproximadamente 25,9 segundos sem nenhum `ACCESSIBILITY_EVENT`, justamente cobrindo o período visual do card no vídeo. A configuração do serviço escutava `typeWindowStateChanged`, `typeWindowContentChanged` e `typeWindowsChanged`, mas não `typeNotificationStateChanged`. Alguns overlays de oferta não alteram a raiz acessível do launcher e, portanto, não despertavam o núcleo.
- **Correção aplicada:** inclusão de `typeNotificationStateChanged`; `FarolNotificationWakeGate0169`; token por geração, deduplicação, TTL de 12 segundos e máximo de quatro capturas pontuais; confirmação visual obrigatória; cancelamento de trabalhos antigos; proteção de launcher/System UI transitórios; sem polling.
- **Fronteira protegida:** Manifest e permissões inalterados; Casa/Alfinetes, Google Maps, parser, card confirmado e limpeza por geração preservados.
- **Testes executados:** aplicação 0.1.151–0.1.169 duas vezes; idempotência; fronteira; testes; Lint; `clean assembleDebug`; APK, Manifest, DEX, pacote, versão, versionCode e assinatura.
- **Workflow final validado:** `Build Rota Certa 0.1.169`, run `30625877873`, job `91140903636`, sucesso.
- **Artifact final:** `rota-certa-0.1.169-uber-notification-wakeup-validated`, ID `8791527911`.
- **APK/SHA-256:** `rota-certa-0.1.169-uber-notification-wakeup-validado.apk` — `0c11a2f7292516f0daf00cf10e30f52e7279f87200a209be78f647458a9d7915`.
- **Resultado em aparelho:** reprovado por encerramento do processo/serviço mostrado no vídeo `1000876918.mp4`; supersedido pela contenção 0.1.170.

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f66d8daefce26f5cc9f78d8c27c581bfd9734314`.
- **Commit documental final:** `bd35e0b721b34b03d8b90d7f36f3ca9cacc1163d`.
- **Pedido:** reunir falhas reais da Uber, 99 e inDrive e corrigir leitura de pop-ups e listas, preservando Modo Trabalho e aplicativos escolhidos.
- **Correção:** primeiro quadro sem atraso artificial; OCR espacial único; fusão com acessibilidade; segmentação por card; assinatura semântica; bloqueio de rua truncada; local nomeado apenas com contexto real; tentativas limitadas e canceláveis; resultado antigo não substitui card novo.
- **Testes:** 237 testes unitários e de contrato; Android Lint; `clean assembleDebug`; ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura APK v2.
- **Workflow final:** run `30602408881`, job `91067728989`, sucesso.
- **Artifact:** `rota-certa-0.1.168-farol-visual-unificado-validated`, ID `8782463049`.
- **APK/SHA-256:** `rota-certa-0.1.168-farol-visual-unificado-validado.apk` — `386cbf256e471bcb6ab2bc79c8564ff9826dbf52bec2fac5bb5a98fd2fe69de2`.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado, sem alteração de bytes, em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
