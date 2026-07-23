from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "app/build.gradle.kts"
VERSION_FILE = ROOT / "version.properties"
MATCHER_FILE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/RideCardTemplateMatcher.kt"

matcher = MATCHER_FILE.read_text()
matcher_changed = False
route_marker = "markerless_opened_indrive_route_block_0_1_128"
if route_marker not in matcher:
    function_start = matcher.find("    private fun isRouteCardCrop(")
    if function_start < 0:
        raise SystemExit("Funcao isRouteCardCrop nao encontrada")
    function_end = matcher.find("\n    private fun ", function_start + 10)
    if function_end < 0:
        function_end = len(matcher)
    block = matcher[function_start:function_end]
    anchor = '''        if (ownAppMarkers.any { marker -> marker in normalized }) return false
'''
    replacement = '''        if (ownAppMarkers.any { marker -> marker in normalized }) return false
        val hasOpenedMarkerlessInDriveCard =
            "pedido de viagem" in normalized &&
                "pedidos de viagem" !in normalized &&
                addressCount >= 2 &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized)
        if (hasOpenedMarkerlessInDriveCard) return true // markerless_opened_indrive_route_block_0_1_128
'''
    if anchor not in block:
        raise SystemExit("Inicio interno de isRouteCardCrop nao encontrado")
    block = block.replace(anchor, replacement, 1)
    matcher = matcher[:function_start] + block + matcher[function_end:]
    matcher_changed = True

contract_marker = "markerless_opened_indrive_contract_0_1_128"
if contract_marker not in matcher:
    function_start = matcher.find("    private fun deterministicFeaturesFor(")
    if function_start < 0:
        raise SystemExit("Funcao deterministicFeaturesFor nao encontrada")
    function_end = matcher.find("\n    private fun ", function_start + 10)
    if function_end < 0:
        function_end = len(matcher)
    block = matcher[function_start:function_end]
    anchor = '''        if (hasRouteBlock) features += "card.crop.route_block"
        return features
'''
    replacement = '''        val hasOpenedMarkerlessInDriveContract =
            "pedido de viagem" in normalized &&
                "pedidos de viagem" !in normalized &&
                addressCount >= 2 &&
                ("aceitar por" in normalized || "ofereca sua tarifa" in normalized)
        if (hasOpenedMarkerlessInDriveContract) {
            features += "card.contract.indrive_opened_single" // markerless_opened_indrive_contract_0_1_128
        }
        if (hasRouteBlock) features += "card.crop.route_block"
        return features
'''
    if anchor not in block:
        raise SystemExit("Retorno de deterministicFeaturesFor nao encontrado")
    block = block.replace(anchor, replacement, 1)
    matcher = matcher[:function_start] + block + matcher[function_end:]
    matcher_changed = True

if matcher_changed:
    MATCHER_FILE.write_text(matcher)

VERSION_FILE.write_text("VERSION_CODE=4067\nVERSION_NAME=0.1.128\n")

BUILD_FILE.write_text(r'''import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val appVersionCode = versionProperties.getProperty("VERSION_CODE").toInt()
val appVersionName = versionProperties.getProperty("VERSION_NAME")
val googleMapsApiKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: System.getenv("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
    ?: ""

val stableDebugKeystoreSource = layout.projectDirectory.file("debug-signing/rota-certa-debug.keystore.b64").asFile
val stableDebugKeystoreProvider = layout.buildDirectory.file("generated/signing/rota-certa-debug.keystore")
val prepareStableDebugKeystore by tasks.registering {
    inputs.file(stableDebugKeystoreSource)
    outputs.file(stableDebugKeystoreProvider)
    doLast {
        if (!stableDebugKeystoreSource.exists()) {
            throw GradleException("Fonte da chave debug estavel nao encontrada.")
        }
        val target = stableDebugKeystoreProvider.get().asFile
        target.parentFile.mkdirs()
        target.writeBytes(Base64.getMimeDecoder().decode(stableDebugKeystoreSource.readText()))
    }
}

android {
    namespace = "br.com.mapeiaia.rotacerta"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.mapeiaia.rotacerta"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsApiKey.escapeForBuildConfig()}\"")
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = stableDebugKeystoreProvider.get().asFile
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

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

tasks.matching { it.name == "preBuild" || it.name == "validateSigningDebug" }.configureEach {
    dependsOn(prepareStableDebugKeystore)
}
''')
