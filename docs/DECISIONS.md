# Rota Certa — Decisões técnicas

## 31/07/2026 — módulos independem da sobreposição e toque longo é configurável por recurso

- **Decisão de disponibilidade:** todo módulo registrado no catálogo da grade deve ter uma entrada direta na Home. A bolinha e sua sobreposição não podem ser a única forma de abrir diagnóstico, configurações ou recursos.
- **Motivo:** quando o processo ou serviço de acessibilidade encerra, a bolinha pode desaparecer. O usuário ainda precisa abrir o aplicativo pelo ícone, gerar relatório e acessar todos os módulos para recuperar e diagnosticar o funcionamento.
- **Papel da grade:** a grade flutuante permanece como painel de execução rápida; ela não substitui a navegação completa da Home.
- **Toque simples:** continua determinístico e executa a ação principal já definida para o atalho.
- **Toque longo:** cada módulo oferece configuração local entre manter comportamento atual, nenhuma ação, abrir o módulo, executar a ação principal e executar a ação secundária quando existir.
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
