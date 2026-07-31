# Rota Certa — Estado do projeto

## 31/07/2026 — 0.1.171 (5320) — módulos na Home e toque longo personalizável

- **Branch:** `agent/customizable-shortcut-long-press-0.1.171`; **PR:** #40, rascunho e sem merge na `main`.
- **Commit funcional validado:** `b9a3984c6bcae40c2abe5959025ec9e0b1d60cdd`.
- **Pedido do usuário:** manter todos os módulos acessíveis diretamente na tela inicial, deixar a grade flutuante somente para execução rápida e permitir que cada módulo configure o comportamento de manter pressionado o seu atalho, sem apagar decisões anteriores.
- **Situação anterior:** recursos importantes ficavam espalhados pelas abas e pela grade da bolinha. Quando o processo ou o serviço de acessibilidade encerrava, a bolinha desaparecia e a grade deixava de ser uma entrada confiável para abrir diagnóstico e outros módulos. O gesto longo também era definido no código como ação secundária ou, quando ausente, repetição da ação principal, sem escolha individual do usuário.
- **Causa arquitetural:** navegação completa e execução rápida estavam acopladas ao mesmo painel de sobreposição. Além disso, a grade aplicava um fallback implícito no toque longo, impossibilitando personalização por recurso e dificultando preservar gestos diferentes de forma explícita.
- **Correção aplicada:**
  - todos os 16 módulos registrados em `BubbleShortcutCatalog` passam a aparecer diretamente na Home em cartões expansíveis;
  - cada cartão possui explicação, botão `Abrir módulo` e configuração `Ação ao manter pressionado o atalho`;
  - opções: manter comportamento atual, não fazer nada, abrir módulo, executar ação principal e, quando o recurso possuir, executar ação secundária;
  - padrão de migração `PreserveExisting`: conserva exatamente o gesto longo anterior de cada atalho até o usuário alterar;
  - toque simples da grade continua determinístico e executa a ação principal já existente;
  - ações longas sensíveis escolhidas pelo usuário passam por confirmação antes de executar;
  - preferências são locais e pequenas, em `SharedPreferences`, sem polling, novo serviço ou trabalho em segundo plano;
  - diagnóstico e geração manual de relatório ficam acessíveis pela Home mesmo sem a bolinha;
  - falha ao abrir um módulo encerra de forma segura e não altera o estado do farol.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_home_modules_long_press_0171.py.part00` a `part04`;
  - `.github/workflows/build-rota-certa-0.1.171.yml`;
  - `app/build.gradle.kts`;
  - `BubbleShortcutModule.kt`;
  - `BubbleShortcutOverlayController.kt`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - novo `ShortcutLongPressPolicy0171.kt`;
  - novos testes `ShortcutLongPressPolicy0171Test.kt` e `ShortcutLongPressContract0171Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, XML de acessibilidade, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, políticas 0.1.166–0.1.170, Casa/Alfinetes, confirmação do card, cores, quilometragem, OCR e rota permaneceram byte a byte preservados em relação à árvore 0.1.170.
- **Falha intermediária localizada:** a primeira execução compilou os contratos, mas o compilador encontrou `Unresolved reference: context` no novo cartão Compose. Foi incluído somente `LocalContext.current` no componente afetado e todo o pipeline foi reexecutado.
- **Testes executados:** materialização completa 0.1.151–0.1.171; aplicação do patch duas vezes e idempotência; fronteira exata; preservação de hashes; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP; Manifest; DEX; pacote, versão, versionCode e assinatura validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.171`, run `30631301134`, job `91158023794`, todos os passos concluídos com sucesso.
- **Artifact funcional:** `rota-certa-0.1.171-home-modules-custom-long-press-validated`, ID `8793578999`, retenção até 29/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.171`, versionCode `5320`.
- **APK:** `rota-certa-0.1.171-home-modulos-toque-longo-personalizado-validado.apk`, 55.927.659 bytes.
- **SHA-256 do APK:** `7dd5fa7fb9dfe86254f9bc7ffc653570a690be15dbaf8d29749613a2c8231b83`.
- **SHA-256 do ZIP do artifact:** `88d04403df4084ec39b13f0b390b4d7e38efdb3531b5a223ac527fe4fadc025a`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências e riscos:** instalar no Samsung SM-S911B/Android 16; confirmar que todos os módulos aparecem e abrem pela Home; verificar persistência de cada escolha após reiniciar; testar o comportamento padrão anterior e as cinco opções; confirmar diálogo em ações sensíveis; gerar relatório pela Home com a bolinha ausente; validar também que a contenção de crash 0.1.170 continua estável durante uma oferta real. A PR permanece em rascunho até essa validação prática.

## 31/07/2026 — 0.1.170 (5310) — falha do despertar por notificação contida

- **Branch:** `agent/contain-notification-crash-0.1.170`; **PR:** #39, rascunho e sem merge na `main`.
- **Commit funcional validado:** `f50710959058054f45962f44e77ee76934bc0666`.
- **Pedido/evidência:** o vídeo real `1000876918.mp4` mostrou o Android encerrando o Rota Certa 0.1.169 e desativando a leitura ao vivo.
- **Causa e limite:** sem stack trace não foi atribuída uma linha única; a nova entrada síncrona e o trabalho assíncrono da notificação não tinham contenção externa.
- **Correção:** fronteiras fail-closed, circuito limitado de 60 segundos somente para notificações, cancelamento e limpeza transitória, diagnóstico limitado e preservação do restante do serviço.
- **Testes:** materialização 0.1.151–0.1.170; idempotência; fronteira; testes; Lint; `clean assembleDebug`; APK, Manifest, DEX, pacote, versão e assinatura.
- **Workflow final:** run `30628394153`, job `91148840468`, sucesso.
- **Artifact:** `rota-certa-0.1.170-accessibility-crash-contained-validated`, ID `8792402353`.
- **APK/SHA-256:** `rota-certa-0.1.170-acessibilidade-falha-contida-validado.apk` — `68ed1f6d7398aa36608a420c18e8f8d16bcd999693dfd9a29d8a9e9d6d785880`.

## 31/07/2026 — 0.1.169 (5300) — despertar pontual de cards sobrepostos

- **Branch:** `agent/fix-uber-overlay-event-gap-0.1.169`; **PR:** #38, rascunho e sem merge na `main`.
- **Causa comprovada:** card Uber sobre o launcher durante aproximadamente 25,9 segundos sem `ACCESSIBILITY_EVENT`; o serviço não escutava `TYPE_NOTIFICATION_STATE_CHANGED`.
- **Correção:** gatilho de notificação somente para pacote selecionado, token por geração, deduplicação, TTL de 12 segundos, máximo de quatro capturas, confirmação visual obrigatória e ausência de polling.
- **Workflow final:** run `30625877873`, job `91140903636`, sucesso.
- **Artifact:** ID `8791527911`; **APK SHA-256:** `0c11a2f7292516f0daf00cf10e30f52e7279f87200a209be78f647458a9d7915`.
- **Resultado em aparelho:** reprovado por encerramento mostrado no vídeo; supersedido pela contenção 0.1.170.

## 31/07/2026 — 0.1.168 (5290) — visão unificada validada

- **Branch:** `agent/farol-unified-visual-0.1.168`; **PR:** #37, rascunho e sem merge na `main`.
- **Correção:** primeiro quadro sem atraso; OCR espacial único; fusão com acessibilidade; segmentação por card; assinatura semântica; endereços incompletos bloqueados; tentativas limitadas e canceláveis.
- **Testes:** 237 testes; Android Lint; `clean assembleDebug`; ZIP; Manifest; DEX; pacote, versão e assinatura.
- **Workflow final:** run `30602408881`, job `91067728989`, sucesso.
- **Artifact:** ID `8782463049`; **APK SHA-256:** `386cbf256e471bcb6ab2bc79c8564ff9826dbf52bec2fac5bb5a98fd2fe69de2`.

## Histórico anterior

O estado completo registrado até 30/07/2026 foi preservado em [`docs/archive/PROJECT_STATUS-pre-0.1.168.md`](archive/PROJECT_STATUS-pre-0.1.168.md).
