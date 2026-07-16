// Compatibilidade final: carrega a trava que impede patches legados de
// reescreverem o leitor universal na segunda chamada do Gradle.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
