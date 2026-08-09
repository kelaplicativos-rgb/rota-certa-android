# Rota Certa 0.1.194 — estado da correção universal de cards

Data: 09/08/2026

## Base e identidade

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Base: `agent/forensic-incident-monitor-0.1.193`
- Branch: `agent/fix-universal-card-confirmation-0.1.194`
- Pacote: `br.com.mapeiaia.rotacerta`
- Versão alvo: `0.1.194`
- versionCode alvo: `5478`

## Evidência física recebida

Três aplicativos foram exercitados no Samsung SM-S911B / Android 16 com a 0.1.193:

1. inDrive: a acessibilidade coletou origem e destino visíveis (`R. Carlos Vivaldi, 197` → `Parque do Carmo`), mas `BUBBLE_ROUTE_GATE_REJECTED_0188` recusou porque o bloco visual superior não continha dois endereços confirmados; o OCR posterior também não adquiriu autoridade de rota.
2. 99 Driver: a acessibilidade não expôs a oferta de forma suficiente; o OCR recuperou partes do card e locais como `281, Jardim Nove de Julho`, `CCB Jardim Nove de Julho` e `Rua Paulino...`, mas o núcleo não confirmou dois locais no mesmo card.
3. Uber Driver: a acessibilidade apresentou a mesma classe de rejeição, porém o OCR conseguiu formar um card coerente e a decisão chegou a verde com 1,611 km. Isso comprova que `DecisionEngine`, cache/rota e pintura funcionam quando o destino é confirmado.

## Causa técnica localizada

A 0.1.189 introduziu autoridade visual monotônica correta, mas o gate passou a ancorar a decisão no bloco superior que contenha qualquer endereço. Quando origem e destino são nós separados da acessibilidade, nenhum nó individual contém dois endereços e um contêiner incompleto/geometricamente imperfeito pode não satisfazer a relação exigida. O resultado é falso negativo mesmo com os dois locais no mesmo card.

Em paralelo, `UniversalScreenAddressParser` reconhece ruas e uma lista genérica de POIs, mas ainda rejeita estabelecimentos/POIs desconhecidos cujo nome não começa por uma categoria cadastrada, mesmo quando carregam contexto geográfico forte.

## Correção 0.1.194

- manter o fragmento visual superior como autoridade;
- se o candidato direto falhar, reconstruir somente o componente de acessibilidade que contém a âncora superior;
- a reconstrução exige mesma fonte de acessibilidade, relação estrutural próxima e proximidade espacial;
- card inferior nunca pode completar card superior parcial;
- cards estruturalmente diferentes nunca podem ser combinados;
- permitir POI/local desconhecido somente quando houver contexto geográfico forte (UF, CEP ou localidade como bairro/jardim/vila/distrito/município/centro) e pelo menos duas palavras alfabéticas úteis;
- manter a mesma lógica para pacote conhecido e desconhecido;
- não alterar `DecisionEngine`, Google Maps/rota, `RideTextParser`, `LiveRideAccessibilityService`, `FarolVisualPriority0189`, Manifest ou permissões.

## Testes adicionados

- fragmentos de origem/destino do mesmo card superior devem ser recompostos;
- fragmentos de cards diferentes permanecem bloqueados;
- aplicativo desconhecido usa a mesma recuperação;
- POI contextual desconhecido é reconhecido;
- fragmento numérico + localidade é reconhecido;
- texto de status sem localidade é rejeitado;
- regressão real `R. Carlos Vivaldi → Parque do Carmo` mantém dois locais.

## Estado

Implementação preparada na branch. Testes Android completos, Lint, `clean assembleDebug`, APK, assinatura, artifact e SHA-256 ainda dependem do workflow 0.1.194. Nenhum sucesso de build deve ser declarado antes da conclusão integral do workflow.
