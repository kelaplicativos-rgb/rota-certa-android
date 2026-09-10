package br.com.mapeiaia.rotacerta.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.com.mapeiaia.rotacerta.BuildConfig
import br.com.mapeiaia.rotacerta.UnifiedDebugEventStore
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Private, read-only forensic evidence for the authenticated BlaBlaCar "Suas viagens" page.
 *
 * This store is deliberately isolated from TripStore, the public Agenda and collector
 * publication state. Files live only under app-private filesDir.
 */
@Serializable
internal data class BlaBlaRidesSnapshotFile0526(
    val relativePath: String = "",
    val bytes: Long = 0L,
    val sha256: String = "",
)

@Serializable
internal data class BlaBlaRidesSnapshotProfile0526(
    val accountKey: String,
    val expectedProfileUuid: String = "",
    val authenticatedProfileUuid: String = "",
    val displayName: String = "",
    val identityConfirmed: Boolean = false,
    val identityEvidence: BlaBlaRidesIdentityEvidence0528 = BlaBlaRidesIdentityEvidence0528(),
    val identityFile: String = "",
    val identityBytes: Long = 0L,
    val identitySha256: String = "",
    val startedAt: String = "",
    val completedAt: String = "",
    val finalUrl: String = "",
    val timestamp: String = "",
    val cardCountInitial: Int = 0,
    val cardCountFinal: Int = 0,
    val scrollIterations: Int = 0,
    val reachedEnd: Boolean = false,
    val endEvidence: BlaBlaRidesEndEvidence0528 = BlaBlaRidesEndEvidence0528(),
    val stabilized: Boolean = false,
    val stabilizationEvidence: BlaBlaRidesStabilizationEvidence0528 = BlaBlaRidesStabilizationEvidence0528(),
    val tripInventory: BlaBlaRidesTripInventory0528 = BlaBlaRidesTripInventory0528(),
    val ridesIndexFile: String = "",
    val ridesIndexBytes: Long = 0L,
    val ridesIndexSha256: String = "",
    val crossFormatConsistency: BlaBlaRidesCrossFormatConsistency0528 = BlaBlaRidesCrossFormatConsistency0528(),
    val rideDateRange: BlaBlaRidesRideDateRange0528 = BlaBlaRidesRideDateRange0528(),
    val htmlCaptured: Boolean = false,
    val mhtmlSupported: Boolean = true,
    val mhtmlCaptured: Boolean = false,
    val htmlFile: String = "",
    val mhtmlFile: String = "",
    val htmlBytes: Long = 0L,
    val mhtmlBytes: Long = 0L,
    val htmlSha256: String = "",
    val mhtmlSha256: String = "",
    val status: String = "PENDING",
    val errorCode: String = "",
)

@Serializable
internal data class BlaBlaRidesSnapshotManifest0526(
    val schemaVersion: String = "blablacar-rides-snapshot-v2",
    val captureId: String,
    val startedAt: String,
    val completedAt: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val commitSha: String = BuildConfig.BUILD_GIT_SHA,
    val branch: String = BuildConfig.BUILD_GIT_BRANCH,
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
    val expectedProfiles: List<String> = emptyList(),
    val manifestChecksumFile: String = "manifest.sha256",
    val result: String = "RUNNING",
    val profiles: List<BlaBlaRidesSnapshotProfile0526> = emptyList(),
)

internal object BlaBlaRidesSnapshotStatus0526 {
    const val PENDING = "PENDING"
    const val SESSION_LOADING = "SESSION_LOADING"
    const val IDENTITY_CHECK = "IDENTITY_CHECK"
    const val IDENTITY_CONFIRMED = "IDENTITY_CONFIRMED"
    const val RIDES_LOADING = "RIDES_LOADING"
    const val SCROLLING = "SCROLLING"
    const val STABILIZING = "STABILIZING"
    const val CAPTURING = "CAPTURING"
    const val COMPLETE = "COMPLETE"
    const val INCOMPLETE = "INCOMPLETE"
    const val INCONSISTENT = "INCONSISTENT"
    const val FAILED_IDENTITY = "FAILED_IDENTITY"
    const val FAILED_SESSION = "FAILED_SESSION"
    const val FAILED_NAVIGATION = "FAILED_NAVIGATION"
    const val FAILED_CAPTURE = "FAILED_CAPTURE"
}

internal class BlaBlaRidesSnapshotStore0526(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun start(accounts: List<BlaBlaDynamicAccount>): BlaBlaRidesSnapshotManifest0526 = synchronized(lock) {
        val now = Instant.now().toString()
        val captureId = now.replace(":", "-") + "_" + UUID.randomUUID().toString().take(8)
        val manifest = BlaBlaRidesSnapshotManifest0526(
            captureId = captureId,
            startedAt = now,
            expectedProfiles = accounts.mapNotNull { strongUuid(it.profileUuid) },
            profiles = accounts.map { account ->
                BlaBlaRidesSnapshotProfile0526(
                    accountKey = accountKey(account.id),
                    expectedProfileUuid = strongUuid(account.profileUuid).orEmpty(),
                    displayName = account.displayLabel.take(120),
                )
            },
        )
        writeManifest(manifest)
        manifest
    }

    fun read(captureId: String): BlaBlaRidesSnapshotManifest0526? = synchronized(lock) {
        val file = manifestFile(captureId)
        if (!file.isFile) return@synchronized null
        runCatching { json.decodeFromString(BlaBlaRidesSnapshotManifest0526.serializer(), file.readText(Charsets.UTF_8)) }
            .getOrNull()
    }

    fun updateProfile(
        captureId: String,
        accountId: String,
        update: (BlaBlaRidesSnapshotProfile0526) -> BlaBlaRidesSnapshotProfile0526,
    ): BlaBlaRidesSnapshotManifest0526? = synchronized(lock) {
        val current = readUnlocked(captureId) ?: return@synchronized null
        val key = accountKey(accountId)
        val replacement = current.copy(
            profiles = current.profiles.map { profile -> if (profile.accountKey == key) update(profile) else profile },
        )
        writeManifest(replacement)
        replacement
    }

    fun finish(captureId: String): BlaBlaRidesSnapshotManifest0526? = synchronized(lock) {
        val current = readUnlocked(captureId) ?: return@synchronized null
        val verifiedProfiles = current.profiles.map { profile ->
            if (profile.status != BlaBlaRidesSnapshotStatus0526.COMPLETE) return@map profile
            val finalError = runCatching {
                validateProfileForComplete0528(captureId, profile)
            }.getOrElse { "FINAL_FORENSIC_REVERIFY_FAILED" }
            if (finalError == null) {
                profile
            } else {
                profile.copy(
                    status = if (
                        finalError.contains("HTML_MHTML") ||
                        finalError.contains("ARTIFACT_INVALID") ||
                        finalError.contains("HASH_MISMATCH")
                    ) {
                        BlaBlaRidesSnapshotStatus0526.INCONSISTENT
                    } else {
                        BlaBlaRidesSnapshotStatus0526.INCOMPLETE
                    },
                    errorCode = "FINAL_REVERIFY_${finalError.take(100)}",
                )
            }
        }
        val statuses = verifiedProfiles.map { it.status }
        val result = ridesSnapshotGlobalResult0526(statuses)
        val replacement = current.copy(
            completedAt = Instant.now().toString(),
            result = result,
            profiles = verifiedProfiles,
        )
        writeManifest(replacement)
        writeManifestChecksum0528(replacement.captureId)
        UnifiedDebugEventStore.recordAlways(
            "RIDES_SNAPSHOT_COMPLETED",
            appContext.packageName,
            "captureId=${safeCaptureId(captureId)} result=$result profiles=${replacement.profiles.size} complete=${replacement.profiles.count { it.status == BlaBlaRidesSnapshotStatus0526.COMPLETE }} partial=${replacement.profiles.count { it.status == BlaBlaRidesSnapshotStatus0526.INCOMPLETE || it.status == BlaBlaRidesSnapshotStatus0526.INCONSISTENT }} failed=${replacement.profiles.count { it.status.startsWith("FAILED_") }} finalForensicReverify=true privateEvidence=true",
        )
        replacement
    }

    fun writeHtml(captureId: String, profileUuid: String, html: String): BlaBlaRidesSnapshotFile0526 {
        require(html.isNotBlank()) { "HTML evidence is empty" }
        val dir = profileDir(captureId, profileUuid).apply { mkdirs() }
        val file = File(dir, "suas-viagens.html")
        val temp = File(dir, "suas-viagens.html.tmp")
        temp.writeText(html, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeBytes(temp.readBytes())
            temp.delete()
        }
        return evidence(captureId, file)
    }

    fun mhtmlTarget(captureId: String, profileUuid: String): File {
        val dir = profileDir(captureId, profileUuid).apply { mkdirs() }
        return File(dir, "suas-viagens.mhtml")
    }

    fun evidence(captureId: String, file: File): BlaBlaRidesSnapshotFile0526 {
        require(file.isFile) { "Evidence file does not exist" }
        val root = captureDir(captureId).canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) { "Evidence escaped capture directory" }
        return BlaBlaRidesSnapshotFile0526(
            relativePath = canonical.relativeTo(root).invariantSeparatorsPath,
            bytes = canonical.length(),
            sha256 = sha256(canonical),
        )
    }

    fun manifestPath(captureId: String): String =
        File(captureDir(captureId), "manifest.json").absolutePath

    fun downloadEntries0527(captureId: String): List<BlaBlaRidesSnapshotDownloadEntry0527> = synchronized(lock) {
        val manifest = readUnlocked(captureId) ?: return@synchronized emptyList()
        val sourceRoot = captureDir(captureId).canonicalFile
        val relativePaths = buildList {
            add("manifest.json")
            manifest.manifestChecksumFile.takeIf(String::isNotBlank)?.let(::add)
            manifest.profiles.forEach { profile ->
                profile.identityFile.takeIf(String::isNotBlank)?.let(::add)
                profile.ridesIndexFile.takeIf(String::isNotBlank)?.let(::add)
                profile.htmlFile.takeIf(String::isNotBlank)?.let(::add)
                profile.mhtmlFile.takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
        relativePaths.mapNotNull { relative ->
            val source = File(sourceRoot, relative).canonicalFile
            if (!source.path.startsWith(sourceRoot.path + File.separator) || !source.isFile) {
                return@mapNotNull null
            }
            BlaBlaRidesSnapshotDownloadEntry0527(
                relativePath = relative.replace('\\', '/').trimStart('/'),
                file = source,
            )
        }
    }

    private fun writeManifestChecksum0528(captureId: String) {
        val manifest = manifestFile(captureId)
        if (!manifest.isFile) return
        val checksum = File(captureDir(captureId), "manifest.sha256")
        val line = "${sha256(manifest)}  manifest.json\n"
        val temp = File(checksum.parentFile, "manifest.sha256.tmp")
        temp.writeText(line, Charsets.UTF_8)
        if (!temp.renameTo(checksum)) {
            checksum.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun writeManifest(manifest: BlaBlaRidesSnapshotManifest0526) {
        val dir = captureDir(manifest.captureId).apply { mkdirs() }
        val target = File(dir, "manifest.json")
        val temp = File(dir, "manifest.json.tmp")
        temp.writeText(json.encodeToString(manifest), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun readUnlocked(captureId: String): BlaBlaRidesSnapshotManifest0526? {
        val file = manifestFile(captureId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(BlaBlaRidesSnapshotManifest0526.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun manifestFile(captureId: String): File = File(captureDir(captureId), "manifest.json")

    private fun captureDir(captureId: String): File {
        require(CAPTURE_ID.matches(captureId)) { "Invalid capture id" }
        return File(appContext.filesDir, "private-evidence/blablacar-rides/$captureId")
    }

    private fun profileDir(captureId: String, profileUuid: String): File {
        val uuid = strongUuid(profileUuid) ?: error("Strong profile UUID required")
        return File(captureDir(captureId), uuid)
    }

    internal fun accountKey(accountId: String): String =
        sha256(accountId.toByteArray(Charsets.UTF_8)).take(16)

    companion object {
        private val lock = Any()
        private val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        private val CAPTURE_ID = Regex("^[A-Za-z0-9._-]{8,96}$")

        internal fun strongUuid(raw: String?): String? =
            raw?.trim()?.lowercase()?.takeIf { UUID_RE.matches(it) }

        internal fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        internal fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        internal fun safeCaptureId(value: String): String = value.filter { it.isLetterOrDigit() || it in "._-" }.take(96)
    }
}

internal fun ridesSnapshotGlobalResult0526(statuses: Collection<String>): String = when {
    statuses.isEmpty() -> "FAILED"
    statuses.all { it == BlaBlaRidesSnapshotStatus0526.COMPLETE } -> "COMPLETE"
    statuses.any { it == BlaBlaRidesSnapshotStatus0526.COMPLETE } -> "PARTIAL_SUCCESS"
    statuses.any { it == BlaBlaRidesSnapshotStatus0526.INCOMPLETE || it == BlaBlaRidesSnapshotStatus0526.INCONSISTENT } -> "INCOMPLETE"
    else -> "FAILED"
}

internal data class BlaBlaRidesSnapshotDownloadEntry0527(
    val relativePath: String,
    val file: File,
)

internal data class BlaBlaRidesSnapshotDownloadResult0527(
    val displayName: String,
    val relativeLocation: String,
    val bytes: Long,
)

internal object BlaBlaRidesSnapshotDownload0527 {
    suspend fun download(
        context: Context,
        manifest: BlaBlaRidesSnapshotManifest0526,
    ): BlaBlaRidesSnapshotDownloadResult0527 = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Download automático requer Android 10 ou superior"
        }
        val app = context.applicationContext
        val entries = BlaBlaRidesSnapshotStore0526(app).downloadEntries0527(manifest.captureId)
        require(entries.isNotEmpty()) { "Nenhuma evidência disponível para download" }

        val displayName =
            "rota-certa-suas-viagens-${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)}.zip"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Rota Certa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android não disponibilizou um destino em Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    entries.forEach { entry ->
                        val safeRelative = entry.relativePath
                            .split('/')
                            .filter { segment -> segment.isNotBlank() && segment != "." && segment != ".." }
                            .joinToString("/")
                        require(safeRelative.isNotBlank()) { "Nome de arquivo inválido na captura" }
                        zip.putNextEntry(ZipEntry(safeRelative))
                        entry.file.inputStream().buffered().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("Não foi possível abrir o arquivo de destino em Downloads")

            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }.also { ready ->
                resolver.update(uri, ready, null, null)
            }

            BlaBlaRidesSnapshotDownloadResult0527(
                displayName = displayName,
                relativeLocation = "$relativePath/$displayName",
                bytes = entries.sumOf { it.file.length() },
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}

internal data class BlaBlaRidesSnapshotIdentity0526(
    val confirmed: Boolean,
    val authenticatedProfileUuid: String = "",
    val errorCode: String = "",
)

internal object BlaBlaRidesSnapshotIdentityPolicy0526 {
    private val UUID_RE = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

    fun resolve(
        expectedProfileUuid: String?,
        profileLinks: List<String>,
        observedUuids: List<String>,
    ): BlaBlaRidesSnapshotIdentity0526 {
        val expected = BlaBlaRidesSnapshotStore0526.strongUuid(expectedProfileUuid)
            ?: return BlaBlaRidesSnapshotIdentity0526(false, errorCode = "EXPECTED_PROFILE_UUID_MISSING")
        val strong = profileLinks.flatMap { link -> UUID_RE.findAll(link).map { it.value.lowercase() }.toList() }
            .distinct()
        val authenticated = strong.singleOrNull().orEmpty()
        if (authenticated == expected) return BlaBlaRidesSnapshotIdentity0526(true, authenticated)
        if (authenticated.isNotBlank()) {
            return BlaBlaRidesSnapshotIdentity0526(false, authenticated, "PROFILE_UUID_MISMATCH")
        }
        if (strong.size > 1) {
            return BlaBlaRidesSnapshotIdentity0526(false, errorCode = "PROFILE_IDENTITY_AMBIGUOUS")
        }
        val observed = observedUuids.mapNotNull(BlaBlaRidesSnapshotStore0526::strongUuid).distinct()
        val diagnosticCandidate = observed.singleOrNull().orEmpty()
        return BlaBlaRidesSnapshotIdentity0526(false, diagnosticCandidate, "PROFILE_IDENTITY_NOT_STRONG")
    }
}

internal data class BlaBlaRidesSnapshotObservation0526(
    val cardCount: Int,
    val scrollY: Int,
    val scrollHeight: Int,
    val viewportHeight: Int,
    val atBottom: Boolean,
    val loadingActive: Boolean,
    val lastMutationAgeMs: Long,
    val explicitEmptyList: Boolean,
    val tripSetSha256: String = "",
    val htmlTruncated: Boolean = false,
    val htmlMaterializedComplete: Boolean = true,
)

internal enum class BlaBlaRidesSnapshotAction0526 { SCROLL, WAIT, CAPTURE, INCOMPLETE }

internal data class BlaBlaRidesSnapshotDecision0526(
    val action: BlaBlaRidesSnapshotAction0526,
    val stablePasses: Int,
    val reason: String = "",
)

internal class BlaBlaRidesSnapshotStabilizer0526(
    private val startedAtMillis: Long = System.currentTimeMillis(),
    private val maxCycles: Int = 60,
    private val maxTotalMillis: Long = 90_000L,
    private val maxNoProgressCycles: Int = 18,
    private val requiredStablePasses: Int = 2,
    private val mutationQuietMillis: Long = 1_000L,
) {
    var cycles: Int = 0
        private set
    var scrollIterations: Int = 0
        private set
    var initialCardCount: Int? = null
        private set
    var finalCardCount: Int = 0
        private set
    val requiredStableIterations: Int
        get() = requiredStablePasses + 1
    var observedStableIterations: Int = 0
        private set
    var noNewTripIterations: Int = 0
        private set
    val cardCountHistory: List<Int>
        get() = cardCountHistoryMutable.toList()
    val tripSetFingerprintHistory: List<String>
        get() = tripSetFingerprintHistoryMutable.toList()
    var finalTripSetSha256: String = ""
        private set

    private val cardCountHistoryMutable = mutableListOf<Int>()
    private val tripSetFingerprintHistoryMutable = mutableListOf<String>()
    private var lastCardCount = -1
    private var lastHeight = -1
    private var lastScrollY = -1
    private var lastTripSetSha256 = ""
    private var noProgressCycles = 0

    fun observe(
        observation: BlaBlaRidesSnapshotObservation0526,
        nowMillis: Long = System.currentTimeMillis(),
    ): BlaBlaRidesSnapshotDecision0526 {
        cycles++
        initialCardCount = initialCardCount ?: observation.cardCount
        finalCardCount = maxOf(finalCardCount, observation.cardCount)

        val currentTripSetSha256 = observation.tripSetSha256.ifBlank {
            // Compatibility fallback for legacy unit fixtures. Runtime 0.1.528 always supplies
            // the deterministic administrative-trip set fingerprint.
            BlaBlaRidesSnapshotStore0526.sha256(
                "legacy-card-count:${observation.cardCount}".toByteArray(Charsets.UTF_8),
            )
        }
        finalTripSetSha256 = currentTripSetSha256
        cardCountHistoryMutable += observation.cardCount
        tripSetFingerprintHistoryMutable += currentTripSetSha256

        val tripSetChanged = lastTripSetSha256.isNotBlank() && currentTripSetSha256 != lastTripSetSha256
        val contentProgress =
            tripSetChanged || observation.cardCount > lastCardCount || observation.scrollHeight > lastHeight
        val scrollProgress = observation.scrollY > lastScrollY
        if (contentProgress || scrollProgress) noProgressCycles = 0 else noProgressCycles++

        noNewTripIterations = if (
            lastTripSetSha256.isNotBlank() &&
            currentTripSetSha256 == lastTripSetSha256
        ) {
            noNewTripIterations + 1
        } else {
            0
        }

        val sameMaterializedContent =
            lastTripSetSha256.isNotBlank() &&
            currentTripSetSha256 == lastTripSetSha256 &&
            observation.cardCount == lastCardCount &&
            observation.scrollHeight == lastHeight
        val terminalShape = observation.atBottom &&
            !observation.loadingActive &&
            observation.lastMutationAgeMs >= mutationQuietMillis &&
            (observation.cardCount > 0 || observation.explicitEmptyList)

        observedStableIterations = when {
            !terminalShape -> 0
            sameMaterializedContent -> observedStableIterations + 1
            else -> 1
        }

        lastCardCount = observation.cardCount
        lastHeight = observation.scrollHeight
        lastScrollY = observation.scrollY
        lastTripSetSha256 = currentTripSetSha256

        if (observedStableIterations >= requiredStableIterations) {
            return when {
                observation.htmlTruncated -> BlaBlaRidesSnapshotDecision0526(
                    BlaBlaRidesSnapshotAction0526.INCOMPLETE,
                    observedStableIterations,
                    "HTML_TRUNCATED",
                )
                !observation.htmlMaterializedComplete -> BlaBlaRidesSnapshotDecision0526(
                    BlaBlaRidesSnapshotAction0526.INCOMPLETE,
                    observedStableIterations,
                    "HTML_NOT_FULLY_MATERIALIZED",
                )
                else -> BlaBlaRidesSnapshotDecision0526(
                    BlaBlaRidesSnapshotAction0526.CAPTURE,
                    observedStableIterations,
                    "TRIP_ID_SET_UNCHANGED_AT_BOTTOM",
                )
            }
        }

        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0L)
        if (cycles >= maxCycles || elapsed >= maxTotalMillis || noProgressCycles >= maxNoProgressCycles) {
            val reason = when {
                elapsed >= maxTotalMillis -> "STABILIZATION_TIMEOUT"
                cycles >= maxCycles -> "MAX_SCROLL_CYCLES"
                else -> "NO_PROGRESS"
            }
            return BlaBlaRidesSnapshotDecision0526(
                BlaBlaRidesSnapshotAction0526.INCOMPLETE,
                observedStableIterations,
                reason,
            )
        }

        return if (!observation.atBottom) {
            scrollIterations++
            BlaBlaRidesSnapshotDecision0526(
                BlaBlaRidesSnapshotAction0526.SCROLL,
                observedStableIterations,
            )
        } else {
            BlaBlaRidesSnapshotDecision0526(
                BlaBlaRidesSnapshotAction0526.WAIT,
                observedStableIterations,
            )
        }
    }
}

/**
 * Sequential multi-profile coordinator. It reuses BlaBlaDynamicAccountSessionController0401
 * for every account, so browser profile/session authority remains singular.
 */
internal object BlaBlaRidesSnapshotCoordinator0526 {
    suspend fun captureAll(
        context: Context,
        onProgress: (String) -> Unit = {},
    ): BlaBlaRidesSnapshotManifest0526 {
        val app = context.applicationContext
        val registry = BlaBlaDynamicAccountRegistry(app)
        val store = BlaBlaRidesSnapshotStore0526(app)
        val accounts = registry.list()
        var manifest = store.start(accounts)
        UnifiedDebugEventStore.recordAlways(
            "RIDES_SNAPSHOT_STARTED",
            app.packageName,
            "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} profiles=${accounts.size} sequential=true privateEvidence=true",
        )
        if (accounts.isEmpty()) {
            onProgress("Nenhum perfil BlaBlaCar conectado.")
            return store.finish(manifest.captureId) ?: manifest
        }

        accounts.forEachIndexed { index, account ->
            val expected = BlaBlaRidesSnapshotStore0526.strongUuid(account.profileUuid)
            onProgress("Capturando perfil ${index + 1}/${accounts.size} • Validando identidade")
            if (expected == null) {
                store.updateProfile(manifest.captureId, account.id) {
                    it.copy(
                        startedAt = Instant.now().toString(),
                        completedAt = Instant.now().toString(),
                        status = BlaBlaRidesSnapshotStatus0526.FAILED_IDENTITY,
                        errorCode = "EXPECTED_PROFILE_UUID_MISSING",
                    )
                }
                UnifiedDebugEventStore.recordAlways(
                    "RIDES_SNAPSHOT_PROFILE_FAILED",
                    app.packageName,
                    "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} profile=${index + 1}/${accounts.size} reason=EXPECTED_PROFILE_UUID_MISSING",
                )
                return@forEachIndexed
            }

            val terminal = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
                runProfile(
                    context = app,
                    account = account,
                    captureId = manifest.captureId,
                    position = index + 1,
                    total = accounts.size,
                    onProgress = onProgress,
                )
            }
            if (terminal != true) {
                store.updateProfile(manifest.captureId, account.id) { previous ->
                    if (previous.status in TERMINAL_STATUSES) previous else previous.copy(
                        completedAt = Instant.now().toString(),
                        status = BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
                        errorCode = "PROFILE_TIMEOUT",
                    )
                }
            }
            manifest = store.read(manifest.captureId) ?: manifest
        }

        return store.finish(manifest.captureId) ?: manifest
    }

    private suspend fun runProfile(
        context: Context,
        account: BlaBlaDynamicAccount,
        captureId: String,
        position: Int,
        total: Int,
        onProgress: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            var controller: BlaBlaDynamicAccountSessionController0401? = null
            val payload = BlaBlaDynamicSessionIntents.ridesSnapshotPayload(
                account = account,
                captureId = captureId,
                position = position,
                total = total,
            )
            controller = BlaBlaDynamicAccountSessionController0401(
                baseContext = context,
                launchIntent = payload,
                visualHost = null,
                snapshotProgress0526 = onProgress,
                finishHost = { _, _ ->
                    controller?.destroy("rides_snapshot_profile_terminal_0526")
                    if (continuation.isActive) continuation.resume(true)
                },
            )
            continuation.invokeOnCancellation {
                controller?.destroy("rides_snapshot_profile_cancelled_0526")
            }
            controller?.start()
        }
    }

    private const val PROFILE_TIMEOUT_MS = 120_000L
    private val TERMINAL_STATUSES = setOf(
        BlaBlaRidesSnapshotStatus0526.COMPLETE,
        BlaBlaRidesSnapshotStatus0526.INCOMPLETE,
        BlaBlaRidesSnapshotStatus0526.FAILED_IDENTITY,
        BlaBlaRidesSnapshotStatus0526.FAILED_SESSION,
        BlaBlaRidesSnapshotStatus0526.FAILED_NAVIGATION,
        BlaBlaRidesSnapshotStatus0526.FAILED_CAPTURE,
    )
}
