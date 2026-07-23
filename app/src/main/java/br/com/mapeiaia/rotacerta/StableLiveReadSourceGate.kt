package br.com.mapeiaia.rotacerta

/**
 * Mantem uma unica fonte responsavel pelo estado visual durante a permanencia
 * na mesma janela monitorada. Isso impede que OCR e Acessibilidade alternem
 * entre resultado valido e leitura transitoria incompleta.
 */
enum class StableLiveReadSource {
    Accessibility,
    Ocr,
}

enum class StableLiveReadAction {
    Analyze,
    Clear,
    Ignore,
}

class StableLiveReadSourceGate {
    private var preferredSource: StableLiveReadSource? = null

    fun submit(source: StableLiveReadSource, validRegisteredCard: Boolean): StableLiveReadAction {
        val preferred = preferredSource
        if (preferred == null) {
            return if (validRegisteredCard) {
                preferredSource = source
                StableLiveReadAction.Analyze
            } else {
                StableLiveReadAction.Ignore
            }
        }

        if (source != preferred) return StableLiveReadAction.Ignore
        return if (validRegisteredCard) StableLiveReadAction.Analyze else StableLiveReadAction.Clear
    }

    fun reset() {
        preferredSource = null
    }

    fun currentSource(): StableLiveReadSource? = preferredSource
}
