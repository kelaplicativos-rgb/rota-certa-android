// Remove a dependencia circular criada pelo filtro generico de tarefas com nome "Fix".
// Ordem final: patches legados -> universal V2 -> compile fix -> compilacao/testes.

tasks.named("universalLastAddressFinalV2").configure {
    setMustRunAfter(
        tasks.matching { task ->
            task.name !in setOf(
                name,
                "universalLastAddressFinalPatch",
                "universalLastAddressCompileFix",
                "universalLastAddressFinalV2",
            ) &&
                !task.name.startsWith("compile") &&
                !task.name.startsWith("test") &&
                task.name !in setOf("preBuild", "assemble", "assembleDebug") &&
                (task.name.contains("patch", true) || task.name.contains("fix", true) || task.name.startsWith("enforce", true))
        },
    )
}

tasks.named("universalLastAddressCompileFix").configure {
    setMustRunAfter(listOf("universalLastAddressFinalV2"))
}
