# Rota Certa 0.1.184 — plano de implementação contínua

Data: 03/08/2026
Base protegida: `agent/contextual-shortcut-menu-0.1.183` / commit `eea3f1a1e11704c78b65a0a643851cd6518875cc`

## Objetivo corrigido

A unidade da grade flutuante será uma **ação executável específica**, não apenas um módulo.

Exemplos de atalhos independentes:

- limpar cache do Rota Certa;
- limpar área de transferência;
- copiar texto visível da tela;
- capturar valor da corrida/passagem;
- criar alerta imediatamente;
- definir destino de trabalho pelo GPS atual;
- salvar local atual e abrir somente o editor de nome;
- criar backup;
- restaurar backup;
- capturar telefone visível e abrir WhatsApp;
- capturar aplicativo, pacote e card atual;
- abrir um módulo completo quando essa for a ação desejada.

O usuário poderá adicionar qualquer ação registrada no catálogo seguro, respeitando o limite da grade. A grade não aceitará comandos arbitrários, código, intents livres ou acesso irrestrito a recursos externos: cada ação precisa existir e ser validada no catálogo interno do Rota Certa.

## Regras da Home

1. A Home exibirá o catálogo completo de módulos e ações.
2. Dentro de cada módulo, cada ação terá seu próprio botão `Adicionar à grade` ou `Remover da grade`.
3. A mesma ação não será duplicada acidentalmente; o usuário poderá editar nome, ícone e ordem depois de adicioná-la.
4. Ações diferentes do mesmo módulo poderão coexistir, como `Criar backup` e `Restaurar backup`.
5. Abrir o módulo completo também poderá ser adicionado como atalho independente.

## Regras da grade

1. Instalações novas começam sem ações escolhidas.
2. Atualizações preservam o JSON de grade existente.
3. A bolinha principal abre a grade quando houver ações.
4. Quando a grade estiver vazia, a bolinha principal abre a Home para seleção.
5. Um toque na ação escolhida executa imediatamente, sem menu contextual intermediário.
6. Arrastar continua cancelando o clique.
7. A configuração ocorre pela Home; a grade não terá botão `+` obrigatório.
8. Limite rígido de 32 ações ativas.

## Alertas e radares por sentido

- exigir GPS recente, preciso, velocidade mínima e rumo confiável;
- exigir alvo à frente do veículo;
- exigir aproximação real por amostras de distância;
- radar com direção informada precisa coincidir explicitamente com o sentido atual;
- radar bidirecional aceita apenas os dois sentidos declarados;
- radar sem direção permanece conservador e depende de alvo à frente + aproximação;
- alerta manual não fala quando estiver atrás ou no sentido oposto;
- depois de ultrapassado, permanecer silenciado até sair da zona.

## Etapas

- Etapa 1 — congelar a 0.1.183 e registrar fronteiras protegidas.
- Etapa 2 — criar branch, versão 0.1.184 e workflow cumulativo.
- Etapa 3 — criar catálogo tipado de ações rápidas seguras.
- Etapa 4 — endurecer direção, rumo, aproximação e qualidade do GPS.
- Etapa 5 — integrar o catálogo completo à Home.
- Etapa 6 — grade vazia em instalação nova e preservação do JSON existente.
- Etapa 7 — adicionar/remover ações individualmente por módulo.
- Etapa 8 — execução direta em dois toques totais e rota para Home quando vazia.
- Etapa 9 — testes unitários, contratos, lint, assembleDebug e validação do APK.
- Etapa 10 — documentação, artifact, assinatura, SHA-256 e validação em aparelho real.

## Fronteiras protegidas

- `AndroidManifest.xml` e permissões;
- `DecisionEngine.kt` e contrato do farol;
- `GoogleMapsService.kt`, geocodificação e rota;
- `RideTextParser.kt`, OCR e confirmação de card cadastrado;
- cancelamento de resultados antigos e estabilidade visual da bolinha;
- armazenamento financeiro e rastreamento, salvo integração explícita de atalhos;
- nenhuma execução arbitrária fora do catálogo tipado.
