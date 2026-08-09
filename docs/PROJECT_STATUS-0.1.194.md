# Rota Certa 0.1.194 — estado da correção universal de leitura de locais

Data: 09/08/2026

## Base e identidade

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Base: `agent/forensic-incident-monitor-0.1.193`
- Branch: `agent/fix-universal-card-confirmation-0.1.194`
- PR: #72
- Pacote: `br.com.mapeiaia.rotacerta`
- Versão alvo: `0.1.194`
- versionCode alvo: `5478`

## Evidência física recebida

Três aplicativos foram exercitados no Samsung SM-S911B / Android 16 com a 0.1.193:

1. inDrive: a acessibilidade coletou a oferta com `R. Carlos Vivaldi, 197` e `Parque do Carmo`, mas `BUBBLE_ROUTE_GATE_REJECTED_0188` recusou porque o bloco visual não chegou a dois locais confirmados.
2. 99 Driver: a acessibilidade não expôs a oferta suficientemente; o OCR recuperou locais como `281, Jardim Nove de Julho`, `CCB Jardim Nove de Julho` e `Rua Paulino...`, porém o núcleo não confirmou dois locais.
3. Uber Driver: a acessibilidade apresentou a mesma classe de rejeição em parte das leituras, mas o OCR conseguiu formar um card coerente e o farol chegou a verde com 1,611 km. Isso comprova que `DecisionEngine`, rota/cache e pintura funcionam quando o destino é confirmado.

## Causa técnica localizada

A 0.1.189 já possui segmentação espacial universal do OCR (`FarolVisualPriority0189`) e `FarolRealDeviceGate0188` já impede combinação de cards distintos. A inspeção do parser encontrou os falsos negativos remanescentes:

- `looksLikeContinuation` pode tratar um segundo POI/local completo como continuação da rua anterior quando a nova linha contém UF/localidade. Assim `R. Carlos Vivaldi ...` + `Parque do Carmo (...)` pode virar um único candidato em vez de dois locais.
- `UniversalScreenAddressParser` aceita POIs por uma lista de categorias genéricas, mas ainda rejeita nomes de locais/estabelecimentos desconhecidos que não começam por categoria cadastrada, mesmo quando carregam contexto geográfico forte, como `CCB Jardim Nove de Julho`.

## Correção 0.1.194

- alterar somente `UniversalScreenAddressParser` no código funcional;
- manter uma linha seguinte como segundo local quando ela própria já constitui POI/local independente suficientemente reconhecível;
- preservar a junção quando o endereço anterior está realmente quebrado, por exemplo parêntese aberto, prefixo/localidade pendente ou delimitador explícito;
- reconhecer local/POI desconhecido somente com contexto geográfico forte (UF, CEP ou marcador de localidade) e pelo menos duas palavras alfabéticas úteis;
- manter o mesmo comportamento para pacote conhecido e desconhecido;
- nenhuma regra por marca ou pacote;
- `FarolRealDeviceGate0188`, `FarolVisualPriority0189`, `DecisionEngine`, Google Maps/rota, `RideTextParser`, `LiveRideAccessibilityService`, Manifest e permissões permanecem protegidos byte a byte.

## Testes adicionados

- `R. Carlos Vivaldi ...` + `Parque do Carmo (...)` permanecem dois locais;
- endereço realmente quebrado `Rua Erundina (Jardim Rodolfo` + `Pirani, Sao Paulo - SP)` continua sendo um único endereço;
- `CCB Jardim Nove de Julho` é reconhecido sem cadastro específico;
- `281, Jardim Nove de Julho` é reconhecido por contexto;
- dois locais contextuais em linhas separadas continuam distintos;
- texto de status sem contexto geográfico continua rejeitado;
- card coerente inDrive é autorizado pelo gate sem alterar o gate;
- card OCR da 99 usa o mesmo gate;
- aplicativo desconhecido usa o mesmo parser universal.

## Estado

Implementação preparada na branch e PR #72 em rascunho. O workflow 0.1.194 deve comprovar a materialização, pelo menos 400 testes sem falha, Android Lint, `clean assembleDebug`, pacote, versão, versionCode, assinatura APK v2, artifact e SHA-256. Validação física posterior continua obrigatória nos aplicativos reais.
