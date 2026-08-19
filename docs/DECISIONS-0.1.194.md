# Decisões técnicas — Rota Certa 0.1.194

Data: 09/08/2026

## O card continua pertencendo ao gate existente

**Decisão:** a 0.1.194 não cria nova regra de fusão de nós/cards. `FarolRealDeviceGate0188` e `FarolVisualPriority0189` permanecem inalterados.

**Motivo:** a 0.1.189 já segmenta o OCR espacialmente e impede endereços de cards distintos de autorizarem uma rota. Introduzir uma segunda recomposição no gate aumentaria o risco de combinar cards irmãos e violaria o contrato universal.

**Evidência final:** o build 0.1.194 protege `FarolRealDeviceGate0188.kt` e `FarolVisualPriority0189.kt` por SHA-256 antes/depois da transformação. O artifact final registra `farol_route_gate_unchanged=true` e `visual_priority_0189_unchanged=true`.

## Um segundo POI já reconhecido não é continuação automática da rua anterior

**Decisão:** `looksLikeContinuation` continua recompondo endereços realmente quebrados, mas não deve colar automaticamente a linha seguinte ao endereço anterior quando a rua anterior já é numerada/completa e a nova linha constitui um segundo local reconhecido com evidência suficiente.

Para categorias de POI fortes já reconhecidas, a segunda linha pode permanecer independente. O termo ambíguo `Parque` exige evidência adicional: localidade interna explícita na própria linha, como `Parque do Carmo (Jardim Nossa Senhora do Carmo, ...)`.

**Preservações:** parêntese aberto, prefixo/localidade pendente e delimitador explícito continuam autorizando continuação. Assim:

- `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` permanece um único endereço;
- `R. Carlos Vivaldi, 197 (...)` + `Parque do Carmo (Jardim ...)` permanecem dois locais;
- `Rua X, 123` + `Parque Sao Jorge, Sao Paulo - SP` permanece um único endereço;
- `Rua X, 123` + `Parque Sao Jorge (Sao Paulo - SP)` permanece um único endereço.

## POI desconhecido continua fail-closed

**Decisão:** a 0.1.194 não amplia `isRecognizedAddress` para aceitar qualquer estabelecimento/local apenas por conter bairro, UF ou CEP.

**Motivo:** a primeira tentativa dessa ampliação causou 5 falhas em 400 testes, incluindo regressão histórica em `FailedCardRecovery0161Test`. Além disso, o relatório real da 99 contém fragmentos/truncamentos que não permitem provar com segurança qual é o destino final do mesmo card.

**Consequência:** quando a evidência da 99 ou de qualquer aplicativo conhecido/desconhecido não for suficiente para confirmar o destino final, o comportamento correto continua amarelo/fail-closed. A correção da 0.1.194 não autoriza adivinhação de destino.

## Normalização de destino pertence à identidade existente

**Decisão:** o gate continua aplicando `DestinationAddressIdentityPolicy.cleanDisplayAddress()` aos candidatos antes de autorizar o destino. A 0.1.194 não altera essa política.

**Motivo:** o run #19 mostrou que o novo teste de integração esperava a string bruta com delimitador final, enquanto o gate devolvia corretamente a forma já normalizada. `assertTrue(decision.authorized)` já passava; apenas a comparação textual do teste falhava. Foi corrigida somente a expectativa do teste, sem alterar código funcional.

## Gerador precisa provar a fonte Kotlin materializada

**Decisão:** o workflow deve validar os escapes `\\b`/`\\s` na fonte Kotlin gerada e rejeitar consumo de barras ou caractere backspace acidental.

**Motivo:** a fonte de verdade efetivamente compilada é materializada pelos scripts cumulativos. Validar apenas a sintaxe Python do gerador não prova que a regex Kotlin resultante é a pretendida.

## Guardas de integridade devem apontar para arquivos reais

**Decisão:** a proteção SHA-256 permanece obrigatória, mas deve usar o caminho real `FailedCardRecovery0161.kt`, onde vive `FailedCardRecoveryEngine0161`.

**Motivo:** o run #18 falhou porque o workflow confundiu nome de classe com nome de arquivo (`FailedCardRecoveryEngine0161.kt`). A solução correta foi corrigir o caminho da guarda, não remover a proteção.

## Fronteiras preservadas

A transformação funcional 0.1.194 não altera:

- `FarolRealDeviceGate0188`;
- `FarolVisualPriority0189`;
- `FailedCardRecoveryEngine0161` / arquivo `FailedCardRecovery0161.kt`;
- `DecisionEngine`;
- Google Maps/rota;
- `RideTextParser`;
- `LiveRideAccessibilityService`;
- Manifest/permissões;
- radares/alertas;
- contrato de cancelamento de gerações antigas;
- regra de que verde/vermelho só existem após destino final confirmado e rota real.

Aplicativos conhecidos e desconhecidos continuam passando pelo mesmo núcleo; nenhuma marca ou pacote recebe autoridade para decidir a cor.

## Critério de aprovação

**Decisão:** somente CI completo e artifact verificado permitem chamar a 0.1.194 de candidata. Sucesso parcial de compilação ou testes não basta.

**Evidência final:** head funcional `9f56f50a4ca302bbfb4f46a7851c1eee398edae8`; workflow `31328995714`; job `93284039823`; 399 testes, 0 falhas; Lint aprovado; `clean assembleDebug` aprovado; assinatura APK v2 válida; pacote/versão/versionCode conferidos; artifact `9042805214`; APK SHA-256 `9de961117a1421e5009d73281689e0f0b61ad63915ec7bca1b49054ebc586961`; candidato público verificado byte a byte.

## Validação física continua obrigatória

**Decisão:** a PR #72 permanece em rascunho. O CI prova a implementação e regressões automatizadas, mas não substitui o aparelho real.

A validação física deve confirmar:

1. inDrive com origem de rua + destino POI (`Parque do Carmo` ou equivalente);
2. Uber como regressão positiva de card já conhecido;
3. 99 sem transformar fragmentos/truncamentos em destino inventado;
4. ausência de km/cor antigos após mudança ou fechamento de card.
