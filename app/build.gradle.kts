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
val appVersionCode = ciVersionCode ?: 68
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
        versionName = "0.1.66"

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
        val dollar = "$"

        text = text.replace(
"""        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            traceEvent("classifier.ignore=true reason=${dollar}reason hash=${dollar}snapshotHash")
            if (allowPopupCandidate) return
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
""",
"""        RideScreenTextClassifier.ignoreReason(snapshotText)?.let { reason ->
            traceEvent("classifier.ignore=true reason=${dollar}reason hash=${dollar}snapshotHash")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ignore_reason keep_decision=true reason=${dollar}reason")
                return
            }
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
""",
        )

        text = text.replace(
"""        if (snapshotHash != lastSnapshotHash) {
            lastSnapshotHash = snapshotHash
            lastAnalyzedHash = null
            showOverlay(RadarColor.Default)
            recordDiagnostic(
                stage = "screen_changed",
                reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                text = snapshotText,
            )
        }
""",
"""        if (snapshotHash != lastSnapshotHash) {
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.screen_changed keep_decision=true hash=${dollar}snapshotHash")
            } else {
                lastSnapshotHash = snapshotHash
                lastAnalyzedHash = null
                showOverlay(RadarColor.Default)
                recordDiagnostic(
                    stage = "screen_changed",
                    reason = "A imagem/texto da tela mudou; aguardando confirmar o card cadastrado.",
                    text = snapshotText,
                )
            }
        }
""",
        )

        text = text.replace(
"""            traceEvent("classifier.ride_offer=false reason=${dollar}reason")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
""",
"""            traceEvent("classifier.ride_offer=false reason=${dollar}reason")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.ride_offer_false keep_decision=true reason=${dollar}reason")
                return
            }
            registeredCardGate.clear()
""",
        )

        text = text.replace(
"""            traceEvent("card_model.missing package=${dollar}{packageName.orEmpty()} templates=${dollar}{currentCardTemplates.size}")
            if (allowPopupCandidate) return
            registeredCardGate.clear()
""",
"""            traceEvent("card_model.missing package=${dollar}{packageName.orEmpty()} templates=${dollar}{currentCardTemplates.size}")
            if (allowPopupCandidate) return
            if (source == TextSource.Ocr && hasActiveRegisteredDecision()) {
                traceEvent("ocr.card_model_missing keep_decision=true templates=${dollar}{currentCardTemplates.size}")
                return
            }
            registeredCardGate.clear()
""",
        )

        if ("private fun hasActiveRegisteredDecision()" !in text) {
            text = text.replace(
"""    private fun resetToDefault(
""",
"""    private fun hasActiveRegisteredDecision(): Boolean =
        currentRadarColor == RadarColor.Green || currentRadarColor == RadarColor.Red

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
