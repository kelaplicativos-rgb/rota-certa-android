package br.com.mapeiaia.rotacerta

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Indice espacial leve para a base do MapaRadar.
 *
 * Em vez de calcular a distancia dos milhares de radares a cada leitura do GPS,
 * somente as celulas ao redor do veiculo sao devolvidas ao motor de alerta.
 */
class ImportedRadarSpatialIndex(
    private val cellSizeDegrees: Double = 0.01,
) {
    private var sourceIdentity: Int = 0
    private var sourceSize: Int = -1
    private var firstRadarId: String? = null
    private var lastRadarId: String? = null
    private var buckets: Map<Long, List<ImportedRadar>> = emptyMap()

    fun query(
        source: List<ImportedRadar>,
        center: Coordinate,
        radiusMeters: Double,
    ): ImportedRadarSpatialQuery {
        val rebuilt = ensureIndex(source)
        if (source.isEmpty() || buckets.isEmpty()) {
            return ImportedRadarSpatialQuery(emptyList(), rebuilt)
        }

        val safeRadius = radiusMeters.coerceAtLeast(0.0)
        val latitudeRadiusDegrees = safeRadius / METERS_PER_LATITUDE_DEGREE
        val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE *
            max(MIN_LONGITUDE_COSINE, cos(Math.toRadians(center.latitude)))
        val longitudeRadiusDegrees = safeRadius / longitudeMetersPerDegree

        val minLatCell = cell(center.latitude - latitudeRadiusDegrees)
        val maxLatCell = cell(center.latitude + latitudeRadiusDegrees)
        val minLonCell = cell(center.longitude - longitudeRadiusDegrees)
        val maxLonCell = cell(center.longitude + longitudeRadiusDegrees)

        val result = ArrayList<ImportedRadar>()
        for (latCell in minLatCell..maxLatCell) {
            for (lonCell in minLonCell..maxLonCell) {
                buckets[key(latCell, lonCell)]?.let(result::addAll)
            }
        }
        return ImportedRadarSpatialQuery(result, rebuilt)
    }

    fun clear() {
        sourceIdentity = 0
        sourceSize = -1
        firstRadarId = null
        lastRadarId = null
        buckets = emptyMap()
    }

    private fun ensureIndex(source: List<ImportedRadar>): Boolean {
        val identity = System.identityHashCode(source)
        val firstId = source.firstOrNull()?.id
        val lastId = source.lastOrNull()?.id
        val unchanged = identity == sourceIdentity &&
            source.size == sourceSize &&
            firstId == firstRadarId &&
            lastId == lastRadarId
        if (unchanged) return false

        val mutable = HashMap<Long, MutableList<ImportedRadar>>()
        source.forEach { radar ->
            val latCell = cell(radar.coordinate.latitude)
            val lonCell = cell(radar.coordinate.longitude)
            mutable.getOrPut(key(latCell, lonCell)) { ArrayList() }.add(radar)
        }
        buckets = mutable
        sourceIdentity = identity
        sourceSize = source.size
        firstRadarId = firstId
        lastRadarId = lastId
        return true
    }

    private fun cell(value: Double): Int = floor(value / cellSizeDegrees).toInt()

    private fun key(latitudeCell: Int, longitudeCell: Int): Long =
        (latitudeCell.toLong() shl 32) xor (longitudeCell.toLong() and 0xffffffffL)

    companion object {
        private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        private const val MIN_LONGITUDE_COSINE = 0.15
    }
}

data class ImportedRadarSpatialQuery(
    val radars: List<ImportedRadar>,
    val rebuilt: Boolean,
)
