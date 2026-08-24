# Rota Certa — Decisões técnicas

## 30/07/2026 — Diagnóstico deve preservar decisões válidas anteriores

**Decisão:** reconstruir o relatório por tentativas independentes, iniciadas somente quando existe avaliação ativa com embarque, destino e mudança real de assinatura. Uma leitura incompleta posterior não pode apagar uma decisão válida já pintada.

**Motivo:** o relatório real da 0.1.164 continha uma decisão verde de 5,363 km, mas o resumo selecionou uma tentativa posterior incompleta e declarou que nenhuma rota havia sido executada.

**Módulos afetados:** somente Diagnóstico manual (`FarolDiagnosticSummary0165` e integração no `ManualTechnicalReportBuilder`). Farol, parser, Google Maps, Casa/Alfinetes, overlay e módulos definidos como OK permanecem congelados.

**Condição para revisão:** somente se o gravador passar a emitir um identificador transacional único que acompanhe nativamente cada tentativa do endereço até a pintura ou cancelamento.

## 30/07/2026 — Resposta de rota após limpeza não deve ser aplicada automaticamente

**Decisão:** não alterar a proteção funcional que impede um resultado atrasado de substituir uma tela mais nova ou um card que saiu. O diagnóstico deve informar claramente quando a resposta chegou após a limpeza.

**Motivo:** na tentativa para Rua Peramirim, o Google Maps retornou 4,161 km, mas `com.android.systemui` provocou limpeza cerca de 31 ms antes. Aplicar essa resposta sem confirmar que o card ainda estava presente violaria o contrato contra resultados atrasados.

**Módulos afetados:** nenhum módulo funcional alterado; somente apresentação diagnóstica.

**Condição para revisão:** nova evidência de aparelho provando que o card permaneceu visível e que a mudança para System UI foi apenas uma sobreposição transitória indevidamente classificada.

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
