# Decisões técnicas — Rota Certa 0.1.194

Data: 09/08/2026

## O card continua pertencendo ao gate existente

**Decisão:** a 0.1.194 não cria nova regra de fusão de nós/cards. `FarolRealDeviceGate0188` e `FarolVisualPriority0189` permanecem inalterados.

**Motivo:** a 0.1.189 já segmenta o OCR espacialmente e impede endereços de cards distintos de autorizarem uma rota. Introduzir uma segunda recomposição no gate aumentaria o risco de combinar cards irmãos e violaria o contrato universal. O menor reparo seguro é entregar ao gate os locais corretamente separados pelo parser.

## Um segundo local completo não é continuação automática do primeiro

**Decisão:** `looksLikeContinuation` deve continuar recompondo endereços realmente quebrados, mas não pode colar a linha seguinte ao endereço anterior quando a linha seguinte já representa um POI/local independente suficientemente confirmado.

**Preservações:** parêntese aberto, prefixo/localidade pendente e delimitador explícito continuam autorizando continuação. Assim endereços como `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` permanecem um único endereço, enquanto `R. Carlos Vivaldi, 197 (...)` + `Parque do Carmo (...)` permanecem dois locais.

## POI desconhecido não pode depender de lista de marcas

**Decisão:** o parser universal pode reconhecer um nome de local/estabelecimento que não comece por categoria conhecida somente quando o próprio texto trouxer evidência geográfica forte: UF, CEP ou marcador de localidade (bairro, jardim, vila, distrito, município, centro etc.) e ao menos duas palavras alfabéticas úteis.

**Motivo:** destinos reais podem ser estabelecimentos e POIs ainda desconhecidos. Uma lista fixa de categorias cria falso negativo. Exigir contexto geográfico forte mantém o comportamento fail-closed e evita transformar texto de status, preço, botão ou mensagem livre em destino.

## Fronteiras preservadas

A 0.1.194 não altera:

- `FarolRealDeviceGate0188`;
- `FarolVisualPriority0189`;
- `DecisionEngine`;
- Google Maps/rota;
- `RideTextParser`;
- `LiveRideAccessibilityService`;
- Manifest/permissões;
- radares/alertas;
- contrato de cancelamento de gerações antigas;
- regra de que verde/vermelho só existem após destino final confirmado e rota real.

Aplicativos conhecidos e desconhecidos continuam passando pelo mesmo núcleo; nenhuma marca ou pacote recebe autoridade para decidir a cor.
