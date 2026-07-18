// Compatibilidade final do leitor universal:
// - mantem a trava de idempotencia;
// - mantem os metadados e a sonda de validacao;
// - nao aplica filtro de pacote;
// - nao exige modelo de card cadastrado.
//
// O contrato ativo e: ler qualquer tela, localizar pelo menos dois enderecos
// reconheciveis, com ou sem numero, usar o ultimo como destino e calcular ate o
// endereco definido pelo usuario.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
apply(from = "universal-runtime-state-probe.gradle.kts")
apply(from = "universal-immediate-gray-clear.gradle.kts")
apply(from = "universal-runtime-stability-guard.gradle.kts")
apply(from = "universal-optional-model-contract.gradle.kts")
apply(from = "universal-flattened-address-boundary.gradle.kts")
apply(from = "universal-idempotence-compatibility.gradle.kts")
apply(from = "universal-probe-idempotence.gradle.kts")
apply(from = "universal-no-card-runtime-final.gradle.kts")
apply(from = "universal-no-card-anchor-compat.gradle.kts")
apply(from = "universal-no-card-process-anchor.gradle.kts")
apply(from = "universal-no-card-compile-repair.gradle.kts")
apply(from = "session-diagnostic-bootstrap.gradle.kts")
apply(from = "session-diagnostic-v2.gradle.kts")
apply(from = "session-diagnostic-retention.gradle.kts")
apply(from = "universal-overlay-self-window-fix.gradle.kts")
apply(from = "universal-poi-destination-boundary.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.108.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.109.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.110.gradle.kts")
apply(from = "universal-99-card-addresses-0.1.111.gradle.kts")
apply(from = "universal-99-card-continuation-0.1.111.gradle.kts")
apply(from = "universal-0.1.111-idempotence-compat.gradle.kts")
apply(from = "version-0.1.101.gradle.kts")
apply(from = "version-0.1.102.gradle.kts")
apply(from = "version-0.1.103.gradle.kts")
apply(from = "version-0.1.104.gradle.kts")
apply(from = "version-0.1.105.gradle.kts")
apply(from = "version-0.1.106.gradle.kts")
apply(from = "version-0.1.107.gradle.kts")
apply(from = "version-0.1.108.gradle.kts")
apply(from = "version-0.1.109.gradle.kts")
apply(from = "version-0.1.110.gradle.kts")
apply(from = "version-0.1.111.gradle.kts")
