package br.com.mapeiaia.rotacerta

/** Tracks the single ride card currently allowed to control the floating bubble. */
class BubbleCardSessionStore {
    private var activeSession: BubbleCardSession? = null

    val current: BubbleCardSession?
        get() = activeSession

    fun startOrUpdate(
        packageName: String?,
        snapshotHash: Int,
        text: String,
        fields: RideFields,
        templateName: String?,
    ): BubbleCardSession {
        val session = BubbleCardSession(
            packageName = packageName?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            snapshotHash = snapshotHash,
            textHash = text.hashCode(),
            destination = fields.destination,
            pickup = fields.pickup,
            templateName = templateName,
            startedAtMillis = activeSession?.takeIf { it.snapshotHash == snapshotHash }?.startedAtMillis
                ?: System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
        )
        activeSession = session
        return session
    }

    fun shouldClearFor(newPackageName: String?, newSnapshotHash: Int?): Boolean {
        val session = activeSession ?: return false
        if (newSnapshotHash == null) return true
        val normalizedPackage = newPackageName?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedPackage != null && session.packageName != null && normalizedPackage != session.packageName) return true
        return session.snapshotHash != newSnapshotHash
    }

    fun clear(reason: String, newSnapshotHash: Int? = null): BubbleSessionClearEvent {
        val previous = activeSession
        activeSession = null
        return BubbleSessionClearEvent(
            reason = reason,
            previousSnapshotHash = previous?.snapshotHash,
            newSnapshotHash = newSnapshotHash,
            previousPackageName = previous?.packageName,
        )
    }
}

data class BubbleCardSession(
    val packageName: String?,
    val snapshotHash: Int,
    val textHash: Int,
    val destination: String?,
    val pickup: String?,
    val templateName: String?,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
)

data class BubbleSessionClearEvent(
    val reason: String,
    val previousSnapshotHash: Int?,
    val newSnapshotHash: Int?,
    val previousPackageName: String?,
)
