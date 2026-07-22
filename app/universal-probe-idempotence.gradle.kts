// A sonda de runtime e instalada antes da guarda 0.1.101 na primeira chamada do
// Gradle. Em chamadas seguintes o servico ja contem a instrumentacao funcional
// e o processo novo; tentar procurar novamente o bloco legado causa falha.
val universalStableServiceSource = layout.projectDirectory.file(
    "src/main/java/br/com/mapeiaia/rotacerta/LiveRideAccessibilityService.kt",
)

tasks.named("universalRuntimeStateProbe").configure {
    onlyIf {
        val file = universalStableServiceSource.asFile
        !file.exists() || "universal_runtime_stability_guard_0_1_101" !in file.readText()
    }
}
