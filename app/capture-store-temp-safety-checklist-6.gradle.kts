// Checklist 6 — arquivos temporários de JPEG não podem ser removidos durante gravação.
fun patchCaptureStoreTempSafetyChecklist6(file: java.io.File) {
    if (!file.exists()) throw GradleException("AutomaticRideCaptureStore.kt ausente.")
    var text = file.readText()
    val old = "            if (file.name !in referenced) file.delete()\n"
    if (old !in text && "capture_tmp_write_safety_checklist_6" !in text) {
        throw GradleException("Limpeza de imagens não encontrada para proteção temporária.")
    }
    text = text.replaceFirst(
        old,
        "            if (!file.name.endsWith(\".tmp\") && file.name !in referenced) file.delete() // capture_tmp_write_safety_checklist_6\n",
    )
    if ("capture_tmp_write_safety_checklist_6" !in text) {
        throw GradleException("Proteção do arquivo temporário não aplicada.")
    }
    file.writeText(text)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doLast {
        patchCaptureStoreTempSafetyChecklist6(
            layout.projectDirectory.file("src/main/java/br/com/mapeiaia/rotacerta/AutomaticRideCaptureStore.kt").asFile,
        )
    }
}
