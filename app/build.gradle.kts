import groovy.json.JsonSlurper
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services") apply false
}

val googleServicesConfigFile = layout.projectDirectory.file("google-services.json").asFile
if (googleServicesConfigFile.isFile) {
    apply(plugin = "com.google.gms.google-services")
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

fun gitOutput(vararg args: String): String = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) output else ""
}.getOrDefault("")

fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

val buildGitSha = firstNonBlank(
    System.getenv("ROTA_CERTA_GIT_SHA"),
    System.getenv("GITHUB_SHA"),
    gitOutput("rev-parse", "HEAD"),
).ifBlank { "unavailable" }

val detectedBuildBranch = firstNonBlank(
    System.getenv("ROTA_CERTA_GIT_BRANCH"),
    System.getenv("GITHUB_HEAD_REF"),
    System.getenv("GITHUB_REF_NAME"),
    gitOutput("rev-parse", "--abbrev-ref", "HEAD"),
)
val buildGitBranch = detectedBuildBranch.takeUnless { it == "HEAD" }.orEmpty().ifBlank { "unavailable" }

val buildGeneratedAt = firstNonBlank(System.getenv("ROTA_CERTA_BUILD_TIME")).ifBlank {
    ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}

val minimumVersionCode = 5_020
val ciVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { maxOf(minimumVersionCode, 5_000 + it) }
val appVersionCode = ciVersionCode ?: minimumVersionCode
val releaseVersionCode = 6_036
val releaseVersionName = "0.1.745"
val stableDebugKeystoreSource = layout.projectDirectory.file("debug-signing/rota-certa-debug.keystore.b64").asFile
val stableDebugKeystoreFile = rootProject.file(".gradle/rota-certa-signing/rota-certa-debug.keystore")
if (stableDebugKeystoreSource.exists()) {
    stableDebugKeystoreFile.parentFile.mkdirs()
    stableDebugKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(stableDebugKeystoreSource.readText()))
}

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
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.mapeiaia.rotacerta"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsApiKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "BUILD_GIT_SHA", "\"${buildGitSha.escapeForBuildConfig()}\"")
        buildConfigField("String", "BUILD_GIT_BRANCH", "\"${buildGitBranch.escapeForBuildConfig()}\"")
        buildConfigField("String", "BUILD_GENERATED_AT", "\"${buildGeneratedAt.escapeForBuildConfig()}\"")
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
        debug { signingConfig = signingConfigs.getByName("stableDebug") }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val releaseHistoryFile = layout.projectDirectory.file("src/main/assets/release_history.json").asFile
val verifyReleaseHistory by tasks.registering {
    group = "verification"
    description = "Verifies that the installed release has exactly one authoritative semantic history record."
    inputs.file(releaseHistoryFile)
    doLast {
        require(releaseHistoryFile.isFile) { "Missing authoritative release history: ${releaseHistoryFile.path}" }
        val root = JsonSlurper().parse(releaseHistoryFile) as? Map<*, *> ?: error("release_history.json must contain a JSON object")
        val releases = root["releases"] as? List<*> ?: error("release_history.json must contain a releases array")
        val matches = releases.mapNotNull { it as? Map<*, *> }.filter { release ->
            release["version"] == releaseVersionName && (release["build"] as? Number)?.toInt() == releaseVersionCode
        }
        require(matches.size == 1) { "Expected exactly one history record for $releaseVersionName/$releaseVersionCode; found ${matches.size}" }
    }
}

val updateBaselineFile = layout.projectDirectory.file("update-baseline.json").asFile
val verifyAndroidUpdateContract by tasks.registering {
    group = "verification"
    description = "Rejects package, version or signing changes that break installed-app updates."
    inputs.file(updateBaselineFile)
    inputs.file(stableDebugKeystoreFile)
    doLast {
        val baseline = JsonSlurper().parse(updateBaselineFile) as Map<*, *>
        require(android.defaultConfig.applicationId == baseline["application_id"]) {
            "Update must preserve the installed applicationId."
        }
        require(releaseVersionCode > (baseline["version_code"] as Number).toInt()) {
            "Update versionCode must be higher than the installed baseline."
        }
        require(android.buildTypes.getByName("debug").signingConfig?.storeFile == stableDebugKeystoreFile) {
            "Update must use the existing stable signing key."
        }
        val keystore = KeyStore.getInstance(stableDebugKeystoreFile, "rotacerta".toCharArray())
        val certificate = requireNotNull(keystore.getCertificate("rotacerta-debug"))
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(fingerprint == baseline["signer_sha256"]) {
            "Signing certificate differs from the installed baseline. Do not uninstall to bypass this failure."
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyReleaseHistory, verifyAndroidUpdateContract)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("app.organicmaps.sdk:sdk:2026.08.27-18")
    implementation("app.organicmaps.sdk:maps-world:2026.08.27-18")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
