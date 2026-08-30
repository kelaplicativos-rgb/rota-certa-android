package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.Build

object AppBuildInfo {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val versionCode: Int
        get() = BuildConfig.VERSION_CODE

    val commit: String
        get() = BuildConfig.BUILD_GIT_SHA.ifBlank { "unavailable" }

    val commitShort: String
        get() = commit.takeIf { it != "unavailable" }?.take(7) ?: "unavailable"

    val branch: String
        get() = BuildConfig.BUILD_GIT_BRANCH.ifBlank { "unavailable" }

    val buildGeneratedAt: String
        get() = BuildConfig.BUILD_GENERATED_AT.ifBlank { "unavailable" }

    fun copyText(): String =
        "Rota Certa $versionName ($versionCode) | commit $commitShort | branch $branch | build $buildGeneratedAt"

    fun reportHeader(context: Context): String = buildString {
        appendLine("ROTA CERTA — IDENTIFICACAO DA BUILD")
        appendLine("VersionName: $versionName")
        appendLine("VersionCode: $versionCode")
        appendLine("Commit: $commitShort")
        appendLine("Commit completo: $commit")
        appendLine("Branch: $branch")
        appendLine("Build gerada em: $buildGeneratedAt")
        appendLine("Pacote: ${context.applicationContext.packageName}")
        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
    }.trimEnd()
}
