# Rota Certa 0.1.194 — correção universal do limite entre destinos validada em CI

Data: 09/08/2026

## Base e identidade

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Base: `agent/forensic-incident-monitor-0.1.193`
- Branch: `agent/fix-universal-card-confirmation-0.1.194`
- PR: #72 (rascunho; validação física ainda obrigatória)
- Pacote: `br.com.mapeiaia.rotacerta`
- versionName: `0.1.194`
- versionCode: `5478`
- Head funcional validado: `9f56f50a4ca302bbfb4f46a7851c1eee398edae8`
- Workflow final: `31328995714` (run #20)
- Job: `93284039823`

## Evidência física que motivou a correção

A 0.1.193 foi exercitada no Samsung SM-S911B / Android 16 em três aplicativos reais:

1. **inDrive:** a acessibilidade expôs `R. Carlos Vivaldi, 197 (...)` e `Parque do Carmo (Jardim Nossa Senhora do Carmo, ...)`, mas o parser/gate terminava com menos de dois locais confirmados e recusava a rota.
2. **99 Driver:** a acessibilidade foi insuficiente e o OCR trouxe fragmentos/truncamentos. Não existe evidência segura para inferir o destino final desse caso; ele permanece deliberadamente fail-closed quando a estrutura não basta.
3. **Uber Driver:** o OCR conseguiu formar um card coerente e o farol chegou a uma decisão real, comprovando que rota, `DecisionEngine` e renderização funcionam quando o destino final é confirmado.

## Causa comprovada

`UniversalScreenAddressParser.looksLikeContinuation` podia anexar uma segunda linha de local à rua anterior apenas porque essa linha continha sinais de localidade/UF. Isso é correto para endereços realmente quebrados, mas incorreto quando a segunda linha já representa outro POI reconhecido.

No caso real do inDrive, `R. Carlos Vivaldi, 197 (...)` seguido por `Parque do Carmo (Jardim Nossa Senhora do Carmo, ...)` podia virar um único candidato em vez de origem + destino.

## Correção funcional final

A mudança funcional da 0.1.194 ficou limitada a `UniversalScreenAddressParser.kt`:

- rua numerada completa seguida de POI de categoria forte já reconhecido pode permanecer como segundo local;
- o termo ambíguo `Parque` só é separado quando a própria linha contém localidade interna explícita entre parênteses, como `Parque do Carmo (Jardim ...)`;
- parêntese aberto, prefixo/localidade pendente e delimitadores explícitos continuam recompondo endereços realmente quebrados;
- `Rua X, 123` + `Parque Sao Jorge, Sao Paulo - SP` continua um único endereço;
- `Rua X, 123` + `Parque Sao Jorge (Sao Paulo - SP)` continua um único endereço;
- POI sem evidência geográfica suficiente continua fail-closed;
- nenhuma marca ou pacote foi adicionado ao parser.

## Tentativas bloqueadas corretamente

- Uma tentativa inicial mais ampla aceitou POIs desconhecidos e tentou recomposição adicional; o CI executou 400 testes e encontrou 5 falhas, incluindo regressão em `FailedCardRecovery0161Test`. A abordagem foi removida; os testes não foram relaxados.
- O gerador Python teve os escapes Kotlin `\\b`/`\\s` endurecidos e o build passou a verificar a fonte materializada para impedir consumo silencioso de barras ou caractere backspace acidental.
- O run #18 falhou porque a proteção de integridade apontava para `FailedCardRecoveryEngine0161.kt`; o arquivo real é `FailedCardRecovery0161.kt`. Foi corrigida somente a guarda de CI.
- O run #19 executou 399 testes e teve apenas 1 falha no novo teste de integração. O gate já autorizava corretamente o card; a expectativa comparava a string bruta com a forma normalizada por `DestinationAddressIdentityPolicy.cleanDisplayAddress()`. Foi corrigido somente o teste, sem alteração funcional.

## Fronteiras protegidas por SHA-256

O build final comprovou inalterados durante a transformação 0.1.194:

- `AndroidManifest.xml` / permissões;
- `DecisionEngine.kt`;
- `RideTextParser.kt`;
- `FarolRealDeviceGate0188.kt`;
- `FarolVisualPriority0189.kt`;
- `FailedCardRecovery0161.kt`;
- `GoogleMapsService.kt` / motor de rota;
- `GpsAddressResolver.kt`;
- `LiveRideAccessibilityService.kt`;
- `ForensicIncidentMonitor0193.kt`;
- subsistema de radares/alertas protegido pelo build.

## Regressões exigidas e aprovadas

- caso real `R. Carlos Vivaldi ...` + `Parque do Carmo (...)` → dois locais no parser;
- o mesmo caso dentro de um único bloco coerente atravessa `FarolRealDeviceGate0188` e autoriza o destino normalizado;
- endereço quebrado `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` → um endereço;
- rua completa + `Terminal Central (...)` → dois locais;
- rua completa + `Parque Sao Jorge, Sao Paulo - SP` → um endereço;
- rua completa + `Parque Sao Jorge (Sao Paulo - SP)` → um endereço;
- POI sem evidência geográfica suficiente → rejeitado;
- duas ruas completas → continuam dois endereços;
- regressões históricas, inclusive recovery, permanecem verdes.

## Validação final de CI

- Workflow: `31328995714` — **sucesso**
- Job: `93284039823` — **sucesso**
- Testes: **399**
- Falhas: **0**
- Android Lint: aprovado
- `clean assembleDebug`: aprovado
- Artifact: `rota-certa-0.1.194-universal-card-confirmation-validated`
- Artifact ID: `9042805214`
- Artifact digest: `sha256:e5b031f60aaf3dd06c9d5f12d5e7ae5fcd88d1018fa9862bd307f84c2ba1f0ff`
- APK: `rota-certa-0.1.194-correcao-universal-cards-validada-em-ci.apk`
- Tamanho: `56.157.135` bytes
- APK SHA-256: `9de961117a1421e5009d73281689e0f0b61ad63915ec7bca1b49054ebc586961`
- Assinatura: APK Signature Scheme v2 = true; 1 signatário; RSA 2048 bits
- Certificado: `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`
- Certificado SHA-256: `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`
- Pacote conferido pelo `aapt`: `br.com.mapeiaia.rotacerta`
- versionName conferido: `0.1.194`
- versionCode conferido: `5478`
- Candidato público publicado e verificado byte a byte: `https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.194/rota-certa-0.1.194-candidate.apk`

## Estado e pendência física

**CI aprovado; candidato pendente de validação em aparelho real.** A PR #72 deve permanecer em rascunho e não deve ser promovida/mesclada apenas pelo sucesso do CI.

Validação física obrigatória:

1. repetir no inDrive o caso de origem de rua + destino POI, especialmente `Parque do Carmo`, confirmando leitura, rota e cor sem pisca/estado antigo;
2. repetir um card conhecido do Uber como regressão positiva, confirmando que OCR/rota/decisão continuam funcionando;
3. testar novamente a 99; se a tela continuar expondo somente fragmentos/truncamentos sem destino final comprovável, o resultado correto continua sendo amarelo/fail-closed — não adivinhar endereço;
4. verificar mudança/fechamento de card e geração antiga para garantir que nenhum km/cor anterior reapareça.
