<!-- ALERT_POPUP_POST_PASS_HOLD_0192_CI_PENDING_DEVICE_START -->
## 08/08/2026 — 0.1.192: pop-up de radar/alerta preserva os 3 s reais após a passagem

- **Branch:** `agent/fix-alert-popup-hold-0.1.192`; **PR:** #70; **base:** `agent/proximity-alerts-no-direction-0.1.191`.
- **Commit funcional validado:** `1a337fc97dc0d52d3e8efce180797a536df9782d`; **workflow run:** `31265686664`.
- **Versão:** `0.1.192`; **versionCode:** `5476`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido / evidência física:** na 0.1.191 o pop-up dos radares e alertas fechava rápido demais, apesar do contrato documentado de 3.000 ms.
- **Causa:** `DirectionalAlertOverlayController.showOrUpdate()` agendava o fechamento em 3.000 ms quando `visual.shouldClose=true`, porém a avaliação seguinte do motor podia retornar `visual=null` porque o alvo já estava `passed`; o serviço então chamava `directionalAlertOverlayChecklist5.hide()`, e `hide()` cancelava `pendingClose`, removendo a janela imediatamente.
- **Correção:** ausência normal de visual vinda do motor usa `hideFromEngineIdle()`. Se existe `pendingClose` pós-passagem, o overlay permanece até o callback de 3.000 ms. Fechamentos explícitos continuam usando `hide()` e permanecem imediatos.
- **Preservado:** botão `Fechar`, exclusão, alerta desligado, ausência prolongada de GPS, destruição do serviço e novo alvo continuam capazes de fechar/substituir imediatamente; radar continua com no máximo 1 fala por aproximação, alerta manual até 2; regra sem filtro de sentido da 0.1.191 permanece intacta.
- **Fronteira:** `DirectionalAlertPolicy`, `DirectionalProximityAlertEngine`, `DecisionEngine`, OCR, parser, rota, `RadarImport`, Manifest e permissões permaneceram byte a byte protegidos durante a transformação 0.1.192.
- **Testes finais:** `tests=382`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Artifact:** `rota-certa-0.1.192-alert-popup-hold-validated`, ID `9024417340`, digest `d462d34d345a2fe259ba0c0bba1fed57010ea0b59bdb43ca67d4882b5175a6fa`.
- **APK:** `rota-certa-0.1.192-popup-radar-alerta-3s-validado-em-ci.apk`, `56157139` bytes; assinatura APK v2 conferida.
- **SHA-256 do APK:** `348c676f722583c91b0388bbd1dbfa02ae3065c42f4f91e0fa7272e1fb64e137`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.192/rota-certa-0.1.192-candidate.apk`.
- **Pendência física:** cronometrar radar importado e alerta manual no aparelho real, confirmando permanência aproximada de 3 s após a passagem, fechamento manual imediato, ausência de reaparecimento na mesma passagem e rearme após sair da zona.
<!-- ALERT_POPUP_POST_PASS_HOLD_0192_CI_PENDING_DEVICE_END -->

<!-- PROXIMITY_NO_DIRECTION_0191_CI_PENDING_DEVICE_START -->
## 07/08/2026 — 0.1.191: radares e alertas avisam por aproximação, sem filtro de sentido

- **Branch:** `agent/proximity-alerts-no-direction-0.1.191`; **PR:** #69; **base:** `agent/alert-popup-lifecycle-0.1.190`.
- **Commit funcional validado:** `474f995ed9c02f4b00fcfe55f48cb5a9484b75db`; **workflow run:** `31258805798`.
- **Versão:** `0.1.191`; **versionCode:** `5475`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** retirar a exigência de o veículo estar no mesmo sentido do alerta/radar, pois heading e direção cadastrada estavam atrasando ou bloqueando avisos; alertar enquanto a distância confirma aproximação, independentemente do sentido.
- **Situação anterior / causa:** `DirectionalProximityAlertEngine` exigia heading utilizável, `isTargetAhead(...)` e, para radar importado, `radarDirectionMatches(...)`. A passagem também dependia de o alvo ficar geometricamente atrás do heading. Leituras de rumo ausentes, instáveis ou divergentes podiam impedir o aviso mesmo com distância diminuindo.
- **Correção:** elegibilidade de radar e alerta passa a usar GPS recente/preciso + distância dentro do limite + tendência de aproximação. Heading, azimute e direção cadastrada do radar deixam de autorizar ou bloquear o aviso. A passagem é reconhecida quando a distância cresce de forma consistente depois do mínimo observado.
- **Preservado:** radar fala no máximo 1 vez por aproximação; alerta manual até 2; reset após sair da zona; `Fechar` silencia o ponto apenas na passagem atual; pop-up continua por 3 s após ultrapassar; farol, OCR, rota, Manifest e permissões não foram alterados.
- **Testes finais:** `tests=378`; `failures=0`; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Artifact:** `rota-certa-0.1.191-proximity-no-direction-validated`, ID `9022428483`, digest `5bd0e44927de1c7c2182a470724d616b1d11fb0ab4c03b693898067e28b34959`.
- **APK:** `rota-certa-0.1.191-alertas-sem-filtro-sentido-validado-em-ci.apk`, `56157135` bytes; assinatura APK v2 conferida.
- **SHA-256 do APK:** `543395feaa02018c3e7e13854a16e3c505005c53b7f8d0ea7e5041766b13b075`.
- **Candidato público verificado byte a byte:** `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.191/rota-certa-0.1.191-candidate.apk`.
- **Pendência física:** testar radar importado e alerta manual em aproximações nos dois sentidos, heading instável/ausente, trânsito lento, passagem do ponto, fechamento manual e nova aproximação futura; confirmar aviso oportuno sem duplicação.
<!-- PROXIMITY_NO_DIRECTION_0191_CI_PENDING_DEVICE_END -->

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

## 03/08/2026 — 0.1.183 (5440) — menu contextual por bolinha da grade

- **Branch:** `agent/contextual-shortcut-menu-0.1.183`; **PR:** empilhada sobre a 0.1.182.
- **Commit funcional validado:** `06129186a41b1aa85845138e6a4c9521b1f3a081`.
- **Pedido do usuário:** ao tocar numa bolinha da grade, abrir um pop-up específico com ações rápidas relacionadas ao recurso e uma opção para abrir o módulo completo; exemplos: criar alerta, criar radar, usar GPS como destino, capturar agora e opções separadas de limpeza.
- **Situação anterior:** a 0.1.182 executava imediatamente a ação principal de cada atalho. Isso era rápido, porém não permitia escolher entre a tarefa imediata e a abertura do módulo, e podia executar uma ação por engano.
- **Causa:** o caminho direto da 0.1.182 normalizava qualquer toque para `PRIMARY_ACTION` e ignorava o menu contextual herdado, que era genérico e não oferecia botões úteis por recurso.
- **Correção aplicada:**
  - o toque na bolinha principal continua abrindo a grade sem atraso;
  - o toque seguinte numa bolinha abre imediatamente um menu contextual leve em `TYPE_ACCESSIBILITY_OVERLAY`;
  - Alertas: `Criar alerta aqui` e `Abrir módulo Alertas`;
  - Radares: `Criar radar neste local` e `Abrir módulo Radares`;
  - Destino: `Usar localização atual como destino` e `Abrir módulo Destino`;
  - Locais: `Salvar localização atual` e `Abrir módulo Locais`;
  - Capturar: `Capturar aplicativo e tela agora` e `Abrir aplicativos e cards`;
  - Respostas: `Criar resposta rápida` e `Abrir Respostas`;
  - Limpar: `Limpar área de transferência`, `Limpar cache do Rota Certa` e `Abrir módulo Limpar`;
  - a limpeza de cache é limitada ao cache do próprio aplicativo e roda fora da thread principal;
  - toque fora/Fechar não executa nada e arraste continua cancelando o clique;
  - Alertas e Locais mantêm seus editores reais em sobreposição sem trocar automaticamente de aplicativo.
- **Arquivos principais alterados/materializados:** `LiveRideAccessibilityService.kt`, novo `ShortcutContextMenuPolicy0183.kt`, testes de política/contrato, `scripts/fix_contextual_shortcuts_0183.py`, script de build e workflow 0.1.183.
- **Fronteira protegida:** Manifest, permissões, `BubbleShortcutOverlayController`, catálogo de atalhos, Home, `DecisionEngine`, Google Maps, parser, OCR, confirmação real de card, Casa/Alfinetes, farol, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por SHA-256.
- **Testes e validações:** testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/DEX, pacote, versão, versionCode, marcadores compilados, assinatura v2 e certificado validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.183`, run `30862120893`.
- **Artifact:** `rota-certa-0.1.183-contextual-shortcut-menu-validated`, ID `8875362563`, digest `cf4f4f9eec19d5acdbdcdd14523d00222cdbe5ecfc84b60f30663aa6d6814d68`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/30862120893/artifacts/8875362563
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.183`, versionCode `5440`.
- **APK:** `rota-certa-0.1.183-menu-contextual-atalhos-validado.apk`, 56058831 bytes.
- **SHA-256 do APK:** `d35964849443e3e74d5fd1aacafdb222d3f6b7fcf7dffa195675688059bfec33`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado de depuração do Rota Certa validado pelo build.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar todas as bolinhas da grade. Confirmar especialmente Alertas, Radares, Destino, Capturar e Limpar; confirmar que o cache apagado é somente o do Rota Certa, que Fechar não executa ações e que o farol não pisca nem perde a decisão durante ofertas reais.

## 03/08/2026 — 0.1.182 (5430) — grade direta em no máximo dois toques

- **Branch:** `agent/direct-shortcut-grid-0.1.182`; **PR:** #53, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `4094b598dab6374067ef1f480bf3f70d7684ccab`.
- **Pedido do usuário:** alinhar a grade para ser realmente um conjunto de atalhos: no máximo um toque na bolinha principal para abrir a grade e outro toque na bolinha escolhida para executar sua ação.
- **Situação anterior:** a 0.1.180 aguardava uma janela de 900 ms para distinguir toque triplo e classificava também o gesto de 1,5 segundo. A 0.1.181 ainda podia abrir um painel genérico antes da função principal. Na prática, o toque rápido tinha atraso e alguns recursos exigiam uma terceira interação.
- **Causa:** reconhecimento de edição e navegação completa estavam misturados ao caminho crítico de execução rápida. A grade acumulava `tapCount0180`, finalizador atrasado, toque de 1,5 segundo e despacho `overlay first`, contrariando o papel de atalho.
- **Correção aplicada:**
  - `ACTION_UP` válido executa imediatamente uma única ação, sem aguardar toque triplo;
  - removida a classificação de 1,5 segundo do toque nas bolinhas da grade;
  - preferências antigas de gesto são normalizadas para a ação principal e não conseguem desativar o atalho;
  - a bolinha `+` permanece como caminho único para editar nome, recurso, ícone, ordem e visibilidade;
  - atalhos comuns não param mais no painel informativo genérico e seguem diretamente para sua ação/módulo real;
  - `Salvar local` e `Alertas` preservam o pop-up real da própria ação sobre a tela atual;
  - nenhum polling, serviço, observador contínuo ou alocação recorrente foi adicionado.
- **Arquivos principais alterados/materializados:** `BubbleShortcutOverlayController.kt`, `LiveRideAccessibilityService.kt`, `MainActivity.kt`, novo `ShortcutDirectTapPolicy0182.kt`, testes de política/contrato, `scripts/fix_direct_shortcuts_0182.py`, compatibilidade da base 0.1.181, script de build e workflow 0.1.182.
- **Falhas intermediárias localizadas:** run `30846355142`, job `91795491937`, interrompido porque o transformador procurava o nome antigo do contrato de cinco segundos, enquanto a base já usava toque triplo; run `30848470633`, job `91802437114`, avançou à compilação dos testes 0.1.182 e encontrou duas aspas internas inválidas em asserções Kotlin geradas. Ambos os pontos foram corrigidos somente nos transformadores/contratos, sem mascarar testes, sem erro de memória e sem alterar a lógica funcional do aplicativo.
- **Fronteira protegida:** Manifest e permissões, geocodificação, `DecisionEngine`, Google Maps, parser, OCR, confirmação real de card, Casa/Alfinetes, farol, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por SHA-256.
- **Testes e validações:** todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/DEX, pacote, versão, versionCode, marcadores compilados, assinatura v2 e certificado validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.182`, run `30850540090`, job `91809194366`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.182-direct-shortcut-grid-validated`, ID `8871170435`, 33732084 bytes, digest `sha256:470a7ed0cdaa599a70ae578bc2b56b7c6e30110a52e861bd478efc01852f78d7`.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.182`, versionCode `5430`.
- **APK:** `rota-certa-0.1.182-grade-atalhos-dois-toques-validado.apk`, 56058835 bytes.
- **SHA-256 do APK:** `1d654096731fec7e8fb0c66500066305b0ee6658ebf0c101352cf239d1f401f7`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar a sequência bolinha principal → cada atalho, confirmando execução com exatamente dois toques totais, ausência do atraso de 900 ms, ausência de ação por arraste, funcionamento da bolinha `+`, pop-ups reais de Local/Alerta e ausência de regressão do farol durante ofertas reais. A PR #53 permanece em rascunho até essa validação prática.

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
