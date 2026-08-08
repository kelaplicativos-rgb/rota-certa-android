<!-- PROXIMITY_NO_DIRECTION_0191_STAGE6_START -->
## 08/08/2026 — 0.1.191: reparo do materializador publicado; validação Android final pendente

- **Branch:** `agent/proximity-alerts-no-direction-0.1.191`; **PR:** #69, aberta, mergeável e em rascunho; **base:** `agent/alert-popup-lifecycle-0.1.190`.
- **Commit do reparo do materializador:** `9d812fe81cd2d0bfa3b17c665a660a804c7dc81a`.
- **Versão alvo:** `0.1.191`; **versionCode alvo:** `5475`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** alertas de proximidade e radares devem avisar quando a distância comprovar aproximação, sem exigir heading, alvo à frente ou coincidência com a direção cadastrada do radar. O farol universal, `DecisionEngine`, rota, OCR e confirmação de card não fazem parte desta alteração.
- **Falha anterior do CI:** workflow run `31204485271`, job `92952148552`. A árvore 0.1.190 foi materializada e validada, mas a transformação 0.1.191 parou antes de testes/Lint/assemble com `gate alvo à frente salvo: esperado exatamente 1 ocorrência, encontrado 2`.
- **Causa comprovada:** o materializador usava `replace_once` global em dois padrões que existem legitimamente duas vezes no motor — uma no fluxo de alerta salvo e outra no fluxo de radar: `isTargetAhead(...)` e `runtime.hasPassed(fix.headingDegrees, bearingToTarget, distance)`. Portanto o erro era de transformação/materialização, não Gradle, Kotlin, teste, Lint, dependência, memória, disco ou timeout.
- **Correção mínima:** `scripts/apply_proximity_alerts_no_direction_0191.py` preserva `replace_once` para substituições realmente únicas e usa `replace_once_before` com âncoras semânticas distintas de elegibilidade para localizar a passagem no fluxo correto. Os blocos de bearing/gate do alerta salvo e do radar são removidos por contexto próprio, sem selecionar “primeira” ou “segunda” ocorrência por ordem global.
- **Fail-closed adicional:** depois da transformação o script exige exatamente dois fluxos `runtime.hasPassed(distance)`, rejeita qualquer chamada antiga com heading e rejeita sobras de `DirectionalAlertPolicy.isTargetAhead(...)` ou `radarDirectionMatches(...)`.
- **Regressão gerada:** `ProximityAlertsNoDirection0191ContractTest` passa a exigir exatamente duas chamadas de passagem por distância, nenhuma chamada antiga com heading, ausência dos gates de direção, preservação de precisão/velocidade do GPS, compatibilidade da assinatura antiga de `hasPassed`, tendência de distância e contrato visual de 3 segundos/fechamento manual.
- **Validação pré-publicação da correção:** `python -m py_compile` aprovado; quatro regressões focadas do materializador aprovadas; teste Kotlin gerado compilado isoladamente com stubs mínimos de JUnit sem erro de sintaxe. Essas verificações não substituem a suíte Android completa.
- **Android Lint:** a base 0.1.190 tem Lint aprovado no workflow `31191545985`; o **Lint da árvore 0.1.191 corrigida ainda não foi executado** porque a fonte Android completa só é materializada pelo pipeline após a publicação do reparo. Não declarar Lint 0.1.191 aprovado antes de execução real.
- **Build/APK 0.1.191:** não executado nesta etapa. O commit usa `[skip ci]` deliberadamente para impedir geração antecipada de APK. Não existe artifact, APK, assinatura ou SHA-256 validado da 0.1.191 neste estado.
- **Arquivos diretamente alterados neste reparo:** `scripts/apply_proximity_alerts_no_direction_0191.py` e esta documentação; o teste Kotlin é gerado pelo próprio materializador. `AndroidManifest.xml`, `DecisionEngine.kt`, Google Maps/rota, `GpsAddressResolver`, `RideTextParser`, `RadarImport`, `DirectionalAlertOverlayController` e `LiveRideAccessibilityService` continuam fora do diff funcional deste reparo.
- **Risco funcional a validar:** ao retirar autoridade de sentido/heading conforme o pedido, um ponto próximo em via paralela ou sentido oposto pode tornar-se elegível se a tendência real de distância satisfizer o contrato. Isso deve ser testado no aparelho sem reintroduzir gate direcional global.
- **Pendências:** executar suíte Android real e Lint sobre a árvore 0.1.191 materializada; somente com autorização da etapa final executar `clean assembleDebug`, validar pacote/versão/versionCode, APK, assinatura v2, artifact, SHA-256 e teste físico de alerta manual/radar importado nos dois sentidos, rearme após saída e ausência de duplicidade/pisca.
<!-- PROXIMITY_NO_DIRECTION_0191_STAGE6_END -->

<!-- ALERT_POPUP_0190_CI_PENDING_DEVICE_START -->
## 07/08/2026 — 0.1.190: pop-ups de radares/alertas permanecem 3 s após o ponto; CI aprovado

- **Branch:** `agent/alert-popup-lifecycle-0.1.190`; **PR:** #68; **base:** `agent/fix-farol-priority-latency-0.1.189`.
- **Commit funcional validado:** `c5805d89e0187d163c7e04a999554a95591700ca`; **workflow run:** `31191545985`; job `92909308180` concluído com sucesso.
- **Versão:** `0.1.190`; **versionCode:** `5474`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** os pop-ups de radares e alertas não devem desaparecer imediatamente ao ultrapassar o ponto; devem permanecer por cerca de 3 segundos e fechar sozinhos. Se o usuário tocar em `Fechar`, o mesmo ponto não deve reaparecer durante a passagem atual.
- **Situação anterior / causa:** `DirectionalAlertOverlayController` usava `PASSED_CLOSE_DELAY_MILLIS = 750L`, mantendo a indicação pós-passagem por somente 750 ms. A supressão manual até sair da zona já existia no motor e não precisava ser redesenhada.
- **Correção:** o atraso visual pós-passagem foi alterado para `3_000L`. O fechamento manual continua chamando `dismissUntilExit(visual.targetId)` no fluxo direcional e `dismissSavedPlaceUntilExit(alert.id)` no fluxo legado; depois de sair da zona de reset, uma aproximação futura pode avisar novamente.
- **Escopo:** alteração funcional isolada no ciclo visual do pop-up. `DecisionEngine`, rota, OCR, Manifest/permissões, `DirectionalAlertPolicy`, `DirectionalProximityAlertEngine` e `LiveRideAccessibilityService` permaneceram protegidos/inalterados pela validação de SHA-256 do build.
- **Primeiro run reprovado:** `31189837277`, job `92903581710`. A primeira causa real foi infraestrutura de materialização: transporte `.gz.b64` da 0.1.189 com CRC/length inválidos; a árvore 0.1.188 havia passado testes/Lint/assemble antes da falha. O validador 0.1.190 foi corrigido para usar `build_rota_certa_0189_parts.sh`, que reconstrói o patch a partir de seis partes com SHA-256 conferido.
- **Testes finais:** `tests=372`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/APK aprovada.
- **Artifact:** `rota-certa-0.1.190-alert-popup-lifecycle-validated`, ID `8999711497`, digest `sha256:042ea788583ca1c63d0502935206b241bb3d5cd380e1df2e59c0d7b81fb6914f`.
- **APK no artifact:** `rota-certa-0.1.190-alertas-popup-3s-validado-em-ci.apk`, `56.157.135` bytes.
- **Assinatura:** APK Signature Scheme v2 válida; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **SHA-256 do APK:** `8c32f2bcf07cc1109e4ac996db406bbbe520c5cd155fab9e703d37ef1b8e9d34`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.190/rota-certa-0.1.190-candidate.apk`.
- **Pendência física:** confirmar em aparelho real radar importado e alerta manual: aviso permanece aproximadamente 3 s depois de ultrapassar; `Fechar` impede reabertura do mesmo ponto na passagem atual; sair da zona e reaproximar rearma o ponto; fala, farol e desempenho permanecem normais.
<!-- ALERT_POPUP_0190_CI_PENDING_DEVICE_END -->

<!-- FAROL_0189_CI_PENDING_DEVICE_START -->
## 07/08/2026 — 0.1.189 prioridade visual/latência aprovada em CI; aparelho pendente

- **Branch:** `agent/fix-farol-priority-latency-0.1.189`; **PR:** #67; **base:** `agent/fix-farol-real-device-0.1.188`.
- **Head validado em CI:** `abfc15ee017f1655804d94199fc63c76061d39e1`; **workflow run:** `31182848428`.
- **Versão:** `0.1.189`; **versionCode:** 5473; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** reduzir decisão para o caminho crítico mínimo, priorizar imediatamente a janela/bloco visual superior e preservar amarelo para pacote ativo, introduzindo laranja quando o último endereço do card atual foi confirmado e a rota real está em cálculo.
- **Causa:** segmentação 0.1.188 tratava linhas OCR do mesmo card como blocos independentes e reagendava OCR repetidamente para a mesma geração; isso gerava falsos negativos e desperdício de tempo.
- **Correção:** `FarolVisualPriority0189` agrupa fragmentos por geometria; maior camada de janela e bloco visual superior têm autoridade monotônica; novo bloco invalida a identidade anterior; dois ou mais endereços do mesmo bloco usam o último como destino; OCR é single-flight/deduplicado por identidade de geração/bloco; laranja é aplicado antes de cache/rede e nunca mostra quilômetros sem rota real.
- **Fronteiras preservadas:** `DecisionEngine`, `GoogleMapsService`, `GpsAddressResolver`, `RideTextParser`, Manifest/permissões, radares e alertas permaneceram byte a byte iguais na cadeia de build.
- **Testes CI:** tests=369; failures=0; Android Lint e `clean assembleDebug` aprovados.
- **Artifact:** `rota-certa-0.1.189-release-candidate`, ID `8996015949`, referência/digest `45f10b1e15339d64c3c31da1e459422b238f1b8aa3d1d056c8b537bc69b35726`.
- **APK candidato:** `rota-certa-0.1.189-candidate.apk`, 56157135 bytes; assinatura APK v2 conferida; SHA-256 `3c81ec2a4984970e9d1003a038c7021fa8ac448d36bf22df5c4aadd830e5eb36`.
- **Link candidato:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.189/rota-certa-0.1.189-candidate.apk
- **Distribuição:** `latest` não é substituído; aprovação funcional exige novo teste físico no Samsung SM-S911B/Android 16, especialmente 99, Uber popup, múltiplos cards, troca de bloco e tempo entre amarelo→laranja→verde/vermelho.
<!-- FAROL_0189_CI_PENDING_DEVICE_END -->

<!-- FAROL_0188_CI_PENDING_DEVICE_START -->
## 07/08/2026 — 0.1.188 reprovada no aparelho real

- **Branch:** `agent/fix-farol-real-device-0.1.188`; **PR:** #66; **versão:** `0.1.188`; **versionCode:** 5472; **pacote:** `br.com.mapeiaia.rotacerta`.
- **CI anterior:** run `31173363429`, tests=364, failures=0, Lint e `clean assembleDebug` aprovados; o sucesso do CI não representou aprovação funcional.
- **Prova física:** relatório manual `rota-certa-relatorio-depuracao (32).txt`, Samsung SM-S911B / Android 16.
- **Falha:** falso negativo real — OCR encontrou origem e destino da mesma oferta, mas o gate os classificou como blocos diferentes e recusou a rota; nenhuma decisão verde/vermelha foi pintada na sessão.
- **Latência:** o relatório registrou `Tempo da ultima decisao: 1643 ms` e forte reagendamento de OCR para as mesmas gerações.
- **Conclusão:** 0.1.188 permanece apenas como referência histórica/candidata reprovada; não promover para `latest`.
<!-- FAROL_0188_CI_PENDING_DEVICE_END -->

<!-- FAROL_PHASE4_VALIDATED_STATUS_START -->
## 06/08/2026 — fase 4 validada — resultado atrasado não substitui leitura nova

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
- **Head funcional validado:** `3537a13edbc0d72627d650a18b4da58da8ecb316`; correção de escopo em `d181b0e126b6d1b2c37001872e8afcf7f302e4fd`; teste em `1a49003e2f347911c748d8f6520eb519773772b4`.
- **Versão:** `0.1.187`; **versionCode:** 5471; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Situação anterior:** o primeiro build conclusivo da fase 4, run `31129590730`, chegou à compilação Kotlin e falhou em `LiveRideAccessibilityService.kt:2548` por uma referência solta `addressSignature` que permaneceu depois de a fase 4 substituir parâmetros avulsos pelo vínculo imutável de decisão.
- **Causa:** a persistência do resultado ainda referenciava o identificador removido do escopo, embora a assinatura correta já estivesse transportada em `FarolDecisionBinding0187Phase4`.
- **Correção:** `fix_farol_phase4_address_signature.py` localiza exatamente uma referência nua sem declaração e exige uma única fonte válida no mesmo método. Na aplicação do resultado, a persistência passa a usar a propriedade `addressSignature` do vínculo monotônico. Ambiguidade, duas fontes ou parâmetro antigo declarado falham fechado.
- **Contrato:** o teste textual amplo foi substituído por verificação estrutural da lista `persistenceSignatureChecklist13`, que deve começar com uma propriedade `.addressSignature` de um vínculo; usos legítimos com argumento nomeado não são bloqueados.
- **Fase 4 preservada:** pacote, geração da sessão, janela, geração da tela, geração da janela, hash da tela e assinatura do destino continuam indivisíveis; resultado antigo é descartado antes de alterar destino, quilômetros ou cor; cancelamento central abrange rota, análise, OCR, screenshot e confirmação parcial.
- **Fronteira protegida:** `DecisionEngine`, cálculo de rota, raio, Casa/Alfinete, Manifest, permissões, parser universal, confirmação individual do card, Google Maps, radares e alertas não foram alterados; hashes protegidos foram aprovados no artifact.
- **Replay determinístico:** run `31135097519`, aprovado, incluindo regressão do reparador e replay histórico estrito.
- **Workflow Android:** `Build Rota Certa 0.1.187`, run `31135097548`, job `92732575581`, concluído com sucesso em materialização, testes, Android Lint, `clean assembleDebug`, validações do APK, publicação e conferência do download permanente.
- **Testes:** tests=356; failures=0.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8977776625`, digest `f427d1d62aa8cfb806c956d95f585a12c35bd1268f60088f30231385254092bd`.
- **Artifact URL:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31135097548/artifacts/8977776625
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; ZIP íntegro; `compileSdk` 35; `minSdk` 26; `targetSdk` 35.
- **Assinatura:** APK Signature Scheme v2 válida; um signatário; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **SHA-256 do APK:** `ca8ce3b5742fea2170dd43425d3b6bf897f4058a3c35073bb8a4aebeb0920a45`.
- **Link permanente verificado:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **Pendência física:** instalar no Samsung SM-S911B/Android 16 e validar troca rápida de cards, rota antiga concluindo depois da troca de janela/sessão, SystemUI, teclado, retorno ao feed, fechamento real do card, ausência de pisca e ausência de quilômetros antigos.
<!-- FAROL_PHASE4_VALIDATED_STATUS_END -->

<!-- FAROL_PHASE3_VALIDATED_STATUS_START -->
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

<!-- FAROL_PHASE2_VALIDATED_STATUS_START -->
## 06/08/2026 — fase 2 validada — raiz atômica e vínculo imutável da leitura

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
- **Commit funcional validado:** `16e130912d4294d269dd2abb15ad6e68105aa4ac`.
- **Versão:** `0.1.187`; **versionCode:** 5471; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** ligar as invariantes do laboratório da fase 1 ao caminho Kotlin real, corrigindo pacote/raiz/janela/geração e a origem das exceções sem alterar `DecisionEngine`, rota ou regras de cor.
- **Situação anterior:** o serviço podia consultar pacote da raiz, janela, texto e nós mediante aquisições diferentes de `rootInActiveWindow`. O Android podia invalidar ou trocar a raiz entre consultas; uma leitura antiga ainda podia chegar após troca de janela, sessão ou geração.
- **Causa:** a evidência de uma leitura não possuía uma única raiz física nem um token imutável revalidado depois das suspensões. A contenção anterior vinculava a recuperação, mas ainda permitia montar parte da evidência em instantes diferentes.
- **Correção:** `FarolRootHandle0187` captura uma única raiz e deriva dela pacote e janela; `FarolRootSnapshotPolicy0187` confere evento, pacote selecionado, raiz e janela antes da travessia; `FarolReadBinding0187` carrega pacote, sessão, janela e gerações; o vínculo é revalidado antes e depois do processamento assíncrono e qualquer divergência descarta a leitura antes de destino, OCR, rota, quilômetros ou cor.
- **Recuperação/OCR:** acessibilidade, nós e assinatura são coletados da mesma raiz; recuperação antiga, raiz nula ou raiz de outro pacote falham fechado e não reutilizam distância anterior.
- **Desempenho:** o gate de eventos permanece antes da coleta completa; eventos externos continuam rejeitados antes de consultar raiz antiga; chamadas repetidas de limpeza e renderização continuam confluídas pelo núcleo já existente.
- **Arquivos principais:** `LiveRideAccessibilityService.kt`, `FarolRuntimeSafety0187.kt`, `FarolRuntimeSafety0187Test.kt`, `FarolRuntimeFix0187ContractTest.kt`, `FarolRealtimeCriticalPathContract0167Test.kt`, patches e scripts cumulativos 0.1.187.
- **Fronteira protegida comprovada por SHA-256:** `AndroidManifest.xml`, `DecisionEngine.kt`, `RideTextParser.kt`, `GoogleMapsService.kt`, `GpsAddressResolver.kt`, confirmação 0.1.185, políticas de eventos/visual, parser universal, alertas e radares não foram alterados.
- **Testes:** tests=352; failures=0; testes unitários e de contrato aprovados; laboratório determinístico aprovado; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Workflow validado:** `Build Rota Certa 0.1.187`, run `31112248496`; fonte protegida `32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42`.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8972452959`, digest `fe1ec1fc52d3b7550088dabf6c27d9802c31cff490dcaba8b6473992c2fc3099`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31112248496/artifacts/8972452959
- **Link permanente do APK:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; ZIP íntegro; pacote/versão/versionCode conferidos; assinatura APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`.
- **SHA-256 do APK:** `8873d82eaf914919c4b41491bb5f51209c9ec142774ff63b81f3c3d8216e90e5`.
- **Pendência real:** instalar no Samsung SM-S911B/Android 16 e validar card individual, troca rápida entre ofertas, saída do card, sobreposição do SystemUI/teclado/Android Auto, ausência de destino/km antigo, ausência de pisca e nenhuma exceção `accessibility_event_0172`. A PR permanece em rascunho até essa comprovação física.
<!-- FAROL_PHASE2_VALIDATED_STATUS_END -->

<!-- FAROL_TRACE_LAB_PHASE1_STATUS_START -->
## 06/08/2026 — fase 1 — laboratório determinístico do farol

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
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

<!-- ROTA_CERTA_0_1_186_STATUS_START -->
## 06/08/2026 — 0.1.186 (5470) — grade, áudio, Links e Correção de texto

- **Branch:** `agent/shortcut-audio-links-text-correction-0.1.186`; **PR:** #57, empilhada sobre a 0.1.185.
- **Commit funcional validado:** `960bd48f897d776ec41aa68d92965f028ac0637b`.
- **Pedido:** fechamento seguro da grade por toque externo/bolinha, Home genérica recolhida, gesto longo de 1,5 s configurável, saída Sem som/Alarme/Mídia, pesquisa e cópia em Links e novo módulo offline Correção de texto.
- **Correção:** backdrop transparente consumível e removível; gesto determinístico sem janela de 900 ms; cancelamento de callback longo ao fechar/desanexar a grade; ação longa tipada/persistida; navegação explícita `collapsed`/`module`; um único TTS com `AudioAttributes`; filtro local normalizado; editor de links com quatro ações e bloqueio explícito ao atingir 40 itens; correção conservadora offline com preservação exata de URLs/e-mails, substituição somente em contexto editável exato, rejeição sem truncamento quando o resultado excederia 12.000 caracteres e remoção imediata do texto/token capturado do `Intent` após consumo.
- **Compatibilidade de compilação:** removidos, de forma estritamente validada, dois imports diretos de `androidx.compose.foundation.layout.weight` incompatíveis com a versão Compose usada; o uso de `Modifier.weight` permanece no escopo público de `RowScope`/`ColumnScope`, sem mudança funcional.
- **Contratos de regressão:** o teste de toque direto passou a verificar o marcador atual `SHORTCUT_DIRECT_TAP_AND_HOLD_0186`; o cenário de migração legado agora limpa explicitamente o novo campo tipado antes de validar `holdAction0180=NONE`. Nenhuma regra de gesto foi alterada para fazer os testes passarem.
- **Fronteira protegida:** Manifest/permissões, `DecisionEngine`, parser, Google Maps, Casa/Alfinetes, confirmação 0.1.185, OCR e políticas universais permaneceram byte a byte inalterados por SHA-256.
- **Pipeline:** as versões-base são materializadas em ordem, com verificação de patches, hashes e contratos estruturais, mas sem repetir Gradle; `testDebugUnitTest`, `lintDebug` e `clean assembleDebug` são executados uma única vez sobre a árvore final 0.1.186. A contagem dos XMLs de testes é validada e preservada antes do `clean`, que remove `app/build`.
- **Testes:** tests=342; failures=0; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Workflow:** `Build Rota Certa 0.1.186`, run `31066287734`; fonte protegida fixada no commit `32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42`; descoberta positiva de testes obrigatória.
- **Artifact:** `rota-certa-0.1.186-shortcuts-audio-links-text-validated`, ID `8954056458`, digest `c797b9bada36516b186f66f9c512fe81b6dd155e51a831e64404f3057dc52d57`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31066287734/artifacts/8954056458
- **Link permanente do APK:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.186-grade-audio-links-corretor-validado.apk`, 56124367 bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.186`; versionCode `5470`.
- **SHA-256 do APK:** `42b8903255818aa1264c6d9b97439b097b279878a9e2851da8a0d27ff51495a2`.
- **Pendências reais:** instalar no Samsung SM-S911B/Android 16 e validar toque externo sem atravessar, 1,5 s/arraste, migração da grade, canais de áudio/Bluetooth, layout de Links e substituição de texto em aplicativos reais. Não declarar essas verificações físicas como concluídas antes do teste no aparelho.
<!-- ROTA_CERTA_0_1_186_STATUS_END -->

# Rota Certa — Estado do projeto

## 05/08/2026 — 0.1.185 (5460) — card individual do inDrive e contenção de acessibilidade

- **Branch:** `agent/fix-indrive-card-isolation-0.1.185`; **PR:** #56, empilhada sobre a 0.1.184.
- **Commit funcional validado:** `eb7c6b117e08e9dd91c48c27deb063ee5424c76f`.
- **Pedido do usuário:** corrigir a mistura de endereços entre ofertas simultâneas do inDrive, impedir alternância/pisca da bolinha e conter a falha ao abrir o seletor de arquivos sem alterar rota, decisão, permissões ou outros módulos.
- **Situação anterior:** a tela de lista do inDrive podia combinar embarque de uma oferta com destino de outra, alternando distâncias e repintando a bolinha. Um evento explícito do `com.google.android.documentsui` também podia reutilizar uma raiz antiga do inDrive e provocar `NullPointerException` durante a leitura da árvore de acessibilidade.
- **Causa:** a confirmação universal considerava o texto completo da tela com dois endereços, sem provar qual card individual estava aberto. O pacote externo era rejeitado tarde demais e propriedades de `AccessibilityNodeInfo` ainda eram lidas sem contenção individual completa.
- **Correção aplicada:**
  - no inDrive, somente o modal individual com `Pedido de viagem`, ação de aceite e `Fechar` autoriza a leitura decisória;
  - o texto é recortado nos limites do card aberto e exclui ofertas de fundo;
  - feed/lista sem card individual falha fechado, limpa cor e km anteriores e permanece aguardando;
  - a confirmação vale para acessibilidade e para a recuperação/OCR, sem caminho alternativo de autorização;
  - pacote externo explícito é rejeitado antes de consultar uma raiz possivelmente antiga;
  - leituras de raiz, pacote, texto, descrição, quantidade de filhos e filhos são isoladas com contenção por nó;
  - falha inesperada limpa também o visual da bolinha, impedindo estado persistido divergente.
- **Arquivos principais materializados:** `LiveRideAccessibilityService.kt`, `RideCardConfirmationPolicy0185.kt`, `ExplicitPackageTransitionPolicy0185.kt`, testes das duas políticas, `app/build.gradle.kts`, patch, script e workflow 0.1.185.
- **Fronteira protegida:** Manifest/permissões, `GpsAddressResolver`, `DecisionEngine`, Google Maps, parser genérico, radares, alertas direcionais, repositórios, fala, Casa/Alfinetes e catálogo da grade permaneceram inalterados por SHA-256.
- **Testes:** testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; pacote, versão, ZIP/DEX, assinatura v2, certificado, marcadores compilados e SHA-256 validados.
- **Workflow:** `Build Rota Certa 0.1.185`, run `31009074787`.
- **Artifact:** `rota-certa-0.1.185-indrive-card-isolation-validated`, ID `8932998388`, digest `bdf035e9d54aeef9be91e682348d0ea10fccbf327ac2290e3fefb1d1b729f6ca`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31009074787/artifacts/8932998388
- **Link permanente do APK assinado:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.185-card-individual-indrive-validado.apk`, 56075219 bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.185`; versionCode `5460`.
- **SHA-256 do APK:** `0288b3b99546b7179ac6034122936bdfe42305b5c356607844c3bcec92ef9f07`.
- **Pendências:** instalar no Samsung SM-S911B/Android 16 e validar com várias ofertas simultâneas do inDrive, abertura/fechamento do card individual, troca para DocumentsUI, saída do card, ausência de mistura de endereços, limpeza imediata de km/cor e ausência de pisca. O PR permanece em rascunho até essa validação real.

## 03/08/2026 — 0.1.184 (5450) — Home completa, atalhos por ação e direção rigorosa

- **Branch:** `agent/home-catalog-directional-shortcuts-0.1.184`; **PR:** #55, empilhada sobre a 0.1.183.
- **Commit funcional validado:** `c3560c62fa398d3b660d99c8f2f24389e58442d6`.
- **Pedido do usuário:** executar a evolução continuamente por etapas; colocar todos os módulos na Home; deixar a grade inicialmente vazia; permitir criar bolinhas independentes para cada ação real; restaurar o limite de dois toques totais; e impedir alertas/radares do sentido oposto.
- **Situação anterior:** a 0.1.183 armazenava módulos na grade e abria um menu contextual intermediário. A mesma bolinha agrupava ações distintas, como criar/restaurar backup ou limpar cache/área de transferência. O motor direcional aceitava uma única amostra de aproximação e tolerâncias angulares mais amplas.
- **Causa:** a persistência usava `shortcutId` de módulo, não identidade de ação. A Home não administrava cada ação individualmente e a grade continha uma bolinha `+`. No filtro direcional, aproximação podia incluir distância estável dentro da margem de erro do GPS.
- **Correção aplicada:**
  - criado catálogo tipado e fechado de ações internas, sem código, URI ou Intent arbitrária;
  - Home ampliada para 21 módulos, incluindo Permissões, Histórico, Frases e Rastreamento;
  - cada módulo oferece `Adicionar`/`Remover` para suas ações específicas;
  - ações independentes para criar/restaurar backup, limpar cache/clipboard, copiar texto, capturar card/pacote, valor, WhatsApp, alertas, locais, radar, destino, links, respostas e rastreamento;
  - instalações novas começam sem ações na grade; atualizações migram o JSON ou a antiga grade implícita;
  - limite rígido de 32 ações e bloqueio de duplicidade da mesma ação;
  - removida a bolinha `+` da grade; organização permanece na Home;
  - grade vazia abre a Home; grade configurada executa diretamente no toque seguinte;
  - alertas e radares exigem GPS recente/preciso, rumo, velocidade, alvo à frente e duas reduções reais de distância;
  - tolerâncias direcionais reduzidas e direção do deslocamento salva em novos alertas/radares manuais quando disponível;
  - alertas/radares antigos continuam compatíveis, mas ainda precisam estar à frente e em aproximação real.
- **Arquivos principais:** `ShortcutActionCatalog0184.kt`, `ShortcutActionHome0184.kt`, `ShortcutGridCustomization0179.kt`, `BubbleShortcutModule.kt`, `BubbleShortcutOverlayController.kt`, `MainActivity.kt`, `LiveRideAccessibilityService.kt`, `DirectionalAlertPolicy.kt`, `DirectionalProximityAlertEngine.kt`, `Models.kt`, testes, transformador, build e workflow 0.1.184.
- **Fronteira protegida:** Manifest/permissões, `DecisionEngine`, Google Maps, parser, geocodificação, repositórios, fala, overlay direcional e contrato do farol permaneceram protegidos por SHA-256.
- **Testes:** unitários e contratos aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; pacote, versão, DEX, assinatura v2, certificado e SHA-256 validados.
- **Workflow:** `Build Rota Certa 0.1.184`, run `30911279540`.
- **Artifact:** `rota-certa-0.1.184-home-action-catalog-directional-validated`, ID `8894266032`, digest `e62d3aad9b53929c598305e4eefd98c072b1e30fe4e74bdb91e0d4fa4e84ac60`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/30911279540/artifacts/8894266032
- **APK:** `rota-certa-0.1.184-home-acoes-direcao-validado.apk`, 56058835 bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.184`; versionCode `5450`.
- **SHA-256 do APK:** `1c95e8fad4bb7b7a189bf116ea98d75428789f82b237df5772332b3f0b6bb78c`.
- **Pendências:** instalação e teste no Samsung SM-S911B/Android 16. Validar migração da grade existente, instalação limpa vazia, todas as ações escolhidas, SAF de backup, rastreamento, direção em vias paralelas e ausência de regressão do farol durante cards reais.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
