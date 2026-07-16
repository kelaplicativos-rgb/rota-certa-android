// Compatibilidade final: carrega a trava de idempotencia, os metadados de
// estado, a troca visual imediata e a sonda do APK instalado.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
apply(from = "universal-runtime-state-probe.gradle.kts")
