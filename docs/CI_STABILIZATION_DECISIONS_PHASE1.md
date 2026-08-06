# Decisões técnicas — estabilização do GitHub Actions, fase 1

Data: 06/08/2026

## Workflow autodestrutivo não participa da validação do produto

- Workflows antigos que alteram Kotlin por substituição textual, removem a si próprios, fazem push a partir do runner e iniciam compilação não podem permanecer na cadeia cumulativa do Rota Certa.
- `.github/workflows/apply-saved-place-naming-flow.yml` foi removido da branch de estabilização 0.1.187.
- A remoção não restaura nem altera a função antiga de nomeação; apenas elimina uma automação obsoleta e independente da fase 4.
- Mudanças no aplicativo devem existir como commits e patches rastreáveis, nunca como transformação oculta executada durante o CI.

## Falha de infraestrutura não é falha do aplicativo

- Uma execução que termina em `Set up job` antes do checkout não comprova erro de Kotlin, teste, Lint, Gradle ou APK.
- Timeout ou `Service Unavailable` ao baixar Marketplace Actions deve ser classificado como falha externa de infraestrutura.
- O pipeline não pode declarar sucesso por ter iniciado, nem declarar erro de código sem chegar ao código.

## Próximo desenho do pipeline

- O caminho inicial da validação da fase 4 deverá ser independente de Marketplace Actions.
- Checkout deve ser feito com `git` e SHA exato.
- Java, Android SDK, Python e Gradle devem ser detectados e suas versões registradas antes do build.
- Ausência ou versão incompatível deve falhar com diagnóstico explícito.
- O build deverá manter testes unitários e de contrato, Android Lint, `clean assembleDebug`, pacote, versão, versionCode, ZIP/DEX, assinatura e SHA-256.
- O asset permanente `rota-certa-latest.apk` só pode ser substituído depois de todas as validações aprovadas e de o download público retornar o mesmo hash.

## Fronteira protegida

Esta fase não altera `DecisionEngine`, parser universal, confirmação de destino, Google Maps, raio, Casa/Alfinetes, OCR, acessibilidade, bolinha, atalhos, alertas, radares, Manifest ou permissões.
