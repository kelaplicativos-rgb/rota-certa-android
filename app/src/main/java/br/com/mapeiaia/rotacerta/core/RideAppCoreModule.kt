package br.com.mapeiaia.rotacerta.core

interface RideAppCoreModule {
    val moduleName: String
    val packageNames: Set<String>

    fun supports(packageName: String?): Boolean =
        packageName?.lowercase() in packageNames

    fun classify(snapshot: RideScreenSnapshot): RideScreenClassification
}
