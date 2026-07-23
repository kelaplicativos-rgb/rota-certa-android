// Compatibilidade final do leitor universal:
// - mantem a trava de idempotencia;
// - mantem os metadados e a sonda de validacao;
// - modelos de cards nascem zerados, podem ser cadastrados manualmente e sao opcionais;
// - aplicativos monitorados nascem zerados e dependem da selecao manual;
// - dois enderecos so liberam rota quando existem sinais reais de corrida.
//
// O contrato ativo e: o usuario escolhe o aplicativo; quando necessario, pode
// cadastrar um modelo do card. O leitor valida passageiro e dois enderecos, usa
// o ultimo como destino e calcula ate o endereco definido pelo usuario.
apply(from = "patch-universal-two-address-idempotence.gradle.kts")
apply(from = "universal-overlay-runtime-metadata.gradle.kts")
apply(from = "in-app-bubble-immediate-state.gradle.kts")
apply(from = "universal-runtime-state-probe.gradle.kts")
apply(from = "universal-immediate-gray-clear.gradle.kts")
apply(from = "universal-runtime-stability-guard.gradle.kts")
apply(from = "universal-optional-model-contract.gradle.kts")
apply(from = "universal-flattened-address-boundary.gradle.kts")
apply(from = "universal-idempotence-compatibility.gradle.kts")
apply(from = "universal-probe-idempotence.gradle.kts")
apply(from = "universal-no-card-runtime-final.gradle.kts")
apply(from = "universal-no-card-anchor-compat.gradle.kts")
apply(from = "universal-no-card-process-anchor.gradle.kts")
apply(from = "universal-no-card-compile-repair.gradle.kts")
apply(from = "session-diagnostic-bootstrap.gradle.kts")
apply(from = "session-diagnostic-v2.gradle.kts")
apply(from = "session-diagnostic-retention.gradle.kts")
apply(from = "universal-overlay-self-window-fix.gradle.kts")
apply(from = "universal-poi-destination-boundary.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.108.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.109.gradle.kts")
apply(from = "universal-fast-read-runtime-0.1.110.gradle.kts")
apply(from = "universal-99-card-addresses-0.1.111.gradle.kts")
apply(from = "universal-99-card-continuation-0.1.111.gradle.kts")
apply(from = "universal-0.1.111-idempotence-compat.gradle.kts")
apply(from = "universal-ride-card-evidence-0.1.112.gradle.kts")
apply(from = "universal-fragmented-street-prefix-0.1.113.gradle.kts")
apply(from = "in-app-grouped-bubble-home-0.1.115.gradle.kts")
apply(from = "in-app-grouped-bubble-anchor-compat-0.1.115.gradle.kts")
apply(from = "in-app-grouped-bubble-navigation-compat-0.1.115.gradle.kts")
apply(from = "bubble-instant-drag-0.1.116.gradle.kts")
apply(from = "bubble-resource-shortcuts-runtime-0.1.117.gradle.kts")
apply(from = "bubble-shortcut-legacy-click-compat-0.1.117.gradle.kts")
apply(from = "bubble-shortcut-navigation-0.1.117.gradle.kts")
apply(from = "professional-bubble-import-compat-0.1.118.gradle.kts")
apply(from = "professional-bubble-home-0.1.118.gradle.kts")
apply(from = "professional-bubble-marker-compat-0.1.118.gradle.kts")
apply(from = "popup-only-control-center-0.1.119.gradle.kts")
apply(from = "popup-only-compile-cleanup-0.1.119.gradle.kts")
apply(from = "popup-navigation-separation-0.1.120.gradle.kts")
apply(from = "popup-navigation-professional-compat-0.1.120.gradle.kts")
apply(from = "popup-navigation-compile-repair-0.1.120.gradle.kts")
apply(from = "popup-navigation-card-state-0.1.120.gradle.kts")
apply(from = "popup-navigation-final-compile-0.1.120.gradle.kts")
apply(from = "popup-gesture-validator-compat-0.1.120.gradle.kts")
apply(from = "universal-ocr-freshness-0.1.120.gradle.kts")
apply(from = "universal-route-inflight-protection-0.1.120.gradle.kts")
apply(from = "maparadar-flexible-file-reader-0.1.120.gradle.kts")
apply(from = "radar-work-tracking-0.1.121.gradle.kts")
apply(from = "work-tracking-card-anchor-compat-0.1.121.gradle.kts")
apply(from = "selected-app-finalizer-0.1.122.gradle.kts")
apply(from = "cards-selected-apps-visible-0.1.123.gradle.kts")
apply(from = "instant-farol-decision-0.1.124.gradle.kts")
apply(from = "passenger-unicode-normalization-0.1.124.gradle.kts")
apply(from = "fast-read-legacy-marker-compat-0.1.124.gradle.kts")
apply(from = "instant-farol-prebuild-finalizer-0.1.124.gradle.kts")
apply(from = "subsecond-exact-red-0.1.125.gradle.kts")
apply(from = "primary-visible-card-scope-0.1.125.gradle.kts")
apply(from = "universal-no-pre-registered-gates-0.1.126.gradle.kts")
apply(from = "selected-app-legacy-idempotence-0.1.126.gradle.kts")
apply(from = "manual-apps-cards-anchor-compat-0.1.127.gradle.kts")
apply(from = "manual-apps-cards-exact-route-0.1.127.gradle.kts")
apply(from = "manual-ui-annotation-cleanup-0.1.127.gradle.kts")
apply(from = "manual-models-optional-finalizer-0.1.127.gradle.kts")
apply(from = "version-0.1.101.gradle.kts")
apply(from = "version-0.1.102.gradle.kts")
apply(from = "version-0.1.103.gradle.kts")
apply(from = "version-0.1.104.gradle.kts")
apply(from = "version-0.1.105.gradle.kts")
apply(from = "version-0.1.106.gradle.kts")
apply(from = "version-0.1.107.gradle.kts")
apply(from = "version-0.1.108.gradle.kts")
apply(from = "version-0.1.109.gradle.kts")
apply(from = "version-0.1.110.gradle.kts")
apply(from = "version-0.1.111.gradle.kts")
apply(from = "version-0.1.112.gradle.kts")
apply(from = "version-0.1.113.gradle.kts")
apply(from = "version-0.1.114.gradle.kts")
apply(from = "version-0.1.115.gradle.kts")
apply(from = "version-0.1.116.gradle.kts")
apply(from = "version-0.1.117.gradle.kts")
apply(from = "version-0.1.118.gradle.kts")
apply(from = "version-0.1.119.gradle.kts")
apply(from = "version-0.1.120.gradle.kts")
apply(from = "version-0.1.121.gradle.kts")
apply(from = "version-0.1.122.gradle.kts")
apply(from = "version-0.1.123.gradle.kts")
apply(from = "version-0.1.124.gradle.kts")
apply(from = "version-0.1.125.gradle.kts")
apply(from = "version-0.1.126.gradle.kts")
apply(from = "version-0.1.127.gradle.kts")

// Ultima porta da cadeia de geracao. Executa depois do preBuild e de todos os
// patches historicos, materializando o codigo realmente compilado da 0.1.128.
val finalizeRotaCerta0128EffectiveSource by tasks.registering {
    dependsOn("preBuild")
    mustRunAfter("clean")
    outputs.upToDateWhen { false }

    doLast {
        val sourceRoot = layout.projectDirectory.dir("src/main/java/br/com/mapeiaia/rotacerta")
        val serviceFile = sourceRoot.file("LiveRideAccessibilityService.kt").asFile
        val mainFile = sourceRoot.file("MainActivity.kt").asFile
        val quickRepliesFile = sourceRoot.file("QuickRepliesActivity.kt").asFile
        if (!serviceFile.exists() || !mainFile.exists() || !quickRepliesFile.exists()) {
            throw GradleException("Fontes 0.1.128 nao encontradas para finalizacao.")
        }

        var service = serviceFile.readText()
        service = service.replace(
            "scope.launch { applyQuickReplyToFocusedField(text) }",
            "scope.launch { QuickReplyAccessibilityFiller.apply(this@LiveRideAccessibilityService, text) }",
        )
        service = service.replace("cardText.take(DIAGNOSTIC_TEXT_LIMIT)", "cardText.take(1_600)")
        service = service.replace("""Regex("\s+")""", """Regex("\\s+")""")
        serviceFile.writeText(service)

        var main = mainFile.readText()
        main = main.replace(
            "    onOpenQuickReplies: () -> Unit,",
            "    onOpenQuickReplies: () -> Unit = {},",
        )
        mainFile.writeText(main)

        val quickReplies = quickRepliesFile.readText()
            .replace("import androidx.compose.foundation.layout.weight\n", "")
        quickRepliesFile.writeText(quickReplies)

        val fillerFile = sourceRoot.file("QuickReplyAccessibilityFiller.kt").asFile
        fillerFile.writeText(
            """package br.com.mapeiaia.rotacerta

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.delay

internal object QuickReplyAccessibilityFiller {
    suspend fun apply(service: AccessibilityService, replyText: String) {
        val normalized = replyText.trim()
        if (normalized.isBlank()) return
        repeat(8) { attempt ->
            if (attempt > 0) delay(120L)
            val root = service.rootInActiveWindow ?: return@repeat
            val target = findEditableNode(root) ?: return@repeat
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, normalized)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                Toast.makeText(service, "Resposta inserida.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resposta rapida", normalized))
        Toast.makeText(
            service,
            "Nao consegui preencher automaticamente. A resposta foi copiada.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) return node
        for (index in 0 until node.childCount) {
            findEditableNode(runCatching { node.getChild(index) }.getOrNull())?.let { return it }
        }
        return node.takeIf { it.isEditable }
    }
} // quick_reply_effective_filler_0_1_128
""",
        )

        val keystoreSource = layout.projectDirectory.file("debug-signing/rota-certa-debug.keystore.b64").asFile
        val keystoreTarget = layout.buildDirectory.file("generated/signing/rota-certa-debug.keystore").get().asFile
        if (!keystoreSource.exists()) throw GradleException("Fonte da chave debug estavel nao encontrada.")
        keystoreTarget.parentFile.mkdirs()
        keystoreTarget.writeBytes(
            java.util.Base64.getMimeDecoder().decode(keystoreSource.readText()),
        )

        listOf(
            "QuickReplyAccessibilityFiller.apply",
            "Regex(\"\\\\s+\")",
            "automatic_card_capture_0_1_128",
            "cache_first_before_yellow_0_1_128",
            "single_bubble_render_coordinator_0_1_128",
        ).forEach { marker ->
            if (marker !in serviceFile.readText()) {
                throw GradleException("Finalizador 0.1.128 sem contrato: $marker")
            }
        }
    }
}

finalizeRotaCerta0128EffectiveSource.configure {
    mustRunAfter(
        tasks.matching { task ->
            task.name != "finalizeRotaCerta0128EffectiveSource" &&
                (task.name.contains("Patch") ||
                    task.name.contains("Finalizer") ||
                    task.name.startsWith("universal") ||
                    task.name.startsWith("manual") ||
                    task.name == "generatedSourceSanitizer")
        },
    )
}

tasks.matching { task ->
    task.name.startsWith("compile") ||
        task.name == "validateSigningDebug" ||
        task.name.startsWith("lint") ||
        task.name.startsWith("testDebug")
}.configureEach {
    dependsOn(finalizeRotaCerta0128EffectiveSource)
}
