from __future__ import annotations

import argparse
from pathlib import Path


def prepend_after_title(path: Path, section: str) -> None:
    text = path.read_text(encoding="utf-8")
    title, separator, rest = text.partition("\n")
    if not separator:
        raise RuntimeError(f"Invalid document without title: {path}")
    if "## 03/08/2026 — 0.1.183" in text or "## 03/08/2026 — Menu contextual" in text:
        return
    path.write_text(f"{title}\n\n{section.strip()}\n\n{rest.lstrip()}", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--artifact-id", required=True)
    parser.add_argument("--artifact-digest", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--functional-commit", required=True)
    args = parser.parse_args()

    root = Path.cwd().resolve()
    artifact_dir = Path(args.artifact_dir).resolve()
    apk_name = "rota-certa-0.1.183-menu-contextual-atalhos-validado.apk"
    apk_path = artifact_dir / apk_name
    sha_line = (artifact_dir / "sha256.txt").read_text(encoding="utf-8").strip()
    apk_sha = sha_line.split()[0]
    apk_size = apk_path.stat().st_size
    artifact_name = "rota-certa-0.1.183-contextual-shortcut-menu-validated"
    artifact_url = (
        f"https://github.com/kelaplicativos-rgb/rota-certa-android/actions/"
        f"runs/{args.run_id}/artifacts/{args.artifact_id}"
    )

    status_section = f'''## 03/08/2026 — 0.1.183 (5440) — menu contextual por bolinha da grade

- **Branch:** `{args.branch}`; **PR:** empilhada sobre a 0.1.182.
- **Commit funcional validado:** `{args.functional_commit}`.
- **Pedido do usuário:** ao tocar numa bolinha da grade, abrir um pop-up específico com ações rápidas relacionadas ao recurso e uma opção para abrir o módulo completo; exemplos: criar alerta, criar radar, usar GPS como destino, capturar agora e opções separadas de limpeza.
- **Situação anterior:** a 0.1.182 executava imediatamente a ação principal de cada atalho. Isso era rápido, porém não permitia escolher entre a tarefa imediata e a abertura do módulo, e podia executar uma ação por engano.
- **Causa:** o caminho direto da 0.1.182 normalizava qualquer toque para `PRIMARY_ACTION` e ignorava o menu contextual herdado, que era genérico e não oferecia botões úteis por recurso.
- **Correção aplicada:**
  - o toque na bolinha principal continua abrindo a grade sem atraso;
  - o toque seguinte numa bolinha abre imediatamente um menu contextual leve em `TYPE_ACCESSIBILITY_OVERLAY`;
  - Alertas: `Criar alerta aqui` e `Abrir módulo Alertas`;
  - Radares: `Criar radar neste local` e `Abrir módulo Radares`;
  - Destino: `Usar localização atual como destino` e `Abrir módulo Destino`;
  - Locais: `Salvar localização atual` e `Abrir módulo Locais`;
  - Capturar: `Capturar aplicativo e tela agora` e `Abrir aplicativos e cards`;
  - Respostas: `Criar resposta rápida` e `Abrir Respostas`;
  - Limpar: `Limpar área de transferência`, `Limpar cache do Rota Certa` e `Abrir módulo Limpar`;
  - a limpeza de cache é limitada ao cache do próprio aplicativo e roda fora da thread principal;
  - toque fora/Fechar não executa nada e arraste continua cancelando o clique;
  - Alertas e Locais mantêm seus editores reais em sobreposição sem trocar automaticamente de aplicativo.
- **Arquivos principais alterados/materializados:** `LiveRideAccessibilityService.kt`, novo `ShortcutContextMenuPolicy0183.kt`, testes de política/contrato, `scripts/fix_contextual_shortcuts_0183.py`, script de build e workflow 0.1.183.
- **Fronteira protegida:** Manifest, permissões, `BubbleShortcutOverlayController`, catálogo de atalhos, Home, `DecisionEngine`, Google Maps, parser, OCR, confirmação real de card, Casa/Alfinetes, farol, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por SHA-256.
- **Testes e validações:** testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP/DEX, pacote, versão, versionCode, marcadores compilados, assinatura v2 e certificado validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.183`, run `{args.run_id}`.
- **Artifact:** `{artifact_name}`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **Link do artifact:** {artifact_url}
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.183`, versionCode `5440`.
- **APK:** `{apk_name}`, {apk_size} bytes.
- **SHA-256 do APK:** `{apk_sha}`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado de depuração do Rota Certa validado pelo build.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B/Android 16 e testar todas as bolinhas da grade. Confirmar especialmente Alertas, Radares, Destino, Capturar e Limpar; confirmar que o cache apagado é somente o do Rota Certa, que Fechar não executa ações e que o farol não pisca nem perde a decisão durante ofertas reais.'''

    decision_section = '''## 03/08/2026 — Menu contextual como contrato dos atalhos da grade

- **Decisão:** o toque numa bolinha interna da grade não executa mais uma ação automaticamente; ele abre um menu contextual específico daquela bolinha.
- **Motivo:** permitir escolher com clareza entre uma ação rápida e a abertura do módulo completo, reduzindo toques acidentais sem reintroduzir gestos demorados, toque triplo ou espera de 900 ms.
- **Módulos afetados:** somente o despacho da grade em `LiveRideAccessibilityService` e a política de rótulos do menu contextual.
- **Condições para revisão:** revisar apenas se a validação real mostrar que o terceiro toque necessário para escolher a ação ficou lento ou se algum aplicativo bloquear `TYPE_ACCESSIBILITY_OVERLAY`. Não restaurar execução automática global sem pedido explícito.
- **Limite de limpeza:** `Limpar cache` pode apagar apenas `cacheDir` do próprio Rota Certa; configurações, banco, cards, destinos e dados de outros aplicativos não podem ser apagados por essa ação.'''

    prepend_after_title(root / "docs/PROJECT_STATUS.md", status_section)
    prepend_after_title(root / "docs/DECISIONS.md", decision_section)

    validation_dir = root / "validation/0.1.183"
    validation_dir.mkdir(parents=True, exist_ok=True)
    validation_text = (
        "Rota Certa 0.1.183 (5440)\n"
        f"branch={args.branch}\n"
        f"functional_commit={args.functional_commit}\n"
        f"workflow_run={args.run_id}\n"
        f"artifact_name={artifact_name}\n"
        f"artifact_id={args.artifact_id}\n"
        f"artifact_digest={args.artifact_digest}\n"
        f"artifact_url={artifact_url}\n"
        f"apk={apk_name}\n"
        f"apk_size={apk_size}\n"
        f"apk_sha256={apk_sha}\n"
        "package=br.com.mapeiaia.rotacerta\n"
        "versionName=0.1.183\n"
        "versionCode=5440\n"
        "tests=passed\n"
        "lint=passed\n"
        "assembleDebug=passed\n"
        "signature_v2=valid\n"
        "real_device_validation=pending\n"
    )
    (validation_dir / "latest.txt").write_text(validation_text, encoding="utf-8")


if __name__ == "__main__":
    main()
