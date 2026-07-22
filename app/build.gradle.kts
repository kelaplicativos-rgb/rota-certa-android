import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val googleMapsApiKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: System.getenv("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: ""

val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1_000 + it }
val appVersionCode = ciVersionCode ?: 101
val stableDebugKeystoreSource = layout.projectDirectory.file("debug-signing/rota-certa-debug.keystore.b64").asFile
val stableDebugKeystoreFile = layout.buildDirectory.file("generated/signing/rota-certa-debug.keystore").get().asFile
if (stableDebugKeystoreSource.exists()) {
    stableDebugKeystoreFile.parentFile.mkdirs()
    stableDebugKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(stableDebugKeystoreSource.readText()))
}

android {
    namespace = "br.com.mapeiaia.rotacerta"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.mapeiaia.rotacerta"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "0.1.100"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsApiKey.escapeForBuildConfig()}\"")
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = stableDebugKeystoreFile
            storePassword = "rotacerta"
            keyAlias = "rotacerta-debug"
            keyPassword = "rotacerta"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val patchLiveRideAccessibilityService by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        var text = file.readText()
        val original = text

        if ("private fun hasActiveRegisteredDecision()" !in text) {
            text = text.replace(
"""    private fun resetToDefault(
""",
"""    private fun hasActiveRegisteredDecision(): Boolean =
        (currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red) &&
            registeredCardGate.hasSeenRecently(DECISION_OVERLAY_STICKY_MS)

    private fun resetToDefault(
""",
            )
        }

        text = text.replace(
            "else -> distanceKm.roundToInt().coerceAtMost(99).toString()",
            "else -> String.format(Locale(\"pt\", \"BR\"), \"%.1f\", distanceKm).removeSuffix(\",0\")",
        )

        if (text != original) {
            file.writeText(text)
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchLiveRideAccessibilityService)
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

apply(from = "patch-live-ride-stability.gradle.kts")
apply(from = "patch-live-ride-bubble-actions.gradle.kts")
apply(from = "patch-resource-groups-compile-fix.gradle.kts")
apply(from = "patch-bubble-shortcut-clipboard.gradle.kts")
apply(from = "patch-ux-places-alerts-radars.gradle.kts")
apply(from = "patch-live-reading-card-restore.gradle.kts")
apply(from = "patch-bubble-card-parity.gradle.kts")
apply(from = "patch-hide-insufficient-result-card.gradle.kts")
apply(from = "patch-fast-popup-analysis.gradle.kts")
apply(from = "patch-remove-live-diagnostics.gradle.kts")
apply(from = "patch-manual-support-report.gradle.kts")
apply(from = "patch-final-diagnostic-cleanup.gradle.kts")
apply(from = "patch-video-bubble-hardening.gradle.kts")
apply(from = "patch-bubble-state-report.gradle.kts")
apply(from = "patch-card-crop-guidance.gradle.kts")
apply(from = "patch-bubble-state-report-compile-fix.gradle.kts")
apply(from = "patch-factory-clean-no-flicker.gradle.kts")
apply(from = "patch-global-light-diagnostics.gradle.kts")
apply(from = "patch-persist-live-event-trace.gradle.kts")
apply(from = "patch-persistent-bubble-state-trace.gradle.kts")
apply(from = "patch-live-ride-window-event-guard.gradle.kts")
apply(from = "patch-keep-decision-during-transient-text.gradle.kts")
apply(from = "patch-hard-clear-unregistered-card-decision.gradle.kts")
apply(from = "patch-modular-live-bubble-core.gradle.kts")
apply(from = "patch-rota-certa-core-stable.gradle.kts")
apply(from = "patch-live-result-freshness-guard.gradle.kts")
apply(from = "patch-indrive-card-contract-match.gradle.kts")
apply(from = "patch-indrive-address-wrap.gradle.kts")
apply(from = "patch-rota-certa-core-gate.gradle.kts")
apply(from = "patch-core-bubble-decision.gradle.kts")
apply(from = "patch-core-bubble-presenter.gradle.kts")
apply(from = "patch-core-bubble-state.gradle.kts")
apply(from = "patch-core-package-monitor.gradle.kts")
apply(from = "patch-core-card-match-engine.gradle.kts")
apply(from = "patch-core-visible-card-lifecycle.gradle.kts")
apply(from = "patch-core-live-analysis-pipeline.gradle.kts")
apply(from = "patch-passive-event-compile-fix.gradle.kts")

val coreScreenReadEngineInlinePatch by tasks.registering {
    val serviceFile = layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt")
    inputs.file(serviceFile)
    outputs.upToDateWhen { false }

    doLast {
        val file = serviceFile.asFile
        if (!file.exists()) return@doLast
        var text = file.readText()
        val original = text
        val dollar = "$"

        if ("core_screen_read_engine_0_1_92" !in text) {
            val startToken = "        val snapshotText ="
            val endToken = "        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->\n"
            val start = text.indexOf(startToken)
            val end = if (start >= 0) text.indexOf(endToken, start) else -1
            if (start < 0 || end < 0) {
                throw org.gradle.api.GradleException("Nao encontrei a regiao de snapshot/merge de leitura para ligar CoreScreenReadEngine.")
            }
            val readBlock = """        val coreReadSnapshot = br.com.mapeiaia.rotacerta.core.CoreScreenReadEngine.prepare(
            accessibilityText = lastAccessibilityText,
            ocrText = lastOcrText,
            fallbackText = text,
            allowPopupCandidate = allowPopupCandidate,
        )
        val snapshotText = coreReadSnapshot.text
        if (coreReadSnapshot.kind == br.com.mapeiaia.rotacerta.core.CoreScreenReadKind.Empty) {
            traceEvent("core.read.empty source=${dollar}source summary=${dollar}{coreReadSnapshot.sourceSummary}") // core_screen_read_engine_0_1_92
            if (allowPopupCandidate) return
            registeredCardGate.clear()
            resetToDefault(reason = "Texto visivel vazio; nenhum card lido neste momento.", record = true)
            return
        }

        val snapshotHash = coreReadSnapshot.hash
        traceEvent("core.read.snapshot length=${dollar}{snapshotText.length} hash=${dollar}snapshotHash summary=${dollar}{coreReadSnapshot.sourceSummary}") // core_screen_read_engine_0_1_92

"""
            text = text.substring(0, start) + readBlock + text.substring(end)
        }

        if ("core_screen_read_engine_0_1_92" !in text) {
            throw org.gradle.api.GradleException("CoreScreenReadEngine nao foi conectado ao servico.")
        }

        if (text != original) file.writeText(text)
    }
}

tasks.named("coreVisibleCardLifecyclePatch").configure {
    dependsOn(coreScreenReadEngineInlinePatch)
}

tasks.named("coreLiveAnalysisPipelinePatch").configure {
    dependsOn(coreScreenReadEngineInlinePatch)
}

tasks.matching { it.name == "preBuild" || it.name.startsWith("compile") }.configureEach {
    dependsOn(coreScreenReadEngineInlinePatch)
}

apply(from = "patch-screen-phone-whatsapp.gradle.kts")
apply(from = "patch-gigu-inspired-live-reader.gradle.kts")
apply(from = "patch-gigu-core-classification.gradle.kts")
apply(from = "patch-unified-bubble-control-center.gradle.kts")
apply(from = "patch-unified-bubble-control-center-compile-final.gradle.kts")
apply(from = "functional-bubble-toggles-final.gradle.kts")
apply(from = "functional-bubble-idempotence-final.gradle.kts")
apply(from = "patch-main-bubble-tap-menu-contract.gradle.kts")
apply(from = "patch-in-app-bubble-home-final.gradle.kts")
apply(from = "patch-universal-two-address-runtime-final.gradle.kts")
apply(from = "patch-universal-two-address-compat.gradle.kts")
