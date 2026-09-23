package br.com.mapeiaia.rotacerta

object FarolPackageEntryGate638 {
    enum class Outcome {
        ALLOW_SELECTED,
        ALLOW_SELECTED_ROOT_UNDER_TRANSIENT,
        IGNORE_OWN_OR_TRANSIENT,
        REJECT_FOREIGN,
    }

    data class Decision(
        val outcome: Outcome,
        val authorityPackage: String? = null,
    ) {
        val allowHeavyPipeline: Boolean
            get() = outcome == Outcome.ALLOW_SELECTED
    }

    fun decide(
        eventPackageName: String?,
        rootPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
        transientOverlay: (String?) -> Boolean,
    ): Decision {
        val event = SelectedRideAppStore.normalize(eventPackageName)
        val root = SelectedRideAppStore.normalize(rootPackageName)
        val own = SelectedRideAppStore.normalize(ownPackageName)
        val selected = selectedPackages.mapNotNull(SelectedRideAppStore::normalize).toSet()
        if (event != null && event in selected) return Decision(Outcome.ALLOW_SELECTED, event)
        if (event == null && root != null && root in selected) return Decision(Outcome.ALLOW_SELECTED, root)
        if (root != null && root in selected && (event == own || transientOverlay(event))) {
            return Decision(Outcome.ALLOW_SELECTED_ROOT_UNDER_TRANSIENT, root)
        }
        if (event == own || transientOverlay(event)) return Decision(Outcome.IGNORE_OWN_OR_TRANSIENT, root?.takeIf { it in selected })
        return Decision(Outcome.REJECT_FOREIGN)
    }
}
