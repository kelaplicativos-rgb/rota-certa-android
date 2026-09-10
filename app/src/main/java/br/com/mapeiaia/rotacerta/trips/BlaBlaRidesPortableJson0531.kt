package br.com.mapeiaia.rotacerta.trips

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Compact, machine-readable projection of a completed forensic "Suas viagens" capture.
 *
 * The forensic ZIP remains the audit artifact. This JSON is intentionally narrower: it contains
 * only the strong profile identity, canonical administrative trip IDs and completeness metadata
 * that Rota Certa itself can strictly decode and validate later. It never copies HTML/MHTML or
 * reusable browser authentication material into the portable file.
 */
@Serializable
internal data class RotaCertaBlaBlaRidesProfile0531(
    val accountKey: String,
    val displayName: String = "",
    val expectedProfileUuid: String,
    val authenticatedProfileUuid: String,
    val identityConfirmed: Boolean,
    val status: String,
    val capturedAt: String,
    val rideCount: Int,
    val tripIds: List<String>,
    val tripIdsSha256: String,
    val duplicateCount: Int,
    val earliestDate: String = "",
    val latestDate: String = "",
    val reachedEnd: Boolean,
    val stabilized: Boolean,
)

@Serializable
internal data class RotaCertaBlaBlaRidesJson0531(
    val schemaVersion: String = SCHEMA_VERSION_0531,
    val kind: String = KIND_0531,
    val captureId: String,
    val captureCompletedAt: String,
    val sourceManifestSchemaVersion: String,
    val sourceAppVersion: String,
    val sourceVersionCode: Int,
    val sourceCommitSha: String,
    val sourceBranch: String,
    val result: String,
    val totalProfiles: Int,
    val totalRides: Int,
    val profiles: List<RotaCertaBlaBlaRidesProfile0531>,
)

internal data class BlaBlaRidesPortableJsonDownloadResult0531(
    val displayName: String,
    val relativeLocation: String,
    val bytes: Long,
)

internal const val SCHEMA_VERSION_0531 = "rota-certa-blablacar-rides-v1"
internal const val KIND_0531 = "BLABLACAR_SUAS_VIAGENS"

internal object BlaBlaRidesPortableJson0531 {
    private const val MAX_PORTABLE_JSON_BYTES_0531 = 2 * 1024 * 1024
    private const val MAX_INDEX_JSON_BYTES_0531 = 512 * 1024
    private val ACCOUNT_KEY_0531 = Regex("^[a-f0-9]{16}$")
    private val STRICT_JSON_0531 = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun build(
        context: Context,
        manifest: BlaBlaRidesSnapshotManifest0526,
    ): RotaCertaBlaBlaRidesJson0531 {
        require(manifest.result == BlaBlaRidesSnapshotStatus0526.COMPLETE) {
            "A captura precisa estar COMPLETE antes de gerar o JSON do Rota Certa"
        }
        require(manifest.profiles.isNotEmpty()) { "A captura não contém perfis" }
        require(manifest.completedAt.isNotBlank()) { "A captura não possui conclusão comprovada" }

        val store = BlaBlaRidesSnapshotStore0526(context.applicationContext)
        val profiles = manifest.profiles.map { profile ->
            require(profile.status == BlaBlaRidesSnapshotStatus0526.COMPLETE) {
                "Perfil ${profile.displayName.ifBlank { profile.accountKey }} não está COMPLETE"
            }
            val expectedUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profile.expectedProfileUuid)
                ?: error("UUID esperado inválido")
            val authenticatedUuid = BlaBlaRidesSnapshotStore0526.strongUuid(profile.authenticatedProfileUuid)
                ?: error("UUID autenticado inválido")
            require(expectedUuid == authenticatedUuid && profile.identityConfirmed) {
                "Identidade forte do perfil não foi comprovada"
            }
            require(profile.reachedEnd && profile.stabilized) {
                "Completude da lista não foi comprovada"
            }
            require(profile.tripInventory.duplicateCount == 0) { "IDs de viagem duplicados na captura" }
            require(profile.crossFormatConsistency.sameTripSet) { "HTML/MHTML não comprovam o mesmo conjunto" }

            val indexFile = store.resolveArtifact0528(manifest.captureId, profile.ridesIndexFile)
            require(
                verifySnapshotArtifact0528(
                    file = indexFile,
                    expectedBytes = profile.ridesIndexBytes,
                    expectedSha256 = profile.ridesIndexSha256,
                ),
            ) { "Índice de viagens não corresponde ao artefato verificado" }
            require(indexFile != null && indexFile.length() <= MAX_INDEX_JSON_BYTES_0531) {
                "Índice de viagens excede o limite do formato portátil"
            }
            val indexRaw = indexFile.readText(Charsets.UTF_8)
            require(sensitiveArtifactMarker0528(indexRaw) == null) {
                "Índice contém material que não pode ser exportado"
            }
            val index = STRICT_JSON_0531.decodeFromString(BlaBlaRidesIndexJson0528.serializer(), indexRaw)
            require(index.schemaVersion == "blablacar-rides-index-v1") { "Schema do índice não suportado" }
            require(BlaBlaRidesSnapshotStore0526.strongUuid(index.profileUuid) == expectedUuid) {
                "Índice pertence a outro perfil"
            }
            val canonicalIds = canonicalTripIds0528(index.tripIds)
            require(canonicalIds == index.tripIds) { "Índice de viagens não está canônico" }
            require(index.duplicateCount == 0) { "Índice contém viagens duplicadas" }
            require(tripSetSha2560528(index.tripIds) == index.tripIdsSha256) {
                "Hash do conjunto de viagens não confere"
            }
            require(profile.tripInventory.uniqueCount == index.tripIds.size) {
                "Quantidade do índice diverge do inventário comprovado"
            }
            require(profile.tripInventory.tripIdsSha256 == index.tripIdsSha256) {
                "Hash do índice diverge do inventário comprovado"
            }
            require(profile.stabilizationEvidence.tripSetSha256 == index.tripIdsSha256) {
                "Hash do índice diverge da estabilização comprovada"
            }
            require(profile.rideDateRange == index.rideDateRange) {
                "Intervalo de datas do índice diverge da captura"
            }

            RotaCertaBlaBlaRidesProfile0531(
                accountKey = profile.accountKey,
                displayName = profile.displayName,
                expectedProfileUuid = expectedUuid,
                authenticatedProfileUuid = authenticatedUuid,
                identityConfirmed = true,
                status = profile.status,
                capturedAt = index.capturedAt,
                rideCount = index.tripIds.size,
                tripIds = index.tripIds,
                tripIdsSha256 = index.tripIdsSha256,
                duplicateCount = 0,
                earliestDate = index.rideDateRange.earliest,
                latestDate = index.rideDateRange.latest,
                reachedEnd = true,
                stabilized = true,
            )
        }

        return validate(
            RotaCertaBlaBlaRidesJson0531(
                captureId = manifest.captureId,
                captureCompletedAt = manifest.completedAt,
                sourceManifestSchemaVersion = manifest.schemaVersion,
                sourceAppVersion = manifest.appVersion,
                sourceVersionCode = manifest.versionCode,
                sourceCommitSha = manifest.commitSha,
                sourceBranch = manifest.branch,
                result = manifest.result,
                totalProfiles = profiles.size,
                totalRides = profiles.sumOf { it.rideCount },
                profiles = profiles,
            ),
        )
    }

    fun encode(value: RotaCertaBlaBlaRidesJson0531): String {
        val verified = validate(value)
        val raw = STRICT_JSON_0531.encodeToString(RotaCertaBlaBlaRidesJson0531.serializer(), verified)
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_PORTABLE_JSON_BYTES_0531) {
            "JSON excede o limite do formato portátil"
        }
        require(sensitiveArtifactMarker0528(raw) == null) {
            "JSON contém material de autenticação reutilizável"
        }
        return raw
    }

    fun decode(raw: String): RotaCertaBlaBlaRidesJson0531 {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_PORTABLE_JSON_BYTES_0531) {
            "JSON vazio ou acima do limite permitido"
        }
        require(sensitiveArtifactMarker0528(raw) == null) {
            "JSON contém material de autenticação reutilizável"
        }
        return validate(
            STRICT_JSON_0531.decodeFromString(RotaCertaBlaBlaRidesJson0531.serializer(), raw),
        )
    }

    fun validate(value: RotaCertaBlaBlaRidesJson0531): RotaCertaBlaBlaRidesJson0531 {
        require(value.schemaVersion == SCHEMA_VERSION_0531) { "Schema portátil não suportado" }
        require(value.kind == KIND_0531) { "Tipo de arquivo não suportado" }
        require(value.captureId.isNotBlank()) { "captureId ausente" }
        require(BlaBlaRidesSnapshotStore0526.safeCaptureId(value.captureId) == value.captureId) {
            "captureId inválido"
        }
        require(value.captureCompletedAt.isNotBlank()) { "Conclusão da captura ausente" }
        require(value.sourceManifestSchemaVersion == "blablacar-rides-snapshot-v2") {
            "Manifesto de origem não suportado"
        }
        require(value.sourceAppVersion.isNotBlank() && value.sourceVersionCode > 0) {
            "Identidade da build de origem ausente"
        }
        require(value.sourceCommitSha.isNotBlank() && value.sourceBranch.isNotBlank()) {
            "Identidade Git da captura ausente"
        }
        require(value.result == BlaBlaRidesSnapshotStatus0526.COMPLETE) {
            "Somente capturas COMPLETE podem ser lidas como arquivo portátil"
        }
        require(value.profiles.isNotEmpty() && value.totalProfiles == value.profiles.size) {
            "Quantidade de perfis inconsistente"
        }
        require(value.profiles.map { it.expectedProfileUuid }.distinct().size == value.profiles.size) {
            "Perfis duplicados no JSON"
        }

        value.profiles.forEach { profile ->
            require(ACCOUNT_KEY_0531.matches(profile.accountKey)) { "accountKey inválido" }
            val expected = BlaBlaRidesSnapshotStore0526.strongUuid(profile.expectedProfileUuid)
                ?: error("UUID esperado inválido")
            val authenticated = BlaBlaRidesSnapshotStore0526.strongUuid(profile.authenticatedProfileUuid)
                ?: error("UUID autenticado inválido")
            require(expected == authenticated && profile.identityConfirmed) {
                "Identidade do perfil não é forte"
            }
            require(profile.status == BlaBlaRidesSnapshotStatus0526.COMPLETE) {
                "Perfil portátil não está COMPLETE"
            }
            require(profile.capturedAt.isNotBlank()) { "Timestamp do perfil ausente" }
            require(profile.reachedEnd && profile.stabilized) { "Completude do perfil não comprovada" }
            require(profile.duplicateCount == 0) { "Perfil contém duplicatas" }
            val canonicalIds = canonicalTripIds0528(profile.tripIds)
            require(canonicalIds == profile.tripIds) { "IDs de viagem não estão canônicos" }
            require(profile.rideCount == profile.tripIds.size) { "Contagem de viagens inconsistente" }
            require(tripSetSha2560528(profile.tripIds) == profile.tripIdsSha256) {
                "Hash das viagens não confere"
            }
            if (profile.tripIds.isEmpty()) {
                require(profile.earliestDate.isBlank() && profile.latestDate.isBlank()) {
                    "Perfil vazio não pode declarar intervalo de datas"
                }
            } else {
                require(profile.earliestDate.isNotBlank() && profile.latestDate.isNotBlank()) {
                    "Intervalo de datas ausente"
                }
                require(profile.earliestDate <= profile.latestDate) { "Intervalo de datas inválido" }
            }
        }
        require(value.totalRides == value.profiles.sumOf { it.rideCount }) {
            "Total de viagens inconsistente"
        }
        return value
    }
}

internal object BlaBlaRidesPortableJsonDownload0531 {
    suspend fun download(
        context: Context,
        manifest: BlaBlaRidesSnapshotManifest0526,
    ): BlaBlaRidesPortableJsonDownloadResult0531 = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Download automático requer Android 10 ou superior"
        }
        val app = context.applicationContext
        val payload = BlaBlaRidesPortableJson0531.build(app, manifest)
        val raw = BlaBlaRidesPortableJson0531.encode(payload)
        val readBack = BlaBlaRidesPortableJson0531.decode(raw)
        require(readBack == payload) { "Rota Certa não conseguiu reler o JSON gerado" }
        val bytes = raw.toByteArray(Charsets.UTF_8)

        val displayName =
            "rota-certa-suas-viagens-${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)}.json"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Rota Certa"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Android não disponibilizou um destino em Downloads")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Não foi possível abrir o arquivo JSON em Downloads")

            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }.also { ready -> resolver.update(uri, ready, null, null) }

            UnifiedDebugEventStore.recordAlways(
                "BLABLACAR_RIDES_PORTABLE_JSON_EXPORTED_0531",
                app.packageName,
                "captureId=${BlaBlaRidesSnapshotStore0526.safeCaptureId(manifest.captureId)} profiles=${payload.totalProfiles} rides=${payload.totalRides} bytes=${bytes.size} schema=$SCHEMA_VERSION_0531 strictReadback=true reusableAuthMaterial=false",
            )
            BlaBlaRidesPortableJsonDownloadResult0531(
                displayName = displayName,
                relativeLocation = "$relativePath/$displayName",
                bytes = bytes.size.toLong(),
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
