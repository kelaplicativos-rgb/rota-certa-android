package br.com.mapeiaia.rotacerta

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Botao mestre inspirado no ciclo explicito de trabalho observado no APK de referencia.
 * A Acessibilidade permanece autorizada pelo Android, mas o Rota Certa nao le, nao tira
 * screenshot, nao pede rota e nao fala alertas enquanto o modo estiver desligado.
 */
object WorkModePolicy0162 {
    fun isEnabled(settings: AppSettings): Boolean = settings.appEnabled && settings.liveReadingEnabled

    fun setEnabled(settings: AppSettings, enabled: Boolean): AppSettings = settings.copy(
        appEnabled = enabled,
        liveReadingEnabled = enabled,
    )
}

/** Impede que launcher, ChatGPT, arquivos, teclado e telas do sistema virem apps de corrida. */
object DriverAppPackagePolicy0162 {
    private val blockedExact = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.documentsui",
        "com.google.android.apps.nbu.files",
        "com.sec.android.app.myfiles",
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.android.settings",
        "com.samsung.android.settings",
        "com.openai.chatgpt",
        "com.google.android.apps.maps",
        "com.waze",
    )
    private val blockedFragments = listOf(
        "launcher",
        "systemui",
        "inputmethod",
        "keyboard",
        "packageinstaller",
        "permissioncontroller",
        "documentsui",
        "filemanager",
    )
    private val transientExact = setOf(
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "com.samsung.android.app.smartcapture",
        "com.google.android.projection.gearhead",
    )

    fun normalize(packageName: String?): String? = packageName
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)

    fun isEligible(packageName: String?, ownPackageName: String): Boolean {
        val normalized = normalize(packageName) ?: return false
        val own = normalize(ownPackageName)
        if (normalized == own || normalized in blockedExact) return false
        return blockedFragments.none(normalized::contains)
    }

    fun sanitize(packages: Iterable<String>, ownPackageName: String): Set<String> = packages
        .mapNotNull(::normalize)
        .filter { isEligible(it, ownPackageName) }
        .toSortedSet()

    fun isTransientOverlay(packageName: String?, ownPackageName: String): Boolean {
        val normalized = normalize(packageName) ?: return false
        val own = normalize(ownPackageName)
        return normalized == own || normalized in transientExact ||
            normalized.contains("inputmethod") || normalized.contains("keyboard") ||
            normalized.contains("smartcapture")
    }
}

data class DriverCardSession0162(
    val packageName: String,
    val windowId: Int,
    val generation: Long,
)

/** Token imutavel: resultado atrasado de outro app/janela nunca pode pintar o farol. */
class DriverCardSessionGate0162 {
    private val serial = AtomicLong(0L)
    @Volatile private var active: DriverCardSession0162? = null

    @Synchronized
    fun begin(packageName: String, windowId: Int): DriverCardSession0162 {
        val previous = active
        val effectiveWindow = windowId.takeIf { it > 0 } ?: previous?.windowId ?: 0
        if (previous != null && previous.packageName == packageName && previous.windowId == effectiveWindow) {
            return previous
        }
        return DriverCardSession0162(packageName, effectiveWindow, serial.incrementAndGet()).also { active = it }
    }

    fun current(): DriverCardSession0162? = active

    fun isCurrent(token: DriverCardSession0162?): Boolean = token != null && token == active

    @Synchronized
    fun invalidate(): DriverCardSession0162? = active.also {
        active = null
        serial.incrementAndGet()
    }
}

/**
 * A raiz selecionada manda. Um evento antigo do Uber nao pode ser usado quando a raiz real
 * ja e ChatGPT/Arquivos/launcher. Eventos de System UI podem apontar para a raiz selecionada.
 */
object DriverCardEventResolver0162 {
    fun resolve(
        eventPackageName: String?,
        rootPackageName: String?,
        selectedPackages: Set<String>,
        ownPackageName: String,
    ): String? {
        val selected = DriverAppPackagePolicy0162.sanitize(selectedPackages, ownPackageName)
        val root = DriverAppPackagePolicy0162.normalize(rootPackageName)
        val event = DriverAppPackagePolicy0162.normalize(eventPackageName)
        if (root != null && root in selected) return root
        if (event != null && event in selected &&
            (root == null || DriverAppPackagePolicy0162.isTransientOverlay(root, ownPackageName))
        ) return event
        return null
    }
}

/** Fingerprint sem preco, contagem regressiva ou texto animado. */
object DriverCardDisplayIdentity0162 {
    fun fingerprint(
        packageName: String,
        windowId: Int,
        activeAddressSignature: String?,
    ): Int = listOf(
        DriverAppPackagePolicy0162.normalize(packageName).orEmpty(),
        windowId.toString(),
        activeAddressSignature.orEmpty(),
    ).joinToString("|").hashCode()
}

/** Adaptadores leves por aplicativo; o texto original sempre e preservado. */
object DriverCardTextSanitizer0162 {
    fun prepare(packageName: String?, text: String): String {
        val original = text
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotBlank)
            .toList()
        if (original.isEmpty()) return ""
        val packageKey = DriverAppPackagePolicy0162.normalize(packageName).orEmpty()
        val aliases = original.mapNotNull { line -> alias(packageKey, line) }
        return (original + aliases).distinct().joinToString("\n")
    }

    private fun alias(packageName: String, line: String): String? {
        val canonical = Normalizer.normalize(line.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val replacements = when (packageName) {
            "com.ubercab.driver" -> listOf(
                "local de embarque" to "Embarque",
                "ponto de encontro" to "Embarque",
                "local de destino" to "Destino",
                "destino da viagem" to "Destino",
            )
            "com.app99.driver" -> listOf(
                "ponto de partida" to "Embarque",
                "local de partida" to "Embarque",
                "ponto de chegada" to "Destino",
                "local de chegada" to "Destino",
            )
            "sinet.startup.indriver" -> listOf(
                "de onde" to "Embarque",
                "para onde" to "Destino",
                "ponto de partida" to "Embarque",
                "ponto de chegada" to "Destino",
            )
            else -> emptyList()
        }
        val match = replacements.firstOrNull { (marker, _) -> marker in canonical } ?: return null
        val markerIndex = canonical.indexOf(match.first)
        if (markerIndex < 0) return null
        val remainder = line.drop(markerIndex + match.first.length)
            .trim()
            .trimStart(':', '-', '–', '—')
            .trim()
        return if (remainder.isBlank()) match.second else "${match.second}: $remainder"
    }
}
