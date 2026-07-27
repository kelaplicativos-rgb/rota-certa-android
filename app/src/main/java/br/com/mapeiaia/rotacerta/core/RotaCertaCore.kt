package br.com.mapeiaia.rotacerta.core

import br.com.mapeiaia.rotacerta.RideFields

/** Generic screen classifier; package authorization is handled by CorePackageMonitor. */
object RotaCertaCore {
    fun classifyScreen(packageName: String?, text: String, fields: RideFields): RideScreenClassification =
        ManualScreenModule.classify(RideScreenSnapshot(packageName, text, fields))
}
