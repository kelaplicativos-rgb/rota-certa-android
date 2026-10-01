#!/usr/bin/env python3
from pathlib import Path

STATUS_MARKER = "<!-- FAROL_PHASE4_VALIDATED_STATUS_START -->"
DECISION_MARKER = "<!-- FAROL_PHASE4_VALIDATED_DECISION_START -->"

STATUS_BLOCK = """<!-- FAROL_PHASE4_VALIDATED_STATUS_START -->
## 06/08/2026 — fase 4 validada — resultado atrasado não substitui leitura nova

- **Branch:** `agent/fix-farol-runtime-0.1.187`; **PR:** #59; **base real:** `agent/shortcut-audio-links-text-correction-0.1.186`.
- **Head funcional validado:** `3537a13edbc0d72627d650a18b4da58da8ecb316`; correção de escopo em `d181b0e126b6d1b2c37001872e8afcf7f302e4fd`; teste em `1a49003e2f347911c748d8f6520eb519773772b4`.
- **Versão:** `0.1.187`; **versionCode:** 5471; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Situação anterior:** o primeiro build conclusivo da fase 4, run `31129590730`, chegou à compilação Kotlin e falhou em `LiveRideAccessibilityService.kt:2548` por uma referência solta `addressSignature` que permaneceu depois de a fase 4 substituir parâmetros avulsos pelo vínculo imutável de decisão.
- **Causa:** a persistência do resultado ainda referenciava o identificador removido do escopo, embora a assinatura correta já estivesse transportada em `FarolDecisionBinding0187Phase4`.
- **Correção:** `fix_farol_phase4_address_signature.py` localiza exatamente uma referência nua sem declaração e exige uma única fonte válida no mesmo método. Na aplicação do resultado, a persistência passa a usar a propriedade `addressSignature` do vínculo monotônico. Ambiguidade, duas fontes ou parâmetro antigo declarado falham fechado.
- **Contrato:** o teste textual amplo foi substituído por verificação estrutural da lista `persistenceSignatureChecklist13`, que deve começar com uma propriedade `.addressSignature` de um vínculo; usos legítimos com argumento nomeado não são bloqueados.
- **Fase 4 preservada:** pacote, geração da sessão, janela, geração da tela, geração da janela, hash da tela e assinatura do destino continuam indivisíveis; resultado antigo é descartado antes de alterar destino, quilômetros ou cor; cancelamento central abrange rota, análise, OCR, screenshot e confirmação parcial.
- **Fronteira protegida:** `DecisionEngine`, cálculo de rota, raio, Casa/Alfinete, Manifest, permissões, parser universal, confirmação individual do card, Google Maps, radares e alertas não foram alterados; hashes protegidos foram aprovados no artifact.
- **Replay determinístico:** run `31135097519`, aprovado, incluindo regressão do reparador e replay histórico estrito.
- **Workflow Android:** `Build Rota Certa 0.1.187`, run `31135097548`, job `92732575581`, concluído com sucesso em materialização, testes, Android Lint, `clean assembleDebug`, validações do APK, publicação e conferência do download permanente.
- **Testes:** tests=356; failures=0.
- **Artifact:** `rota-certa-0.1.187-farol-runtime-validated`, ID `8977776625`, digest `f427d1d62aa8cfb806c956d95f585a12c35bd1268f60088f30231385254092bd`.
- **Artifact URL:** https://github.com/kelaplicativos-rgb/rota-certa-android/actions/runs/31135097548/artifacts/8977776625
- **APK:** `rota-certa-0.1.187-farol-runtime-validado.apk`, 56.124.367 bytes; ZIP íntegro; `compileSdk` 35; `minSdk` 26; `targetSdk` 35.
- **Assinatura:** APK Signature Scheme v2 válida; um signatário; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`.
- **SHA-256 do APK:** `ca8ce3b5742fea2170dd43425d3b6bf897f4058a3c35073bb8a4aebeb0920a45`.
- **Link permanente verificado:** https://github.com/kelaplicativos-rgb/rota-certa-android/releases/download/latest/rota-certa-latest.apk
- **Pendência física:** instalar no Samsung SM-S911B/Android 16 e validar troca rápida de cards, rota antiga concluindo depois da troca de janela/sessão, SystemUI, teclado, retorno ao feed, fechamento real do card, ausência de pisca e ausência de quilômetros antigos.
<!-- FAROL_PHASE4_VALIDATED_STATUS_END -->

"""

DECISION_BLOCK = """<!-- FAROL_PHASE4_VALIDATED_DECISION_START -->
## 06/08/2026 — persistência e aplicação pertencem ao vínculo imutável da decisão

- **Fonte única:** depois que uma rota é iniciada, pacote, sessão, janela, gerações, hash da tela e assinatura do destino pertencem ao mesmo `FarolDecisionBinding0187Phase4`.
- **Sem parâmetros soltos:** aplicação, deduplicação e persistência do resultado não podem usar uma assinatura avulsa removida do escopo; devem ler a assinatura do vínculo que foi revalidado.
- **Antes e depois da suspensão:** o vínculo deve continuar atual antes da chamada, após a rede/cache e imediatamente antes de alterar destino, quilômetros, cor ou histórico.
- **Resultado atrasado:** vínculo de sessão ou janela anterior registra descarte e não substitui a decisão visual mais recente, mesmo que a chamada de rede ignore cancelamento.
- **Reparo fail-closed:** correção de materialização só pode atuar quando existir exatamente uma referência nua inválida e uma única fonte tipada no método. Ambiguidade interrompe o build.
- **Teste estrutural:** contratos devem verificar o uso da propriedade do vínculo no bloco funcional, não proibir sequências textuais legítimas em outras estruturas.
- **Núcleo universal:** nenhuma correção de concorrência autoriza adaptador a decidir cor, altera `DecisionEngine`, mistura cards ou relaxa a confirmação do destino final.
<!-- FAROL_PHASE4_VALIDATED_DECISION_END -->

"""


def prepend_once(path: Path, marker: str, block: str) -> None:
    current = path.read_text(encoding="utf-8")
    if marker not in current:
        path.write_text(block + current, encoding="utf-8")


repo = Path(__file__).resolve().parents[1]
prepend_once(repo / "docs/PROJECT_STATUS.md", STATUS_MARKER, STATUS_BLOCK)
prepend_once(repo / "docs/DECISIONS.md", DECISION_MARKER, DECISION_BLOCK)
print("farol_phase4_validated_records=registered")
