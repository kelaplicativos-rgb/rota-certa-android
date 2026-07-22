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
apply(from = "selected-app-waiting-yellow-0.1.127.gradle.kts")
apply(from = "atomic-selected-app-yellow-0.1.127.gradle.kts")
apply(from = "yellow-idempotent-no-redraw-0.1.127.gradle.kts")
apply(from = "stable-debug-signing-prepare-0.1.127.gradle.kts")

// Etapa 2: a interface regional e aplicada depois dos restauradores de tela
// 0.1.127 e antes dos patches 0.1.128 que alteram apenas servico/matcher.
apply(from = "region-acceleration-ui-0.1.128.gradle.kts")

// Etapa 3: galeria das capturas automaticas no mesmo modulo dos modelos manuais.
apply(from = "automatic-capture-gallery-0.1.128.gradle.kts")
