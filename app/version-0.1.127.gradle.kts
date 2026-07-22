val androidExtension127 = extensions.getByName("android")
val defaultConfig127 = androidExtension127.javaClass.methods
    .first { method -> method.name == "getDefaultConfig" && method.parameterCount == 0 }
    .invoke(androidExtension127)
defaultConfig127.javaClass.methods
    .first { method -> method.name == "setVersionName" && method.parameterCount == 1 }
    .invoke(defaultConfig127, "0.1.127")

// Ultimos aplicadores da versao: nenhuma etapa posterior pode reabrir leitura universal,
// tornar cards opcionais, restaurar polling agressivo ou remover a busca de locais.
apply(from = "manual-strict-contract-finalizer-0.1.127.gradle.kts")
apply(from = "saved-places-search-0.1.127.gradle.kts")
apply(from = "compile-final-cleanup-0.1.127.gradle.kts")
apply(from = "strict-model-default-finalizer-0.1.127.gradle.kts")
apply(from = "strict-idempotence-compat-0.1.127.gradle.kts")
apply(from = "strict-legacy-126-preflight-0.1.127.gradle.kts")
apply(from = "instant-accessibility-first-pipeline-0.1.127.gradle.kts")
apply(from = "instant-valid-route-cache-0.1.127.gradle.kts")
