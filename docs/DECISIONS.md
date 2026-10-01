# Rota Certa — Decisões técnicas

## 30/07/2026 — Não calcular rota sem região ativa

**Decisão:** manter a regra atual: o farol só consulta rota e decide verde/vermelho quando Casa ou ao menos um Alfinete estiver ativo com coordenada válida.

**Motivo:** sem um alvo configurado não existe ponto de referência para comparar o destino final. Forçar Google Maps nesse estado gastaria rede, CPU e bateria sem permitir uma decisão válida.

**Módulos afetados:** nenhum código funcional alterado. Regra pertence a Farol e Casa/Alfinetes, ambos congelados como OK.

**Condição para revisão:** somente mediante pedido explícito de mudança no contrato da região de trabalho.

## 30/07/2026 — Diagnóstico deve usar a sessão atual como fonte principal

**Decisão:** o topo do relatório manual deve ser reconstruído a partir da trilha do gravador de voo da sessão atual. Campos antigos persistidos permanecem no relatório apenas como contexto secundário.

**Motivo:** campos independentes do `SharedPreferences` podem representar momentos diferentes e produzir combinações impossíveis, como “nenhuma sessão” junto de dezenas de sessões reais registradas.

**Módulos afetados:** somente Diagnóstico manual (`ManualTechnicalReportBuilder` e `FarolDiagnosticSummary0164`).

**Condição para revisão:** se a trilha do gravador for substituída por um estado transacional único e atômico com a mesma precisão temporal.

## 30/07/2026 — Módulos OK permanecem congelados

**Decisão:** Bolinha/Farol, Valor, Financeiro, Alertas, Radares, Casa/Alfinetes, Rastreamento, WhatsApp, Respostas, Backup, Captura e Modo Trabalho não recebem alterações oportunistas.

**Motivo:** preservar comportamento validado e evitar regressões causadas por arquivos e scripts compartilhados.

**Condição para revisão:** pedido explícito do usuário para o módulo específico, com branch, testes e APK isolados.
