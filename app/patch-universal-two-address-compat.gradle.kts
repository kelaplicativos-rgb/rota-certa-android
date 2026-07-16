// Compatibilidade final: carrega a trava de idempotencia e os metadados de
// estado usados na validacao real da bolinha em Android.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
