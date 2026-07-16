// Compatibilidade final: carrega a trava de idempotencia, os metadados de
// estado e a troca visual imediata das bolinhas internas.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
