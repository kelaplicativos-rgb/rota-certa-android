# Decisões técnicas — Rota Certa 0.1.194

Data: 09/08/2026

## Fragmentos separados podem formar um card somente sob a mesma autoridade visual

**Decisão:** quando a acessibilidade entrega origem, parada e destino em nós separados, o gate universal pode reconstruir o card superior atual somente a partir do componente conectado à âncora visual superior.

A conexão exige simultaneamente:

- mesma fonte `Accessibility`;
- mesma camada/janela já escolhida pelo gate;
- relação hierárquica próxima entre os nós;
- proximidade espacial e sobreposição horizontal suficientes;
- pelo menos dois locais distintos depois da recomposição.

**Proibição:** um card inferior, outro ramo estrutural ou outro bloco visual não pode fornecer o endereço que falta ao card superior. Se a âncora superior não conseguir formar um card coerente com seus próprios fragmentos, a decisão continua bloqueada.

## POI desconhecido não pode depender de lista de marcas

**Decisão:** o parser universal pode reconhecer um nome de local/estabelecimento que não comece por categoria conhecida somente quando o próprio texto trouxer evidência geográfica forte: UF, CEP ou marcador de localidade (bairro, jardim, vila, distrito, município, centro etc.) e ao menos duas palavras alfabéticas úteis.

**Motivo:** destinos reais podem ser parques, igrejas, terminais, lojas, condomínios ou estabelecimentos ainda desconhecidos. Uma lista fixa de nomes/categorias cria falso negativo e viola a universalidade. Ao mesmo tempo, exigir contexto geográfico forte evita transformar texto de status, preço, botão ou mensagem livre em destino.

## Fronteiras preservadas

A 0.1.194 não altera:

- `DecisionEngine`;
- Google Maps/rota;
- `RideTextParser`;
- `LiveRideAccessibilityService`;
- `FarolVisualPriority0189`;
- Manifest/permissões;
- radares/alertas;
- contrato de cancelamento de gerações antigas;
- regra de que verde/vermelho só existem após destino final confirmado e rota real.

Aplicativos conhecidos e desconhecidos continuam passando pelo mesmo núcleo; nenhuma marca ou pacote recebe autoridade para decidir a cor.
