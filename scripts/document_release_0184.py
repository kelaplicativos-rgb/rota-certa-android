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
    apk_name = "rota-certa-0.1.184-home-acoes-direcao-validado.apk"
    apk_path = artifact_dir / apk_name
    apk_sha = (artifact_dir / "sha256.txt").read_text(encoding="utf-8").split()[0]
    apk_size = apk_path.stat().st_size
    artifact_name = "rota-certa-0.1.184-home-action-catalog-directional-validated"
    artifact_url = (
        f"https://github.com/kelaplicativos-rgb/rota-certa-android/actions/"
        f"runs/{args.run_id}/artifacts/{args.artifact_id}"
    )

    status = f'''## 03/08/2026 — 0.1.184 (5450) — Home completa, atalhos por ação e direção rigorosa

- **Branch:** `{args.branch}`; **PR:** #55, empilhada sobre a 0.1.183.
- **Commit funcional validado:** `{args.functional_commit}`.
- **Pedido do usuário:** executar a evolução continuamente por etapas; colocar todos os módulos na Home; deixar a grade inicialmente vazia; permitir criar bolinhas independentes para cada ação real; restaurar o limite de dois toques totais; e impedir alertas/radares do sentido oposto.
- **Situação anterior:** a 0.1.183 armazenava módulos na grade e abria um menu contextual intermediário. A mesma bolinha agrupava ações distintas, como criar/restaurar backup ou limpar cache/área de transferência. O motor direcional aceitava uma única amostra de aproximação e tolerâncias angulares mais amplas.
- **Causa:** a persistência usava `shortcutId` de módulo, não identidade de ação. A Home não administrava cada ação individualmente e a grade continha uma bolinha `+`. No filtro direcional, aproximação podia incluir distância estável dentro da margem de erro do GPS.
- **Correção aplicada:**
  - criado catálogo tipado e fechado de ações internas, sem código, URI ou Intent arbitrária;
  - Home ampliada para 21 módulos, incluindo Permissões, Histórico, Frases e Rastreamento;
  - cada módulo oferece `Adicionar`/`Remover` para suas ações específicas;
  - ações independentes para criar/restaurar backup, limpar cache/clipboard, copiar texto, capturar card/pacote, valor, WhatsApp, alertas, locais, radar, destino, links, respostas e rastreamento;
  - instalações novas começam sem ações na grade; atualizações migram o JSON ou a antiga grade implícita;
  - limite rígido de 32 ações e bloqueio de duplicidade da mesma ação;
  - removida a bolinha `+` da grade; organização permanece na Home;
  - grade vazia abre a Home; grade configurada executa diretamente no toque seguinte;
  - alertas e radares exigem GPS recente/preciso, rumo, velocidade, alvo à frente e duas reduções reais de distância;
  - tolerâncias direcionais reduzidas e direção do deslocamento salva em novos alertas/radares manuais quando disponível;
  - alertas/radares antigos continuam compatíveis, mas ainda precisam estar à frente e em aproximação real.
- **Arquivos principais:** `ShortcutActionCatalog0184.kt`, `ShortcutActionHome0184.kt`, `ShortcutGridCustomization0179.kt`, `BubbleShortcutModule.kt`, `BubbleShortcutOverlayController.kt`, `MainActivity.kt`, `LiveRideAccessibilityService.kt`, `DirectionalAlertPolicy.kt`, `DirectionalProximityAlertEngine.kt`, `Models.kt`, testes, transformador, build e workflow 0.1.184.
- **Fronteira protegida:** Manifest/permissões, `DecisionEngine`, Google Maps, parser, geocodificação, repositórios, fala, overlay direcional e contrato do farol permaneceram protegidos por SHA-256.
- **Testes:** unitários e contratos aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; pacote, versão, DEX, assinatura v2, certificado e SHA-256 validados.
- **Workflow:** `Build Rota Certa 0.1.184`, run `{args.run_id}`.
- **Artifact:** `{artifact_name}`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **Link do artifact:** {artifact_url}
- **APK:** `{apk_name}`, {apk_size} bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.184`; versionCode `5450`.
- **SHA-256 do APK:** `{apk_sha}`.
- **Pendências:** instalação e teste no Samsung SM-S911B/Android 16. Validar migração da grade existente, instalação limpa vazia, todas as ações escolhidas, SAF de backup, rastreamento, direção em vias paralelas e ausência de regressão do farol durante cards reais.'''

    decision = '''## 03/08/2026 — A grade armazena ações tipadas; a Home é o catálogo e o filtro direcional falha fechado

- **Decisão:** cada entrada da grade representa exatamente uma ação interna registrada em `ShortcutActionCatalog0184`, não um módulo e não um comando arbitrário.
- **Home:** todos os módulos permanecem visíveis; cada painel mostra suas ações com `Adicionar` ou `Remover`. Ações diferentes do mesmo módulo podem coexistir, como `Criar backup` e `Restaurar backup`.
- **Grade:** instalação nova começa vazia; atualização preserva ou migra a grade; máximo de 32 ações; a mesma ação não é duplicada; a bolinha `+` não faz parte do painel de execução.
- **Interação:** bolinha principal abre a grade; o toque na ação executa imediatamente. Quando vazia, a bolinha principal abre a Home. Pop-up específico da própria ação continua permitido quando necessário para nome, confirmação ou seletor de arquivo.
- **Segurança:** não aceitar código, shell, URI livre, pacote livre ou Intent fornecida pelo usuário. Nova função só vira atalho após receber identificador estável, executor explícito e testes.
- **Direção:** sem GPS recente, precisão, velocidade e rumo confiáveis, não notificar. Alvo deve estar à frente e aproximar-se em duas amostras reais. Direção cadastrada deve coincidir; sentido oposto falha fechado.
- **Compatibilidade:** dados antigos sem direção continuam válidos, mas dependem do cone à frente e da aproximação estrita. Novos alertas/radares manuais guardam o rumo quando disponível.
- **Fronteira:** não alterar decisão do farol, OCR, parser, rotas, cores, km ou cancelamento de resultados antigos para implementar a grade ou os avisos direcionais.'''

    prepend_after_title(root / "docs/PROJECT_STATUS.md", status, "## 03/08/2026 — 0.1.184")
    prepend_after_title(root / "docs/DECISIONS.md", decision, "## 03/08/2026 — A grade armazena ações tipadas")

    validation_dir = root / "validation/0.1.184"
    validation_dir.mkdir(parents=True, exist_ok=True)
    (validation_dir / "latest.txt").write_text(
        "Rota Certa 0.1.184 (5450)\n"
        f"branch={args.branch}\nfunctional_commit={args.functional_commit}\n"
        f"workflow_run={args.run_id}\nartifact_name={artifact_name}\n"
        f"artifact_id={args.artifact_id}\nartifact_digest={args.artifact_digest}\n"
        f"artifact_url={artifact_url}\napk={apk_name}\napk_size={apk_size}\n"
        f"apk_sha256={apk_sha}\npackage=br.com.mapeiaia.rotacerta\n"
        "versionName=0.1.184\nversionCode=5450\ntests=passed\nlint=passed\n"
        "assembleDebug=passed\nsignature_v2=valid\nreal_device_validation=pending\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
