# Rota Certa — Estado do projeto

## 03/08/2026 — 0.1.180 (5410) — três gestos configuráveis por bolinha

- **Branch:** `agent/per-shortcut-gesture-config-0.1.180`; **PR:** #51, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `dcc2babd102efb0fc3af09e9db4fa99d05409f4a`.
- **Pedido do usuário:** cada bolinha da grade flutuante deve permitir configurar separadamente o toque rápido e o gesto de segurar por 1,5 segundo; segurar a própria bolinha por 5 segundos deve abrir sempre a configuração daquela entrada. As duas ações configuráveis devem aceitar executar imediatamente, abrir módulo ou não fazer nada, e o editor deve permitir excluir a bolinha da grade.
- **Situação anterior:** a 0.1.179 fixava o toque curto na ação principal, o toque de 2 segundos na abertura do módulo e reservava 5 segundos apenas para abrir a Central pela bolinha principal. Não existia editor individual aberto diretamente pela bolinha pressionada.
- **Causa:** o modelo persistido armazenava somente recurso, nome, ícone e ativação. O overlay recebia callbacks fixos para toque curto e toque longo, sem identificar duas ações configuráveis por entrada e sem uma resolução exclusiva para o limiar de 5 segundos.
- **Correção aplicada:**
  - cada entrada persiste `quickTapAction` e `holdAction` usando somente o enum seguro `ExecuteImmediately`, `OpenModule` ou `DoNothing`;
  - toque rápido é resolvido ao soltar antes de 1,5 segundo;
  - segurar por pelo menos 1,5 segundo e soltar antes de 5 segundos executa a segunda ação configurada;
  - ao atingir 5 segundos, abre imediatamente o editor da bolinha específica e nenhuma ação de 1,5 segundo é executada antes;
  - o editor individual exibe os botões `Toque rápido`, `Segurar 1,5 s`, as três opções de ação e `Excluir da grade`;
  - nome, recurso, ícone e visibilidade continuam configuráveis;
  - dados salvos pela 0.1.179 migram deterministicamente para toque rápido = executar imediatamente e 1,5 segundo = abrir módulo;
  - na bolinha Alertas, o padrão preserva toque rápido criando alerta pelo fluxo existente, 1,5 segundo abrindo o módulo e 5 segundos abrindo a configuração; o usuário pode inverter as duas primeiras ações;
  - nenhuma intent arbitrária, pacote externo, polling, observador contínuo ou serviço adicional foi introduzido.
- **Arquivos principais alterados/materializados:**
  - `ShortcutGridCustomization0179.kt`;
  - `BubbleShortcutOverlayController.kt`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - `ShortcutGridCustomization0179Test.kt`;
  - `ShortcutGridCustomizationContract0179Test.kt`;
  - `ShortcutLongPressContract0171Test.kt`;
  - `scripts/build_rota_certa_0180.sh`;
  - `.github/workflows/build-rota-certa-0.1.180.yml`;
  - `patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk00` a `chunk06`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, OCR, confirmação real de card, Casa/Alfinetes, cores, km, cancelamento de resultados antigos, radares, alertas direcionais, repositórios e fala permaneceram inalterados e foram conferidos por checksums.
- **Testes e validações:** testes unitários/de contrato aprovados, incluindo o caso em que manter pressionado até 5 segundos não dispara a ação intermediária; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP e DEX, pacote, versão, versionCode, assinatura v2, certificado e marcadores dos gestos validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.180`, run `30806876085`, job `91664179413`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.180-per-shortcut-gesture-config-validated`, ID `8853609304`, 33.735.866 bytes, retenção até 01/11/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.180`, versionCode `5410`, minSdk 26 e targetSdk 35.
- **APK:** `rota-certa-0.1.180-gestos-atalhos-configuraveis-validado.apk`, 56.058.831 bytes.
- **SHA-256 do APK:** `180d76d50071cd401f3934fe8a391c7e70acf2fa6c575f1a66e1fe8d0bd9f493`.
- **SHA-256 do ZIP do artifact:** `3fd5e29ebcd5899d8584111971de8c19f37bb2ffab0ba50cef0768c05ecf3a60`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B com Android 16 e validar: toque rápido e 1,5 segundo com as três opções; inversão dos gestos da bolinha Alertas; 5 segundos abrindo o editor da entrada correta sem criar alerta nem abrir módulo; exclusão, migração, reordenação e persistência; ausência de regressão do farol, rota, radares e alertas. A PR #51 deve permanecer em rascunho até essa validação real.

## 03/08/2026 — 0.1.179 (5400) — CI estabilizado e grade personalizável validada

- **Branch:** `agent/customizable-shortcut-grid-0.1.179`; **PR:** #48, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `fd9cd3db74d1f5433bff68a0e385e2c22832dcc2`.
- **Pedido do usuário:** aplicar uma correção segura no build da versão 0.1.179, sem remover validações nem alterar o comportamento funcional do aplicativo.
- **Situação anterior:** os builds da 0.1.179 eram encerrados pelo runner com `exit code 137` durante a cadeia cumulativa de Gradle. Após limitar a memória, a execução avançou e revelou dois contratos antigos de teste incompatíveis com o gesto e o despacho autoritativos da 0.1.179.
- **Causa:** a cadeia 0.1.177 → 0.1.178 → 0.1.179 executava testes, lint e assemble com concorrência e processos Kotlin suficientes para pressionar a memória do runner. Além disso, `AuthorizedAppsCards146ContractTest` ainda exigia o literal antigo de 1,5 segundo e `ShortcutActivityLaunchContract0176Test` ainda exigia despacho direto por `module.spec`, embora a 0.1.179 resolva a entrada personalizável antes do despacho.
- **Correção aplicada:**
  - toda a cadeia cumulativa herda Gradle sem daemon, sem paralelismo, com um worker, VFS watch desligado, heap limitado a 2.560 MiB, metaspace de 768 MiB e compilador Kotlin em processo;
  - testes unitários/de contrato, Android Lint e `clean assembleDebug` continuam obrigatórios e não foram pulados;
  - os dois checkouts do workflow usam `persist-credentials: false`;
  - o timeout do job foi ampliado para 180 minutos, privilegiando estabilidade em vez de concorrência;
  - os gatilhos de push e PR ficaram restritos aos arquivos que realmente materializam a 0.1.179;
  - criado o patch versionado `customizable-shortcut-grid-0179-contracts.patch`, validado por SHA-256 e `git apply --check`, que atualiza somente as duas asserções legadas para o contrato autoritativo de 2 segundos e despacho pela entrada resolvida;
  - nenhum teste foi removido e nenhum código funcional Kotlin do aplicativo foi alterado por esta correção de CI.
- **Arquivos principais alterados:**
  - `scripts/build_rota_certa_0179.sh`;
  - `.github/workflows/build-rota-certa-0.1.179.yml`;
  - `patches/customizable-shortcut-grid-0179-contracts.patch`;
  - `scripts/inspect_shortcut_customization_0179.sh`;
  - contratos materializados `AuthorizedAppsCards146ContractTest.kt` e `ShortcutActivityLaunchContract0176Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, OCR, confirmação real de card, Casa/Alfinetes, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por checksums e validações do artifact.
- **Testes e validações:** todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP e DEX, pacote, versão, versionCode, assinatura v2, certificado e marcadores da personalização validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.179`, run `30786663851`, job `91601374049`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.179-customizable-shortcut-grid-validated`, ID `8845723352`, 33.710.976 bytes, retenção até 01/11/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.179`, versionCode `5400`, minSdk 26 e targetSdk 35.
- **APK:** `rota-certa-0.1.179-grade-atalhos-personalizavel-validado.apk`, 56.042.447 bytes.
- **SHA-256 do APK:** `081cffc8837b8544c69d1422f21286ab0da3ffb483f378810d5feb70d565f52c`.
- **SHA-256 do ZIP do artifact:** `71d8a6c52c5bc7f0a1a6eb0ca7484c292f4574da90838c061ba9e1e98fb80ca8`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B com Android 16 e validar toque simples na ação principal selecionada, toque longo de 2 segundos abrindo o módulo correspondente na Home, toque de 5 segundos na bolinha principal abrindo a personalização, preservação do toque simples e duplo da bolinha principal, migração/ordem/duplicidade do catálogo e ausência de regressão do farol, rota, radares e alertas. A PR #48 deve permanecer em rascunho até essa validação real.

## 02/08/2026 — 0.1.177 (5380) — módulos da grade abrem e entram na área visível

- **Branch:** `agent/fix-shortcut-module-focus-0.1.177`; **PR:** #46, empilhada sobre `agent/fix-shortcut-single-tap-0.1.176`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `7da8ce4a50414566938151416f4ae860858daf46`.
- **Pedido e evidência do usuário:** o vídeo real `1000879762.mp4`, gravado no Samsung SM-S911B com Android 16 usando a 0.1.176, mostrou que o toque simples já abria Destino, WhatsApp, Valor, Financeiro, Limpar, Depurar, Respostas, Links e Encerrar, mas Alertas, Locais, Radares, Aparência e Backup apenas fechavam a grade e deixavam a Home no topo; Rota também não apresentava confirmação visual clara. `Capturar` abriu `Aplicativos e cards autorizados`, conforme o contrato atual.
- **Situação anterior:** a 0.1.176 corrigiu o bloqueio de Activities pelo Android, porém os módulos compostos dentro da Home não eram Activities dedicadas. Eles ainda eram despachados pelo caminho antigo de grupo/aba.
- **Causa:** `LiveRideAccessibilityService.executeShortcutModule` chamava `openResourceGroup(...)` para os módulos inline e enviava somente `EXTRA_OPEN_BUBBLE_GROUP`. A Home 0.1.175 controla a expansão por `EXTRA_OPEN_SHORTCUT_MODULE_0171`; portanto o grupo podia ser selecionado internamente sem que a bolinha e o painel correspondentes fossem expandidos. Mesmo quando expandido, um módulo abaixo da dobra podia permanecer fora da área visível.
- **Correção aplicada:**
  - Rota, Destino, Alertas, Locais, Radares, Aparência, Permissões, Backup, Relatórios e Configurações passam por `openShortcutModule0171(spec)` e recebem ID autoritativo, grupo e aba;
  - a Home mantém um `BringIntoViewRequester` leve por módulo;
  - depois que o módulo solicitado é composto, uma única chamada orientada ao evento traz sua fileira e seu painel para a área visível;
  - um novo Intent para o mesmo módulo repete o foco uma vez, sem polling, observador contínuo ou loop;
  - Activities dedicadas e ações imediatas permanecem no caminho seguro validado na 0.1.176;
  - o contrato de `Capturar` permanece abrir `Aplicativos e cards autorizados` no toque simples.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_shortcut_module_focus_0177.py`;
  - `scripts/build_rota_certa_0177.sh`;
  - `.github/workflows/build-rota-certa-0.1.177.yml`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - novo `ShortcutModuleFocusPolicy0177.kt`;
  - novos testes `ShortcutModuleFocusPolicy0177Test.kt` e `ShortcutModuleFocusContract0177Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `BubbleShortcutOverlayController.kt`, `BubbleShortcutModule.kt`, `ShortcutLongPressPolicy0171.kt` e `ShortcutActivityLaunchPolicy0176.kt` permaneceram idênticos por SHA-256. Não houve alteração em permissões, farol, parser, OCR, rota calculada, Casa/Alfinetes, confirmação real de card, cores, km, cancelamento de resultados antigos ou toque longo.
- **Testes e validações:** materialização completa 0.1.151–0.1.176; novos testes de política e contrato; todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falhas intermediárias localizadas:** a primeira execução parou na compilação porque a API de foco do Compose exige opt-in experimental; o `@OptIn` foi restrito ao componente da grade. A segunda execução compilou o aplicativo e parou apenas no teste de contrato por uso de `Files.readString` incompatível com o nível Java configurado; o teste foi ajustado para `Files.readAllBytes`, sem mudança funcional. A terceira execução passou integralmente.
- **Workflow funcional validado:** `Build Rota Certa 0.1.177`, run `30751876132`, job `91507226425`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.177-shortcut-module-focus-validated`, ID `8834751631`, 31.569.733 bytes, retenção até 31/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.177`, versionCode `5380`.
- **APK:** `rota-certa-0.1.177-modulos-grade-foco-validado.apk`, 55.993.295 bytes.
- **SHA-256 do APK:** `21996c09751c3704c82bcd575f2fadab1f48455a824a5d5a8b17f6fa4eb089c5`.
- **SHA-256 do ZIP do artifact:** `418dda808c2f5403bae31f11c25a7d9100fac50647ae8e821aa0818999a07008`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e repetir o toque simples nas 17 bolinhas. Priorizar Rota, Alertas, Locais, Radares, Aparência e Backup e confirmar que a Home rola diretamente para o painel correto. Confirmar também os outros 11 atalhos, o toque longo, o retorno da bolinha e a ausência de regressão do farol. A PR #46 permanece em rascunho até essa validação real.

## 02/08/2026 — 0.1.176 (5370) — toque simples da grade com despacho seguro

- **Branch:** `agent/fix-shortcut-single-tap-0.1.176`; **PR:** #45, empilhada sobre `agent/home-module-bubble-grid-0.1.175`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `47fa9cbd4d96815794ab3db88253f984453b217d`.
- **Pedido do usuário:** corrigir bolinhas da grade flutuante que recebiam um toque simples, fechavam a grade, mas não abriam nem executavam o módulo correspondente.
- **Evidência real:** o relatório manual da versão 0.1.175 registrou `bubble.shortcut.clicked` para `saved_places`, `alerts`, `radars` e `quick_replies`, sem evento posterior de abertura ou execução. Isso comprovou que a área clicável e o reconhecimento do toque funcionavam e isolou a falha no despacho da ação principal.
- **Causa:** os métodos que abriam Activities chamavam `shortcutOverlayController.hideAll()` antes de `startActivity`. No Android moderno, remover também a última janela visível antes do despacho podia retirar a condição de interação visível e fazer o sistema bloquear silenciosamente a abertura iniciada pelo usuário.
- **Correção aplicada:**
  - criado o lançador único `launchShortcutActivity0176`;
  - a bolinha principal permanece visível enquanto o despacho solicitado pelo usuário é enviado;
  - Android 14 ou superior usa `PendingIntent` com opt-in explícito do criador e do remetente para início de Activity em segundo plano;
  - a sobreposição é escondida somente depois que o despacho foi aceito sem exceção;
  - falhas permanecem fail-closed e registram apenas os marcadores limitados `SHORTCUT_ACTIVITY_DISPATCHED_0176` ou `SHORTCUT_ACTIVITY_DISPATCH_FAILED_0176`;
  - Locais, Alertas, Radares, Respostas, Rota, Destino, Financeiro, Links, Diagnóstico, Aparência, Backup e demais destinos internos passaram pelo mesmo caminho seguro;
  - ações que não abrem Activity continuaram com o comportamento original.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_shortcut_single_tap_0176.py`;
  - `scripts/fix_shortcut_single_tap_0176_test_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.176.yml`;
  - `LiveRideAccessibilityService.kt`;
  - novo `ShortcutActivityLaunchPolicy0176.kt`;
  - novos testes `ShortcutActivityLaunchPolicy0176Test.kt` e `ShortcutActivityLaunchContract0176Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `BubbleShortcutOverlayController.kt`, `BubbleShortcutModule.kt`, `ShortcutLongPressPolicy0171.kt` e `MainActivity.kt` permaneceram idênticos por SHA-256. Manifest, permissões, catálogo, toque longo, Home, farol, parser, OCR, rota, Casa/Alfinetes, confirmação real do card, cores e km não foram alterados.
- **Testes e validações:** materialização completa 0.1.151–0.1.175; contratos do despacho; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falhas intermediárias localizadas:** as duas primeiras execuções pararam em um literal incorreto do novo teste gerado. Nenhuma delas chegou a Lint ou APK. O contrato foi corrigido sem alterar o código funcional e a execução final passou integralmente.
- **Workflow funcional validado:** `Build Rota Certa 0.1.176`, run `30749272318`, job `91500269757`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.176-shortcut-single-tap-validated`, ID `8833961162`, retenção de 90 dias.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.176`, versionCode `5370`.
- **APK:** `rota-certa-0.1.176-toque-simples-grade-validado.apk`, 55.993.295 bytes.
- **SHA-256 do APK:** `b7180112912cc0656fd41c592e23e737677a37dbdcbedeef0afef2956d04eddb`.
- **Digest do ZIP publicado pelo GitHub Actions:** `9c9417a2e08aa5c93678c213b8a595273cdaef53def39136ef49a5c9b80c6db3`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar toque simples nas 17 bolinhas, com prioridade para Locais, Alertas, Radares, Respostas, Rota, Destino, Financeiro, Links e Diagnóstico. Confirmar abertura imediata, retorno da bolinha, preservação do toque longo e ausência de regressão do farol. A PR #45 deve permanecer em rascunho até essa validação real.

## 01/08/2026 — 0.1.175 (5360) — Home em grade de bolinhas por módulo e recurso

- **Branch:** `agent/home-module-bubble-grid-0.1.175`; **PR:** #44, empilhada sobre `agent/home-module-launcher-0.1.174`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `7ea558deb4307fd1cdc273509695abf04487b04f`.
- **Pedido do usuário:** manter a grade flutuante como painel de ações rápidas e transformar a Home no catálogo completo do aplicativo, com uma bolinha própria para cada módulo ou recurso.
- **Situação anterior:** a Home 0.1.174 usava cartões expansíveis verticais. Embora o conteúdo já aparecesse no local correto, a lista continuava longa e menos adequada à identificação rápida de todos os recursos.
- **Causa:** a apresentação por cartões priorizava detalhes textuais antes da seleção. Para o contexto de uso do Rota Certa, o catálogo completo precisava privilegiar reconhecimento visual, alvos grandes e uma entrada direta para cada módulo.
- **Correção aplicada:**
  - todos os 17 módulos do `BubbleShortcutCatalog` são apresentados na Home como bolinhas circulares de 96 dp, com ícone e nome;
  - a grade da Home usa três colunas e seis fileiras, preservando a ordem autoritativa do catálogo;
  - toque simples seleciona ou recolhe o módulo, sem toque longo e sem personalização;
  - apenas um módulo fica aberto por vez;
  - o conteúdo aparece imediatamente abaixo da fileira da bolinha selecionada, sem salto ao fim da página;
  - a bolinha selecionada recebe destaque visual;
  - a grade flutuante permanece separada e conserva suas ações fixas de toque simples e longo restauradas na 0.1.173;
  - nenhuma ação é executada apenas por compor ou visualizar a grade da Home.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `MainActivity.kt`;
  - novo `HomeModuleBubbleGridPolicy0175.kt`;
  - novo `HomeModuleBubbleGridPolicy0175Test.kt`;
  - contrato `ShortcutLongPressContract0171Test.kt` atualizado para a Home em bolinhas;
  - `scripts/fix_home_bubble_grid_0175.py`;
  - `scripts/fix_home_bubble_grid_0175_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.175.yml`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `LiveRideAccessibilityService.kt`, `BubbleShortcutOverlayController.kt`, `BubbleShortcutModule.kt` e `ShortcutLongPressPolicy0171.kt` permaneceram preservados por checksum. Não houve mudança em permissões, acessibilidade, parser, OCR, rota, Casa/Alfinetes, confirmação real de card, decisão, cores, km ou grade flutuante.
- **Testes e validações:** materialização completa 0.1.151–0.1.174; contratos estruturais; 267 testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falhas intermediárias localizadas:** dois testes legados ainda exigiam textos e estrutura dos cartões expansíveis e foram atualizados para o novo contrato; posteriormente, a única falha do pipeline era um passo não funcional que tentava comentar automaticamente na PR, removido sem alteração do APK.
- **Workflow funcional validado:** `Build Rota Certa 0.1.175`, run `30713519235`, job `91405065129`; todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.175-home-bubble-grid-validated`, ID `8822675670`, retenção até 30/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.175`, versionCode `5360`.
- **APK:** `rota-certa-0.1.175-home-grade-bolinhas-validado.apk`, 55.993.299 bytes.
- **SHA-256 do APK:** `33c8f8eb41c61eb4cb69fd0a5aba8eefd4f09474c027a9648a050ebc47170816`.
- **SHA-256 do ZIP do artifact funcional:** `16c942dac2374259a3c5d7d91284eb2efa2174353af3f377c20573a011fb25d7`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar visualmente as 17 bolinhas, legibilidade dos nomes, três colunas sem sobreposição, conteúdo abaixo da fileira correta, recolhimento ao tocar novamente, troca entre módulos e ausência de regressão na grade flutuante e no farol. A PR #44 deve permanecer em rascunho até essa validação real.

## 01/08/2026 — 0.1.174 (5350) — conteúdo dos módulos dentro do próprio expander

- **Branch:** `agent/home-module-launcher-0.1.174`; **PR:** #43, empilhada sobre `agent/deterministic-shortcut-grid-0.1.173`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `c617f7a558d8f77a5f6f805762b8d0ff3e58842f`; **head funcional final validado:** `93abd6d02cae7a3a5eb6f1635e53971f3aafe55c`.
- **Pedido do usuário:** ao tocar em um expander, mostrar o conteúdo do módulo imediatamente dentro do próprio cartão, sem renderizá-lo no final da página e sem obrigar o usuário a percorrer toda a Home.
- **Evidência:** o vídeo real `1000878234.mp4` mostrou que a lista de módulos era expandida no topo, mas o conteúdo selecionado continuava sendo renderizado por um `SettingsScreen` global depois de todos os cartões.
- **Causa:** `ShortcutModulesHome0171` apenas alterava `selectedBubbleGroup`; em seguida, a Home sempre compunha um único `SettingsScreen` fora da lista. O expander não possuía o conteúdo do módulo como filho visual.
- **Correção aplicada:**
  - cada módulo passa a compor seu conteúdo dentro do próprio `Card` expandido;
  - somente um módulo permanece aberto por vez, controlado por `HomeModuleExpansionPolicy0174`;
  - tocar novamente no módulo aberto recolhe o conteúdo no mesmo local;
  - removido o botão genérico `Abrir módulo` dos módulos internos;
  - Destino, Rota, Alertas, Locais, Radares, Aparência, Permissões, Backup, Cards, Encerrar e Relatórios são renderizados inline no expander correspondente;
  - módulos que exigem Activity própria mantêm, dentro do próprio expander, resumo e botão com ação específica;
  - removido o `SettingsScreen` global que aparecia após toda a lista;
  - grade flutuante, toque simples/longo restaurado e contrato do farol foram preservados.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt`;
  - novo `app/src/main/java/br/com/mapeiaia/rotacerta/HomeModuleExpansionPolicy0174.kt`;
  - novo teste `HomeModuleExpansionPolicy0174Test.kt`;
  - contrato legado `ShortcutLongPressContract0171Test.kt` atualizado para validar a arquitetura inline sem reintroduzir personalização;
  - `scripts/fix_home_inline_modules_0174.patch.gz.b64`;
  - `scripts/fix_home_inline_modules_0174_test_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.174.yml`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt` e `LiveRideAccessibilityService.kt` permaneceram preservados por checksum. Não houve alteração em permissões, parser, OCR automático, rota, Casa/Alfinetes, confirmação de card, cores, km ou cancelamento de resultado atrasado.
- **Testes e validações:** materialização completa 0.1.151–0.1.173; checksum exato do patch `1a0622e82db7aa3468851a7fcfab324d885b7c614567de0f4485e8553784e154`; aplicação limpa; contrato estrutural inline; política de apenas um expander aberto; 244 testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falha intermediária localizada:** houve uma execução com checksum divergente durante atualização concorrente da branch. O hash foi recalculado no próprio runner, confirmado e o pipeline foi repetido no head limpo.
- **Workflow final validado:** `Build Rota Certa 0.1.174`, run `30702465004`, job `91375754777`; todos os passos concluídos com sucesso.
- **Artifact final:** `rota-certa-0.1.174-inline-home-modules-validated`, ID `8819317727`, retenção até 30/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.174`, versionCode `5350`.
- **APK:** `rota-certa-0.1.174-modulos-inline-validado.apk`, 55.976.915 bytes.
- **SHA-256 do APK:** `ce3560232b905499246d1d215784fb18af3162c9be8aeb005f587df605e0ba5b`.
- **SHA-256 do ZIP do artifact:** `5ec230c94831f9c9630883e7c81d54e1401b0b68747e0ae8649e9113fe613057`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar visualmente que o conteúdo surge logo abaixo do título do módulo tocado, que o módulo anterior recolhe, que não há salto ao fim da página, que os botões de Activities externas abrem o destino correto e que a grade e o farol não regrediram. A PR #43 deve continuar em rascunho até essa validação real.
## 01/08/2026 — 0.1.173 (5340) — grade restaurada sem personalização

- **Branch:** `agent/deterministic-shortcut-grid-0.1.173`; **PR:** #42, empilhada sobre `agent/accessibility-resilience-and-tools-0.1.172`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional:** `e339adbb1590a872f6d3bec34a489abb5553bc05`; **commit do workflow final validado:** `c49fb2cbac19d0cfedec5c7b4febf488d48ecb5c`.
- **Pedido do usuário:** os atalhos da grade não estavam executando corretamente o comportamento determinado; remover a personalização e restaurar as ações originais dos atalhos.
- **Situação anterior:** a 0.1.171 permitia escolher individualmente a ação do toque longo e a 0.1.172 herdava essas preferências. Na validação prática, a combinação entre estado persistido, ação escolhida, ação principal e ação secundária tornou o resultado difícil de prever e de diagnosticar.
- **Causa:** a personalização passou a participar do despacho da grade e substituiu o contrato original, no qual o toque simples executava a ação principal e o toque longo executava a ação secundária existente ou repetia a principal quando não havia secundária.
- **Correção aplicada:**
  - removida a interface de escolha da ação do toque longo na Home;
  - removida a leitura de preferências personalizadas durante o gesto;
  - preferências antigas da personalização são ignoradas e limpas;
  - toque simples permanece ligado à ação principal fixa do módulo;
  - toque longo restaura a ação secundária original e, quando ela não existe, repete a ação principal;
  - ações sensíveis mantêm confirmação quando prevista, incluindo limpeza do cache;
  - módulos continuam disponíveis diretamente pela Home;
  - falha de ação encerra de forma segura, sem alterar o farol;
  - nenhum polling, novo serviço ou processamento em segundo plano foi adicionado.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - `ShortcutLongPressPolicy0171.kt`;
  - testes `ShortcutLongPressPolicy0171Test.kt` e `ShortcutLongPressContract0171Test.kt`;
  - `scripts/fix_deterministic_shortcut_grid_0173.patch.gz.b64`;
  - `scripts/fix_deterministic_shortcut_grid_0173_test_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.173.yml`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine.kt`, `RideTextParser.kt`, `GoogleMapsService.kt`, seleção de aplicativos, confirmação de card, OCR automático, rota, Casa/Alfinetes, cores, km e cancelamento de resultados antigos permaneceram preservados por checksum.
- **Testes e validações:** materialização completa 0.1.151–0.1.172; aplicação limpa do patch; contrato de restauração; testes unitários e de contrato; Android Lint; `clean assembleDebug`; integridade ZIP; pacote; versão; versionCode; marcador DEX estável; assinatura APK Signature Scheme v2 e certificado.
- **Falha intermediária do workflow:** a primeira compilação e todos os testes passaram, mas a validação procurava a frase acentuada `Ações fixas na grade` por `strings` bruto no DEX e gerou `FALHA_DEX_HOME_0173`. A checagem inadequada foi removida; a frase continua validada diretamente no código-fonte e o marcador ASCII compilado permanece validado no APK.
- **Workflow final validado:** `Build Rota Certa 0.1.173`, run `30682334191`, job `91321705773`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.173-restored-shortcut-grid-validated`, ID `8812754254`, retenção até 30/10/2026.
- **SHA-256 do ZIP do artifact:** `ee1e20b5796608d544e98b2afa69698498725ad924a5335d8d5d89934de7f5c4`.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.173`, versionCode `5340`.
- **APK:** `rota-certa-0.1.173-atalhos-grade-restaurados-validado.apk`, 55.976.911 bytes.
- **SHA-256 do APK:** `c9e554dfba02128ed2a0132e0c50afc5d2d9cb6fae20039fef20abdd73499ac6`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar cada atalho da grade com toque simples e toque longo, especialmente módulos com secundária própria, módulos com fallback para a principal, limpeza com confirmação, Copiar/OCR e Links rápidos. Confirmar também que preferências antigas não reaparecem após atualização ou reinício e que o farol permanece inalterado. A PR #42 deve continuar em rascunho até essa validação real.

## 31/07/2026 — 0.1.172 (5330) — acessibilidade resiliente e ferramentas rápidas

- **Branch:** `agent/accessibility-resilience-and-tools-0.1.172`; **PR:** #41, empilhada sobre `agent/customizable-shortcut-long-press-0.1.171`, em rascunho, aberta e sem merge.
- **Commit funcional:** `57761d1e5ec26b50867fe7d4b16f94a69d2ae4c9`; **head final validado:** `38e58f413957479d88563183a95553441d262a23`.
- **Pedido do usuário:** reunir os erros encontrados na simulação de fluxos com as sugestões registradas na conversa e iniciar imediatamente as correções e implementações.
- **Situação anterior:** o Samsung SM-S911B/Android 16 mantinha a permissão de acessibilidade aparentemente autorizada, mas o relatório manual mostrava `session=not-initialized`, nenhum evento de ciclo de vida e somente `REPORT_EXPORT`. A bolinha conservava estado amarelo, destino antigo e `serviceReady=true`, embora o serviço não estivesse conectado. A auditoria também confirmou navegação presa nos módulos, contraste ruim em Respostas rápidas e ausência dos recursos de links, limpeza segura, OCR manual universal e edição compartilhada de frases.
- **Causas localizadas:**
  - a contenção 0.1.170 protegia somente o despertar por notificação, deixando o restante de `onAccessibilityEvent`, ciclo de vida, coroutines raiz e localização expostos a exceções;
  - interrupção ou destruição do serviço não invalidava imediatamente o estado persistido, produzindo relatório e bolinha com dados antigos;
  - os módulos internos eram controlados por estados Compose sem semântica completa de retorno pelo botão/gesto Voltar;
  - `QuickRepliesActivity` forçava esquema escuro e o campo de busca dependia de cores que não garantiam contraste;
  - ações novas ainda não possuíam módulos e armazenamento locais limitados.
- **Correção aplicada:**
  - fronteira externa fail-closed para eventos e ciclo de vida da acessibilidade, sem alterar parser, confirmação do card, rota ou decisão;
  - `CoroutineExceptionHandler` e contenção das tarefas raiz, preservando cancelamento normal e evitando que exceções inesperadas derrubem silenciosamente o processo;
  - invalidação imediata de `serviceReady`, sessão, cor, km, destino e trabalhos transitórios em interrupção, destruição ou falha crítica;
  - registro limitado dos marcadores reais `UNEXPECTED_FAILURE_CONTAINED_0172`, `SERVICE_LIFECYCLE_FAILURE_CONTAINED_0172`, `SESSION_CONNECTED_0172` e `SERVICE_DISCONNECTED_0172`;
  - diagnóstico permanente orientado a eventos e modo intensivo manual, temporário e limitado, com pulso de um segundo sem screenshot/OCR contínuo e consulta do motivo de encerramento pelo Android;
  - proteção do callback de localização do rastreamento de trabalho;
  - tratamento do botão físico e gesto Voltar: fecha diálogo interno primeiro, sai do módulo para a Home e só então aplica o comportamento normal da Activity;
  - campo de pesquisa de Respostas rápidas usando cores do tema/sistema e fechamento correto quando aberto pela Home;
  - novo módulo `Links rápidos`, com múltiplos links HTTP/HTTPS, nome editável, link principal e abertura somente por toque no aplicativo compatível ou navegador;
  - toque longo de `Limpar` apaga somente `cacheDir` e `externalCacheDir`, com confirmação, sem remover cards, Casa, Alfinetes, configurações, frases, links ou histórico persistente;
  - toque longo de `Copiar` executa uma única captura do quadro atual e um único OCR, cancela trabalho anterior, não usa loop e não armazena screenshot;
  - editor compartilhado `MessageTemplatesActivity` para visualizar e editar as frases predefinidas usadas por Copiar e Valor;
  - catálogo final com 17 módulos na Home, incluindo `QuickLinksBubbleShortcutModule`; o editor de frases permanece compartilhado com Copiar e Valor, sem criar outra bolinha permanente.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `app/src/main/AndroidManifest.xml`;
  - `BubbleShortcutModule.kt`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - `QuickRepliesActivity.kt`;
  - `WorkTrackingService.kt`;
  - novos `QuickLinksActivity.kt`, `MessageTemplatesActivity.kt` e `RotaCertaTools0172.kt`;
  - `ShortcutLongPressPolicy0171.kt`;
  - novos testes `AccessibilityResilienceAndTools0172ContractTest.kt` e `MessageTemplateRenderer0172Test.kt`;
  - `scripts/fix_accessibility_resilience_tools_0172.patch.gz.b64.part00` a `part04`;
  - `scripts/fix_accessibility_resilience_tools_0172_test_contract.py`;
  - `.github/workflows/materialize-rota-certa-0.1.172.yml`.
- **Fronteira protegida:** contrato cinza/amarelo/verde/vermelho, seleção manual de aplicativos, confirmação real do card, destino final, Casa/Alfinetes, `DecisionEngine`, `RideTextParser`, Google Maps, cancelamento de resultados antigos, ausência de km sem rota e ausência de polling visual foram preservados.
- **Testes e validações executados:** materialização completa 0.1.151–0.1.171; checksum e aplicação limpa do patch 0.1.172; contratos estruturais; catálogo com 17 módulos e IDs/ações únicas; testes unitários e de contrato; Android Lint; `clean assembleDebug`; integridade ZIP; Manifest; DEX; pacote; versão; versionCode; assinatura APK Signature Scheme v2 e certificado.
- **Workflow final validado:** `Build Rota Certa 0.1.172`, run `30652939441`, job `91230344117`; todos os passos concluídos com sucesso no head final.
- **Artifact final:** `rota-certa-0.1.172-accessibility-resilience-tools-validated`, ID `8802226490`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.172`, versionCode `5330`.
- **APK:** `rota-certa-0.1.172-estabilidade-acessibilidade-ferramentas-validado.apk`, 55.976.915 bytes.
- **SHA-256 do APK:** `eda20d6e8d96a8e7db3e30ce5bcab556ce5df63cbac1268f71f61b4ee0c26009`.
- **SHA-256 do ZIP do artifact:** `ee6133e8e97a4c04a3f5d16da0532a941ba2219dd981f3b2960cbf7007ed48b9`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar conexão real da acessibilidade, permanência do serviço durante ofertas da Uber/99/inDrive, geração dos novos marcadores, retorno físico/gesto de todos os módulos, contraste em tema claro e escuro, abertura de links, confirmação e escopo da limpeza, OCR pontual em imagem/vídeo pausado/jogo, persistência das frases e encerramento automático do diagnóstico intensivo. A PR #41 permanece em rascunho e não deve ser mesclada antes desses testes.

## 31/07/2026 — 0.1.171 (5320) — módulos na Home e toque longo personalizável

- **Branch:** `agent/customizable-shortcut-long-press-0.1.171`; **PR:** #40, rascunho e sem merge na `main`.
- **Commit funcional validado:** `b9a3984c6bcae40c2abe5959025ec9e0b1d60cdd`.
- **Pedido do usuário:** manter todos os módulos acessíveis diretamente na tela inicial, deixar a grade flutuante somente para execução rápida e permitir que cada módulo configure o comportamento de manter pressionado o seu atalho, sem apagar decisões anteriores.
- **Situação anterior:** recursos importantes ficavam espalhados pelas abas e pela grade da bolinha. Quando o processo ou o serviço de acessibilidade encerrava, a bolinha desaparecia e a grade deixava de ser uma entrada confiável para abrir diagnóstico e outros módulos. O gesto longo também era definido no código como ação secundária ou, quando ausente, repetição da ação principal, sem escolha individual do usuário.
- **Causa arquitetural:** navegação completa e execução rápida estavam acopladas ao mesmo painel de sobreposição. Além disso, a grade aplicava um fallback implícito no toque longo, impossibilitando personalização por recurso e dificultando preservar gestos diferentes de forma explícita.
- **Correção aplicada:**
  - todos os 16 módulos registrados em `BubbleShortcutCatalog` passam a aparecer diretamente na Home em cartões expansíveis;
  - cada cartão possui explicação, botão `Abrir módulo` e configuração `Ação ao manter pressionado o atalho`;
  - opções: manter comportamento atual, não fazer nada, abrir módulo, executar ação principal e, quando o recurso possuir, executar ação secundária;
  - padrão de migração `PreserveExisting`: conserva exatamente o gesto longo anterior de cada atalho até o usuário alterar;
  - toque simples da grade continua determinístico e executa a ação principal já existente;
  - ações longas sensíveis escolhidas pelo usuário passam por confirmação antes de executar;
  - preferências são locais e pequenas, em `SharedPreferences`, sem polling, novo serviço ou trabalho em segundo plano;
  - diagnóstico e geração manual de relatório ficam acessíveis pela Home mesmo sem a bolinha;
  - falha ao abrir um módulo encerra de forma segura e não altera o estado do farol.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_home_modules_long_press_0171.py.part00` a `part04`;
  - `.github/workflows/build-rota-certa-0.1.171.yml`;
  - `app/build.gradle.kts`;
  - `BubbleShortcutModule.kt`;
  - `BubbleShortcutOverlayController.kt`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - novo `ShortcutLongPressPolicy0171.kt`;
  - novos testes `ShortcutLongPressPolicy0171Test.kt` e `ShortcutLongPressContract0171Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, XML de acessibilidade, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, políticas 0.1.166–0.1.170, Casa/Alfinetes, confirmação do card, cores, quilometragem, OCR e rota permaneceram byte a byte preservados em relação à árvore 0.1.170.
- **Falha intermediária localizada:** a primeira execução compilou os contratos, mas o compilador encontrou `Unresolved reference: context` no novo cartão Compose. Foi incluído somente `LocalContext.current` no componente afetado e todo o pipeline foi reexecutado.
- **Testes executados:** materialização completa 0.1.151–0.1.171; aplicação do patch duas vezes e idempotência; fronteira exata; preservação de hashes; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.171`, run `30631301134`, job `91158023794`, todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.171-home-modules-custom-long-press-validated`, ID `8793578999`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.171`, versionCode `5320`.
- **APK:** `rota-certa-0.1.171-home-modulos-toque-longo-personalizado-validado.apk`, 55.927.659 bytes.
- **SHA-256 do APK:** `7dd5fa7fb9dfe86254f9bc7ffc653570a690be15dbaf8d29749613a2c8231b83`.
- **SHA-256 do ZIP do artifact:** `88d04403df4084ec39b13f0b390b4d7e38efdb3531b5a223ac527fe4fadc025a`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e riscos:** instalar no Samsung SM-S911B/Android 16; confirmar que todos os módulos aparecem e abrem pela Home; verificar persistência de cada escolha após reiniciar; testar o comportamento padrão anterior e as cinco opções; confirmar diálogo em ações sensíveis; gerar relatório pela Home com a bolinha ausente; validar também que a contenção de crash 0.1.170 continua estável durante uma oferta real. A PR permanece em rascunho até essa validação prática.

## 31/07/2026 — 0.1.170 (5310) — falha do despertar por notificação contida

- **Branch:** `agent/contain-notification-crash-0.1.170`; **PR:** #39, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f50710959058054f45962f44e77ee76934bc0666`.
- **Pedido/evidência:** o vídeo real `1000876918.mp4` mostrou o Android encerrando o Rota Certa 0.1.169 e desativando a leitura ao vivo.
- **Causa e limite:** sem stack trace não foi atribuída uma linha única; a nova entrada síncrona e o trabalho assíncrono da notificação não tinham contenção externa.
- **Correção:** fronteiras fail-closed, circuito limitado de 60 segundos somente para notificações, cancelamento e limpeza transitória, diagnóstico limitado e preservação do restante do serviço.
- **Testes:** materialização 0.1.151–0.1.170; idempotência; fronteira; testes; Lint; `clean assembleDebug`; APK, Manifest, DEX, pacote, versão e assinatura.
- **Workflow final:** run `30628394153`, job `91148840468`, sucesso.
- **Artifact:** `rota-certa-0.1.170-accessibility-crash-contained-validated`, ID `8792402353`.
- **APK/SHA-256:** `rota-certa-0.1.170-acessibilidade-falha-contida-validado.apk` — `68ed1f6d7398aa36608a420c18e8f8d16bcd999693dfd9a29d8a9e9d6d785880`.

## 31/07/2026 — 0.1.169 (5300) — despertar pontual de cards sobrepostos

- **Branch:** `agent/fix-uber-overlay-event-gap-0.1.169`; **PR:** #38, rascunho e sem merge na `main`.
- **Causa comprovada:** card Uber sobre o launcher durante aproximadamente 25,9 segundos sem `ACCESSIBILITY_EVENT`; o serviço não escutava `TYPE_NOTIFICATION_STATE_CHANGED`.
- **Correção:** gatilho de notificação somente para pacote selecionado, token por geração, deduplicação, TTL de 12 segundos, máximo de quatro capturas, confirmação visual obrigatória e ausência de polling.
- **Workflow final:** run `30625877873`, job `91140903636`, sucesso.
- **Artifact:** ID `8791527911`; **APK SHA-256:** `0c11a2f7292516f0daf00cf10e30f52e7279f87200a209be78f647458a9d7915`.
- **Resultado em aparelho:** reprovado por encerramento mostrado no vídeo; supersedido pela contenção 0.1.170.

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Correção:** primeiro quadro sem atraso; OCR espacial único; fusão com acessibilidade; segmentação por card; assinatura semântica; endereços incompletos bloqueados; tentativas limitadas e canceláveis.
- **Testes:** 237 testes; Android Lint; `clean assembleDebug`; ZIP; Manifest; DEX; pacote, versão e assinatura.
- **Workflow final:** run `30602408881`, job `91067728989`, sucesso em todos os passos.
- **Artifact:** ID `8782463049`; **APK SHA-256:** `386cbf256e471bcb6ab2bc79c8564ff9826dbf52bec2fac5bb5a98fd2fe69de2`.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
