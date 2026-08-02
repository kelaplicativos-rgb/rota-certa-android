#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "## 02/08/2026 — 0.1.177 (5380) — módulos da grade abrem e entram na área visível"
DECISION_MARKER = "## 02/08/2026 — atalhos inline navegam pela identidade do módulo e recebem foco único"

status_entry = r'''## 02/08/2026 — 0.1.177 (5380) — módulos da grade abrem e entram na área visível

- **Branch:** `agent/fix-shortcut-module-focus-0.1.177`; **PR:** #46, empilhada sobre `agent/fix-shortcut-single-tap-0.1.176`, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `7da8ce4a50414566938151416f4ae860858daf46`.
- **Pedido e evidência do usuário:** o vídeo real `1000879762.mp4`, gravado no Samsung SM-S911B com Android 16 usando a 0.1.176, mostrou que o toque simples já abria Destino, WhatsApp, Valor, Financeiro, Limpar, Depurar, Respostas, Links e Encerrar, mas Alertas, Locais, Radares, Aparência e Backup apenas fechavam a grade e deixavam a Home no topo; Rota também não apresentava confirmação visual clara. `Capturar` abriu `Aplicativos e cards autorizados`, conforme o contrato atual.
- **Situação anterior:** a 0.1.176 corrigiu o bloqueio de Activities pelo Android, porém os módulos compostos dentro da Home não eram Activities dedicadas. Eles ainda eram despachados pelo caminho antigo de grupo/aba.
- **Causa:** `LiveRideAccessibilityService.executeShortcutModule` chamava `openResourceGroup(...)` para os módulos inline e enviava somente `EXTRA_OPEN_BUBBLE_GROUP`. A Home 0.1.175 controla a expansão por `EXTRA_OPEN_SHORTCUT_MODULE_0171`; portanto o grupo podia ser selecionado internamente sem que a bolinha e o painel correspondentes fossem expandidos. Mesmo quando expandido, um módulo abaixo da dobra podia permanecer fora da área visível.
- **Correção aplicada:**
  - Rota, Destino, Alertas, Locais, Radares, Aparência, Permissões, Backup, Relatórios e Configurações passam por `openShortcutModule0171(spec)` e recebem ID autoritativo, grupo e aba;
  - a Home mantém um `BringIntoViewRequester` leve por módulo;
  - depois que o módulo solicitado é composto, uma única chamada orientada ao evento traz sua fileira e seu painel para a área visível;
  - um novo Intent para o mesmo módulo repete o foco uma vez, sem polling, observador contínuo ou loop;
  - Activities dedicadas e ações imediatas permanecem no caminho seguro validado na 0.1.176;
  - o contrato de `Capturar` permanece abrir `Aplicativos e cards autorizados` no toque simples.
- **Arquivos principais alterados/materializados:**
  - `scripts/fix_shortcut_module_focus_0177.py`;
  - `scripts/build_rota_certa_0177.sh`;
  - `.github/workflows/build-rota-certa-0.1.177.yml`;
  - `LiveRideAccessibilityService.kt`;
  - `MainActivity.kt`;
  - novo `ShortcutModuleFocusPolicy0177.kt`;
  - novos testes `ShortcutModuleFocusPolicy0177Test.kt` e `ShortcutModuleFocusContract0177Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, `BubbleShortcutOverlayController.kt`, `BubbleShortcutModule.kt`, `ShortcutLongPressPolicy0171.kt` e `ShortcutActivityLaunchPolicy0176.kt` permaneceram idênticos por SHA-256. Não houve alteração em permissões, farol, parser, OCR, rota calculada, Casa/Alfinetes, confirmação real de card, cores, km, cancelamento de resultados antigos ou toque longo.
- **Testes e validações:** materialização completa 0.1.151–0.1.176; novos testes de política e contrato; todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP, DEX, pacote, versão, versionCode, assinatura v2 e certificado validados.
- **Falhas intermediárias localizadas:** a primeira execução parou na compilação porque a API de foco do Compose exige opt-in experimental; o `@OptIn` foi restrito ao componente da grade. A segunda execução compilou o aplicativo e parou apenas no teste de contrato por uso de `Files.readString` incompatível com o nível Java configurado; o teste foi ajustado para `Files.readAllBytes`, sem mudança funcional. A terceira execução passou integralmente.
- **Workflow funcional validado:** `Build Rota Certa 0.1.177`, run `30751876132`, job `91507226425`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.177-shortcut-module-focus-validated`, ID `8834751631`, 31.569.733 bytes, retenção até 31/10/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.177`, versionCode `5380`.
- **APK:** `rota-certa-0.1.177-modulos-grade-foco-validado.apk`, 55.993.295 bytes.
- **SHA-256 do APK:** `21996c09751c3704c82bcd575f2fadab1f48455a824a5d5a8b17f6fa4eb089c5`.
- **SHA-256 do ZIP do artifact:** `418dda808c2f5403bae31f11c25a7d9100fac50647ae8e821aa0818999a07008`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e repetir o toque simples nas 17 bolinhas. Priorizar Rota, Alertas, Locais, Radares, Aparência e Backup e confirmar que a Home rola diretamente para o painel correto. Confirmar também os outros 11 atalhos, o toque longo, o retorno da bolinha e a ausência de regressão do farol. A PR #46 permanece em rascunho até essa validação real.
'''

decision_entry = r'''## 02/08/2026 — atalhos inline navegam pela identidade do módulo e recebem foco único

- **Decisão:** todo atalho da grade flutuante cujo destino é um módulo composto dentro da Home deve enviar o ID autoritativo do módulo, além de grupo e aba; enviar somente o grupo não é navegação suficiente.
- **Motivo:** a Home expande a bolinha pelo ID do catálogo. Grupo e aba identificam o conteúdo, mas não determinam qual bolinha/painel precisa ficar aberto.
- **Foco visual:** após a composição do módulo solicitado, a Home executa uma única operação `bringIntoView` para a fileira e o painel correspondentes. O mesmo módulo pode receber novo foco quando chegar um novo Intent explícito.
- **Desempenho:** o foco é estritamente orientado ao toque e ao novo Intent. Não usar polling, temporizador recorrente, observador contínuo, OCR, captura ou trabalho em segundo plano para manter a posição.
- **Módulos afetados:** Rota, Destino, Alertas, Locais, Radares, Aparência, Permissões, Backup, Relatórios e Configurações da Home. Activities dedicadas continuam usando o lançador seguro da 0.1.176.
- **Capturar:** o toque simples continua abrindo `Aplicativos e cards autorizados`; mudança de finalidade exige pedido explícito e nova decisão.
- **Fronteira protegida:** Manifest, permissões, catálogo, toque longo, farol, parser, OCR, Google Maps, Casa/Alfinetes, confirmação real de card, decisão de cores, km e proteção contra resultado atrasado não podem ser alterados por esta navegação.
- **Condição para revisão:** revisar somente se a Home adotar navegação formal por rotas/telas independentes ou se teste real mostrar que o foco único não é suficiente em algum fabricante. Qualquer revisão deve continuar orientada a eventos e sem processamento contínuo.
'''

root = Path(".")
status_path = root / "docs/PROJECT_STATUS.md"
decisions_path = root / "docs/DECISIONS.md"

status = status_path.read_text(encoding="utf-8")
if STATUS_MARKER not in status:
    header = "# Rota Certa — Estado do projeto\n\n"
    if not status.startswith(header):
        raise SystemExit("Cabeçalho inesperado em PROJECT_STATUS.md")
    status = header + status_entry + "\n" + status[len(header):]
    status_path.write_text(status, encoding="utf-8")

decisions = decisions_path.read_text(encoding="utf-8")
if DECISION_MARKER not in decisions:
    header = "# Rota Certa — Decisões técnicas\n\n"
    if not decisions.startswith(header):
        raise SystemExit("Cabeçalho inesperado em DECISIONS.md")
    decisions = header + decision_entry + "\n" + decisions[len(header):]
    decisions_path.write_text(decisions, encoding="utf-8")

validation = root / "validation/0.1.177/latest.txt"
validation.parent.mkdir(parents=True, exist_ok=True)
validation.write_text(
    "head=7da8ce4a50414566938151416f4ae860858daf46\n"
    "run=30751876132\n"
    "job=91507226425\n"
    "conclusion=success\n"
    "artifact_id=8834751631\n"
    "artifact_name=rota-certa-0.1.177-shortcut-module-focus-validated\n"
    "artifact_zip_sha256=418dda808c2f5403bae31f11c25a7d9100fac50647ae8e821aa0818999a07008\n"
    "artifact_zip_bytes=31569733\n"
    "apk=rota-certa-0.1.177-modulos-grade-foco-validado.apk\n"
    "apk_sha256=21996c09751c3704c82bcd575f2fadab1f48455a824a5d5a8b17f6fa4eb089c5\n"
    "apk_bytes=55993295\n"
    "package=br.com.mapeiaia.rotacerta\n"
    "version=0.1.177\n"
    "version_code=5380\n"
    "signature_scheme_v2=true\n"
    "certificate_sha256=d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd\n",
    encoding="utf-8",
)
print("DOCUMENTACAO_0177_FINALIZADA")
