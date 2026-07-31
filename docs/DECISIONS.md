# Rota Certa — Decisões técnicas

## 31/07/2026 — notificação selecionada pode despertar OCR, mas nunca autoriza decisão

- **Decisão:** incluir `TYPE_NOTIFICATION_STATE_CHANGED` como gatilho nativo e orientado a evento para ofertas que aparecem como overlay sobre launcher/System UI sem gerar mudança acessível de janela ou conteúdo.
- **Motivo:** no vídeo e relatório reais da versão 0.1.168, o card UberX permaneceu visível durante um intervalo sem qualquer `ACCESSIBILITY_EVENT`; sem um gatilho de entrada, screenshot, OCR e decisão nunca eram iniciados.
- **Limite de autorização:** somente notificações cujo pacote esteja na seleção manual do usuário podem abrir um token de confirmação. Modo Trabalho, leitura ao vivo, serviço pronto e ausência de gesto na bolinha também são obrigatórios.
- **Decisão de segurança:** texto da notificação não é evidência de card e nunca libera verde ou vermelho. A decisão continua dependendo de confirmação visual por OCR, card coerente, dois endereços válidos, destino final e rota real.
- **Decisão de desempenho:** cada token tem geração própria, deduplicação, TTL de 12 segundos e no máximo quatro capturas pontuais. Não haverá timer contínuo, polling, loop de screenshots nem trabalho simultâneo duplicado.
- **Decisão de concorrência:** novo token cancela análise, rota e fallback antigos; entrada real no aplicativo selecionado encerra o despertar por notificação; desligamento do Modo Trabalho e destruição do serviço também cancelam imediatamente.
- **Privacidade e permissões:** não adicionar nova permissão no Manifest, não analisar notificação de app não selecionado e não persistir conteúdo para além dos buffers de diagnóstico já limitados.
- **Módulos afetados:** configuração XML do serviço de acessibilidade, filtro de eventos, `LiveRideAccessibilityService`, novo `FarolNotificationWakeup0169` e seus contratos.
- **Condições para revisão:** revisar somente se testes em aparelho comprovarem que o firmware não entrega `TYPE_NOTIFICATION_STATE_CHANGED` para o overlay. Qualquer alternativa deve permanecer nativa, limitada, cancelável, filtrada pelo pacote selecionado e sem polling.

## 31/07/2026 — OCR espacial único e limites de card preservados

- **Decisão:** preservar o objeto estruturado `Text` do ML Kit dentro do `OcrService` até a ordenação por blocos e linhas; não executar uma segunda captura ou um segundo reconhecedor para obter geometria.
- **Motivo:** a arquitetura atual centraliza OCR em `AndroidServices.kt`. Integrar ali mantém uma única leitura, reduz CPU/memória e entrega ao parser texto espacialmente coerente para pop-ups e listas.
- **Decisão:** marcadores textuais em conteúdo achatado devem usar separadores horizontais `[ \t]+`; `\s+` não pode envolver ações como `Pular`, pois consome quebras de linha e pode unir cards distintos.
- **Decisão:** o primeiro fallback OCR não terá atraso fixo artificial. Ele continua orientado a evento, cancelável e protegido por pacote selecionado, sessão, geração, serviço pronto e prevenção de execução duplicada.
- **Decisão:** aplicadores de versão devem reconhecer identificadores Kotlin válidos e a arquitetura real da base materializada, falhando de forma fechada quando a âncora não for única.
- **Módulos afetados:** visão unificada 0.1.168, `OcrService`, entrada de processamento ao vivo, parser e contratos do pipeline imediato.
- **Condições para revisão:** somente se a API do ML Kit, o contrato de `OcrService` ou a estratégia de fallback forem substituídos; qualquer revisão deve preservar leitura única, cancelamento, isolamento por card e ausência de polling.

## Histórico anterior

As decisões registradas antes da 0.1.168 foram preservadas, sem alteração de bytes, em [`docs/archive/DECISIONS-pre-0.1.168.md`](archive/DECISIONS-pre-0.1.168.md).
