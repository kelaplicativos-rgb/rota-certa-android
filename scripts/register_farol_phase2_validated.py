#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "<!-- FAROL_PHASE2_VALIDATED_STATUS_START -->"
DECISION_MARKER = "<!-- FAROL_PHASE2_VALIDATED_DECISION_START -->"

STATUS_BLOCK = """<!-- FAROL_PHASE2_VALIDATED_STATUS_START -->
## 06/08/2026 — fase 2 validada — raiz atômica e vínculo imutável da leitura

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
- **Commit funcional validado:** `16e130912d4294d269dd2abb15ad6e68105aa4ac`.
- **Versão:** `0.1.187`; **versionCode:** 5471; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** ligar as invariantes do laboratório da fase 1 ao caminho Kotlin real, corrigindo pacote/raiz/janela/geração e a origem das exceções sem alterar `DecisionEngine`, rota ou regras de cor.
- **Situação anterior:** o serviço podia consultar pacote da raiz, janela, texto e nós mediante aquisições diferentes de `rootInActiveWindow`. O Android podia invalidar ou trocar a raiz entre consultas; uma leitura antiga ainda podia chegar após troca de janela, sessão ou geração.
- **Causa:** a evidência de uma leitura não possuía uma única raiz física nem um token imutável revalidado depois das suspensões. A contenção anterior vinculava a recuperação, mas ainda permitia montar parte da evidência em instantes diferentes.
- **Correção:** `FarolRootHandle0187` captura uma única raiz e deriva dela pacote e janela; `FarolRootSnapshotPolicy0187` confere evento, pacote selecionado, raiz e janela antes da travessia; `FarolReadBinding0187` carrega pacote, sessão, janela e gerações; o vínculo é revalidado antes e depois do processamento assíncrono e qualquer divergência descarta a leitura antes de destino, OCR, rota, quilômetros ou cor.
- **Recuperação/OCR:** acessibilidade, nós e assinatura são coletados da mesma raiz; recuperação antiga, raiz nula ou raiz de outro pacote falham fechado e não reutilizam distância anterior.
- **Desempenho:** o gate de eventos permanece antes da coleta completa; eventos externos continuam rejeitados antes de consultar raiz antiga; chamadas repetidas de limpeza e renderização continuam confluídas pelo núcleo já existente.
- **Arquivos principais:** `LiveRideAccessibilityService.kt`, `FarolRuntimeSafety0187.kt`, `FarolRuntimeSafety0187Test.kt`, `FarolRuntimeFix0187ContractTest.kt`, `FarolRealtimeCriticalPathContract0167Test.kt`, patches e scripts cumulativos 0.1.187.
- **Fronteira protegida comprovada por SHA-256:** `AndroidManifest.xml`, `DecisionEngine.kt`, `RideTextParser.kt`, `GoogleMapsService.kt`, `GpsAddressResolver.kt`, confirmação 0.1.185, políticas de eventos/visual, parser universal, alertas e radares não foram alterados.
- **Testes:** tests=352; failures=0; testes unitários e de contrato aprovados; laboratório determinístico aprovado; Android Lint aprovado; `clean assembleDebug` aprovado.
- **Workflow validado:** `Build Rota Certa 0.1.187`, run `31112248496`; fonte protegida `32da54cd112c8ecb8b43b40c5cdb87ef13c4ec42`.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8972452959`, digest `fe1ec1fc52d3b7550088dabf6c27d9802c31cff490dcaba8b6473992c2fc3099`.
- **Link do artifact:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31112248496/artifacts/8972452959
- **Link permanente do APK:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; ZIP íntegro; pacote/versão/versionCode conferidos; assinatura APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`.
- **SHA-256 do APK:** `8873d82eaf914919c4b41491bb5f51209c9ec142774ff63b81f3c3d8216e90e5`.
- **Pendência real:** instalar no Samsung SM-S911B/Android 16 e validar card individual, troca rápida entre ofertas, saída do card, sobreposição do SystemUI/teclado/Android Auto, ausência de destino/km antigo, ausência de pisca e nenhuma exceção `accessibility_event_0172`. A PR permanece em rascunho até essa comprovação física.
<!-- FAROL_PHASE2_VALIDATED_STATUS_END -->

"""

DECISION_BLOCK = """<!-- FAROL_PHASE2_VALIDATED_DECISION_START -->
## 06/08/2026 — pacote, raiz, janela e geração formam uma leitura indivisível

- **Entrada:** pacote externo explícito deve ser rejeitado antes de consultar uma raiz potencialmente antiga.
- **Snapshot:** para evento autorizado, pacote da raiz, janela, texto e nós devem derivar do mesmo objeto `AccessibilityNodeInfo`; é proibido remontar a leitura mediante novas aquisições de `rootInActiveWindow`.
- **Admissão:** evento, pacote selecionado, pacote da raiz e janela precisam ser coerentes. Raiz nula, pacote divergente ou janela incompatível falham fechado antes da travessia completa.
- **Vínculo:** toda leitura assíncrona transporta pacote, geração da sessão, janela, geração da tela e geração da janela. O vínculo é revalidado antes e após qualquer suspensão.
- **Ordem:** validação ocorre antes de alterar buffers de OCR, destino, assinatura, cache, geração de rota ou visual. Resultado descartado não pode pintar e depois tentar se corrigir.
- **Recuperação:** OCR não é autorização alternativa; acessibilidade, nós, assinatura e confirmação continuam sujeitos ao mesmo card e ao mesmo instante lógico.
- **Estado visual:** cinza ou amarelo não conservam quilômetros; falha, troca de card ou desaparecimento eliminam imediatamente decisão e distância antigas.
- **Desempenho:** a coleta completa continua depois do gate de eventos e a raiz é reutilizada no evento, evitando aquisição e travessia duplicadas.
- **Fronteira:** nenhuma correção de concorrência pode alterar `DecisionEngine`, cálculo de rota, raio, Casa/Alfinete ou permitir que um adaptador decida a cor.
<!-- FAROL_PHASE2_VALIDATED_DECISION_END -->

"""


def prepend_once(path: Path, marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8")
    if marker not in current:
        path.write_text(block + current, encoding="utf-8")


repo = Path(__file__).resolve().parents[1]
prepend_once(repo / "docs/PROJECT_STATUS.md", STATUS_MARKER, STATUS_BLOCK)
prepend_once(repo / "docs/DECISIONS.md", DECISION_MARKER, DECISION_BLOCK)
