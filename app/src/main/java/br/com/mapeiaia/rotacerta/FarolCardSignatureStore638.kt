package br.com.mapeiaia.rotacerta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FarolCardSignatureStore638(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun modelsFor(packageName: String): List<FarolCardSignatureModel638> {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return emptyList()
        return readAll().filter { it.packageName == normalized }.sortedByDescending { it.updatedAtMillis }
    }

    fun hasModels(packageName: String): Boolean = modelsFor(packageName).isNotEmpty()

    fun saveOrMerge(model: FarolCardSignatureModel638): FarolCardSignatureModel638 {
        val all = readAll().toMutableList()
        val samePackage = all.withIndex().filter { it.value.packageName == model.packageName }
        val mergeTarget = samePackage
            .mapNotNull { indexed ->
                FarolCardSignatureCompiler638.mergeIfSameFamily(indexed.value, model)?.let { indexed.index to it }
            }
            .maxByOrNull { it.second.sampleCount }
        val saved = if (mergeTarget != null) {
            all[mergeTarget.first] = mergeTarget.second
            mergeTarget.second
        } else {
            all += model
            model
        }
        writeAll(
            all.groupBy { it.packageName }
                .flatMap { (_, models) -> models.sortedByDescending { it.updatedAtMillis }.take(MAX_PER_PACKAGE) }
                .sortedByDescending { it.updatedAtMillis }
                .take(MAX_TOTAL),
        )
        return saved
    }

    fun removePackage(packageName: String) {
        val normalized = SelectedRideAppStore.normalize(packageName) ?: return
        writeAll(readAll().filterNot { it.packageName == normalized })
    }

    fun countFor(packageName: String): Int = modelsFor(packageName).size

    private fun readAll(): List<FarolCardSignatureModel638> {
        val raw = prefs.getString(KEY_MODELS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val pkg = SelectedRideAppStore.normalize(item.optString("packageName")) ?: continue
                    add(
                        FarolCardSignatureModel638(
                            id = item.optString("id"),
                            packageName = pkg,
                            createdAtMillis = item.optLong("createdAtMillis"),
                            updatedAtMillis = item.optLong("updatedAtMillis"),
                            sampleCount = item.optInt("sampleCount", 1).coerceAtLeast(1),
                            anchorTokens = jsonSet(item.optJSONArray("anchorTokens")),
                            structureTokens = jsonSet(item.optJSONArray("structureTokens")),
                            visualHash = item.optString("visualHash").takeIf { it.isNotBlank() },
                            confidence = item.optInt("confidence", 60).coerceIn(0, 100),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(models: List<FarolCardSignatureModel638>) {
        val array = JSONArray()
        models.forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("packageName", model.packageName)
                put("createdAtMillis", model.createdAtMillis)
                put("updatedAtMillis", model.updatedAtMillis)
                put("sampleCount", model.sampleCount)
                put("anchorTokens", JSONArray(model.anchorTokens.sorted()))
                put("structureTokens", JSONArray(model.structureTokens.sorted()))
                put("visualHash", model.visualHash.orEmpty())
                put("confidence", model.confidence)
            })
        }
        prefs.edit().putString(KEY_MODELS, array.toString()).apply()
    }

    private fun jsonSet(array: JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) {
            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    companion object {
        private const val PREFS = "farol_card_signature_store_0638"
        private const val KEY_MODELS = "models_v1"
        private const val MAX_PER_PACKAGE = 8
        private const val MAX_TOTAL = 32
    }
}
