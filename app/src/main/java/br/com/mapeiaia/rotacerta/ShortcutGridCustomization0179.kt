package br.com.mapeiaia.rotacerta

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/** Entrada persistida da grade flutuante. A ação sempre referencia o catálogo seguro. */
enum class ShortcutGestureAction0180(val displayLabel: String) {
    PRIMARY_ACTION("Executar ação imediatamente"),
    OPEN_MODULE("Abrir módulo"),
    NONE("Não fazer nada"),
}

enum class ShortcutGestureSlot0180(val displayLabel: String) {
    QUICK_TAP("Toque rápido"),
    HOLD_1500("Segurar 1,5 segundo"),
}

enum class ShortcutHoldActionType0186(val displayLabel: String) {
    OPEN_MODULE("Abrir o módulo relacionado"),
    SAFE_ACTION("Selecionar outra ação segura"),
    NONE("Não fazer nada"),
}

data class ShortcutGridEntry0179(
    val entryId: String,
    val shortcutId: String,
    val label: String,
    val emoji: String,
    val enabled: Boolean = true,
    val quickAction0180: ShortcutGestureAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION,
    val holdAction0180: ShortcutGestureAction0180 = ShortcutGestureAction0180.OPEN_MODULE,
    val holdActionType0186: ShortcutHoldActionType0186? = null,
    val holdShortcutId0186: String? = null,
)

data class ResolvedShortcutGridEntry0179(
    val entryId: String,
    val shortcutId: String,
    val spec: BubbleShortcutSpec,
    val quickAction0180: ShortcutGestureAction0180,
    val holdAction0180: ShortcutGestureAction0180,
    val holdActionType0186: ShortcutHoldActionType0186,
    val holdShortcutSpec0186: BubbleShortcutSpec?,
)

object ShortcutGesturePolicy0179 {
    const val SHORTCUT_LONG_PRESS_MILLIS: Long = ShortcutInteractionPolicy0186.HOLD_MILLIS
    const val MAIN_CUSTOMIZATION_HOLD_MILLIS: Long = 5_000L
    const val MAX_GRID_ITEMS: Int = ShortcutActionCatalog0184.MAX_ACTIVE_ACTIONS
    const val CONTRACT_MARKER: String = "shortcut_grid_customization_0_1_179"
}

object ShortcutGridCustomizationPolicy0179 {
    const val CONTRACT_MARKER_0184: String = "HOME_ACTION_SHORTCUTS_0184"
    const val CONTRACT_MARKER_0186: String = "PERSISTED_HOLD_ACTION_0186"

    val iconChoices: List<String>
        get() = (ShortcutActionCatalog0184.allSpecs().map { it.emoji } + listOf(
            "⭐", "🚗", "🧭", "🏠", "✅", "🔔", "📌", "📝", "⚙️", "➕",
        )).distinct()

    /** Instalação nova: nenhuma ação é imposta ao usuário. */
    fun defaults(): List<ShortcutGridEntry0179> = emptyList()

    /** Atualização da 0.1.183: conserva exatamente os 17 atalhos que já existiam. */
    fun legacyDefaults(): List<ShortcutGridEntry0179> =
        ShortcutActionCatalog0184.legacyDefaultSpecs().map { spec ->
            ShortcutGridEntry0179(
                entryId = "legacy:${spec.id}",
                shortcutId = spec.id,
                label = spec.displayLabel,
                emoji = spec.emoji,
                enabled = true,
                holdActionType0186 = defaultHoldActionType(spec.id),
            )
        }

    fun initialEntries(isUpgrade: Boolean): List<ShortcutGridEntry0179> =
        if (isUpgrade) legacyDefaults() else defaults()

    fun normalize(entries: List<ShortcutGridEntry0179>): List<ShortcutGridEntry0179> {
        val validIds = ShortcutActionCatalog0184.allSpecs().map { it.id }.toSet()
        val usedEntryIds = mutableSetOf<String>()
        val usedShortcutIds = mutableSetOf<String>()
        return entries.asSequence()
            .filter { it.shortcutId in validIds }
            .filter { usedShortcutIds.add(it.shortcutId) }
            .take(ShortcutGesturePolicy0179.MAX_GRID_ITEMS)
            .mapIndexed { index, item ->
                val spec = requireNotNull(BubbleShortcutCatalog.findSpec(item.shortcutId))
                val rawEntryId = item.entryId.trim().take(80).ifBlank { "entry:${item.shortcutId}:$index" }
                var entryId = rawEntryId
                var suffix = 1
                while (!usedEntryIds.add(entryId)) {
                    entryId = "$rawEntryId:$suffix"
                    suffix += 1
                }
                val requestedType = item.holdActionType0186 ?: legacyHoldType(item)
                val normalizedType = when (requestedType) {
                    ShortcutHoldActionType0186.OPEN_MODULE -> if (
                        ShortcutActionCatalog0184.moduleSpecForAction(item.shortcutId) != null
                    ) requestedType else ShortcutHoldActionType0186.NONE
                    ShortcutHoldActionType0186.SAFE_ACTION -> if (
                        item.holdShortcutId0186 in validIds
                    ) requestedType else ShortcutHoldActionType0186.NONE
                    ShortcutHoldActionType0186.NONE -> requestedType
                }
                item.copy(
                    entryId = entryId,
                    label = sanitizeLabel(item.label, spec.displayLabel),
                    emoji = sanitizeEmoji(item.emoji, spec.emoji),
                    quickAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION,
                    holdAction0180 = when (normalizedType) {
                        ShortcutHoldActionType0186.OPEN_MODULE -> ShortcutGestureAction0180.OPEN_MODULE
                        ShortcutHoldActionType0186.SAFE_ACTION -> ShortcutGestureAction0180.PRIMARY_ACTION
                        ShortcutHoldActionType0186.NONE -> ShortcutGestureAction0180.NONE
                    },
                    holdActionType0186 = normalizedType,
                    holdShortcutId0186 = item.holdShortcutId0186?.takeIf {
                        normalizedType == ShortcutHoldActionType0186.SAFE_ACTION && it in validIds
                    },
                )
            }
            .toList()
    }

    fun resolve(entries: List<ShortcutGridEntry0179>): List<ResolvedShortcutGridEntry0179> =
        normalize(entries)
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { entry ->
                val original = BubbleShortcutCatalog.findSpec(entry.shortcutId) ?: return@mapNotNull null
                val holdType = requireNotNull(entry.holdActionType0186)
                ResolvedShortcutGridEntry0179(
                    entryId = entry.entryId,
                    shortcutId = entry.shortcutId,
                    spec = original.copy(
                        emoji = entry.emoji,
                        label = entry.label,
                        displayLabel = entry.label,
                    ),
                    quickAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION,
                    holdAction0180 = entry.holdAction0180,
                    holdActionType0186 = holdType,
                    holdShortcutSpec0186 = entry.holdShortcutId0186?.let(BubbleShortcutCatalog::findSpec),
                )
            }
            .toList()

    fun contains(entries: List<ShortcutGridEntry0179>, shortcutId: String): Boolean =
        normalize(entries).any { it.shortcutId == shortcutId }

    fun add(
        entries: List<ShortcutGridEntry0179>,
        shortcutId: String,
        nowMillis: Long,
    ): List<ShortcutGridEntry0179> {
        val normalized = normalize(entries)
        if (normalized.size >= ShortcutGesturePolicy0179.MAX_GRID_ITEMS) return normalized
        if (normalized.any { it.shortcutId == shortcutId }) return normalized
        val spec = BubbleShortcutCatalog.findSpec(shortcutId) ?: return normalized
        return normalize(
            normalized + ShortcutGridEntry0179(
                entryId = "action:${spec.id}:$nowMillis:${normalized.size}",
                shortcutId = spec.id,
                label = spec.displayLabel,
                emoji = spec.emoji,
                enabled = true,
                holdActionType0186 = defaultHoldActionType(spec.id),
            ),
        )
    }

    fun remove(entries: List<ShortcutGridEntry0179>, shortcutId: String): List<ShortcutGridEntry0179> =
        normalize(entries).filterNot { it.shortcutId == shortcutId }

    fun move(
        entries: List<ShortcutGridEntry0179>,
        fromIndex: Int,
        toIndex: Int,
    ): List<ShortcutGridEntry0179> {
        if (fromIndex !in entries.indices || toIndex !in entries.indices || fromIndex == toIndex) {
            return entries
        }
        return entries.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun nextShortcutId(currentId: String): String {
        val ids = ShortcutActionCatalog0184.allSpecs().map { it.id }
        if (ids.isEmpty()) return currentId
        val index = ids.indexOf(currentId).coerceAtLeast(0)
        return ids[(index + 1) % ids.size]
    }

    fun nextEmoji(current: String): String {
        val choices = iconChoices
        if (choices.isEmpty()) return current
        val index = choices.indexOf(current).coerceAtLeast(0)
        return choices[(index + 1) % choices.size]
    }

    fun defaultHoldActionType(shortcutId: String): ShortcutHoldActionType0186 =
        if (ShortcutActionCatalog0184.moduleSpecForAction(shortcutId) != null) {
            ShortcutHoldActionType0186.OPEN_MODULE
        } else {
            ShortcutHoldActionType0186.NONE
        }

    fun holdActionLabel(entry: ShortcutGridEntry0179): String = when (
        entry.holdActionType0186 ?: legacyHoldType(entry)
    ) {
        ShortcutHoldActionType0186.OPEN_MODULE -> "Abrir o módulo relacionado"
        ShortcutHoldActionType0186.SAFE_ACTION -> {
            val spec = BubbleShortcutCatalog.findSpec(entry.holdShortcutId0186)
            "Outra ação: ${spec?.displayLabel ?: "não definida"}"
        }
        ShortcutHoldActionType0186.NONE -> "Não fazer nada"
    }

    private fun legacyHoldType(entry: ShortcutGridEntry0179): ShortcutHoldActionType0186 = when (entry.holdAction0180) {
        ShortcutGestureAction0180.NONE -> ShortcutHoldActionType0186.NONE
        ShortcutGestureAction0180.PRIMARY_ACTION -> ShortcutHoldActionType0186.SAFE_ACTION
        ShortcutGestureAction0180.OPEN_MODULE -> defaultHoldActionType(entry.shortcutId)
    }

    private fun sanitizeLabel(value: String, fallback: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(24)
        .ifBlank { fallback.take(24) }

    private fun sanitizeEmoji(value: String, fallback: String): String = value
        .trim()
        .take(4)
        .ifBlank { fallback }
}

class ShortcutGridPreferenceStore0179(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): List<ShortcutGridEntry0179> {
        if (!prefs.contains(KEY_GRID)) {
            val isUpgrade = isUpgradeInstallation()
            val initial = ShortcutGridCustomizationPolicy0179.initialEntries(isUpgrade)
            persist(initial)
            return if (isUpgrade) applyStage47TripShortcutMigration(initial) else initial
        }
        val raw = prefs.getString(KEY_GRID, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val entries = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val shortcutId = item.optString("shortcutId")
                    val oldHold = runCatching {
                        ShortcutGestureAction0180.valueOf(item.optString("holdAction0180"))
                    }.getOrDefault(ShortcutGestureAction0180.OPEN_MODULE)
                    val holdType = runCatching {
                        ShortcutHoldActionType0186.valueOf(item.optString("holdActionType0186"))
                    }.getOrNull()
                    add(
                        ShortcutGridEntry0179(
                            entryId = item.optString("entryId"),
                            shortcutId = shortcutId,
                            label = item.optString("label"),
                            emoji = item.optString("emoji"),
                            enabled = item.optBoolean("enabled", true),
                            quickAction0180 = ShortcutGestureAction0180.PRIMARY_ACTION,
                            holdAction0180 = oldHold,
                            holdActionType0186 = holdType,
                            holdShortcutId0186 = item.optString("holdShortcutId0186").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            ShortcutGridCustomizationPolicy0179.normalize(entries)
        }.getOrElse { emptyList() }.let(::applyStage47TripShortcutMigration)
    }

    private fun applyStage47TripShortcutMigration(entries: List<ShortcutGridEntry0179>): List<ShortcutGridEntry0179> {
        if (prefs.getBoolean(KEY_STAGE47_TRIP_SHORTCUT_MIGRATED, false)) return entries
        val migrated = if (
            !ShortcutGridCustomizationPolicy0179.contains(entries, "trip_agenda") &&
            entries.size < ShortcutGesturePolicy0179.MAX_GRID_ITEMS
        ) {
            ShortcutGridCustomizationPolicy0179.add(
                entries = entries,
                shortcutId = "trip_agenda",
                nowMillis = System.currentTimeMillis(),
            )
        } else {
            entries
        }
        if (migrated != entries) persist(migrated)
        prefs.edit().putBoolean(KEY_STAGE47_TRIP_SHORTCUT_MIGRATED, true).apply()
        return migrated
    }

    fun readResolved(): List<ResolvedShortcutGridEntry0179> =
        ShortcutGridCustomizationPolicy0179.resolve(read())

    fun write(entries: List<ShortcutGridEntry0179>) {
        persist(ShortcutGridCustomizationPolicy0179.normalize(entries))
    }

    /** Restaurar na 0.1.184 significa esvaziar e escolher novamente pela Home. */
    fun reset() = persist(emptyList())

    private fun persist(entries: List<ShortcutGridEntry0179>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("entryId", entry.entryId)
                    .put("shortcutId", entry.shortcutId)
                    .put("label", entry.label)
                    .put("emoji", entry.emoji)
                    .put("enabled", entry.enabled)
                    .put("quickAction0180", ShortcutGestureAction0180.PRIMARY_ACTION.name)
                    .put("holdAction0180", entry.holdAction0180.name)
                    .put("holdActionType0186", entry.holdActionType0186?.name)
                    .put("holdShortcutId0186", entry.holdShortcutId0186),
            )
        }
        prefs.edit()
            .putString(KEY_GRID, array.toString())
            .putBoolean(KEY_INITIALIZED_0184, true)
            .apply()
    }

    private fun isUpgradeInstallation(): Boolean = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false)

    private companion object {
        const val PREFS_NAME = "rota_certa_shortcut_grid_0179"
        const val KEY_GRID = "grid_json_v1"
        const val KEY_INITIALIZED_0184 = "initialized_action_grid_0184"
        const val KEY_STAGE47_TRIP_SHORTCUT_MIGRATED = "stage47_trip_agenda_shortcut_migrated"
    }
}

// shortcut_grid_customization_0_1_179
// per_shortcut_menu_0_1_180
// home_action_shortcuts_0_1_184
// persisted_hold_action_0_1_186
