from pathlib import Path

STATUS = Path("docs/PROJECT_STATUS.md")
DECISIONS = Path("docs/DECISIONS.md")

status_marker = "## 01/08/2026 — 0.1.174 (5350) — conteúdo dos módulos dentro do próprio expander"
status_entry = '''## 01/08/2026 — 0.1.174 (5350) — conteúdo dos módulos dentro do próprio expander

- **Branch:** `agent/home-module-launcher-0.1.174`; **PR:** #43, empilhada sobre `agent/deterministic-shortcut-grid-0.1.173`, aberta, em rascunho e sem merge.
- **Commit funcional validado:** `c617f7a558d8f77a5f6f805762b8d0ff3e58842f`.
- **Pedido do usuário:** ao tocar em um expander, mostrar o conteúdo do módulo imediatamente dentro do próprio cartão, sem renderizá-lo no final da página e sem obrigar o usuário a percorrer toda a Home.
- **Evidência:** o vídeo real `1000878234.mp4` mostrou que a lista de módulos era expandida no topo, mas o conteúdo selecionado continuava sendo renderizado por um `SettingsScreen` global depois de todos os cartões.
- **Causa:** `ShortcutModulesHome0171` apenas alterava `selectedBubbleGroup`; em seguida, a Home sempre compunha um único `SettingsScreen` fora da lista. O expander não possuía o conteúdo do módulo como filho visual.
- **Correção aplicada:**
  - cada módulo passa a compor seu conteúdo dentro do próprio `Card` expandido;
  - somente um módulo permanece aberto por vez, controlado por `HomeModuleExpansionPolicy0174`;
  - tocar novamente no módulo aberto recolhe o conteúdo no mesmo local;
  - removido o botão genérico `Abrir módulo` dos módulos internos;
  - Destino, Rota, Alertas, Locais, Radares, Aparência, Permissões, Backup, Cards, Encerrar e Relatórios são renderizados inline no expander correspondente;
  - módulos que exigem Activity própria mantêm, dentro do próprio expander, resumo e botão com ação específica;
  - removido o `SettingsScreen` global que aparecia após toda a lista;
  - grade flutuante, toque simples/longo restaurado e contrato do farol foram preservados.
- **Arquivos principais alterados/materializados:**
  - `app/build.gradle.kts`;
  - `app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt`;
  - novo `app/src/main/java/br/com/mapeiaia/rotacerta/HomeModuleExpansionPolicy0174.kt`;
  - novo teste `HomeModuleExpansionPolicy0174Test.kt`;
  - contrato legado `ShortcutLongPressContract0171Test.kt` atualizado para validar a arquitetura inline sem reintroduzir personalização;
  - `scripts/fix_home_inline_modules_0174.patch.gz.b64`;
  - `scripts/fix_home_inline_modules_0174_test_contract.py`;
  - `.github/workflows/build-rota-certa-0.1.174.yml`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt` e `LiveRideAccessibilityService.kt` permaneceram preservados por checksum. Não houve alteração em permissões, parser, OCR automático, rota, Casa/Alfinetes, confirmação de card, cores, km ou cancelamento de resultado atrasado.
- **Testes e validações:** materialização completa 0.1.151–0.1.173; aplicação limpa do patch; contrato estrutural inline; política de apenas um expander aberto; 244 testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falha intermediária localizada:** o primeiro pipeline chegou aos testes e falhou porque o contrato legado 0.1.171 ainda exigia a antiga interface de abertura. O teste foi atualizado para exigir disponibilidade de todos os módulos, conteúdo dentro do cartão, ausência do botão genérico e preservação do toque longo determinístico e das confirmações sensíveis.
- **Workflow funcional validado:** `Build Rota Certa 0.1.174`, run `30698222636`, job `91364596001`; todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.174-inline-home-modules-validated`, ID `8818002744`, retenção até 30/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.174`, versionCode `5350`.
- **APK:** `rota-certa-0.1.174-modulos-inline-validado.apk`, 55.976.915 bytes.
- **SHA-256 do APK:** `ce3560232b905499246d1d215784fb18af3162c9be8aeb005f587df605e0ba5b`.
- **SHA-256 do ZIP do artifact:** `efdf7ebf547159b06e187216c611ac81a8b711c884f15cfcff157dd9892d9444`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar visualmente que o conteúdo surge logo abaixo do título do módulo tocado, que o módulo anterior recolhe, que não há salto ao fim da página, que os botões de Activities externas abrem o destino correto e que a grade e o farol não regrediram. A PR #43 deve continuar em rascunho até essa validação real.

'''

decision_marker = "## 01/08/2026 — o conteúdo do módulo pertence ao próprio expander"
decision_entry = '''## 01/08/2026 — o conteúdo do módulo pertence ao próprio expander

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

'''

status = STATUS.read_text(encoding="utf-8")
if status_marker not in status:
    prefix = "# Rota Certa — Estado do projeto\n\n"
    if not status.startswith(prefix):
        raise SystemExit("Cabeçalho inesperado em PROJECT_STATUS.md")
    STATUS.write_text(prefix + status_entry + status[len(prefix):], encoding="utf-8")

decisions = DECISIONS.read_text(encoding="utf-8")
if decision_marker not in decisions:
    prefix = "# Rota Certa — Decisões técnicas\n\n"
    if not decisions.startswith(prefix):
        raise SystemExit("Cabeçalho inesperado em DECISIONS.md")
    DECISIONS.write_text(prefix + decision_entry + decisions[len(prefix):], encoding="utf-8")

print("DOCUMENTACAO_0_1_174_ATUALIZADA")
