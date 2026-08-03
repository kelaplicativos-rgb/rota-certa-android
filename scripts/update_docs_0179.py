from pathlib import Path

PROJECT_HEADING = "# Rota Certa — Estado do projeto"
DECISIONS_HEADING = "# Rota Certa — Decisões técnicas"

PROJECT_MARKER = "## 03/08/2026 — 0.1.179 (5400) — CI estabilizado e grade personalizável validada"
PROJECT_ENTRY = r'''## 03/08/2026 — 0.1.179 (5400) — CI estabilizado e grade personalizável validada

- **Branch:** `agent/customizable-shortcut-grid-0.1.179`; **PR:** #48, aberta, mergeável, em rascunho e sem merge.
- **Commit funcional validado:** `fd9cd3db74d1f5433bff68a0e385e2c22832dcc2`.
- **Pedido do usuário:** aplicar uma correção segura no build da versão 0.1.179, sem remover validações nem alterar o comportamento funcional do aplicativo.
- **Situação anterior:** os builds da 0.1.179 eram encerrados pelo runner com `exit code 137` durante a cadeia cumulativa de Gradle. Após limitar a memória, a execução avançou e revelou dois contratos antigos de teste incompatíveis com o gesto e o despacho autoritativos da 0.1.179.
- **Causa:** a cadeia 0.1.177 → 0.1.178 → 0.1.179 executava testes, lint e assemble com concorrência e processos Kotlin suficientes para pressionar a memória do runner. Além disso, `AuthorizedAppsCards146ContractTest` ainda exigia o literal antigo de 1,5 segundo e `ShortcutActivityLaunchContract0176Test` ainda exigia despacho direto por `module.spec`, embora a 0.1.179 resolva a entrada personalizável antes do despacho.
- **Correção aplicada:**
  - toda a cadeia cumulativa herda Gradle sem daemon, sem paralelismo, com um worker, VFS watch desligado, heap limitado a 2.560 MiB, metaspace de 768 MiB e compilador Kotlin em processo;
  - testes unitários/de contrato, Android Lint e `clean assembleDebug` continuam obrigatórios e não foram pulados;
  - os dois checkouts do workflow usam `persist-credentials: false`;
  - o timeout do job foi ampliado para 180 minutos, privilegiando estabilidade em vez de concorrência;
  - os gatilhos de push e PR ficaram restritos aos arquivos que realmente materializam a 0.1.179;
  - criado o patch versionado `customizable-shortcut-grid-0179-contracts.patch`, validado por SHA-256 e `git apply --check`, que atualiza somente as duas asserções legadas para o contrato autoritativo de 2 segundos e despacho pela entrada resolvida;
  - nenhum teste foi removido e nenhum código funcional Kotlin do aplicativo foi alterado por esta correção de CI.
- **Arquivos principais alterados:**
  - `scripts/build_rota_certa_0179.sh`;
  - `.github/workflows/build-rota-certa-0.1.179.yml`;
  - `patches/customizable-shortcut-grid-0179-contracts.patch`;
  - `scripts/inspect_shortcut_customization_0179.sh`;
  - contratos materializados `AuthorizedAppsCards146ContractTest.kt` e `ShortcutActivityLaunchContract0176Test.kt`.
- **Fronteira protegida:** `AndroidManifest.xml`, permissões, `DecisionEngine.kt`, `GoogleMapsService.kt`, `RideTextParser.kt`, OCR, confirmação real de card, Casa/Alfinetes, cores, km, cancelamento de resultados antigos, radares e alertas direcionais permaneceram protegidos por checksums e validações do artifact.
- **Testes e validações:** todos os testes unitários/de contrato aprovados; Android Lint aprovado; `clean assembleDebug` aprovado; integridade ZIP e DEX, pacote, versão, versionCode, assinatura v2, certificado e marcadores da personalização validados.
- **Workflow funcional validado:** `Build Rota Certa 0.1.179`, run `30786663851`, job `91601374049`; todos os passos concluídos com sucesso.
- **Artifact:** `rota-certa-0.1.179-customizable-shortcut-grid-validated`, ID `8845723352`, 31.599.304 bytes, retenção até 01/11/2026.
- **Pacote e versão validados:** `br.com.mapeiaia.rotacerta`, versão `0.1.179`, versionCode `5400`, minSdk 26 e targetSdk 35.
- **APK:** `rota-certa-0.1.179-grade-atalhos-personalizavel-validado.apk`, 56.042.447 bytes.
- **SHA-256 do APK:** `081c9651f0733213c364d6f9502788c6fc4e6c443c62c1d02439b2bd58215713`.
- **SHA-256 do ZIP do artifact:** `71d8e08bbb41261cdd16a9a90ee3106276867c99e25a92254663a01747f94a26`.
- **Assinatura:** APK Signature Scheme v2 válida; certificado `CN=Rota Certa Debug, O=Kel Aplicativos, C=BR`; certificado SHA-256 `d9ee577b5bb9a4c72bce115e974c9ecf1ec8c7382bcd034e88d433e01eb0e7fd`; RSA 2048 bits.
- **Pendências, riscos e próxima validação:** instalar no Samsung SM-S911B com Android 16 e validar toque simples na ação principal selecionada, toque longo de 2 segundos abrindo o módulo correspondente na Home, toque de 5 segundos na bolinha principal abrindo a personalização, preservação do toque simples e duplo da bolinha principal, migração/ordem/duplicidade do catálogo e ausência de regressão do farol, rota, radares e alertas. A PR #48 deve permanecer em rascunho até essa validação real.
'''

DECISIONS_MARKER = "## 03/08/2026 — cadeia Gradle limitada e contratos de gesto versionados"
DECISIONS_ENTRY = r'''## 03/08/2026 — cadeia Gradle limitada e contratos de gesto versionados

- **Decisão:** builds cumulativos da 0.1.179 devem herdar limites globais de Gradle: daemon e paralelismo desligados, um worker, VFS watch desligado, heap e metaspace limitados e compilador Kotlin em processo.
- **Motivo:** a cadeia de materialização executa versões anteriores antes do código final e o runner estava encerrando o Gradle com `exit code 137`. A solução deve reduzir concorrência e pico de memória sem apagar testes, lint ou assemble.
- **Cobertura obrigatória:** todos os testes unitários/de contrato, Android Lint e `clean assembleDebug` permanecem obrigatórios. Não mascarar falta de memória pulando validações de versões-base.
- **Contrato de gesto:** `ShortcutGesturePolicy0179.SHORTCUT_LONG_PRESS_MILLIS` é a fonte autoritativa do toque longo de 2 segundos. Testes não devem voltar a exigir o literal legado de 1,5 segundo.
- **Contrato de despacho:** a grade personalizável resolve primeiro a entrada selecionada e então despacha `entry0179.spec`. Testes legados devem validar a entrada resolvida, não exigir o caminho direto antigo por `module.spec`.
- **Versionamento dos contratos:** ajustes de compatibilidade em testes materializados são aplicados por patch dedicado, com SHA-256 conhecido e `git apply --check`; não usar substituição textual silenciosa durante o build e não remover testes conflitantes.
- **Segurança do workflow:** checkouts de build usam `persist-credentials: false`; gatilhos de push e PR devem ficar restritos aos arquivos que podem alterar o resultado da 0.1.179.
- **Fronteira protegida:** esta decisão é exclusiva do pipeline e dos contratos de teste. Manifest, permissões, farol, parser, OCR, Google Maps, Casa/Alfinetes, decisão de cores, km, radares, alertas e proteção contra resultados atrasados não podem ser alterados para resolver pressão de memória do runner.
- **Condição para revisão:** revisar os limites quando o runner ou a estrutura de materialização mudar. Uma futura separação entre materialização e validação única só pode substituir a cadeia atual se testes de equivalência comprovarem que nenhuma proteção das versões-base foi perdida.
'''


def prepend_entry(path: str, heading: str, marker: str, entry: str) -> bool:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8") if file_path.exists() else f"{heading}\n"
    if marker in text:
        return False

    normalized_entry = entry.strip() + "\n\n"
    if text.startswith(heading + "\n\n"):
        new_text = heading + "\n\n" + normalized_entry + text[len(heading) + 2 :]
    elif text.startswith(heading + "\n"):
        new_text = heading + "\n\n" + normalized_entry + text[len(heading) + 1 :]
    else:
        new_text = heading + "\n\n" + normalized_entry + text

    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_text(new_text, encoding="utf-8")
    return True


changed = False
changed |= prepend_entry("docs/PROJECT_STATUS.md", PROJECT_HEADING, PROJECT_MARKER, PROJECT_ENTRY)
changed |= prepend_entry("docs/DECISIONS.md", DECISIONS_HEADING, DECISIONS_MARKER, DECISIONS_ENTRY)
print("updated" if changed else "already up to date")
