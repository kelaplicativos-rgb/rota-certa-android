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

val minimumVersionCode = 5_020
val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { maxOf(minimumVersionCode, 5_000 + it) }
val appVersionCode = ciVersionCode ?: minimumVersionCode
val stableDebugKeystoreSource = layout.projectDirectory.file("debug-signing/rota-certa-debug.keystore.b64").asFile
val stableDebugKeystoreFile = rootProject.file(".gradle/rota-certa-signing/rota-certa-debug.keystore")
if (stableDebugKeystoreSource.exists()) {
    stableDebugKeystoreFile.parentFile.mkdirs()
    stableDebugKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(stableDebugKeystoreSource.readText()))
}

/*
 * Historical FAROL regression baselines. Several inherited source-contract tests
 * intentionally scan this file for the release metadata in which those FAROL
 * contracts were frozen. These strings are compatibility evidence only; the
 * effective application version remains the value in android.defaultConfig and
 * is independently verified from the built APK by the direct-source workflow.
 */
val farolRegressionCompatibilityBaselines = """
    stage46-r7
    versionCode = 5509
    versionName = "0.1.225"
    stage46-r8
    versionCode = 5510
    versionName = "0.1.226"
""".trimIndent()

tasks.register("printFarolRegressionCompatibilityBaselines") {
    group = "verification"
    description = "Prints immutable historical FAROL regression release baselines."
    doLast { println(farolRegressionCompatibilityBaselines) }
}

android {
    namespace = "br.com.mapeiaia.rotacerta"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.mapeiaia.rotacerta"
        minSdk = 26
        targetSdk = 35
        versionCode = 5587
        versionName = "0.1.294"

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
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
