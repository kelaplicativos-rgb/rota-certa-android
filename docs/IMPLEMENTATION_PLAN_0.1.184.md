# Rota Certa 0.1.184 — plano de implementação contínua

Data: 03/08/2026
Base protegida: `agent/contextual-shortcut-menu-0.1.183` / commit `eea3f1a1e11704c78b65a0a643851cd6518875cc`

## Objetivo

1. Manter todos os módulos e recursos no catálogo da Home.
2. Permitir adicionar ou remover cada módulo da grade diretamente na Home.
3. Fazer instalações novas iniciarem sem atalhos funcionais na grade.
4. Preservar grades já persistidas pelo usuário.
5. Remover o menu contextual intermediário e restaurar execução direta no toque do atalho.
6. Não exibir o botão `+` dentro da grade; configuração ocorre pela Home.
7. Quando a grade estiver vazia, o toque na bolinha principal abre a Home.
8. Reforçar o filtro direcional de alertas e radares sem alterar o farol de corridas.

## Etapas

- Etapa 1 — congelar a 0.1.183 e registrar fronteiras protegidas.
- Etapa 2 — criar branch, versão 0.1.184 e workflow cumulativo.
- Etapa 3 — endurecer direção, rumo, aproximação e qualidade do GPS.
- Etapa 4 — integrar a persistência da grade ao catálogo completo da Home.
- Etapa 5 — grade vazia em instalação nova e preservação do JSON existente.
- Etapa 6 — adicionar/remover atalhos por módulo, sem duplicidade acidental.
- Etapa 7 — execução direta em dois toques totais e rota para Home quando vazia.
- Etapa 8 — testes unitários, contratos, lint, assembleDebug e validação do APK.
- Etapa 9 — documentação, artifact, assinatura, SHA-256 e validação em aparelho real.

## Fronteiras protegidas

- `AndroidManifest.xml` e permissões;
- `DecisionEngine.kt` e contrato do farol;
- `GoogleMapsService.kt`, geocodificação e rota;
- `RideTextParser.kt`, OCR e confirmação de card cadastrado;
- cancelamento de resultados antigos e estabilidade visual da bolinha;
- backup, financeiro, rastreamento, respostas e demais módulos não relacionados.
