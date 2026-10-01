# Catálogo de ações da grade — Rota Certa 0.1.184

A grade armazena somente identificadores de ações internas conhecidas. Não armazena código, comandos, URI arbitrária, pacote livre nem `Intent` fornecida pelo usuário.

## Rota e destino

- `route.open_module` — abrir módulo Rota.
- `destination.use_current_gps` — definir o GPS atual como destino de trabalho.
- `destination.open_module` — abrir módulo Destino.

## Alertas, locais e radares

- `alerts.create_here` — criar alerta no local atual e abrir editor do nome.
- `alerts.open_module` — abrir módulo Alertas.
- `places.save_here` — salvar local atual e abrir editor do nome.
- `places.open_module` — abrir módulo Locais.
- `radars.create_here` — criar radar no local atual.
- `radars.open_module` — abrir módulo Radares.

## Captura e leitura

- `screen.copy_visible_text` — copiar texto visível da tela.
- `screen.capture_app_card` — capturar aplicativo, pacote e card/tela atual.
- `screen.open_apps_cards` — abrir aplicativos autorizados e cards.
- `passenger.capture_value` — capturar valor/dados do passageiro.
- `passenger.open_module` — abrir módulo Valor.
- `trip.copy_confirmation` — gerar/copiar confirmação da viagem.
- `trip.open_templates` — abrir frases/modelos de mensagem.

## WhatsApp, respostas e links

- `whatsapp.capture_phone_open` — capturar telefone visível e abrir conversa no WhatsApp.
- `whatsapp.open` — abrir WhatsApp.
- `replies.create` — criar resposta rápida.
- `replies.open_module` — abrir módulo Respostas.
- `links.open_primary` — abrir link principal.
- `links.open_module` — abrir módulo Links.

## Backup

- `backup.create` — abrir criação de arquivo de backup.
- `backup.restore` — abrir seleção de arquivo para restauração.
- `backup.open_module` — abrir módulo Backup.

## Limpeza

- `clean.clipboard` — limpar área de transferência.
- `clean.cache` — limpar somente o cache do Rota Certa.
- `clean.open_module` — abrir módulo Limpar.

## Controle e ferramentas

- `finance.open_module` — abrir Financeiro.
- `tracking.open_module` — abrir Rastreamento de trabalho.
- `diagnostic.export` — exportar diagnóstico manual.
- `diagnostic.open_module` — abrir Diagnóstico.
- `appearance.open_module` — abrir Aparência.
- `permissions.open_module` — abrir Permissões.
- `history.open_module` — abrir Histórico.
- `app.stop` — encerrar/desativar o Rota Certa.

## Extensão

Novas funções podem virar atalhos quando receberem:

1. identificador estável;
2. rótulo e ícone;
3. grupo/módulo da Home;
4. executor interno explícito;
5. testes de permissão, cancelamento, duplicidade e falha;
6. garantia de que não interfere no farol.
