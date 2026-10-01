# Rota Certa — Decisões técnicas

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
