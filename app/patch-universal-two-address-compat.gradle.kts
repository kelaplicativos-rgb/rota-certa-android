// Compatibilidade final: carrega a trava de idempotencia, os metadados de
// estado, a troca visual imediata, a sonda do APK instalado e, por ultimo,
// restaura o contrato de card cadastrado com leitura estavel.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
apply(from = "universal-runtime-state-probe.gradle.kts")
apply(from = "registered-card-stable-address-runtime.gradle.kts")
