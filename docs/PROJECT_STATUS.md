# Rota Certa — Estado do projeto

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f66d8daefce26f5cc9f78d8c27c581bfd9734314`.
- **Pedido:** analisar os workflows anexados, localizar a primeira causa real, corrigir a 0.1.168 sem regressão do núcleo 0.1.167 e entregar um APK efetivamente validado.
- **Situação anterior:** os workflows 0.1.168 encerravam antes de testes, Lint e build. Depois da primeira âncora, novas incompatibilidades do aplicador antigo surgiam em sequência; a suíte também revelou mistura entre dois cards, caminho de teste dependente do diretório e contrato legado que ainda exigia atraso fixo no OCR.
- **Causas comprovadas:**
  - a expressão do aplicador não aceitava o parâmetro válido chamado exatamente `text`;
  - o hash semântico não reconhecia `immediateTextChecklist13` no `analysisHash143`;
  - o fallback atual usa `Job`/corrotina, mas o aplicador procurava `Handler.postDelayed`;
  - o OCR real está em `AndroidServices.kt`, enquanto a correção auxiliar procurava uma chamada direta ao ML Kit dentro do serviço de acessibilidade;
  - expressões com `\s+` ao redor de `Pular` consumiam a quebra de linha e juntavam o início do card seguinte.
- **Correção aplicada:** executor idempotente e fechado para adaptar as âncoras à base 0.1.167 real; hash semântico somente no hash de análise; remoção exclusiva do atraso artificial do fallback, preservando cancelamento, geração e filtros; integração espacial dentro do `OcrService` usando o mesmo resultado ML Kit, sem segunda leitura; limites horizontais `[ \t]+` preservando quebras entre cards; contratos compatíveis com execução pela raiz ou pelo módulo `app`.
- **Arquivos principais da entrega:**
  - `scripts/run_fix_farol_unified_visual_0168.py`;
  - `scripts/fix_farol_unified_visual_0168_compile.py`;
  - `.github/workflows/build-rota-certa-0.1.168-final.yml`;
  - `.github/workflows/build-rota-certa-0.1.168.yml`;
  - código materializado: `FarolUnifiedVisual0168.kt`, `AndroidServices.kt`, `LiveRideAccessibilityService.kt`, `RideTextParser.kt` e testes 0.1.168.
- **Fronteira protegida:** Manifest e permissões inalterados; hashes de `DecisionEngine.kt`, `GoogleMapsService.kt`, `FarolSelectedAppInputPolicy0166.kt` e `FarolRealtimeEventGate0167.kt` preservados; Casa/Alfinetes, Modo Trabalho e regra de somente card confirmado continuam vigentes.
- **Testes executados:** materialização completa 0.1.151–0.1.168 duas vezes; idempotência; fronteira exata; contratos visuais; **237 testes unitários e de contrato aprovados**; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura APK v2.
- **Workflows validados:**
  - `Build Rota Certa 0.1.168 Final`, run `30601624749`, job `91065374576`, sucesso em todos os passos;
  - `Build Rota Certa 0.1.168`, run `30601624765`, job `91065374526`, sucesso em todos os passos.
- **Artifact:** `rota-certa-0.1.168-farol-visual-unificado-validated`, ID `8782207626`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.168`, versionCode `5290`.
- **APK:** `rota-certa-0.1.168-farol-visual-unificado-validado.apk`, 55.894.891 bytes.
- **SHA-256 do APK:** `386cbf256e471bcb6ab2bc79c8564ff9826dbf52bec2fac5bb5a98fd2fe69de2`.
- **SHA-256 do ZIP do artifact:** `feef33784c0c111842976e645937fb8a32e08cc8265f8515102bdbfc4197553b`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendência prática:** instalar em aparelho real e testar cards individuais e listas da Uber, 99, inDrive e de outro aplicativo selecionado, verificando mudança imediata para amarelo/verde/vermelho, limpeza ao sair do card e ausência de mistura entre ofertas. Build, testes e APK estão validados; conteúdo visual de terceiros e condições reais de acessibilidade/OCR ainda exigem aparelho.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado, sem alteração de bytes, em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
