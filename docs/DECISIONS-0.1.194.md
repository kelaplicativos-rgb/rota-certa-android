# Decisões técnicas — Rota Certa 0.1.194

Data: 09/08/2026

## O card continua pertencendo ao gate existente

**Decisão:** a 0.1.194 não cria nova regra de fusão de nós/cards. `FarolRealDeviceGate0188` e `FarolVisualPriority0189` permanecem inalterados.

**Motivo:** a 0.1.189 já segmenta o OCR espacialmente e impede endereços de cards distintos de autorizarem uma rota. Introduzir uma segunda recomposição no gate aumentaria o risco de combinar cards irmãos e violaria o contrato universal.

## Um segundo POI já reconhecido não é continuação automática da rua anterior

**Decisão:** `looksLikeContinuation` continua recompondo endereços realmente quebrados, mas não deve colar a linha seguinte ao endereço anterior quando a rua anterior já é numerada/completa e a nova linha já satisfaz as regras existentes de POI reconhecido.

**Preservações:** parêntese aberto, prefixo/localidade pendente e delimitador explícito continuam autorizando continuação. Assim `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` permanece um único endereço, enquanto `R. Carlos Vivaldi, 197 (...)` + `Parque do Carmo (...)` permanecem dois locais.

## POI desconhecido continua fail-closed

**Decisão:** a 0.1.194 não amplia `isRecognizedAddress` para aceitar qualquer estabelecimento/local apenas por conter bairro, UF ou CEP.

**Motivo:** a primeira tentativa dessa ampliação fez uma regressão histórica mudar a fonte de confiança de `marcadores_confirmados` para `acessibilidade_mais_ocr`. Além disso, o relatório da 99 contém fragmentos que não permitem provar com segurança que um nome de estabelecimento desconhecido é o destino final do mesmo card. Portanto, essa hipótese permanece bloqueada até haver evidência estrutural suficiente.

## Fronteiras preservadas

A 0.1.194 não altera:

- `FarolRealDeviceGate0188`;
- `FarolVisualPriority0189`;
- `FailedCardRecoveryEngine0161`;
- `DecisionEngine`;
- Google Maps/rota;
- `RideTextParser`;
- `LiveRideAccessibilityService`;
- Manifest/permissões;
- radares/alertas;
- contrato de cancelamento de gerações antigas;
- regra de que verde/vermelho só existem após destino final confirmado e rota real.

Aplicativos conhecidos e desconhecidos continuam passando pelo mesmo núcleo; nenhuma marca ou pacote recebe autoridade para decidir a cor.
