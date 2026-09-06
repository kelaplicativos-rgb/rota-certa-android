from __future__ import annotations

import argparse
from pathlib import Path


def prepend_after_title(path: Path, section: str, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    title, separator, rest = text.partition("\n")
    if not separator:
        raise RuntimeError(f"Invalid document without title: {path}")
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
    apk_name = "rota-certa-0.1.185-card-individual-indrive-validado.apk"
    apk_path = artifact_dir / apk_name
    apk_sha = (artifact_dir / "sha256.txt").read_text(encoding="utf-8").split()[0]
    apk_size = apk_path.stat().st_size
    artifact_name = "rota-certa-0.1.185-indrive-card-isolation-validated"
    artifact_url = (
        "https://github.com/kelaplicativos-rgb/rota-certa-android/actions/"
        f"runs/{args.run_id}/artifacts/{args.artifact_id}"
    )

    status = f'''## 05/08/2026 — 0.1.185 (5460) — card individual do inDrive e contenção de acessibilidade

- **Branch:** `{args.branch}`; **PR:** #56, empilhada sobre a 0.1.184.
- **Commit funcional validado:** `{args.functional_commit}`.
- **Pedido do usuário:** corrigir a mistura de endereços entre ofertas simultâneas do inDrive, impedir alternância/pisca da bolinha e conter a falha ao abrir o seletor de arquivos sem alterar rota, decisão, permissões ou outros módulos.
- **Situação anterior:** a tela de lista do inDrive podia combinar embarque de uma oferta com destino de outra, alternando distâncias e repintando a bolinha. Um evento explícito do `com.google.android.documentsui` também podia reutilizar uma raiz antiga do inDrive e provocar `NullPointerException` durante a leitura da árvore de acessibilidade.
- **Causa:** a confirmação universal considerava o texto completo da tela com dois endereços, sem provar qual card individual estava aberto. O pacote externo era rejeitado tarde demais e propriedades de `AccessibilityNodeInfo` ainda eram lidas sem contenção individual completa.
- **Correção aplicada:**
  - no inDrive, somente o modal individual com `Pedido de viagem`, ação de aceite e `Fechar` autoriza a leitura decisória;
  - o texto é recortado nos limites do card aberto e exclui ofertas de fundo;
  - feed/lista sem card individual falha fechado, limpa cor e km anteriores e permanece aguardando;
  - a confirmação vale para acessibilidade e para a recuperação/OCR, sem caminho alternativo de autorização;
  - pacote externo explícito é rejeitado antes de consultar uma raiz possivelmente antiga;
  - leituras de raiz, pacote, texto, descrição, quantidade de filhos e filhos são isoladas com contenção por nó;
  - falha inesperada limpa também o visual da bolinha, impedindo estado persistido divergente.
- **Arquivos principais materializados:** `LiveRideAccessibilityService.kt`, `RideCardConfirmationPolicy0185.kt`, `ExplicitPackageTransitionPolicy0185.kt`, testes das duas políticas, `app/build.gradle.kts`, patch, script e workflow 0.1.185.
- **Fronteira protegida:** Manifest/permissões, `GpsAddressResolver`, `DecisionEngine`, Google Maps, parser genérico, radares, alertas direcionais, repositórios, fala, Casa/Alfinetes e catálogo da grade permaneceram inalterados por SHA-256.
- **Testes:** testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; pacote, versão, ZIP/DEX, assinatura v2, certificado, marcadores compilados e SHA-256 validados.
- **Workflow:** `Build Rota Certa 0.1.185`, run `{args.run_id}`.
- **Artifact:** `{artifact_name}`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **Link do artifact:** {artifact_url}
- **APK:** `{apk_name}`, {apk_size} bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.185`; versionCode `5460`.
- **SHA-256 do APK:** `{apk_sha}`.
- **Pendências:** instalar no Samsung SM-S911B/Android 16 e validar com várias ofertas simultâneas do inDrive, abertura/fechamento do card individual, troca para DocumentsUI, saída do card, ausência de mistura de endereços, limpeza imediata de km/cor e ausência de pisca. O PR permanece em rascunho até essa validação real.'''

    decision = '''## 05/08/2026 — o inDrive só autoriza decisão com card individual confirmado

- **Decisão:** texto agregado da lista/feed do inDrive nunca autoriza verde ou vermelho, mesmo contendo dois ou mais endereços.
- **Confirmação:** a decisão exige o modal individual com marcadores coerentes de pedido, ação de aceite e fechamento; o recorte deve excluir ofertas visíveis ao fundo.
- **Fail-closed:** sem card individual confirmado, limpar imediatamente decisão, km e cor anteriores e manter o estado de espera do aplicativo monitorado.
- **Transição de pacote:** um pacote externo explícito deve ser rejeitado antes de consultar ou reutilizar a raiz ativa anterior.
- **Acessibilidade:** cada leitura de `AccessibilityNodeInfo` deve ser contida individualmente; uma propriedade defeituosa não pode derrubar o evento nem conservar visual antigo.
- **OCR/recuperação:** nenhum caminho alternativo pode contornar a mesma política de confirmação do card.
- **Fronteira:** não alterar `DecisionEngine`, cálculo de rota, parser genérico, Manifest, permissões, Casa/Alfinetes, grade, alertas ou radares para resolver esta regressão.
- **Condição para revisão:** revisar marcadores somente após evidência real de mudança da interface do inDrive, mantendo a exigência de identidade inequívoca do card individual.'''

    prepend_after_title(root / "docs/PROJECT_STATUS.md", status, "## 05/08/2026 — 0.1.185")
    prepend_after_title(root / "docs/DECISIONS.md", decision, "## 05/08/2026 — o inDrive só autoriza")

    validation_dir = root / "validation/0.1.185"
    validation_dir.mkdir(parents=True, exist_ok=True)
    (validation_dir / "latest.txt").write_text(
        "Rota Certa 0.1.185 (5460)\n"
        f"branch={args.branch}\nfunctional_commit={args.functional_commit}\n"
        f"workflow_run={args.run_id}\nartifact_name={artifact_name}\n"
        f"artifact_id={args.artifact_id}\nartifact_digest={args.artifact_digest}\n"
        f"artifact_url={artifact_url}\napk={apk_name}\napk_size={apk_size}\n"
        f"apk_sha256={apk_sha}\npackage=br.com.mapeiaia.rotacerta\n"
        "versionName=0.1.185\nversionCode=5460\ntests=passed\nlint=passed\n"
        "assembleDebug=passed\nsignature_v2=valid\nreal_device_validation=pending\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
