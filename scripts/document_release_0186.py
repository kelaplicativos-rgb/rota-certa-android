#!/usr/bin/env python3
from __future__ import annotations
import argparse
from datetime import date
from pathlib import Path


def prepend_unique(path: Path, start_marker: str, end_marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8") if path.exists() else ""
    while start_marker in current:
        start = current.index(start_marker)
        end = current.find(end_marker, start)
        if end < 0:
            current = current[:start]
            break
        current = current[:start] + current[end + len(end_marker):]
    path.parent.mkdir(parents=True, exist_ok=True)
    marked = f"{start_marker}\n{block.rstrip()}\n{end_marker}"
    path.write_text(marked + "\n\n" + current.lstrip(), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--artifact-id", required=True)
    parser.add_argument("--artifact-digest", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--functional-commit", required=True)
    args = parser.parse_args()

    artifact = Path(args.artifact_dir)
    apk_name = "rota-certa-0.1.186-grade-audio-links-corretor-validado.apk"
    sha256 = (artifact / "sha256.txt").read_text(encoding="utf-8").split()[0]
    size = (artifact / "size-bytes.txt").read_text(encoding="utf-8").strip()
    tests = (artifact / "test-count.txt").read_text(encoding="utf-8").strip().replace("\n", "; ")
    today = date.today().strftime("%d/%m/%Y")
    artifact_url = f"https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/{args.run_id}/artifacts/{args.artifact_id}"
    permanent_url = "https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk"

    status = f"""## {today} — 0.1.186 (5470) — grade, áudio, Links e Correção de texto

- **Branch:** `{args.branch}`; **PR:** #57, empilhada sobre a 0.1.185.
- **Commit funcional validado:** `{args.functional_commit}`.
- **Pedido:** fechamento seguro da grade por toque externo/bolinha, Home genérica recolhida, gesto longo de 1,5 s configurável, saída Sem som/Alarme/Mídia, pesquisa e cópia em Links e novo módulo offline Correção de texto.
- **Correção:** backdrop transparente consumível e removível; gesto determinístico sem janela de 900 ms; cancelamento de callback longo ao fechar/desanexar a grade; ação longa tipada/persistida; navegação explícita `collapsed`/`module`; um único TTS com `AudioAttributes`; filtro local normalizado; editor de links com quatro ações e bloqueio explícito ao atingir 40 itens; correção conservadora offline com preservação exata de URLs/e-mails, substituição somente em contexto editável exato, rejeição sem truncamento quando o resultado excederia 12.000 caracteres e remoção imediata do texto/token capturado do `Intent` após consumo.
- **Compatibilidade de compilação:** removidos, de forma estritamente validada, dois imports diretos de `androidx.compose.foundation.layout.weight` incompatíveis com a versão Compose usada; o uso de `Modifier.weight` permanece no escopo público de `RowScope`/`ColumnScope`, sem mudança funcional.
- **Fronteira protegida:** Manifest/permissões, `DecisionEngine`, parser, Google Maps, Casa/Alfinetes, confirmação 0.1.185, OCR e políticas universais permaneceram byte a byte inalterados por SHA-256.
- **Pipeline:** as versões-base são materializadas em ordem, com verificação de patches, hashes e contratos estruturais, mas sem repetir Gradle; `testDebugUnitTest`, `lintDebug` e `clean assembleDebug` são executados uma única vez sobre a árvore final 0.1.186.
- **Testes:** {tests}; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Workflow:** `Build Rota Certa 0.1.186`, run `{args.run_id}`; fonte protegida fixada no commit `32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42`; descoberta positiva de testes obrigatória.
- **Artifact:** `rota-certa-0.1.186-shortcuts-audio-links-text-validated`, ID `{args.artifact_id}`, digest `{args.artifact_digest}`.
- **Link do artifact:** {artifact_url}
- **Link permanente do APK:** {permanent_url}
- **APK:** `{apk_name}`, {size} bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.186`; versionCode `5470`.
- **SHA-256 do APK:** `{sha256}`.
- **Pendências reais:** instalar no Samsung SM-S911B/Android 16 e validar toque externo sem atravessar, 1,5 s/arraste, migração da grade, canais de áudio/Bluetooth, layout de Links e substituição de texto em aplicativos reais. Não declarar essas verificações físicas como concluídas antes do teste no aparelho.
"""

    decisions = f"""## {today} — grade fecha sem atravessar, Home genérica recolhe e ação longa é tipada

- **Fechamento:** a grade usa uma camada transparente de tela inteira que consome o toque externo e é removida junto com o menu; fechar não altera o farol.
- **Gestos:** toque rápido executa imediatamente; 1,5 segundo consome o gesto longo no limiar; movimento, fechamento e desanexação cancelam callbacks pendentes; toque triplo e janela de 900 ms permanecem ausentes.
- **Home:** abertura genérica envia modo `collapsed`; abertura deliberada envia modo `module` e identidade do módulo.
- **Persistência:** cada entrada mantém ação rápida e armazena ação longa como módulo relacionado, outra ação do catálogo tipado ou nenhuma ação.
- **Áudio:** um único TTS consulta a preferência Sem som/Alarme/Mídia em cada fala; Sem som trata o evento sem bloquear avisos visuais.
- **Links:** pesquisa exclusivamente local por nome, descrição ou URL normalizados; copiar coloca somente a URL na área de transferência; ao atingir 40 itens, nova inclusão é bloqueada antes de alterar o link principal ou descartar dados.
- **Correção de texto:** mecanismo conservador e offline, sem Samsung/nuvem/histórico; URLs e e-mails são isolados e restaurados byte a byte; resultado sempre revisável; substituição somente por ação explícita e se pacote, classe, texto e seleção ainda coincidirem; resultado acima do limite é rejeitado sem cortar o sufixo do texto original; texto, token e chave de solicitação são removidos do `Intent` logo após serem copiados para o estado efêmero da tela.
- **Pipeline cumulativo:** scripts anteriores continuam aplicando patches e verificações de integridade, porém encerram antes do primeiro Gradle quando executados em modo de materialização. A validação completa ocorre uma vez na árvore final; o wrapper confirma sintaxe e exige que a 0.1.185 receba explicitamente esse modo.
- **Compose:** imports diretos de `foundation.layout.weight` são removidos somente quando aparecem exatamente uma vez nos dois arquivos afetados; qualquer divergência falha fechado. O layout continua usando a extensão pública disponível no escopo de linha/coluna.
- **Fronteira:** interfaces e ferramentas não podem alterar o motor universal do farol.
"""

    prepend_unique(
        Path("docs/PROJECT_STATUS.md"),
        "<!-- ROTA_CERTA_0_1_186_STATUS_START -->",
        "<!-- ROTA_CERTA_0_1_186_STATUS_END -->",
        status,
    )
    prepend_unique(
        Path("docs/DECISIONS.md"),
        "<!-- ROTA_CERTA_0_1_186_DECISION_START -->",
        "<!-- ROTA_CERTA_0_1_186_DECISION_END -->",
        decisions,
    )
    validation = Path("validation/0.1.186/latest.txt")
    validation.parent.mkdir(parents=True, exist_ok=True)
    validation.write_text(
        f"versionName=0.1.186\nversionCode=5470\npackage=br.com.mapeiaia.rotacerta\n"
        f"branch={args.branch}\nfunctional_commit={args.functional_commit}\nrun_id={args.run_id}\n"
        f"artifact_id={args.artifact_id}\nartifact_digest={args.artifact_digest}\n"
        f"apk={apk_name}\nsize_bytes={size}\nsha256={sha256}\n{tests}\n"
        f"artifact_url={artifact_url}\npermanent_url={permanent_url}\n"
        "pipeline_mode=single_final_gradle_validation\n"
        "compose_weight_import_compatibility=true\n"
        "device_validation=pendent_samsung_sm_s911b_android_16\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
