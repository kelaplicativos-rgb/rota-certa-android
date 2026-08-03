from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


branch = env("RC_BRANCH")
commit = env("RC_COMMIT")
run_id = env("RC_RUN_ID")
job_id = env("RC_JOB_ID")
artifact_name = env("RC_ARTIFACT_NAME")
artifact_id = env("RC_ARTIFACT_ID")
artifact_size = env("RC_ARTIFACT_SIZE")
artifact_digest = env("RC_ARTIFACT_DIGEST")
apk_name = env("RC_APK_NAME")
apk_size = env("RC_APK_SIZE")
apk_sha256 = env("RC_APK_SHA256")

status_path = ROOT / "docs/PROJECT_STATUS.md"
decisions_path = ROOT / "docs/DECISIONS.md"
validation_path = ROOT / "validation/0.1.182/latest.txt"

status = status_path.read_text(encoding="utf-8")
status_heading = "## 03/08/2026 — 0.1.182 (5430) — grade direta em no máximo dois toques"
if status_heading not in status:
    title = "# Rota Certa — Estado do projeto\n\n"
    if not status.startswith(title):
        raise RuntimeError("Unexpected PROJECT_STATUS.md title")
    section = f'''{status_heading}

- **Branch:** `{branch}`; **PR:** #53, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `{commit}`.
- **Pedido do usuário:** alinhar a grade para ser realmente um conjunto de atalhos: no máximo um toque na bolinha principal para abrir a grade e outro toque na bolinha escolhida para executar sua ação.
- **Situação anterior:** a 0.1.180 aguardava uma janela de 900 ms para distinguir toque triplo e classificava também o gesto de 1,5 segundo. A 0.1.181 ainda podia abrir um painel genérico antes da função principal. Na prática, o toque rápido tinha atraso e alguns recursos exigiam uma terceira interação.
- **Causa:** reconhecimento de edição e navegação completa estavam misturados ao caminho crítico de execução rápida. A grade acumulava `tapCount0180`, finalizador atrasado, toque de 1,5 segundo e despacho `overlay first`, contrariando o papel de atalho.
- **Correção aplicada:**
  - `ACTION_UP` válido executa imediatamente uma única ação, sem aguardar toque triplo;
  - removida a classificação de 1,5 segundo do toque nas bolinhas da grade;
  - preferências antigas de gesto são normalizadas para a ação principal e não conseguem desativar o atalho;
  - a bolinha `+` permanece como caminho único para editar nome, recurso, ícone, ordem e visibilidade;
  - atalhos comuns não param mais no painel informativo genérico e seguem diretamente para sua ação/módulo real;
  - `Salvar local` e `Alertas` preservam o pop-up real da própria ação sobre a tela atual;
  - nenhum polling, serviço, observador contínuo ou alocação recorrente foi adicionado.
- **Arquivos principais alterados/materializados:** `BubbleShortcutOverlayController.kt`, `LiveRideAccessibilityService.kt`, `MainActivity.kt`, novo `ShortcutDirectTapPolicy0182.kt`, testes de política/contrato, `scripts/fix_direct_shortcuts_0182.py`, compatibilidade da base 0.1.181, script de build e workflow 0.1.182.
- **Falhas intermediárias localizadas:** run `30846355142`, job `91795491937`, interrompido porque o transformador procurava o nome antigo do contrato de cinco segundos, enquanto a base já usava toque triplo; run `30848470633`, job `91802437114`, avançou à compilação dos testes 0.1.182 e encontrou duas aspas internas inválidas em asserções Kotlin geradas. Ambos os pontos foram corrigidos somente nos transformadores/contratos, sem mascarar testes, sem erro de memória e sem alterar a lógica funcional do aplicativo.
- **Fronteira protegida:** Manifest e permissões, geocodificação, `DecisionEngine`, Google Maps, parser, OCR, confirmação real de card, Casa/Alfinetes, farol, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por SHA-256.
- **Testes e validações:** todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/DEX, pacote, versão, versionCode, marcadores compilados, assinatura v2 e certificado validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.182`, run `{run_id}`, job `{job_id}`; todos os passos concluídos com sucesso.
- **Artifact:** `{artifact_name}`, ID `{artifact_id}`, {artifact_size} bytes, digest `{artifact_digest}`.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.182`, versionCode `5430`.
- **APK:** `{apk_name}`, {apk_size} bytes.
- **SHA-256 do APK:** `{apk_sha256}`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar a sequência bolinha principal → cada atalho, confirmando execução com exatamente dois toques totais, ausência do atraso de 900 ms, ausência de ação por arraste, funcionamento da bolinha `+`, pop-ups reais de Local/Alerta e ausência de regressão do farol durante ofertas reais. A PR #53 permanece em rascunho até essa validação prática.

'''
    status_path.write_text(title + section + status[len(title):], encoding="utf-8")

decisions = decisions_path.read_text(encoding="utf-8")
decision_heading = "## 2026-08-03 — A grade flutuante executa em no máximo dois toques"
if decision_heading not in decisions:
    title = "# Rota Certa — Decisões técnicas\n\n"
    if not decisions.startswith(title):
        raise RuntimeError("Unexpected DECISIONS.md title")
    section = f'''{decision_heading}

- **Decisão:** um toque na bolinha principal abre/fecha a grade; um toque na bolinha escolhida executa imediatamente sua ação principal. Não existe espera para toque triplo nem classificação de 1,5 segundo no caminho da grade.
- **Motivo:** a grade é uma superfície de ação rápida. Gestos múltiplos e painéis informativos intermediários aumentavam latência, ambiguidade e número de interações.
- **Configuração:** a bolinha permanente `+` é o caminho autoritativo para editar nome, recurso, ícone, ordem e visibilidade. Preferências legadas de toque rápido/segurar são ignoradas no despacho.
- **Exceções funcionais:** quando a própria ação exige dados ou confirmação, o segundo toque pode abrir diretamente o pop-up real dessa ação, como em `Salvar local` e `Alertas`; não pode abrir um painel genérico antes dela.
- **Módulos afetados:** `BubbleShortcutOverlayController`, despacho da grade em `LiveRideAccessibilityService`, Central de atalhos na `MainActivity` e política `ShortcutDirectTapPolicy0182`.
- **Condições para revisão:** somente mediante pedido explícito e com alternativa que preserve a meta de no máximo dois toques, não atrase o toque simples e não misture edição ao caminho crítico.
- **Validação:** commit `{commit}`, workflow run `{run_id}`, artifact `{artifact_id}`, APK SHA-256 `{apk_sha256}`.

'''
    decisions_path.write_text(title + section + decisions[len(title):], encoding="utf-8")

validation_path.parent.mkdir(parents=True, exist_ok=True)
validation_path.write_text(
    "\n".join(
        [
            "versionName=0.1.182",
            "versionCode=5430",
            "package=br.com.mapeiaia.rotacerta",
            f"branch={branch}",
            f"functional_commit={commit}",
            "pull_request=53",
            f"workflow_run={run_id}",
            f"workflow_job={job_id}",
            f"artifact_name={artifact_name}",
            f"artifact_id={artifact_id}",
            f"artifact_size_bytes={artifact_size}",
            f"artifact_digest={artifact_digest}",
            f"apk_name={apk_name}",
            f"apk_size_bytes={apk_size}",
            f"apk_sha256={apk_sha256}",
            "certificate_sha256=d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd",
            "main_bubble_tap=opens_grid",
            "shortcut_tap=executes_immediately",
            "maximum_total_taps=2",
            "triple_tap_wait=false",
            "shortcut_hold_classification=false",
            "configuration_route=plus_shortcut",
            "device_validation_pending=Samsung_SM-S911B_Android_16",
            "",
        ],
    ),
    encoding="utf-8",
)

print("docs_0_1_182_updated")
