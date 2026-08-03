# Rota Certa — Decisões técnicas

## 30/07/2026 — Rajadas de acessibilidade devem ser confluídas antes da leitura da árvore

**Decisão:** o farol usa `FarolRealtimeEventGate0167` para admitir imediatamente o primeiro evento útil de uma sessão e recusar rajadas equivalentes antes de percorrer `rootInActiveWindow`. Não usar debounce que atrase o primeiro evento. Quando existir mudança real de pacote, janela ou geração, o estado mais novo deve prevalecer.

**Motivo:** o relatório real da 0.1.166 mostrou centenas de coletas repetidas do mesmo texto. A duplicação era reconhecida somente depois de uma travessia da árvore que consumia dezenas ou centenas de milissegundos na thread principal. Isso atrasava inclusive decisões já prontas entre aproximadamente 227 e 302 ms antes do início da pintura.

**Módulos afetados:** `LiveRideAccessibilityService`, política pura `FarolRealtimeEventGate0167` e testes do caminho crítico. Parser, decisão, Google Maps, contrato das cores, Casa/Alfinetes e OCR pontual permanecem preservados.

**Condição para revisão:** apenas se testes em aparelhos reais demonstrarem perda de uma mudança legítima de card. A revisão deve ajustar a identidade de pacote/janela/geração, nunca voltar a processar toda a tempestade nem inserir espera artificial antes do primeiro evento.

## 30/07/2026 — Diagnóstico e atualização visual não podem bloquear decisão pronta

**Decisão:** snapshots de checkpoint do gravador devem ser construídos integralmente em executor de IO de baixa prioridade. Uma atualização visual idêntica deve retornar antes de telemetria, criação de drawable ou outras alocações. A coleta de acessibilidade permanece limitada a 768 nós e 24.000 caracteres.

**Motivo:** a aplicação efetiva do overlay levava poucos milissegundos, porém a thread principal ficava ocupada com eventos, coletas e instrumentação antes de iniciar a renderização. O diagnóstico é necessário, mas não pode competir com leitura, rota, decisão ou pintura.

**Módulos afetados:** `FarolFlightRecorder0163` e caminho de coleta/renderização em `LiveRideAccessibilityService`.

**Condição para revisão:** os limites podem ser reduzidos se aparelhos modestos ainda mostrarem pressão de CPU ou memória. Só devem ser ampliados mediante evidência de que cards válidos foram truncados e com teste de desempenho correspondente.

## 30/07/2026 — O farol deve ser universal para qualquer pacote selecionado

**Decisão:** o nome do aplicativo não participa da autorização do caminho de leitura. Qualquer pacote persistido em `SelectedRideAppStore`, presente como raiz estrita da janela e com sessão atual válida recebe o mesmo parser e o mesmo fallback de OCR pontual.

**Motivo:** o usuário pode selecionar aplicativos além de 99, Uber e inDrive. Uma lista fixa criaria novas falhas sempre que outro aplicativo fosse cadastrado. A seleção explícita do usuário é a autorização universal; verde/vermelho continuam dependendo das regras existentes de card confirmado, destino e rota.

**Módulos afetados:** somente entrada do Farol em `LiveRideAccessibilityService` e a política pura `FarolSelectedAppInputPolicy0166`. Decisão, parser, Google Maps, Casa/Alfinetes, Manifest e permissões permanecem preservados.

**Condição para revisão:** apenas se Android passar a oferecer uma API oficial e estável que identifique semanticamente cards de corrida sem acessibilidade ou screenshot.

## 30/07/2026 — A janela da raiz selecionada prevalece sobre o windowId do evento

**Decisão:** quando `rootInActiveWindow` pertence ao pacote selecionado, a identidade da sessão usa o `windowId` dessa raiz. O `event.windowId` não pode substituir a sessão quando vier de bolinha, overlay, System UI ou evento sem pacote.

**Motivo:** no Uber, a captura começou na janela real `1759`, mas um evento transitório com pacote nulo carregou `windowId=1766`, abriu uma nova sessão e descartou o OCR. A raiz continuava pertencendo ao Uber; portanto o identificador do evento não representava o card.

**Módulos afetados:** resolução da janela estável antes de `DriverCardSessionGate0162`.

**Condição para revisão:** se testes em aparelho provarem que `rootInActiveWindow.windowId` não acompanha uma troca real de card/aplicativo em algum fabricante; nesse caso deve ser criado um segundo sinal de confirmação, nunca voltar a confiar isoladamente no evento.

## 30/07/2026 — OCR pontual não deve exigir que o texto incompleto já pareça um card

**Decisão:** quando o pacote está selecionado, é a raiz estrita atual, o parser ainda não encontrou dois endereços e não existe rota/decisão concorrente, o fallback pode reservar uma captura OCR limitada mesmo que `probableRideCard` seja falso.

**Motivo:** na 99, os endereços que provariam a existência do card não eram expostos pela acessibilidade. Exigir esses sinais antes do screenshot criava um bloqueio circular: o OCR era necessário para obter a evidência, mas a evidência era exigida para permitir o OCR.

**Módulos afetados:** admissão da captura automática limitada; os gates existentes de intervalo, assinatura, sessão atual, `screenshotInProgress` e cancelamento de resultado antigo permanecem ativos.

**Condição para revisão:** se medições reais mostrarem consumo excessivo em algum aplicativo selecionado; a revisão deve melhorar a deduplicação por sessão/assinatura, não reintroduzir nomes fixos de aplicativos.

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
