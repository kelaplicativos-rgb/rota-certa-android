# Rota Certa 0.1.194 — estado da correção universal do limite entre destinos

Data: 09/08/2026

## Base e identidade

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Base: `agent/forensic-incident-monitor-0.1.193`
- Branch: `agent/fix-universal-card-confirmation-0.1.194`
- PR: #72
- Pacote: `br.com.mapeiaia.rotacerta`
- Versão alvo: `0.1.194`
- versionCode alvo: `5478`

## Evidência física recebida

Três aplicativos foram exercitados no Samsung SM-S911B / Android 16 com a 0.1.193:

1. inDrive: a acessibilidade coletou `R. Carlos Vivaldi, 197` e `Parque do Carmo`, mas o gate recebeu menos de dois locais confirmados e recusou a rota.
2. 99 Driver: a acessibilidade não expôs a oferta suficientemente; o OCR recuperou fragmentos/localidades, porém a evidência não permite relaxar com segurança o reconhecimento de POIs desconhecidos nem misturar blocos.
3. Uber Driver: em uma leitura o OCR formou card coerente e o farol chegou a verde com rota real, comprovando que decisão/rota/pintura funcionam quando o destino é confirmado.

## Causa comprovada no parser

`UniversalScreenAddressParser.looksLikeContinuation` pode anexar a linha seguinte à rua anterior apenas porque a nova linha contém UF/localidade. Isso é correto quando o endereço está realmente quebrado, mas é incorreto quando a nova linha já é um segundo POI reconhecido.

Caso real: `R. Carlos Vivaldi, 197 (...)` seguido por `Parque do Carmo (...)`. A segunda linha já é um POI reconhecido pelo parser, porém a regra de continuação podia uni-la à rua anterior, reduzindo dois locais visíveis a um único candidato.

## Run de validação que falhou

- Run: `31315421286`
- Job: `93249753095`
- Etapa: `Build and validate 0.1.194`
- Classificação: falha de testes, não infraestrutura/Gradle/Lint.
- Resultado: 400 testes executados, 5 falhas.
- Regressão antiga detectada: `FailedCardRecovery0161Test.labeledLocationsCreateAHighConfidenceLocalModel` mudou indevidamente de `marcadores_confirmados` para `acessibilidade_mais_ocr`.

A causa dessa regressão foi a primeira tentativa de ampliar o parser para POIs desconhecidos por contexto geográfico. Essa ampliação foi removida. Os testes de gate adicionados na primeira tentativa também foram removidos porque o gate e a segmentação 0.1.189 já possuem seus próprios contratos e permanecem inalterados.

## Correção 0.1.194 revisada

- alterar somente `UniversalScreenAddressParser.kt` no código funcional;
- quando a rua anterior já é um endereço numerado completo e a nova linha já é um POI reconhecido, manter a nova linha como segundo local;
- não separar quando o endereço anterior está realmente quebrado: parêntese aberto, prefixo pendente ou delimitador explícito continuam autorizando continuação;
- não adicionar reconhecimento genérico novo para `CCB`, estabelecimentos ou POIs desconhecidos;
- continuar fail-closed onde o relatório não confirma card/destino com segurança;
- nenhuma regra por marca ou pacote.

## Fronteiras protegidas por hash no build

- `FarolRealDeviceGate0188` e sua regra anti-mistura;
- `FarolVisualPriority0189`/segmentação OCR;
- `FailedCardRecoveryEngine0161`;
- `DecisionEngine`;
- Google Maps/rota;
- `RideTextParser`;
- `LiveRideAccessibilityService`;
- Manifest/permissões;
- radares/alertas.

## Regressões 0.1.194

- caso real `R. Carlos Vivaldi ...` + `Parque do Carmo (...)` deve produzir dois locais;
- endereço realmente quebrado `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` continua único;
- caso universal rua completa + `Terminal Central (...)` deve produzir dois locais;
- POI sem evidência geográfica suficiente continua rejeitado;
- duas ruas completas continuam dois endereços;
- toda a suíte antiga, inclusive `FailedCardRecovery0161Test`, deve permanecer verde.

## Estado

A correção foi estreitada após a primeira falha real do CI. O novo workflow deve comprovar pelo menos 396 testes sem falha, Android Lint, `clean assembleDebug`, pacote, versão/versionCode, assinatura APK v2, artifact e SHA-256. Validação física posterior continua obrigatória.
