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

## 04/08/2026 — Saída configurável para falas, alertas e sirenes

- **Status:** sugestão registrada; não implementada.
- **Pedido:** permitir que o usuário escolha onde e por qual canal os avisos sonoros do Rota Certa serão reproduzidos, evitando interromper uma música que esteja tocando pelo Bluetooth.
- **Separação obrigatória:** tratar `dispositivo de saída` e `canal de volume` como configurações diferentes. O dispositivo define onde o som será ouvido; o canal define qual controle de volume e política sonora serão usados.
- **Dispositivos sugeridos:** `Automático/sistema`, `Alto-falante do telefone` e `Bluetooth conectado`, mostrando somente opções realmente disponíveis.
- **Canais sugeridos:** `Mídia`, `Alarme` e `Toque/alerta`. A opção padrão deve continuar sendo a configuração atual até validação prática.
- **Comportamento com música:** oferecer `não interromper`, `reduzir temporariamente` e, somente mediante escolha explícita, `pausar durante a fala`. Não pedir foco de áudio exclusivo por padrão.
- **Samsung Galaxy:** aproveitar ou orientar a configuração nativa `Som do aplicativo separado`, capaz de direcionar o áudio de um aplicativo para o telefone ou Bluetooth enquanto outro aplicativo usa a saída principal. Não depender de componentes internos não documentados da One UI.
- **Android geral:** usar `AudioAttributes` para classificar fala, alarme ou toque. Quando o reprodutor utilizado permitir, aceitar dispositivo preferido apenas como preferência e confirmar a rota efetiva; o sistema pode substituir a escolha conforme modelo, versão, Bluetooth, chamada ativa ou política do fabricante.
- **TTS:** `TextToSpeech.setAudioAttributes` permite selecionar a categoria sonora, mas não garante sozinho o alto-falante físico. Não prometer que escolher `Alarme` sempre desviará a fala do Bluetooth para o telefone.
- **Falha segura:** se a saída escolhida desaparecer, voltar ao padrão do sistema, registrar somente um evento limitado e nunca repetir a fala em duas saídas sem autorização.
- **Interface sugerida:** módulo `Som dos avisos`, com botão `Testar saída`, identificação do dispositivo realmente usado e restauração rápida para `Automático`.
- **Desempenho:** configuração orientada a eventos, sem polling de Bluetooth. Consultar dispositivos somente ao abrir o módulo, conectar/desconectar ou iniciar uma fala.
- **Segurança:** alarmes críticos não devem ficar inaudíveis por escolha incompatível; mostrar aviso antes de salvar volume/canal que esteja silenciado. Respeitar Não Perturbe e as políticas do sistema, sem tentar contorná-las.
- **Fronteira protegida:** implementação independente do farol, parser, OCR, rota, leitura de cards, cálculo de km e ciclo visual da bolinha.
- **Condição para implementação:** versão própria, com testes no Samsung SM-S911B/Android 16 usando alto-falante, Bluetooth do carro/fone, música simultânea, tela bloqueada, chamada ativa e desconexão durante a fala.
