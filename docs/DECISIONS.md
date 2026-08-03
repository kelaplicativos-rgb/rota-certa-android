# Rota Certa — Decisões técnicas

## 2026-08-03 — A grade flutuante executa em no máximo dois toques

- **Decisão:** um toque na bolinha principal abre/fecha a grade; um toque na bolinha escolhida executa imediatamente sua ação principal. Não existe espera para toque triplo nem classificação de 1,5 segundo no caminho da grade.
- **Motivo:** a grade é uma superfície de ação rápida. Gestos múltiplos e painéis informativos intermediários aumentavam latência, ambiguidade e número de interações.
- **Configuração:** a bolinha permanente `+` é o caminho autoritativo para editar nome, recurso, ícone, ordem e visibilidade. Preferências legadas de toque rápido/segurar são ignoradas no despacho.
- **Exceções funcionais:** quando a própria ação exige dados ou confirmação, o segundo toque pode abrir diretamente o pop-up real dessa ação, como em `Salvar local` e `Alertas`; não pode abrir um painel genérico antes dela.
- **Módulos afetados:** `BubbleShortcutOverlayController`, despacho da grade em `LiveRideAccessibilityService`, Central de atalhos na `MainActivity` e política `ShortcutDirectTapPolicy0182`.
- **Condições para revisão:** somente mediante pedido explícito e com alternativa que preserve a meta de no máximo dois toques, não atrase o toque simples e não misture edição ao caminho crítico.
- **Validação:** commit `4094b598dab6374067ef1f480bf3f70d7684ccab`, workflow run `30850540090`, artifact `8871170435`, APK SHA-256 `1d654096731fec7e8fb0c66500066305b0ee6658ebf0c101352cf239d1f401f7`.

## 03/08/2026 — cadeia Gradle limitada e contratos de gesto versionados

- **Decisão:** builds cumulativos da 0.1.179 devem herdar limites globais de Gradle: daemon e paralelismo desligados, um worker, VFS watch desligado, heap e metaspace limitados e compilador Kotlin em processo.
- **Motivo:** a cadeia de materialização executa versões anteriores antes do código final e o runner estava encerrando o Gradle com `exit code 137`. A solução deve reduzir concorrência e pico de memória sem apagar testes, lint ou assemble.
- **Cobertura obrigatória:** todos os testes unitários/de contrato, Android Lint e `clean assembleDebug` permanecem obrigatórios. Não mascarar falta de memória pulando validações de versões-base.
- **Contrato de gesto:** `ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS` é a fonte autoritativa do toque longo de 2 segundos. Testes não devem voltar a exigir o literal legado de 1,5 segundo.
- **Contrato de despacho:** a grade personalizável resolve primeiro a entrada selecionada e então despacha `entry0179.spec`. Testes legados devem validar a entrada resolvida, não exigir o caminho direto antigo por `module.spec`.
- **Versionamento dos contratos:** ajustes de compatibilidade em testes materializados são aplicados por patch dedicado, com SHA-256 conhecido e `git apply --check`; não usar substituição textual silenciosa durante o build e não remover testes conflitantes.
- **Segurança do workflow:** checkouts de build usam `persist-credentials: false`; gatilhos de push e PR devem ficar restritos aos arquivos que podem alterar o resultado da 0.1.179.
- **Fronteira protegida:** esta decisão é exclusiva do pipeline e dos contratos de teste. Manifest, permissões, farol, parser, OCR, Google Maps, Casa/Alfinetes, decisão de cores, km, radares, alertas e proteção contra resultados atrasados não podem ser alterados para resolver pressão de memória do runner.
- **Condição para revisão:** revisar os limites quando o runner ou a estrutura de materialização mudar. Uma futura separação entre materialização e validação única só pode substituir a cadeia atual se testes de equivalência comprovarem que nenhuma proteção das versões-base foi perdida.

## 02/08/2026 — atalhos inline navegam pela identidade do módulo e recebem foco único

- **Decisão:** todo atalho da grade flutuante cujo destino é um módulo composto dentro da Home deve enviar o ID autoritativo do módulo, além de grupo e aba; enviar somente o grupo não é navegação suficiente.
- **Motivo:** a Home expande a bolinha pelo ID do catálogo. Grupo e aba identificam o conteúdo, mas não determinam qual bolinha/painel precisa ficar aberto.
- **Foco visual:** após a composição do módulo solicitado, a Home executa uma única operação `bringIntoView` para a fileira e o painel correspondentes. O mesmo módulo pode receber novo foco quando chegar um novo Intent explícito.
- **Desempenho:** o foco é estritamente orientado ao toque e ao novo Intent. Não usar polling, temporizador recorrente, observador contínuo, OCR, captura ou trabalho em segundo plano para manter a posição.
- **Módulos afetados:** Rota, Destino, Alertas, Locais, Radares, Aparência, Permissões, Backup, Relatórios e Configurações da Home. Activities dedicadas continuam usando o lançador seguro da 0.1.176.
- **Capturar:** o toque simples continua abrindo `Aplicativos e cards autorizados`; mudança de finalidade exige pedido explícito e nova decisão.
- **Fronteira protegida:** Manifest, permissões, catálogo, toque longo, farol, parser, OCR, Google Maps, Casa/Alfinetes, confirmação real de card, decisão de cores, km e proteção contra resultado atrasado não podem ser alterados por esta navegação.
- **Condição para revisão:** revisar somente se a Home adotar navegação formal por rotas/telas independentes ou se teste real mostrar que o foco único não é suficiente em algum fabricante. Qualquer revisão deve continuar orientada a eventos e sem processamento contínuo.

## 02/08/2026 — Preservar janela visível até o despacho dos atalhos flutuantes

- **Decisão:** qualquer toque da grade flutuante que precise abrir uma Activity interna deve manter a bolinha principal visível até o sistema receber o despacho solicitado pelo usuário.
- **Motivo:** remover todas as sobreposições antes da abertura pode eliminar a condição de interação visível e permitir bloqueio silencioso da Activity em Android moderno.
- **Implementação:** Android 14 ou superior usa `PendingIntent` com permissão explícita do criador e do remetente para início em segundo plano; versões anteriores usam `startActivity`. A sobreposição só é escondida após o envio sem exceção.
- **Módulos afetados:** roteamento da grade para Activities internas, incluindo Locais, Alertas, Radares, Respostas, Rota, Destino, Financeiro, Links, Diagnóstico, Aparência e Backup.
- **Fronteira:** não muda catálogo, toque longo, Home, Manifest, permissões, farol, parser, OCR, Google Maps, Casa/Alfinetes ou decisão de cores.
- **Diagnóstico:** manter somente marcadores de evento limitados para despacho aceito ou falha; não criar polling nem log contínuo adicional.
- **Revisão futura:** somente se nova política do Android alterar o contrato de background activity launch ou se teste real demonstrar falha residual.

## 01/08/2026 — a Home é o catálogo completo em bolinhas; a grade flutuante continua sendo o painel rápido

- **Decisão:** cada módulo ou recurso registrado no catálogo possui uma bolinha própria na Home, enquanto a grade flutuante permanece separada e dedicada às ações rápidas sobre outros aplicativos.
- **Motivo:** a Home precisa permitir reconhecimento visual rápido de todos os recursos sem transformar a grade flutuante em navegação completa nem ocultar módulos em uma lista longa de cartões.
- **Home:** usa bolinhas grandes, com ícone e nome curto, em três colunas. Cada bolinha representa exatamente um módulo do catálogo autoritativo.
- **Interação:** somente toque simples. Tocar seleciona ou recolhe; não existe personalização nem gesto longo na Home.
- **Conteúdo:** aparece imediatamente abaixo da fileira que contém a bolinha selecionada. Não renderizar painel global no fim da lista e não deslocar o resultado para outro ponto da página.
- **Estado:** apenas um módulo pode permanecer aberto. A seleção usa o mesmo ID autoritativo da política de expansão e não cria estados independentes por bolinha.
- **Grade flutuante:** conserva as ações fixas restauradas na 0.1.173. A mudança visual da Home não altera despacho, toque simples, toque longo, confirmação de ações sensíveis ou sobreposição.
- **Segurança durante condução:** a interface reduz procura visual, mas não autoriza manipulação prolongada do celular com o veículo em movimento. Configurações e tarefas detalhadas devem ser preparadas com o veículo parado.
- **Desempenho:** somente o conteúdo do módulo selecionado é composto; nenhuma bolinha inicia OCR, rota, captura, serviço ou polling apenas por estar visível.
- **Fronteira protegida:** Manifest, permissões, acessibilidade, parser, OCR automático, Google Maps, Casa/Alfinetes, confirmação de card, decisão, cores, km, cancelamento de resultados antigos e grade flutuante permanecem inalterados.
- **Condição para revisão:** revisar quantidade de colunas ou dimensões somente após teste visual em diferentes larguras de tela. Não remover a correspondência de uma bolinha por módulo sem decisão explícita.

## 01/08/2026 — o conteúdo do módulo pertence ao próprio expander

- **Decisão:** o conteúdo funcional de um módulo da Home deve ser composto como filho do cartão expandido daquele módulo, imediatamente abaixo do seu cabeçalho.
- **Motivo:** selecionar um grupo e renderizar um painel global depois da lista cria distância entre comando e resultado, exige rolagem desnecessária e faz parecer que o expander não funcionou.
- **Expansão exclusiva:** somente um módulo permanece aberto por vez. Abrir outro recolhe o anterior; tocar novamente no módulo aberto o fecha.
- **Módulos internos:** telas e grupos que já são componentes Compose devem ser renderizados inline, sem botão intermediário `Abrir módulo` e sem painel global no fim da página.
- **Módulos externos:** recursos que dependem de Activity própria permanecem representados dentro do expander com descrição curta e botão de ação específico; não usar rótulo genérico quando o destino puder ser nomeado.
- **Estado:** a expansão é controlada por um único ID autoritativo na Home, não por um estado local independente em cada cartão.
- **Desempenho:** somente o conteúdo do módulo atualmente aberto é composto. Não manter todos os módulos pesados montados, não adicionar polling e não executar ações ao apenas expandir o cartão.
- **Navegação:** fechar ou alternar expanders não altera a grade flutuante, não dispara a ação rápida do módulo e não muda o estado do farol.
- **Fronteira protegida:** esta decisão é exclusiva de apresentação e navegação da Home. Manifest, permissões, acessibilidade, parser, OCR automático, rota, Casa/Alfinetes, decisão, cores, km e proteção contra resultados atrasados permanecem inalterados.
- **Condição para revisão:** revisar somente se a Home adotar navegação formal por rotas ou telas independentes. Mesmo nesse caso, comando e conteúdo devem continuar visualmente próximos e a volta deve preservar a posição do usuário.

## 01/08/2026 — a grade usa ações fixas e restaura o contrato original

- **Decisão:** a grade flutuante não oferece personalização de toque simples ou toque longo por usuário.
- **Motivo:** preferências individuais criaram combinações persistidas que dificultavam garantir e diagnosticar qual ação seria executada por cada atalho.
- **Toque simples:** executa sempre a ação principal definida no catálogo do módulo.
- **Toque longo:** executa a ação secundária original quando ela existir; quando não existir, repete a ação principal, preservando o comportamento anterior à personalização.
- **Migração:** preferências antigas de toque longo são ignoradas e removidas. Elas não podem voltar a influenciar o despacho após atualização ou reinício.
- **Segurança:** confirmação continua obrigatória quando a ação fixa já for sensível, como limpeza de cache. Não criar fallback destrutivo para ação ausente ou falha de abertura.
- **Home:** todos os módulos continuam acessíveis diretamente pela Home; a remoção da personalização não remove módulos nem transforma a grade na única entrada para diagnóstico.
- **Desempenho:** a resolução é local e orientada ao gesto, sem observador, polling, novo serviço, OCR ou captura em segundo plano.
- **Validação:** texto de interface com caracteres acentuados deve ser validado no código-fonte ou por teste de recurso, não por `strings` bruto sobre DEX. O APK mantém validação por marcador ASCII estável, pacote, versão, assinatura e contratos de código.
- **Fronteira protegida:** Manifest, permissões, acessibilidade, parser, OCR automático, Google Maps, Casa/Alfinetes, confirmação real de card, decisão, cores e quilometragem não podem ser alterados pela restauração da grade.
- **Condição para revisão:** revisar apenas se o catálogo de ações mudar de forma explícita. Qualquer nova personalização exigirá evidência de necessidade, migração determinística e testes em aparelho antes de voltar ao produto.

## 31/07/2026 — acessibilidade falha fechada e ferramentas manuais não usam polling

- **Fronteira do serviço:** `onAccessibilityEvent`, criação, conexão, interrupção e destruição do serviço são fronteiras fail-closed. Uma exceção inesperada deve ser contida, registrada de forma limitada e limpar somente o estado transitório; não pode escapar silenciosamente para o Android.
- **Coroutines:** tarefas raiz do serviço usam supervisão e tratamento explícito. `CancellationException` continua representando cancelamento normal; outras falhas são contidas com estágio e tipo, sem texto pessoal e sem rajada de logs.
- **Estado autoritativo:** `serviceReady=true`, cor, km e destino só podem sobreviver enquanto a sessão atual estiver realmente conectada. Interrupção, destruição ou falha crítica invalidam imediatamente sessão, geração, trabalhos antigos e estado visual persistido.
- **Diagnóstico permanente:** registrar apenas eventos reais em buffers circulares limitados. Não adicionar varredura contínua, screenshot em ciclo, OCR recorrente ou gravação ilimitada.
- **Investigação intensiva:** o pulso de um segundo é permitido somente quando iniciado manualmente, por tempo limitado e com desligamento automático. Ele registra saúde do processo e etapas, sem capturar a tela. Na abertura seguinte, consultar o histórico de encerramento fornecido pelo Android para diferenciar crash, ANR, pouca memória e encerramento pelo sistema.
- **OCR de Copiar:** o toque longo captura somente o quadro atual e executa um OCR. Trabalho anterior é cancelado, a imagem temporária é liberada e telas protegidas não são contornadas. Vídeo deve ser lido pelo quadro visível, preferencialmente pausado; nunca analisar continuamente os quadros.
- **Links rápidos:** armazenar somente nome, URL HTTP/HTTPS e indicação de link principal em armazenamento local pequeno. Abrir somente após toque do usuário, usando aplicativo compatível ou navegador; nenhum link é consultado em segundo plano.
- **Limpeza:** o toque longo de Limpar remove somente caches recriáveis do próprio Rota Certa, com confirmação. Não apagar `SharedPreferences`, DataStore, bancos, modelos de card, Casa/Alfinetes, aplicativos selecionados, frases, links, histórico ou backups.
- **Frases predefinidas:** Copiar e Valor compartilham um único editor e armazenamento para impedir listas divergentes. O editor pode visualizar, alterar e restaurar frases; a substituição de variáveis deve ser determinística e local.
- **Navegação:** botão físico, gesto Voltar e seta superior seguem a mesma ordem: fechar diálogo interno, retornar do módulo para a Home preservando o estado necessário e, somente na Home, aplicar o comportamento normal da Activity.
- **Respostas rápidas:** campos de texto usam cores semânticas do tema (`onSurface` e equivalentes) em vez de fixar preto ou branco. Fechar uma tela aberta pela Home retorna à Home; somente a abertura sobre outro aplicativo pode revelar a tela externa anterior.
- **Catálogo:** a 0.1.172 possui 17 módulos na Home, incluindo Links rápidos. O editor de frases é compartilhado por Copiar e Valor e não cria uma segunda bolinha permanente.
- **Fronteira protegida:** estas mudanças não autorizam verde/vermelho, não alteram parser, confirmação real do card, destino final, Google Maps, Casa/Alfinetes, decisão, formato de km ou proteção contra resultado antigo.
- **Condição para revisão:** revisar se o Android mudar o ciclo do serviço de acessibilidade, se surgir evidência de falha ainda escapando das fronteiras, ou se o diagnóstico intensivo afetar bateria/memória. Qualquer evolução deve preservar limites rígidos, ação manual e ausência de polling visual.

## 31/07/2026 — módulos independem da sobreposição e toque longo é configurável por recurso

- **Decisão de disponibilidade:** todo módulo registrado no catálogo da grade deve ter uma entrada direta na Home. A bolinha e sua sobreposição não podem ser a única forma de abrir diagnóstico, configurações ou recursos.
- **Motivo:** quando o processo ou serviço de acessibilidade encerra, a bolinha pode desaparecer. O usuário ainda precisa abrir o aplicativo pelo ícone, gerar relatório e acessar todos os módulos para recuperar e diagnosticar o funcionamento.
- **Papel da grade:** a grade flutuante permanece como painel de execução rápida; ela não substitui a navegação completa da Home.
- **Toque simples:** continua determinístico e executa a ação principal já definida para o atalho.
- **Toque longo:** cada módulo oferece configuração local entre manter comportamento atual, nenhuma ação, abrir módulo, executar ação principal e executar ação secundária quando existir.
- **Migração:** o valor padrão é `PreserveExisting`. Assim, atalhos com ação longa específica continuam executando-a; atalhos que antes repetiam a ação principal mantêm esse comportamento até escolha explícita do usuário.
- **Segurança:** ações sensíveis escolhidas pelo usuário para toque longo exigem confirmação. O comportamento legado preservado não recebe uma confirmação nova, evitando alteração silenciosa dos gestos já decididos.
- **Desempenho:** preferências são armazenadas em `SharedPreferences` e lidas somente na interação. Não adicionar polling, observador contínuo, serviço, OCR, captura ou trabalho em segundo plano.
- **Falha fechada:** ação ausente ou módulo que não possa abrir não executa fallback destrutivo; encerra com aviso e preserva o estado do farol.
- **Diagnóstico:** o módulo de depuração e a geração manual do relatório devem continuar acessíveis pela Home mesmo quando a bolinha estiver ausente.
- **Módulos afetados:** catálogo da grade, controlador do overlay, serviço que despacha atalhos, Home Compose, nova política e armazenamento de toque longo.
- **Fronteira protegida:** Manifest, permissões, acessibilidade, parser, OCR, Google Maps, Casa/Alfinetes, confirmação de card, decisão, cores e quilometragem não podem ser alterados por esta personalização.
- **Condição para revisão:** revisar somente se o catálogo de módulos ou o modelo de ações rápidas mudar; qualquer evolução deve preservar migração, confirmação de ações sensíveis, acesso independente pela Home e ausência de trabalho contínuo.

## 31/07/2026 — falha no despertar por notificação deve ser contida sem derrubar a acessibilidade

- **Evidência:** vídeo real da 0.1.169 mostrou encerramento do processo e posterior desativação da leitura ao vivo.
- **Limite:** sem stack trace, não atribuir a falha a uma linha única.
- **Decisão:** a entrada de `TYPE_NOTIFICATION_STATE_CHANGED` é uma fronteira fail-closed; exceções síncronas e assíncronas não podem derrubar o processo.
- **Circuito:** após exceção, pausar somente o despertar por notificação por 60 segundos; manter o restante do serviço ativo.
- **Limpeza e diagnóstico:** invalidar token, cancelar trabalho, liberar screenshot, limpar estado transitório e registrar somente estágio e tipo da exceção em buffers limitados.
- **Fronteira protegida:** Manifest, permissões, XML de acessibilidade, parser, decisão, Google Maps e Casa/Alfinetes.

## 31/07/2026 — notificação selecionada pode despertar OCR, mas nunca autoriza decisão

- **Decisão:** usar `TYPE_NOTIFICATION_STATE_CHANGED` como gatilho nativo para overlays que não geram mudança acessível de janela ou conteúdo.
- **Autorização:** somente pacote selecionado, Modo Trabalho e leitura ao vivo ativos; texto da notificação nunca libera verde ou vermelho.
- **Segurança:** decisão continua exigindo confirmação visual, card coerente, dois endereços, destino final e rota real.
- **Desempenho:** geração, deduplicação, TTL de 12 segundos, máximo de quatro capturas, cancelamento e ausência de polling.
- **Privacidade:** nenhuma nova permissão e nenhuma análise de notificações de app não selecionado.

## 31/07/2026 — OCR espacial único e limites de card preservados

- **Decisão:** preservar o objeto estruturado do ML Kit até a ordenação por blocos e linhas, sem segundo OCR.
- **Motivo:** uma única leitura reduz CPU e memória e entrega ao parser texto espacialmente coerente.
- **Decisão:** marcadores em texto achatado usam separadores horizontais; não consumir quebras de linha entre cards.
- **Decisão:** primeiro fallback OCR sem atraso artificial, orientado a evento, cancelável e sem execução duplicada.

## Histórico anterior

As decisões registradas antes da 0.1.168 foram preservadas em [`docs/archive/DECISIONS-pre-0.1.168.md`](archive/DECISIONS-pre-0.1.168.md).
