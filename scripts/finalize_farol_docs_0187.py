#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "<!-- ROTA_CERTA_0_1_187_STATUS_START -->"
DECISION_MARKER = "<!-- ROTA_CERTA_0_1_187_DECISION_START -->"
WORKFLOW_MARKER = "# project_records_finalized_0_1_187"

status_block = """<!-- ROTA_CERTA_0_1_187_STATUS_START -->
## 06/08/2026 — 0.1.187 (5471) — recuperação do farol vinculada ao mesmo card

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59, empilhada sobre a 0.1.186.
- **Commit funcional validado:** `e5de25196468115a94997745df4f7e549c8d7afb`.
- **Pedido:** identificar por que o farol não funcionava no Samsung SM-S911B/Android 16, corrigir a causa real sem alterar o `DecisionEngine`, preservar o núcleo universal e entregar APK validado.
- **Situação anterior comprovada:** a sessão 0.1.186 terminou cinza mesmo com o inDrive ativo; houve `NullPointerException` contido em `accessibility_event_0172`, 2.032 eventos descartados e rajadas de rejeições externas. Uma recuperação atrasada calculou `Rua Cantagalo, 74` enquanto o card confirmado mostrava `Rua Azevedo Soares, 690`, produzindo cor/quilometragem de outra leitura antes de voltar ao card atual.
- **Causa:** a recuperação de captura/OCR podia combinar buffers de acessibilidade e OCR de momentos diferentes e aplicar o resultado sem provar novamente pacote, sessão, janela, geração e assinatura do mesmo card. Idades críticas usavam relógio civil; rejeições externas repetidas e diagnóstico podiam ocupar ou lançar no caminho principal.
- **Correção:** recuperação imutavelmente vinculada a pacote selecionado, sessão do card, janela, geração da janela, geração da captura e assinatura; nova coleta no momento da aplicação; descarte fail-closed de recuperação atrasada; remoção da combinação direta de buffers de momentos diferentes; relógio monotônico; confluência de rajadas externas quando o visual já está cinza; gravador de voo e log convertidos em operações sem exceção no caminho crítico.
- **Classificação arquitetural:** a falha era universal no controle de geração/recuperação e contenção, enquanto a exigência de card individual do inDrive permanece no adaptador/política 0.1.185. Nenhum adaptador decide a cor e nenhum aplicativo conhecido ou desconhecido foi bloqueado por marca.
- **Arquivos principais materializados:** `LiveRideAccessibilityService.kt`, `FarolRuntimeSafety0187.kt`, `UnifiedDebugLog.kt`, `FarolFlightRecorder0163.kt`, testes 0.1.187 e compatibilidade do contrato legado de espera amarela.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine`, `RideTextParser`, `GoogleMapsService`, `GpsAddressResolver`, políticas universais de confirmação, Casa/Alfinetes, alertas e radares permaneceram protegidos por SHA-256.
- **Testes:** tests=348; failures=0; testes unitários e de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Workflow validado:** `Build Rota Certa 0.1.187`, run `31095303469`; fonte protegida `32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42`.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8965382236`, digest `27cea00bccfbf121175965f7b51afe9fae4620fdefaae038e8dfc218592d7671`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31095303469/artifacts/8965382236
- **Link permanente do APK:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; pacote `br.com.mapeiaia.rotacerta`; versão `0.1.187`; versionCode `5471`; assinatura APK Signature Scheme v2 válida, certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`.
- **SHA-256 do APK:** `cb1ff362bcf73bad33ae96045eb62bc6b6c9e48abea65964c278fe924f375faa`.
- **Riscos e pendências:** a compilação comprova contratos, cancelamento e presença dos marcadores no DEX, mas não comprova a integração visual do inDrive no aparelho. Instalar no Samsung SM-S911B/Android 16 e testar card individual, troca rápida de oferta, retorno ao feed, pacote externo e ausência de cor/km de destino antigo. O PR permanece em rascunho até essa validação física.
<!-- ROTA_CERTA_0_1_187_STATUS_END -->

"""

decision_block = """<!-- ROTA_CERTA_0_1_187_DECISION_START -->
## 06/08/2026 — recuperação só pode aplicar dados do mesmo card e do mesmo instante lógico

- **Identidade obrigatória:** todo fallback de captura/OCR deve guardar e revalidar pacote selecionado, sessão do card, janela, geração da janela, geração da captura e assinatura da tela. Divergência em qualquer campo descarta o resultado.
- **Evidência atual:** a aplicação da recuperação deve coletar novamente acessibilidade e nós atuais; não combinar diretamente buffers persistidos de momentos diferentes.
- **Ordem de validação:** validar o vínculo antes de alterar destino, assinatura, hash, cache, geração de rota ou estado visual. Um resultado antigo não pode mudar o estado para depois se declarar atual.
- **Confirmação:** a recuperação continua sujeita ao mesmo núcleo universal e à mesma confirmação de card individual aplicada à leitura normal. OCR não é caminho alternativo de autorização.
- **Tempo:** idades do farol, preservação de decisão e janelas de deduplicação usam relógio monotônico; relógio civil fica apenas para data/hora de relatório.
- **Pacotes externos:** o primeiro evento externo limpa o farol; repetições idênticas em rajada, quando o visual já está cinza e sem quilômetros, são confluídas sem consultar raiz antiga nem redesenhar.
- **Diagnóstico:** gravador de voo, trilha unificada e exportação são auxiliares; nenhuma exceção de diagnóstico pode escapar para o processamento de acessibilidade ou pintar/limpar o farol.
- **Fronteira:** não alterar `DecisionEngine`, rota, raio, Casa/Alfinetes ou contrato de cores para corrigir concorrência. Adaptadores apenas entregam card segmentado e confirmado ao núcleo.
- **Fail-closed:** na dúvida sobre identidade, geração ou confirmação, limpar dados transitórios, manter espera coerente e não mostrar quilômetros antigos.
<!-- ROTA_CERTA_0_1_187_DECISION_END -->

"""


def prepend_once(path: Path, marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8")
    if marker not in current:
        path.write_text(block + current, encoding="utf-8")


repo = Path(__file__).resolve().parents[1]
prepend_once(repo / "docs/PROJECT_STATUS.md", STATUS_MARKER, status_block)
prepend_once(repo / "docs/DECISIONS.md", DECISION_MARKER, decision_block)

for relative in (
    ".github/workflows/diagnose-farol-0.1.187.yml",
    "scripts/diagnose_farol_0187.sh",
    "scripts/inject_farol_diagnostic_0187.py",
):
    path = repo / relative
    if path.exists():
        path.unlink()

workflow = repo / ".github/workflows/build-rota-certa-0.1.187.yml"
workflow_text = workflow.read_text(encoding="utf-8")
if WORKFLOW_MARKER not in workflow_text:
    workflow.write_text(workflow_text.rstrip() + "\n\n" + WORKFLOW_MARKER + "\n", encoding="utf-8")
