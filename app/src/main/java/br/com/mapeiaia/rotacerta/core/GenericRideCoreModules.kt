package br.com.mapeiaia.rotacerta.core

object ManualScreenModule : RideAppCoreModule {
    override val moduleName: String = "Manual"
    override val packageNames: Set<String> = emptySet()
    override fun supports(packageName: String?): Boolean = true

    override fun classify(snapshot: RideScreenSnapshot): RideScreenClassification {
        if (snapshot.text.isBlank()) {
            return RideScreenClassification(
                kind = RideScreenKind.PartialRideCard,
                packageName = snapshot.packageName,
                reason = "Texto vazio; aguardando dois endereços visíveis.",
                confidence = 0.0,
            )
        }
        val complete = !snapshot.fields.pickup.isNullOrBlank() && !snapshot.fields.destination.isNullOrBlank()
        return RideScreenClassification(
            kind = if (complete) RideScreenKind.OpenRideCard else RideScreenKind.PartialRideCard,
            packageName = snapshot.packageName,
            reason = if (complete) "Dois endereços visíveis; último endereço definido como destino." else "Aguardando dois endereços visíveis.",
            confidence = if (complete) 1.0 else 0.25,
        )
    }
}
