# Rota Certa — Estado do projeto

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f66d8daefce26f5cc9f78d8c27c581bfd9734314`.
- **Commit documental validado:** `cae46bbc2439db0aa0c0179e8d066285733fbb54`.
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
