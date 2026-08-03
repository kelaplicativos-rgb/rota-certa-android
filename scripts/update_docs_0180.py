from pathlib import Path

STATUS_MARKER = "## 03/08/2026 — 0.1.180 (5410) — três gestos configuráveis por bolinha"
STATUS_ENTRY = r'''
## 03/08/2026 — 0.1.180 (5410) — três gestos configuráveis por bolinha

- **Branch:** `agent/per-shortcut-gesture-config-0.1.180`; **PR:** #51, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `dcc2babd102efb0fc3af09e9db4fa99d05409f4a`.
- **Pedido do usuário:** cada bolinha da grade flutuante deve permitir configurar separadamente o toque rápido e o gesto de segurar por 1,5 segundo; segurar a própria bolinha por 5 segundos deve abrir sempre a configuração daquela entrada. As duas ações configuráveis devem aceitar executar imediatamente, abrir módulo ou não fazer nada, e o editor deve permitir excluir a bolinha da grade.
- **Situação anterior:** a 0.1.179 fixava o toque curto na ação principal, o toque de 2 segundos na abertura do módulo e reservava 5 segundos apenas para abrir a Central pela bolinha principal. Não existia editor individual aberto diretamente pela bolinha pressionada.
- **Causa:** o modelo persistido armazenava somente recurso, nome, ícone e ativação. O overlay recebia callbacks fixos para toque curto e toque longo, sem identificar duas ações configuráveis por entrada e sem uma resolução exclusiva para o limiar de 5 segundos.
- **Correção aplicada:**
  - cada entrada persiste `quickTapAction` e `holdAction` usando somente o enum seguro `ExecuteImmediately`, `OpenModule` ou `DoNothing`;
  - toque rápido é resolvido ao soltar antes de 1,5 segundo;
  - segurar por pelo menos 1,5 segundo e soltar antes de 5 segundos executa a segunda ação configurada;
  - ao atingir 5 segundos, abre imediatamente o editor da bolinha específica e nenhuma ação de 1,5 segundo é executada antes;
  - o editor individual exibe os botões `Toque rápido`, `Segurar 1,5 s`, as três opções de ação e `Excluir da grade`;
  - nome, recurso, ícone e visibilidade continuam configuráveis;
  - dados salvos pela 0.1.179 migram deterministicamente para toque rápido = executar imediatamente e 1,5 segundo = abrir módulo;
  - na bolinha Alertas, o padrão preserva toque rápido criando alerta pelo fluxo existente, 1,5 segundo abrindo o módulo e 5 segundos abrindo a configuração; o usuário pode inverter as duas primeiras ações;
  - nenhuma intent arbitrária, pacote externo, polling, observador contínuo ou serviço adicional foi introduzido.
- **Arquivos principais alterados/materializados:**
  - `ShortcutGridCustomization0179.kt`;
  - `BubbleShortcutOverlayController.kt`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - `ShortcutGridCustomization0179Test.kt`;
  - `ShortcutGridCustomizationContract0179Test.kt`;
  - `ShortcutLongPressContract0171Test.kt`;
  - `scripts/build_rota_certa_0180.sh`;
  - `.github/workflows/build-rota-certa-0.1.180.yml`;
  - `patches/per-shortcut-gesture-config-0180.patch.gz.b64.chunk00` a `chunk06`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, OCR, confirmação real de card, Casa/Alfinetes, cores, km, cancelamento de resultados antigos, radares, alertas direcionais, repositórios e fala permaneceram inalterados e foram conferidos por checksums.
- **Testes e validações:** testes unitários/de contrato aprovados, incluindo o caso em que manter pressionado até 5 segundos não dispara a ação intermediária; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP e DEX, pacote, versão, versionCode, assinatura v2, certificado e marcadores dos gestos validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.180`, run `30806876085`, job `91664179413`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.180-per-shortcut-gesture-config-validated`, ID `8853609304`, 33.735.866 bytes, retenção até 01/11/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.180`, versionCode `5410`, minSdk 26 e targetSdk 35.
- **APK:** `rota-certa-0.1.180-gestos-atalhos-configuraveis-validado.apk`, 56.058.831 bytes.
- **SHA-256 do APK:** `180d76d50071cd401f3934fe8a391c7e70acf2fa6c575f1a66e1fe8d0bd9f493`.
- **SHA-256 do ZIP do artifact:** `3fd5e29ebcd5899d8584111971de8c19f37bb2ffab0ba50cef0768c05ecf3a60`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B com Android 16 e validar: toque rápido e 1,5 segundo com as três opções; inversão dos gestos da bolinha Alertas; 5 segundos abrindo o editor da entrada correta sem criar alerta nem abrir módulo; exclusão, migração, reordenação e persistência; ausência de regressão do farol, rota, radares e alertas. A PR #51 deve permanecer em rascunho até essa validação real.
'''.strip()

DECISION_MARKER = "## 03/08/2026 — três faixas de gesto por bolinha com ação intermediária somente ao soltar"
DECISION_ENTRY = r'''
## 03/08/2026 — três faixas de gesto por bolinha com ação intermediária somente ao soltar

- **Decisão:** cada entrada da grade flutuante possui toque rápido configurável, gesto configurável de 1,5 segundo e gesto reservado de 5 segundos para abrir o editor daquela própria entrada.
- **Resolução temporal:** soltar antes de 1,5 segundo resolve `QuickTap`; soltar entre 1,5 e 5 segundos resolve `HoldOnePointFiveSeconds`; atingir 5 segundos resolve `Customize`. Movimento além do limite cancela todos os gestos.
- **Proteção contra execução dupla:** a ação de 1,5 segundo nunca é disparada por temporizador. Ela é executada somente no `ACTION_UP`; assim, quem continua segurando até 5 segundos abre a configuração sem executar antes a ação intermediária.
- **Ações permitidas:** os dois slots configuráveis aceitam exclusivamente `ExecuteImmediately`, `OpenModule` e `DoNothing`. A persistência guarda apenas enums conhecidos e IDs do catálogo seguro; não aceitar intents, pacotes, URIs ou comandos arbitrários.
- **Executar imediatamente:** reutiliza a ação rápida existente do recurso. Quando o recurso não possui ação rápida dedicada, mantém o fallback seguro para sua ação principal.
- **Abrir módulo:** usa o roteamento autoritativo existente por ID do módulo, grupo e aba, preservando o foco único da Home e o lançamento seguro de Activities.
- **Editor individual:** segurar uma bolinha por 5 segundos abre diretamente sua entrada. O menu deve exibir nome, recurso, ícone, visibilidade, botão de toque rápido, botão de 1,5 segundo, opção `Não fazer nada` e `Excluir da grade`.
- **Migração:** configurações 0.1.179 sem campos de gesto migram para toque rápido = executar imediatamente e 1,5 segundo = abrir módulo, preservando o comportamento útil anterior.
- **Desempenho:** processamento estritamente orientado aos eventos de toque. Não criar polling, coroutine recorrente, observador contínuo, captura, OCR ou serviço para medir gestos.
- **Fronteira protegida:** esta decisão não autoriza mudanças em Manifest, permissões, farol, parser, OCR, Google Maps, Casa/Alfinetes, decisão de cores, km, proteção contra resultados atrasados, radares ou alertas direcionais.
- **Condição para revisão:** revisar apenas se testes reais mostrarem conflito de duração em fabricante específico ou necessidade explícita de novas ações seguras. O limiar de 5 segundos continua reservado à configuração individual para garantir recuperação da entrada.
'''.strip()


def prepend(path: Path, heading: str, marker: str, entry: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if not text.startswith(heading):
        raise SystemExit(f"Cabeçalho inesperado em {path}")
    remainder = text[len(heading):].lstrip("\n")
    path.write_text(f"{heading}\n\n{entry}\n\n{remainder}", encoding="utf-8")


prepend(Path("docs/PROJECT_STATUS.md"), "# Rota Certa — Estado do projeto", STATUS_MARKER, STATUS_ENTRY)
prepend(Path("docs/DECISIONS.md"), "# Rota Certa — Decisões técnicas", DECISION_MARKER, DECISION_ENTRY)
