# Rota Certa — Estado do projeto

## 31/07/2026 — 0.1.169 (5300) — despertar pontual de cards sobrepostos validado

- **Branch:** `agent/fix-uber-overlay-event-gap-0.1.169`; **PR:** #38, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f2ba7afe6bed450393ae6e9a479330644fa06239`.
- **Pedido do usuário:** analisar o vídeo `1000876623.mp4` e o relatório `rota-certa-relatorio-depuracao (16).txt`, localizar por que um card UberX ficava visível sobre a tela inicial enquanto a bolinha permanecia cinza e aplicar a correção completa, sem polling contínuo.
- **Situação anterior:** o card Uber aparecia sobre o launcher por vários segundos, mas o serviço não recebia evento de janela ou conteúdo durante esse intervalo. Sem evento, o pipeline não solicitava screenshot, não executava OCR, não confirmava o card, não calculava a rota e não atualizava a bolinha.
- **Causa comprovada:** o relatório registrou um intervalo de aproximadamente 25,9 segundos sem nenhum `ACCESSIBILITY_EVENT`, justamente cobrindo o período visual do card no vídeo. A configuração do serviço escutava `typeWindowStateChanged`, `typeWindowContentChanged` e `typeWindowsChanged`, mas não `typeNotificationStateChanged`. Alguns overlays de oferta não alteram a raiz acessível do launcher e, portanto, não despertavam o núcleo.
- **Correção aplicada:**
  - inclusão de `typeNotificationStateChanged` na configuração do serviço e no filtro de eventos relevantes;
  - criação de `FarolNotificationWakeGate0169`, que aceita somente notificações de pacote selecionado pelo usuário, com Modo Trabalho, leitura ao vivo e serviço prontos;
  - criação de token por geração, deduplicação e TTL de 12 segundos;
  - máximo rígido de quatro capturas pontuais por token, canceláveis e sem laço de polling;
  - captura visual imediata, uma repetição curta se necessário e verificações limitadas enquanto o mesmo card pode continuar visível;
  - texto da notificação não autoriza verde/vermelho: OCR, isolamento do card, dois endereços válidos, rota real e decisão normal continuam obrigatórios;
  - cancelamento ao entrar no aplicativo selecionado, desligar o Modo Trabalho ou destruir o serviço;
  - proteção para que launcher/System UI transitórios não encerrem o token antes da confirmação visual.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_uber_notification_wakeup_0169.py`;
  - `.github/workflows/build-rota-certa-0.1.169.yml`;
  - `app/src/main/res/xml/rota_certa_accessibility.xml`;
  - `AccessibilityEventFloodGate.kt`;
  - `LiveRideAccessibilityService.kt`;
  - novo `FarolNotificationWakeup0169.kt`;
  - novos testes `FarolNotificationWakeup0169Test.kt` e `FarolNotificationWakeupContract0169Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml` e permissões permaneceram idênticos; hashes de `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `FarolSelectedAppInputPolicy0166.kt`, `FarolRealtimeEventGate0167.kt` e `FarolUnifiedVisual0168.kt` foram preservados. Casa/Alfinetes, Google Maps, parser, regra de card confirmado e limpeza por geração não foram alterados.
- **Testes executados:** aplicação completa 0.1.151–0.1.169 duas vezes; idempotência; fronteira exata; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura validados.
- **Workflow validado:** `Build Rota Certa 0.1.169`, run `30625265577`, job `91138922633`, todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.169-uber-notification-wakeup-validated`, ID `8791189083`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.169`, versionCode `5300`.
- **APK:** `rota-certa-0.1.169-uber-notification-wakeup-validado.apk`, 55.911.275 bytes.
- **SHA-256 do APK:** `0c11a2f7292516f0daf00cf10e30f52e7279f87200a209be78f647458a9d7915`.
- **SHA-256 do ZIP do artifact:** `f767155278c62f7990ee190147e97c810d53a306eff8d86118995e7611958391`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e riscos:** instalar no Samsung SM-S911B com Android 16 e confirmar que o sistema entrega `TYPE_NOTIFICATION_STATE_CHANGED` para a oferta real da Uber; verificar amarelo imediato, verde/vermelho somente após destino final confirmado, limpeza ao desaparecer e ausência de captura em notificações de apps não selecionados. O build está validado, mas comportamento do overlay de terceiro e particularidades do firmware ainda exigem aparelho real. Se esse firmware também suprimir o evento de notificação, a próxima investigação deve procurar outro gatilho nativo do sistema, sem introduzir polling.

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f66d8daefce26f5cc9f78d8c27c581bfd9734314`.
- **Commit documental final:** `bd35e0b721b34b03d8b90d7f36f3ca9cacc1163d`.
- **Pedido:** reunir as falhas reais da Uber, 99 e inDrive e corrigir de uma única vez a leitura de pop-ups individuais e telas/listas de corridas, preservando o Modo Trabalho como chave mestre e os aplicativos escolhidos como filtro de privacidade.
- **Situação anterior:** a Uber agendava OCR, mas o primeiro pedido efetivo podia ocorrer depois do desaparecimento do pop-up; a 99 expunha árvore Flutter vazia e um fragmento como `Rua Joaquim` podia chegar à rota; o inDrive rejeitava locais nomeados e o texto global da lista podia misturar endereços de ofertas diferentes. Contadores, preços e distâncias voláteis também mudavam o hash da mesma oferta e reabriam a análise.
- **Causas comprovadas:**
  - atraso artificial e reiniciável antes do primeiro screenshot;
  - OCR achatado sem preservar blocos e linhas do resultado ML Kit;
  - parser global da tela em vez de isolamento por card;
  - validação permissiva de rua truncada;
  - validação rígida demais para local nomeado acompanhado de localidade real;
  - assinatura de tela sensível a contador, preço e distância voláteis;
  - expressões com `\s+` ao redor de `Pular` consumiam a quebra de linha e juntavam o início do card seguinte.
- **Correção aplicada:** primeiro quadro visual sem atraso artificial; OCR espacial dentro do `OcrService` usando uma única leitura do ML Kit; fusão com acessibilidade; segmentação coerente por card para pop-up e lista; assinatura semântica que ignora campos voláteis; rua truncada bloqueada antes de cache/Google Maps; local nomeado aceito somente com contexto geográfico real; máximo de tentativas limitado e cancelável; resultado antigo continua impedido de substituir card novo.
- **Arquivos principais da entrega:**
  - `scripts/run_fix_farol_unified_visual_0168.py`;
  - `scripts/fix_farol_unified_visual_0168_compile.py`;
  - `.github/workflows/build-rota-certa-0.1.168-final.yml`;
  - `.github/workflows/build-rota-certa-0.1.168.yml`;
  - código materializado: `FarolUnifiedVisual0168.kt`, `AndroidServices.kt`, `LiveRideAccessibilityService.kt`, `RideTextParser.kt` e testes 0.1.168.
- **Fronteira protegida:** Manifest e permissões inalterados; hashes de `DecisionEngine.kt`, `GoogleMapsService.kt`, `FarolSelectedAppInputPolicy0166.kt` e `FarolRealtimeEventGate0167.kt` preservados; Casa/Alfinetes, Modo Trabalho, aplicativos selecionados e regra de somente card confirmado continuam vigentes.
- **Testes executados:** materialização completa 0.1.151–0.1.168 duas vezes; idempotência; fronteira exata; contratos visuais; **237 testes unitários e de contrato aprovados**; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura APK v2.
- **Workflow final do commit documental:** `Build Rota Certa 0.1.168 Final`, run `30602408881`, job `91067728989`, todos os passos concluídos com sucesso.
- **Workflows funcionais anteriores também aprovados:** run `30601624749` e run `30601624765`, todos os passos concluídos com sucesso.
- **Artifact final atual:** `rota-certa-0.1.168-farol-visual-unificado-validated`, ID `8782463049`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.168`, versionCode `5290`.
- **APK:** `rota-certa-0.1.168-farol-visual-unificado-validado.apk`, 55.894.891 bytes.
- **SHA-256 do APK:** `386cbf256e471bcb6ab2bc79c8564ff9826dbf52bec2fac5bb5a98fd2fe69de2`.
- **SHA-256 do ZIP do artifact atual:** `64d313530755c3ed089a19b09bd7ba98c710615e58510c4b3e2da6e5a32a2139`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Conferência independente:** o APK do artifact final atual é byte a byte idêntico ao APK validado no run funcional anterior.
- **Pendência prática:** instalar em aparelho real e testar cards individuais e listas da Uber, 99, inDrive e de outro aplicativo selecionado, verificando mudança imediata para amarelo/verde/vermelho, limpeza ao sair do card e ausência de mistura entre ofertas. Build, testes e APK estão validados; conteúdo visual de terceiros e condições reais de acessibilidade/OCR ainda exigem aparelho.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado, sem alteração de bytes, em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
