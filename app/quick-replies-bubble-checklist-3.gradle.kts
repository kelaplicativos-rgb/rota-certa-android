// Checklist 3 — Respostas rápidas ligadas ao popup final da bolinha.
// Executa por último para sobreviver à cadeia histórica que materializa o serviço
// e o catálogo de atalhos durante o preBuild.

fun patchQuickReplyCatalogChecklist3(file: java.io.File) {
    if (!file.exists()) throw GradleException("BubbleShortcutModule.kt ausente para o checklist 3.")
    var text = file.readText()

    if ("    OpenQuickReplies," !in text) {
        val enumAnchor = "enum class BubbleShortcutAction {\n"
        if (enumAnchor !in text) throw GradleException("Enum de atalhos não encontrado para respostas rápidas.")
        text = text.replaceFirst(enumAnchor, enumAnchor + "    OpenQuickReplies,\n")
    }

    if ("object QuickRepliesBubbleShortcutModule" !in text) {
        val catalogAnchor = "object BubbleShortcutCatalog {"
        if (catalogAnchor !in text) throw GradleException("Catálogo da bolinha não encontrado para respostas rápidas.")
        val module = """object QuickRepliesBubbleShortcutModule : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "quick_replies",
        emoji = "💬",
        label = "Respostas rápidas",
        action = BubbleShortcutAction.OpenQuickReplies,
        displayLabel = "Respostas",
    )
}

"""
        text = text.replaceFirst(catalogAnchor, module + catalogAnchor)
    }

    val listStartToken = "    val modules: List<BubbleShortcutModule> = listOf(\n"
    val listStart = text.indexOf(listStartToken)
    val listEnd = if (listStart >= 0) text.indexOf("    )", listStart + listStartToken.length) else -1
    if (listStart < 0 || listEnd < 0) throw GradleException("Lista de atalhos não encontrada para respostas rápidas.")
    var listRegion = text.substring(listStart, listEnd)
    if ("QuickRepliesBubbleShortcutModule," !in listRegion) {
        val stopAnchor = "        StopBubbleShortcutModule,\n"
        listRegion = if (stopAnchor in listRegion) {
            listRegion.replaceFirst(stopAnchor, "        QuickRepliesBubbleShortcutModule,\n" + stopAnchor)
        } else {
            listRegion + "        QuickRepliesBubbleShortcutModule,\n"
        }
        text = text.substring(0, listStart) + listRegion + text.substring(listEnd)
    }

    val refreshedStart = text.indexOf(listStartToken)
    val refreshedEnd = text.indexOf("    )", refreshedStart + listStartToken.length)
    val refreshedRegion = text.substring(refreshedStart, refreshedEnd)
    val moduleCount = Regex("(?m)^\\s{8}[A-Za-z0-9_]+,\\s*$").findAll(refreshedRegion).count()
    if (moduleCount <= 0) throw GradleException("Não consegui contar os atalhos finais do popup.")
    text = text.replace(
        Regex("require\\(modules\\.size == \\d+\\) \\{ \\\"[^\\\"]*\\\" \\}"),
        "require(modules.size == $moduleCount) { \"O popup deve conter $moduleCount módulos.\" }",
    )

    listOf(
        "OpenQuickReplies",
        "QuickRepliesBubbleShortcutModule",
        "id = \"quick_replies\"",
        "displayLabel = \"Respostas\"",
    ).forEach { marker ->
        if (marker !in text) throw GradleException("Contrato do atalho de respostas ausente: $marker")
    }
    file.writeText(text)
}

fun addQuickReplyImportChecklist3(source: String, importLine: String, anchor: String): String {
    if (importLine in source) return source
    if (anchor !in source) throw GradleException("Âncora de importação ausente para $importLine")
    return source.replaceFirst(anchor, anchor + importLine + "\n")
}

fun patchQuickReplyServiceChecklist3(file: java.io.File) {
    if (!file.exists()) throw GradleException("LiveRideAccessibilityService.kt ausente para o checklist 3.")
    var service = file.readText()

    service = addQuickReplyImportChecklist3(
        service,
        "import android.content.BroadcastReceiver",
        "import android.content.Context\n",
    )
    service = addQuickReplyImportChecklist3(
        service,
        "import android.content.IntentFilter",
        "import android.content.Intent\n",
    )
    service = addQuickReplyImportChecklist3(
        service,
        "import androidx.core.content.ContextCompat",
        "import androidx.annotation.RequiresApi\n",
    )

    if ("quick_reply_receiver_checklist_3" !in service) {
        val fieldAnchor = "    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)\n"
        if (fieldAnchor !in service) throw GradleException("Escopo do serviço ausente para o receptor de respostas.")
        val fields = """    private var quickReplyTargetPackageNameChecklist3: String? = null
    private var quickReplyReceiverRegisteredChecklist3 = false
    private val quickReplyReceiverChecklist3 = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_APPLY_QUICK_REPLY) return
            val replyText = intent.getStringExtra(EXTRA_QUICK_REPLY_TEXT)?.trim().orEmpty()
            val expectedPackage = QuickReplyTargetPolicy.normalize(
                intent.getStringExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE),
            ) ?: quickReplyTargetPackageNameChecklist3
            if (replyText.isBlank() || expectedPackage == null) return
            scope.launch {
                delay(80L)
                QuickReplyAccessibilityFiller.apply(
                    service = this@LiveRideAccessibilityService,
                    replyText = replyText,
                    expectedPackageName = expectedPackage,
                )
            }
        }
    } // quick_reply_receiver_checklist_3
"""
        service = service.replaceFirst(fieldAnchor, fieldAnchor + fields)
    }

    if ("quick_reply_receiver_registration_checklist_3" !in service) {
        val createAnchor = "        super.onCreate()\n"
        if (createAnchor !in service) throw GradleException("onCreate do serviço ausente para registrar respostas rápidas.")
        val registration = """        if (!quickReplyReceiverRegisteredChecklist3) {
            ContextCompat.registerReceiver(
                this,
                quickReplyReceiverChecklist3,
                IntentFilter(ACTION_APPLY_QUICK_REPLY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            quickReplyReceiverRegisteredChecklist3 = true
        } // quick_reply_receiver_registration_checklist_3
"""
        service = service.replaceFirst(createAnchor, createAnchor + registration)
    }

    if ("quick_reply_receiver_unregister_checklist_3" !in service) {
        val destroyAnchor = "    override fun onDestroy() {\n"
        if (destroyAnchor !in service) throw GradleException("onDestroy do serviço ausente para remover receptor.")
        val cleanup = """        if (quickReplyReceiverRegisteredChecklist3) {
            runCatching { unregisterReceiver(quickReplyReceiverChecklist3) }
            quickReplyReceiverRegisteredChecklist3 = false
        } // quick_reply_receiver_unregister_checklist_3
"""
        service = service.replaceFirst(destroyAnchor, destroyAnchor + cleanup)
    }

    val executeStart = service.indexOf("    private fun executeShortcutModule(spec: BubbleShortcutSpec) {")
    val executeEnd = if (executeStart >= 0) service.indexOf("    private fun ", executeStart + 10) else -1
    if (executeStart < 0 || executeEnd < 0) throw GradleException("Executor final dos atalhos ausente para respostas rápidas.")
    var executeRegion = service.substring(executeStart, executeEnd)
    if ("quick_reply_action_checklist_3" !in executeRegion) {
        val whenAnchor = "        when (spec.action) {\n"
        if (whenAnchor !in executeRegion) throw GradleException("when dos atalhos ausente para respostas rápidas.")
        executeRegion = executeRegion.replaceFirst(
            whenAnchor,
            whenAnchor + "            BubbleShortcutAction.OpenQuickReplies -> openQuickRepliesFromBubble() // quick_reply_action_checklist_3\n",
        )
        service = service.substring(0, executeStart) + executeRegion + service.substring(executeEnd)
    }

    if ("open_quick_replies_checklist_3" !in service) {
        val helperAnchor = "    private fun openCollectorFromBubble() {\n"
        val fallbackAnchor = "    private fun toggleLiveReadingFromBubble() {\n"
        val insertionAnchor = when {
            helperAnchor in service -> helperAnchor
            fallbackAnchor in service -> fallbackAnchor
            else -> throw GradleException("Ponto de inserção do fluxo de respostas rápidas ausente.")
        }
        val helper = """    private fun openQuickRepliesFromBubble() {
        shortcutOverlayController.hideAll()
        persistResourceShortcutState()
        val targetPackage = listOf(currentRootPackageName(), currentWindowPackageName())
            .firstNotNullOfOrNull { candidate ->
                QuickReplyTargetPolicy.normalize(candidate)
                    ?.takeUnless { normalized -> normalized == packageName }
            }
        if (targetPackage == null) {
            toast("Abra primeiro a conversa onde deseja inserir a resposta.")
            return
        }
        quickReplyTargetPackageNameChecklist3 = targetPackage
        runCatching {
            startActivity(
                Intent(this, QuickRepliesActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                    .putExtra(EXTRA_QUICK_REPLY_TARGET_PACKAGE, targetPackage),
            )
        }.onFailure {
            toast("Não foi possível abrir as respostas rápidas.")
        }
    } // open_quick_replies_checklist_3

"""
        service = service.replaceFirst(insertionAnchor, helper + insertionAnchor)
    }

    listOf(
        "quick_reply_receiver_checklist_3",
        "quick_reply_receiver_registration_checklist_3",
        "quick_reply_receiver_unregister_checklist_3",
        "quick_reply_action_checklist_3",
        "open_quick_replies_checklist_3",
        "QuickReplyAccessibilityFiller.apply",
        "EXTRA_QUICK_REPLY_TARGET_PACKAGE",
        "ContextCompat.RECEIVER_NOT_EXPORTED",
    ).forEach { marker ->
        if (marker !in service) throw GradleException("Contrato de respostas rápidas ausente no serviço: $marker")
    }

    file.writeText(service)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchQuickReplyCatalogChecklist3(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/BubbleShortcutModule.kt").asFile,
        )
        patchQuickReplyServiceChecklist3(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt").asFile,
        )
    }
}
