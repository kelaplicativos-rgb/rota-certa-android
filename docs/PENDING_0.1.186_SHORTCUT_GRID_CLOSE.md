# Rota Certa 0.1.186 — fechamento seguro da grade de atalhos

Data: 05/08/2026

## Estado

- Branch: `agent/fix-shortcut-grid-close-0.1.186`
- Base: `agent/fix-indrive-card-isolation-0.1.185`
- Commit-base: `db8bf6dd1bac65b0622a0b53bc1fb321b86a9908`
- PR: #58, em rascunho
- Pacote preservado: `br.com.mapeiaia.rotacerta`
- Versão planejada: `0.1.186`
- versionCode planejado: `5470`

## Pedido

Corrigir somente o fechamento da grade: tocar novamente na região da bolinha principal ou tocar fora da grade deve fechá-la sem executar atalhos e sem deixar o gesto atravessar para o aplicativo inferior.

## Situação anterior e causa

A grade usava `FLAG_NOT_TOUCH_MODAL` e `FLAG_WATCH_OUTSIDE_TOUCH`. Ela podia receber `ACTION_OUTSIDE` e fechar, mas o toque real ainda podia alcançar a janela do aplicativo abaixo. A janela ocupava apenas o painel e não era proprietária de toda a sequência de toque fora dele.

## Correção preparada

- camada transparente de tela inteira em `TYPE_ACCESSIBILITY_OVERLAY`;
- toque externo consumido integralmente;
- fechamento imediato apenas em `ACTION_DOWN`;
- toque sobre a região da bolinha central fecha apenas a grade;
- painel interno mantém posição, clique, arraste e descendentes acessíveis;
- camada usa `IMPORTANT_FOR_ACCESSIBILITY_NO`, sem esconder descendentes;
- remoção de `FLAG_NOT_TOUCH_MODAL` e `FLAG_WATCH_OUTSIDE_TOUCH` somente da janela da grade;
- fechamento não chama atalhos, `DecisionEngine`, parser, Maps ou qualquer função do farol.

## Arquivos materializados pelo patch

- `BubbleShortcutOverlayController.kt`
- `ShortcutGridBackdropContract0186Test.kt`
- `ShortcutGridBackdropPolicy0186Test.kt`

## Validação pré-build

- 10 testes novos aprovados em execução Kotlin isolada;
- 25 contratos e regressões existentes aprovados;
- 18 verificações estruturais aprovadas;
- `git diff --check` aprovado;
- patch aplicado sobre cópia limpa da 0.1.185;
- 14 arquivos protegidos permaneceram byte a byte inalterados;
- patch SHA-256: `7ddb46422bf27507083e1a361511373dd36aea4045a71bf7dbb2d00928e98a80`.

## Fronteira protegida

Manifest e permissões, `DecisionEngine`, `RideTextParser`, `UniversalScreenAddressParser`, OCR, Google Maps, Casa/Alfinetes, confirmação de cards, cores, quilômetros, alertas e radares não são alterados.

## Não executado nesta etapa

- testes Gradle Android;
- Android Lint;
- `assembleDebug`;
- assinatura e certificado;
- artifact;
- APK;
- release;
- validação física no Samsung SM-S911B/Android 16.

Nenhum APK deve ser publicado a partir desta preparação antes de autorização explícita e validação completa posterior. Após o build autorizado, este registro deverá ser consolidado em `docs/PROJECT_STATUS.md` e `docs/DECISIONS.md` com os dados reais do workflow, commit funcional, artifact, assinatura, SHA-256 e pendências de aparelho.
