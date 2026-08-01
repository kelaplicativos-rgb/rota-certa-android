# Rota Certa — Decisões técnicas

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
