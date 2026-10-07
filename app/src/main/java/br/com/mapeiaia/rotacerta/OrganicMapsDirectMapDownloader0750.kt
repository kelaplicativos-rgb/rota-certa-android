package br.com.mapeiaia.rotacerta

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.ArrayDeque
import java.util.Locale

/**
 * 0.1.750 — direct official Organic Maps downloader for the embedded runtime.
 *
 * No browser and no manually constructed map URL are used. The embedded Organic Maps
 * storage engine owns server selection, compatible map version, resume/retry and installation
 * into the same private map storage used by the offline geocoder/router.
 */
class OrganicMapsDirectMapDownloader0750(context: Context) {
    data class Snapshot(
        val ready: Boolean,
        val targetId: String? = null,
        val targetName: String? = null,
        val targetStatus: Int = CountryItem.STATUS_UNKNOWN,
        val progressPercent: Int = 0,
        val downloadedRegionalMaps: Int = 0,
        val totalBytes: Long? = null,
        val message: String = "",
    ) {
        val isBusy: Boolean
            get() = targetStatus == CountryItem.STATUS_PROGRESS ||
                targetStatus == CountryItem.STATUS_APPLYING ||
                targetStatus == CountryItem.STATUS_ENQUEUED
    }

    data class ActionResult(
        val accepted: Boolean,
        val targetId: String? = null,
        val targetName: String? = null,
        val message: String,
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var activeTargetId: String? = prefs.getString(KEY_ACTIVE_TARGET_ID, null)
    @Volatile private var activeTargetName: String? = prefs.getString(KEY_ACTIVE_TARGET_NAME, null)
    @Volatile private var listener: ((Snapshot) -> Unit)? = null
    private var subscriptionSlot: Int? = null

    private val storageCallback = object : MapManager.StorageCallback {
        override fun onStatusChanged(data: List<MapManager.StorageCallbackData>) {
            emitSnapshot("status")
        }

        override fun onProgress(countryId: String, localSize: Long, remoteSize: Long) {
            emitSnapshot("progress")
        }
    }

    suspend fun attach(listener: (Snapshot) -> Unit): Snapshot {
        val ready = OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)
        if (!ready) {
            val failed = Snapshot(
                ready = false,
                message = "Organic Maps incorporado indisponível: " +
                    OrganicMapsEmbeddedRuntime0711.failureReason().orEmpty(),
            )
            listener(failed)
            return failed
        }
        return withContext(Dispatchers.Main.immediate) {
            this@OrganicMapsDirectMapDownloader0750.listener = listener
            if (subscriptionSlot == null) {
                subscriptionSlot = MapManager.nativeSubscribe(storageCallback)
            }
            snapshotOnMain("ready").also(listener)
        }
    }

    fun detach() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachOnMain()
        } else {
            mainHandler.post(::detachOnMain)
        }
    }

    suspend fun startBrazilDownload(): ActionResult {
        if (!OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)) {
            return ActionResult(false, message = "O motor Organic Maps não inicializou.")
        }
        return withContext(Dispatchers.Main.immediate) {
            val target = findBrazilTargetOnMain()
                ?: return@withContext ActionResult(
                    false,
                    message = "Não encontrei o pacote Brasil no catálogo oficial compatível com este motor.",
                )
            FarolFlightRecorder0163.record(BRAZIL_MARKER, null, "target=${target.id}; name=${target.name}")
            startTargetOnMain(target)
        }
    }

    suspend fun startCurrentRegionDownload(coordinate: Coordinate): ActionResult {
        if (!OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)) {
            return ActionResult(false, message = "O motor Organic Maps não inicializou.")
        }
        return withContext(Dispatchers.Main.immediate) {
            val id = runCatching {
                MapManager.nativeFindCountry(coordinate.latitude, coordinate.longitude)
            }.getOrNull().orEmpty()
            if (id.isBlank()) {
                return@withContext ActionResult(
                    false,
                    message = "O Organic Maps não conseguiu identificar o mapa regional desta posição.",
                )
            }
            val item = runCatching { CountryItem.fill(id) }.getOrNull()
                ?: return@withContext ActionResult(false, message = "O mapa regional foi encontrado, mas seus dados não puderam ser lidos.")
            FarolFlightRecorder0163.record(REGION_MARKER, null, "target=${item.id}; name=${item.name}")
            startTargetOnMain(item)
        }
    }

    suspend fun cancelActiveDownload(): ActionResult {
        if (!OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)) {
            return ActionResult(false, message = "O motor Organic Maps não inicializou.")
        }
        return withContext(Dispatchers.Main.immediate) {
            val id = activeTargetId
            if (id.isNullOrBlank()) {
                return@withContext ActionResult(false, message = "Não há download direto ativo para cancelar.")
            }
            runCatching { MapManager.nativeCancel(id) }
                .fold(
                    onSuccess = {
                        val snap = snapshotOnMain("cancel")
                        listener?.invoke(snap)
                        ActionResult(true, id, activeTargetName, "Download cancelado.")
                    },
                    onFailure = { ActionResult(false, id, activeTargetName, it.message ?: "Falha ao cancelar o download.") },
                )
        }
    }

    suspend fun currentSnapshot(): Snapshot {
        val ready = OrganicMapsEmbeddedRuntime0711.ensureInitialized(appContext)
        if (!ready) {
            return Snapshot(
                ready = false,
                message = "Organic Maps incorporado indisponível: " +
                    OrganicMapsEmbeddedRuntime0711.failureReason().orEmpty(),
            )
        }
        return withContext(Dispatchers.Main.immediate) { snapshotOnMain("manual") }
    }

    private fun startTargetOnMain(item: CountryItem): ActionResult {
        rememberTarget(item)
        val status = runCatching { MapManager.nativeGetStatus(item.id) }.getOrDefault(item.status)

        if (status == CountryItem.STATUS_DONE) {
            val snap = snapshotOnMain("already_done")
            listener?.invoke(snap)
            return ActionResult(true, item.id, item.name, "${item.name} já está baixado e pronto para uso offline.")
        }

        val enoughSpace = runCatching { MapManager.nativeHasSpaceToDownloadCountry(item.id) }.getOrDefault(false)
        if (!enoughSpace) {
            val snap = snapshotOnMain("no_space")
            listener?.invoke(snap)
            return ActionResult(
                false,
                item.id,
                item.name,
                "Espaço insuficiente no aparelho para concluir ${item.name}. Nenhum arquivo foi iniciado.",
            )
        }

        val result = runCatching {
            when (status) {
                CountryItem.STATUS_PROGRESS,
                CountryItem.STATUS_APPLYING,
                CountryItem.STATUS_ENQUEUED -> Unit
                CountryItem.STATUS_FAILED -> MapManager.retryDownload(item.id)
                CountryItem.STATUS_UPDATABLE -> MapManager.startUpdate(item.id)
                else -> MapManager.startDownload(item.id)
            }
        }
        if (result.isFailure) {
            return ActionResult(
                false,
                item.id,
                item.name,
                result.exceptionOrNull()?.message ?: "Falha ao iniciar download pelo Organic Maps.",
            )
        }

        val snap = snapshotOnMain("started")
        listener?.invoke(snap)
        return ActionResult(
            true,
            item.id,
            item.name,
            when (status) {
                CountryItem.STATUS_PROGRESS,
                CountryItem.STATUS_APPLYING,
                CountryItem.STATUS_ENQUEUED -> "${item.name} já está sendo baixado."
                CountryItem.STATUS_FAILED -> "Download de ${item.name} retomado diretamente pelo Organic Maps."
                CountryItem.STATUS_UPDATABLE -> "Atualização de ${item.name} iniciada diretamente pelo Organic Maps."
                else -> "Download de ${item.name} iniciado diretamente pelo Organic Maps."
            },
        )
    }

    private fun findBrazilTargetOnMain(): CountryItem? {
        val cachedId = prefs.getString(KEY_BRAZIL_TARGET_ID, null)
        if (!cachedId.isNullOrBlank()) {
            runCatching { CountryItem.fill(cachedId) }.getOrNull()
                ?.takeIf { matchesBrazilName(it.name) }
                ?.let { return it }
        }

        val root = runCatching { MapManager.nativeGetRoot() }.getOrNull().orEmpty()
        if (root.isBlank()) return null

        data class QueueNode(val id: String, val depth: Int)
        val queue = ArrayDeque<QueueNode>()
        val visited = linkedSetOf<String>()
        queue.add(QueueNode(root, 0))

        var visitedNodes = 0
        while (queue.isNotEmpty() && visitedNodes < MAX_DISCOVERY_NODES) {
            val node = queue.removeFirst()
            if (!visited.add(node.id)) continue

            val children = mutableListOf<CountryItem>()
            runCatching {
                MapManager.nativeListItems(node.id, 0.0, 0.0, false, false, children)
            }.getOrElse { continue }

            for (item in children) {
                visitedNodes += 1
                if (matchesBrazilName(item.name)) {
                    prefs.edit().putString(KEY_BRAZIL_TARGET_ID, item.id).apply()
                    return item
                }
                if (node.depth < MAX_DISCOVERY_DEPTH && item.totalChildCount > 0 && item.id.isNotBlank()) {
                    queue.add(QueueNode(item.id, node.depth + 1))
                }
            }
        }
        return null
    }

    private fun rememberTarget(item: CountryItem) {
        activeTargetId = item.id
        activeTargetName = item.name
        prefs.edit()
            .putString(KEY_ACTIVE_TARGET_ID, item.id)
            .putString(KEY_ACTIVE_TARGET_NAME, item.name)
            .apply()
    }

    private fun snapshotOnMain(reason: String): Snapshot {
        val id = activeTargetId
        val item = id?.takeIf { it.isNotBlank() }?.let { runCatching { CountryItem.fill(it) }.getOrNull() }
        val status = if (id.isNullOrBlank()) {
            CountryItem.STATUS_UNKNOWN
        } else {
            runCatching { MapManager.nativeGetStatus(id) }.getOrDefault(item?.status ?: CountryItem.STATUS_UNKNOWN)
        }
        val progress = if (id.isNullOrBlank()) {
            0
        } else {
            runCatching { MapManager.nativeGetOverallProgress(arrayOf(id)) }
                .getOrDefault(if (status == CountryItem.STATUS_DONE) 100 else 0)
                .coerceIn(0, 100)
        }
        val count = runCatching { MapManager.nativeGetDownloadedCount() }.getOrDefault(0)
        val name = item?.name ?: activeTargetName
        val message = when (status) {
            CountryItem.STATUS_PROGRESS -> "${name.orEmpty()} — baixando $progress%."
            CountryItem.STATUS_APPLYING -> "${name.orEmpty()} — finalizando instalação $progress%."
            CountryItem.STATUS_ENQUEUED -> "${name.orEmpty()} — aguardando na fila de download."
            CountryItem.STATUS_FAILED -> "${name.orEmpty()} — download interrompido; toque novamente para retomar."
            CountryItem.STATUS_UPDATABLE -> "${name.orEmpty()} — atualização disponível."
            CountryItem.STATUS_DONE -> "${name.orEmpty()} — mapa offline pronto. $count mapa(s) regional(is) instalado(s)."
            CountryItem.STATUS_PARTLY -> "${name.orEmpty()} — download parcial; toque novamente para completar."
            CountryItem.STATUS_DOWNLOADABLE -> "${name.orEmpty()} — disponível para download direto."
            else -> if (count > 0) "$count mapa(s) regional(is) disponível(is) no motor offline." else "Nenhum mapa regional baixado no motor offline."
        }

        FarolFlightRecorder0163.record(
            stage = STATUS_MARKER,
            packageName = null,
            details = "reason=$reason; target=${id.orEmpty()}; status=$status; progress=$progress; downloadedMaps=$count",
        )
        return Snapshot(
            ready = true,
            targetId = id,
            targetName = name,
            targetStatus = status,
            progressPercent = progress,
            downloadedRegionalMaps = count,
            totalBytes = item?.totalSize?.takeIf { it > 0L },
            message = message,
        )
    }

    private fun emitSnapshot(reason: String) {
        mainHandler.post {
            if (listener == null) return@post
            listener?.invoke(snapshotOnMain(reason))
        }
    }

    private fun detachOnMain() {
        subscriptionSlot?.let { slot -> runCatching { MapManager.nativeUnsubscribe(slot) } }
        subscriptionSlot = null
        listener = null
    }

    companion object {
        const val CONTRACT_MARKER = "ORGANIC_MAPS_DIRECT_DOWNLOAD_0750"
        const val BRAZIL_MARKER = "ORGANIC_MAPS_BRAZIL_DIRECT_0750"
        const val REGION_MARKER = "ORGANIC_MAPS_REGION_DIRECT_0750"
        const val STATUS_MARKER = "ORGANIC_MAPS_DOWNLOAD_STATUS_0750"

        private const val PREFS = "organic_maps_direct_download_0750"
        private const val KEY_BRAZIL_TARGET_ID = "brazil_target_id"
        private const val KEY_ACTIVE_TARGET_ID = "active_target_id"
        private const val KEY_ACTIVE_TARGET_NAME = "active_target_name"
        private const val MAX_DISCOVERY_DEPTH = 5
        private const val MAX_DISCOVERY_NODES = 4_000

        internal fun matchesBrazilName(value: String?): Boolean {
            val normalized = normalizeName(value)
            return normalized == "brasil" || normalized == "brazil"
        }

        internal fun normalizeName(value: String?): String = Normalizer
            .normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
            .lowercase(Locale.ROOT)
    }
}
