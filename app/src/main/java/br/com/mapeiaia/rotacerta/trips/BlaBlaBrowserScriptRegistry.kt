package br.com.mapeiaia.rotacerta.trips

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Loads one standalone browser script for one documented request. */
internal class BlaBlaBrowserScriptRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, String>()

    fun script(
        request: BlaBlaBrowserRequest,
        arguments: Map<String, String> = emptyMap(),
    ): String {
        val template = cache.getOrPut(request.assetName) {
            appContext.assets.open("blablacar/scripts/${request.assetName}")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
        val rendered = arguments.entries.fold(template) { value, (key, replacement) ->
            value.replace("{{$key}}", replacement)
        }
        require(!UNRESOLVED_PLACEHOLDER.containsMatchIn(rendered)) {
            "Unresolved browser-script placeholder in ${request.assetName}"
        }
        return rendered
    }

    fun availableRequests(): Set<BlaBlaBrowserRequest> = BlaBlaBrowserRequest.values().toSet()

    private companion object {
        val UNRESOLVED_PLACEHOLDER = Regex("\\{\\{[A-Z0-9_]+}}")
    }
}
