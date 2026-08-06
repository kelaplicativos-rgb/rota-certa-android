# Estabilização do GitHub Actions — fase 1

Data: 06/08/2026

## Referências

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Branch: `agent/ci-stabilization-phase1-0.1.187`
- Base: `agent/fix-farol-runtime-0.1.187`
- Base SHA: `cb3abe04125e7d33c6730b77b1da3704dc7be11d`
- Commit da remoção: `35756938301888e605a9f3be9cd771596b0fe4cd`
- Versão preservada: `0.1.187`
- versionCode preservado: `5471`
- Pacote preservado: `br.com.mapeiaia.rotacerta`

## Pedido

Eliminar progressivamente os erros exibidos no GitHub Actions sem misturar alterações no aplicativo e sem declarar sucesso antes da validação completa.

## Situação anterior

O relatório `Workflow runs · kelaplicativos-rgb_rota-certa-android (29).mht` mostrava diversas execuções vermelhas associadas a `.github/workflows/apply-saved-place-naming-flow.yml`. As execuções mais recentes desse arquivo não possuíam jobs, etapas ou logs de código Android.

O workflow removido tinha 315 linhas, aplicava substituições textuais diretamente em arquivos Kotlin, tentava apagar o próprio arquivo, fazia commit e push a partir do runner e depois iniciava outra compilação. Esse comportamento era incompatível com a validação isolada da fase 4 e produzia ruído em branches que carregavam o arquivo.

Separadamente, o build Android `31120432822` falhou em `Set up job` porque o GitHub retornou timeout e `Service Unavailable` ao resolver downloads de Marketplace Actions. Nenhum checkout, teste, Lint ou Gradle foi executado nessa falha.

## Correção desta fase

- removido `.github/workflows/apply-saved-place-naming-flow.yml` da linha de validação 0.1.187;
- preservado integralmente o código Kotlin, Manifest, permissões, patches, testes, scripts de build, parser, rota, `DecisionEngine`, Casa/Alfinetes, radares e alertas;
- mantida a fase 4 no mesmo conteúdo funcional;
- isolada a limpeza de CI em branch própria e rastreável.

## Verificações

- comparação contra a base: branch está um commit à frente e zero atrás;
- único arquivo funcionalmente alterado antes deste registro: remoção do workflow obsoleto;
- alterações no aplicativo Android: nenhuma;
- testes Android, Lint e assembleDebug: não executados nesta fase, pois o escopo é apenas remover a fonte de falhas falsas;
- artifact e APK: não gerados nesta fase;
- link permanente continua apontando para o último APK comprovadamente validado da fase 3;
- SHA-256 do APK da fase 4: ainda indisponível.

## Próxima fase autorizável

Criar uma validação resiliente da fase 4 sem dependência de `actions/checkout`, `actions/setup-java`, `android-actions/setup-android` ou `gradle/actions/setup-gradle` no caminho inicial. O pipeline deverá usar checkout por `git`, ferramentas preinstaladas verificadas explicitamente, falha fechada e publicação do APK somente depois de testes, Lint, build, assinatura e hash aprovados.

## Riscos e pendências

- a remoção impede novas falhas desse workflow, mas não apaga o histórico vermelho já registrado;
- a indisponibilidade externa do GitHub pode continuar afetando workflows que ainda usam Marketplace Actions;
- nenhuma correção deve ser publicada no link `latest` antes da validação Android completa da fase 4;
- validação física no Samsung SM-S911B/Android 16 permanece obrigatória depois do APK validado.
