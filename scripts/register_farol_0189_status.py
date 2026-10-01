#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


def replace_or_prepend(text: str, start: str, end: str, block: str) -> str:
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.DOTALL)
    if pattern.search(text):
        return pattern.sub(block.rstrip(), text, count=1)
    return block.rstrip() + "\n\n" + text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--head", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--tests", required=True)
    parser.add_argument("--apk-sha", required=True)
    parser.add_argument("--apk-size", required=True)
    parser.add_argument("--artifact-id", required=True)
    parser.add_argument("--artifact-digest", default="GitHub Actions")
    args = parser.parse_args()

    status_path = Path("docs/PROJECT_STATUS.md")
    decisions_path = Path("docs/DECISIONS.md")
    status = status_path.read_text(encoding="utf-8")
    decisions = decisions_path.read_text(encoding="utf-8")

    old_start = "<!-- FAROL_0188_CI_PENDING_DEVICE_START -->"
    old_end = "<!-- FAROL_0188_CI_PENDING_DEVICE_END -->"
    old_block = f"""{old_start}
## 07/08/2026 — 0.1.188 reprovada no aparelho real

- **Branch:** `agent/fix-farol-real-device-0.1.188`; **PR:** #66; **versão:** `0.1.188`; **versionCode:** 5472; **pacote:** `br.com.mapeiaia.rotacerta`.
- **CI anterior:** run `31173363429`, tests=364, failures=0, Lint e `clean assembleDebug` aprovados; o sucesso do CI não representou aprovação funcional.
- **Prova física:** relatório manual `rota-certa-relatorio-depuracao (32).txt`, Samsung SM-S911B / Android 16.
- **Falha:** falso negativo real — OCR encontrou origem e destino da mesma oferta, mas o gate os classificou como blocos diferentes e recusou a rota; nenhuma decisão verde/vermelha foi pintada na sessão.
- **Latência:** o relatório registrou `Tempo da ultima decisao: 1643 ms` e forte reagendamento de OCR para as mesmas gerações.
- **Conclusão:** 0.1.188 permanece apenas como referência histórica/candidata reprovada; não promover para `latest`.
{old_end}"""
    status = replace_or_prepend(status, old_start, old_end, old_block)

    new_start = "<!-- FAROL_0189_CI_PENDING_DEVICE_START -->"
    new_end = "<!-- FAROL_0189_CI_PENDING_DEVICE_END -->"
    new_block = f"""{new_start}
## 07/08/2026 — 0.1.189 prioridade visual/latência aprovada em CI; aparelho pendente

- **Branch:** `agent/fix-farol-priority-latency-0.1.189`; **PR:** #67; **base:** `agent/fix-farol-real-device-0.1.188`.
- **Head validado em CI:** `{args.head}`; **workflow run:** `{args.run_id}`.
- **Versão:** `0.1.189`; **versionCode:** 5473; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** reduzir decisão para o caminho crítico mínimo, priorizar imediatamente a janela/bloco visual superior e preservar amarelo para pacote ativo, introduzindo laranja quando o último endereço do card atual foi confirmado e a rota real está em cálculo.
- **Causa:** segmentação 0.1.188 tratava linhas OCR do mesmo card como blocos independentes e reagendava OCR repetidamente para a mesma geração; isso gerava falsos negativos e desperdício de tempo.
- **Correção:** `FarolVisualPriority0189` agrupa fragmentos por geometria; maior camada de janela e bloco visual superior têm autoridade monotônica; novo bloco invalida a identidade anterior; dois ou mais endereços do mesmo bloco usam o último como destino; OCR é single-flight/deduplicado por identidade de geração/bloco; laranja é aplicado antes de cache/rede e nunca mostra quilômetros sem rota real.
- **Fronteiras preservadas:** `DecisionEngine`, `GoogleMapsService`, `GpsAddressResolver`, `RideTextParser`, Manifest/permissões, radares e alertas permaneceram byte a byte iguais na cadeia de build.
- **Testes CI:** tests={args.tests}; failures=0; Android Lint e `clean assembleDebug` aprovados.
- **Artifact:** `rota-certa-0.1.189-release-candidate`, ID `{args.artifact_id}`, referência/digest `{args.artifact_digest}`.
- **APK candidato:** `rota-certa-0.1.189-candidate.apk`, {args.apk_size} bytes; assinatura APK v2 conferida; SHA-256 `{args.apk_sha}`.
- **Link candidato:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/ci-0.1.189/rota-certa-0.1.189-candidate.apk
- **Distribuição:** `latest` não é substituído; aprovação funcional exige novo teste físico no Samsung SM-S911B/Android 16, especialmente 99, Uber popup, múltiplos cards, troca de bloco e tempo entre amarelo→laranja→verde/vermelho.
{new_end}"""
    status = replace_or_prepend(status, new_start, new_end, new_block)
    status_path.write_text(status, encoding="utf-8")

    decision_start = "<!-- FAROL_0189_DECISION_START -->"
    decision_end = "<!-- FAROL_0189_DECISION_END -->"
    decision_block = f"""{decision_start}
## 07/08/2026 — autoridade visual monotônica e estado laranja

- **Cinza:** aplicativo monitorado inativo ou tela externa/passiva confirmada.
- **Amarelo:** pacote selecionado ativo, ainda sem destino final suficientemente confirmado.
- **Laranja:** dois ou mais endereços pertencem ao mesmo bloco/card atual e o último foi confirmado como destino final; a rota real para Casa/Alfinete já está em cálculo. Laranja nunca exibe quilômetros antigos.
- **Verde/vermelho:** continuam exclusivamente após rota real e `DecisionEngine`, conforme raio de Casa/Alfinete.
- **Prioridade:** maior camada de janela vence; dentro dela o bloco visual superior atual possui autoridade. Novo bloco superior invalida imediatamente OCR, rota e identidade visual anteriores.
- **Último endereço:** com dois ou mais endereços no mesmo bloco coerente, o último desse bloco é o destino final; é proibido usar o último endereço do texto global da tela ou juntar cards distintos.
- **OCR:** no máximo uma execução ativa por identidade de pacote/sessão/janela/geração/bloco; eventos repetidos são deduplicados e trabalho antigo é cancelado na mudança de autoridade.
- **Universalidade:** nenhuma regra depende de Uber, 99 ou inDrive para decidir cor; aplicativos conhecidos e desconhecidos seguem o mesmo núcleo.
- **Limite real:** latência de rede do provedor de rota não pode ser garantida abaixo de 1 segundo, mas o aplicativo não adiciona espera deliberada depois de confirmar o destino; cache válido pode finalizar imediatamente.
{decision_end}"""
    decisions = replace_or_prepend(decisions, decision_start, decision_end, decision_block)
    decisions_path.write_text(decisions, encoding="utf-8")


if __name__ == "__main__":
    main()
