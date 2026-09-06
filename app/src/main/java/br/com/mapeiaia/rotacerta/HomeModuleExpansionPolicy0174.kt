package br.com.mapeiaia.rotacerta

object HomeModuleExpansionPolicy0174 {
    const val CONTRACT_MARKER = "HOME_MODULE_CONTENT_INLINE_0174"

    fun toggle(currentId: String?, requestedId: String): String? =
        if (currentId == requestedId) null else requestedId

    fun isExpanded(currentId: String?, moduleId: String): Boolean = currentId == moduleId
}
