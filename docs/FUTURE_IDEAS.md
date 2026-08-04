# Rota Certa — Sugestões futuras

## 04/08/2026 — Atalho para Ortografia e Gramática do Teclado Samsung

- **Status:** sugestão registrada; não implementada.
- **Pedido:** adicionar ao catálogo de ações do Rota Certa um atalho chamado `Revisar texto`, voltado ao recurso existente de **Ortografia e Gramática** do Teclado Samsung.
- **Fluxo sugerido:** copiar uma mensagem, tocar no atalho, abrir um editor leve preenchido com a área de transferência e solicitar a exibição do teclado. O usuário então aciona manualmente `Assistente de escrita` → `Ortografia e gramática` no próprio Teclado Samsung.
- **Ações previstas no editor:** copiar texto corrigido, substituir a área de transferência, compartilhar e fechar.
- **Identificador sugerido:** `writing.review_clipboard`.
- **Limitação técnica:** não depender de Activity, Intent ou componente interno não documentado da Samsung. O Android pode abrir e focar o campo de texto, mas o acionamento direto da ferramenta interna do teclado não deve ser presumido como API pública estável.
- **Compatibilidade:** mostrar orientação específica quando o Teclado Samsung compatível estiver ativo e manter funcionamento básico com outros teclados, sem bloquear o editor.
- **Desempenho:** fluxo estritamente orientado ao toque; sem serviço adicional, polling, OCR contínuo, captura de tela em ciclo ou processamento em segundo plano.
- **Fronteira protegida:** não alterar farol, leitura de cards, OCR do monitoramento, rota, permissões, bolinha principal ou cancelamento de resultados antigos.
- **Condição para implementação:** tratar em versão própria, com testes em Samsung SM-S911B/Android 16 e validação após atualizações da One UI e do Teclado Samsung.
