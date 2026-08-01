# Rota Certa — Estado do projeto

## 01/08/2026 — 0.1.174 (5350) — conteúdo dos módulos dentro do próprio expander

- **Branch:** `agent/home-module-launcher-0.1.174`; **PR:** #43, empilhada sobre `agent/deterministic-shortcut-grid-0.1.173`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `c617f7a558d8f77a5f6f805762b8d0ff3e58842f`; **head final validado:** `93abd6d02cae7a3a5eb6f1635e53971f3aafe55c`.
- **Pedido do usuário:** ao tocar em um expander, mostrar o conteúdo do módulo imediatamente dentro do próprio cartão, sem renderizá-lo no final da página e sem obrigar o usuário a percorrer toda a Home.
- **Evidência:** o vídeo real `1000878234.mp4` mostrou que a lista de módulos era expandida no topo, mas o conteúdo selecionado continuava sendo renderizado por um `SettingsScreen` global depois de todos os cartões.
- **Causa:** `ShortcutModulesHome0171` apenas alterava `selectedBubbleGroup`; em seguida, a Home sempre compunha um único `SettingsScreen` fora da lista. O expander não possuía o conteúdo do módulo como filho visual.
- **Correção aplicada:** cada módulo compõe seu conteúdo dentro do próprio `Card` expandido; apenas um permanece aberto; tocar novamente recolhe; abrir outro fecha o anterior; o botão genérico `Abrir módulo` foi removido dos módulos internos; o painel global no fim da lista foi eliminado; módulos com Activity própria mantêm resumo e botão específico dentro do expander.
- **Arquivos principais alterados/materializados:** `app/build.gradle.kts`, `MainActivity.kt`, novo `HomeModuleExpansionPolicy0174.kt`, testes da política e contratos, `scripts/fix_home_inline_modules_0174.patch.gz.b64`, contrato do patch e workflow 0.1.174.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt` e `LiveRideAccessibilityService.kt` permaneceram preservados por checksum. Não houve alteração em permissões, parser, OCR automático, rota, Casa/Alfinetes, confirmação de card, cores, km ou cancelamento de resultado atrasado.
- **Testes e validações:** materialização completa 0.1.151–0.1.173; checksum exato do patch `1a0622e82db7aa3468851a7fcfab324d885b7c614567de0f4485e8553784e154`; aplicação limpa; contratos inline; 244 testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Workflow final validado:** `Build Rota Certa 0.1.174`, run `30702465004`, job `91375754777`; todos os passos concluídos com sucesso.
- **Artifact final:** `rota-certa-0.1.174-inline-home-modules-validated`, ID `8819317727`, retenção até 30/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.174`, versionCode `5350`.
- **APK:** `rota-certa-0.1.174-modulos-inline-validado.apk`, 55.976.915 bytes.
- **SHA-256 do APK:** `ce3560232b905499246d1d215784fb18af3162c9be8aeb005f587df605e0ba5b`.
- **SHA-256 do ZIP do artifact:** `5ec230c94831f9c9630883e7c81d54e1401b0b68747e0ae8649e9113fe613057`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e próxima validação:** instalar no Samsung SM-S911B/Android 16 e confirmar visualmente que o conteúdo surge logo abaixo do título do módulo tocado, que o módulo anterior recolhe, que não há salto ao fim da página, que os botões de Activities externas abrem o destino correto e que a grade e o farol não regrediram. A PR #43 deve continuar em rascunho até essa validação real.

## Histórico anterior

O estado completo anterior permanece no histórico Git da branch e nos documentos das versões 0.1.173 e anteriores.
