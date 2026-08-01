#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
status = root / "docs/PROJECT_STATUS.md"
decisions = root / "docs/DECISIONS.md"

status_block = '''## 01/08/2026 — 0.1.175 (5360) — Home em grade de bolinhas por módulo e recurso

- **Branch:** `agent/home-module-bubble-grid-0.1.175`; **PR:** #44, empilhada sobre `agent/home-module-launcher-0.1.174`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `7ea558deb4307fd1cdc273509695abf04487b04f`.
- **Pedido do usuário:** manter a grade flutuante como painel de ações rápidas e transformar a Home no catálogo completo do aplicativo, com uma bolinha própria para cada módulo ou recurso.
- **Situação anterior:** a Home 0.1.174 usava cartões expansíveis verticais. Embora o conteúdo já aparecesse no local correto, a lista continuava longa e menos adequada à identificação rápida de todos os recursos.
- **Causa:** a apresentação por cartões priorizava detalhes textuais antes da seleção. Para o contexto de uso do Rota Certa, o catálogo completo precisava privilegiar reconhecimento visual, alvos grandes e uma entrada direta para cada módulo.
- **Correção aplicada:**
  - todos os 17 módulos do `BubbleShortcutCatalog` são apresentados na Home como bolinhas circulares de 96 dp, com ícone e nome;
  - a grade da Home usa três colunas e seis fileiras, preservando a ordem autoritativa do catálogo;
  - toque simples seleciona ou recolhe o módulo, sem toque longo e sem personalização;
  - apenas um módulo fica aberto por vez;
  - o conteúdo aparece imediatamente abaixo da fileira da bolinha selecionada, sem salto ao fim da página;
  - a bolinha selecionada recebe destaque visual;
  - a grade flutuante permanece separada e conserva suas ações fixas de toque simples e longo restauradas na 0.1.173;
  - nenhuma ação é executada apenas por compor ou visualizar a grade da Home.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `MainActivity.kt`;
  - novo `HomeModuleBubbleGridPolicy0175.kt`;
  - novo `HomeModuleBubbleGridPolicy0175Test.kt`;
  - contrato `ShortcutLongPressContract0171Test.kt` atualizado para a Home em bolinhas;
  - `scripts/fix_home_bubble_grid_0175.py`;
  - `scripts/fix_home_bubble_grid_0175_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.175.yml`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `LiveRideAccessibilityService.kt`, `BubbleShortcutOverlayController.kt`, `BubbleShortcutModule.kt` e `ShortcutLongPressPolicy0171.kt` permaneceram preservados por checksum. Não houve mudança em permissões, acessibilidade, parser, OCR, rota, Casa/Alfinetes, confirmação real de card, decisão, cores, km ou grade flutuante.
- **Testes e validações:** materialização completa 0.1.151–0.1.174; contratos estruturais; 267 testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falhas intermediárias localizadas:** dois testes legados ainda exigiam textos e estrutura dos cartões expansíveis e foram atualizados para o novo contrato; posteriormente, a única falha do pipeline era um passo não funcional que tentava comentar automaticamente na PR, removido sem alteração do APK.
- **Workflow funcional validado:** `Build Rota Certa 0.1.175`, run `30713519235`, job `91405065129`; todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.175-home-bubble-grid-validated`, ID `8822675670`, retenção até 30/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.175`, versionCode `5360`.
- **APK:** `rota-certa-0.1.175-home-grade-bolinhas-validado.apk`, 55.993.299 bytes.
- **SHA-256 do APK:** `33c8f8eb41c61eb4cb69fd0a5aba8eefd4f09474c027a9648a050ebc47170816`.
- **SHA-256 do ZIP do artifact funcional:** `16c942dac2374259a3c5d7d91284eb2efa2174353af3f377c20573a011fb25d7`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar visualmente as 17 bolinhas, legibilidade dos nomes, três colunas sem sobreposição, conteúdo abaixo da fileira correta, recolhimento ao tocar novamente, troca entre módulos e ausência de regressão na grade flutuante e no farol. A PR #44 deve permanecer em rascunho até essa validação real.

'''

decision_block = '''## 01/08/2026 — a Home é o catálogo completo em bolinhas; a grade flutuante continua sendo o painel rápido

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

'''

current_status = status.read_text(encoding="utf-8")
status_header = "# Rota Certa — Estado do projeto\n\n"
if "## 01/08/2026 — 0.1.175 (5360)" not in current_status:
    if not current_status.startswith(status_header):
        raise SystemExit("Cabecalho de PROJECT_STATUS.md inesperado")
    status.write_text(status_header + status_block + current_status[len(status_header):], encoding="utf-8")

current_decisions = decisions.read_text(encoding="utf-8")
decisions_header = "# Rota Certa — Decisões técnicas\n\n"
if "## 01/08/2026 — a Home é o catálogo completo em bolinhas" not in current_decisions:
    if not current_decisions.startswith(decisions_header):
        raise SystemExit("Cabecalho de DECISIONS.md inesperado")
    decisions.write_text(decisions_header + decision_block + current_decisions[len(decisions_header):], encoding="utf-8")

print("DOCUMENTACAO_0175_ATUALIZADA")
