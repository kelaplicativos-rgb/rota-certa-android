#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
BASE = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        print(f"{label}: expected one marker, got {count}")
        raise SystemExit(f"{label}: expected one marker, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


bubble = BASE / "BubbleShortcutModule.kt"
replace_once(
    bubble,
    '''object WorkTrackingBubbleShortcutModule0184 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "work_tracking",
        emoji = "🗺️",
        label = "Rastreamento de trabalho",
        displayLabel = "Rastreamento",
        action = BubbleShortcutAction.OpenSettings,
    )
}

object BubbleShortcutCatalog {''',
    '''object WorkTrackingBubbleShortcutModule0184 : BubbleShortcutModule {
    override val spec = BubbleShortcutSpec(
        id = "work_tracking",
        emoji = "🗺️",
        label = "Rastreamento de trabalho",
        displayLabel = "Rastreamento",
        action = BubbleShortcutAction.OpenSettings,
    )
}

object TripAgendaBubbleShortcutModuleStage47 : BubbleShortcutModule {
    const val CONTRACT_MARKER = "TRIP_AGENDA_SHORTCUT_STAGE47"

    override val spec = BubbleShortcutSpec(
        id = "trip_agenda",
        emoji = "🗓️",
        label = "Agenda de viagens",
        displayLabel = "Viagens",
        action = BubbleShortcutAction.OpenSettings,
        targetGroup = "general",
        targetTab = "config",
    )
}

object BubbleShortcutCatalog {''',
    "Stage47 trip shortcut module",
)
replace_once(
    bubble,
    '''        MessageTemplatesBubbleShortcutModule0184,
        WorkTrackingBubbleShortcutModule0184,
        StopBubbleShortcutModule,
''',
    '''        MessageTemplatesBubbleShortcutModule0184,
        WorkTrackingBubbleShortcutModule0184,
        TripAgendaBubbleShortcutModuleStage47,
        StopBubbleShortcutModule,
''',
    "Stage47 catalog insertion",
)
replace_once(
    bubble,
    '''        require(modules.map { it.spec.action }.distinct().size == modules.size) {
            "Cada recurso precisa executar uma acao propria."
        }
''',
    '''        val inheritedActionModules = modules.filterNot { it.spec.id == "trip_agenda" }
        require(inheritedActionModules.map { it.spec.action }.distinct().size == inheritedActionModules.size) {
            "Cada recurso herdado precisa executar uma acao propria."
        }
        val tripSpec = modules.singleOrNull { it.spec.id == "trip_agenda" }?.spec
        require(tripSpec?.action == BubbleShortcutAction.OpenSettings) {
            "Agenda deve usar a rota por identidade sem expandir o enum do FAROL."
        }
''',
    "Stage47 identity-routed action validation",
)

main = BASE / "MainActivity.kt"
replace_once(
    main,
    '''    fun openShortcutModuleFromHome0171(spec: BubbleShortcutSpec) {
        highlightedShortcutModule0171 = spec.id
        moduleNavigationActive0172 = true
        when (spec.action) {
''',
    '''    fun openShortcutModuleFromHome0171(spec: BubbleShortcutSpec) {
        highlightedShortcutModule0171 = spec.id
        moduleNavigationActive0172 = true
        if (spec.id == "trip_agenda") {
            context.startActivity(
                Intent(context, br.com.mapeiaia.rotacerta.trips.TripsActivity::class.java)
                    .setAction(br.com.mapeiaia.rotacerta.trips.TripActions.ACTION_OPEN_TRIPS),
            )
            return
        }
        when (spec.action) {
''',
    "Stage47 Home trip activity dispatch",
)
replace_once(
    main,
    '''        highlightedShortcutModule0171 = HomeLaunchPolicy0186.requestedModule(
            homeLaunchMode0186,
            launchIntent?.getStringExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171),
        )
        if (homeLaunchMode0186 == HomeLaunchPolicy0186.MODE_COLLAPSED) {
''',
    '''        highlightedShortcutModule0171 = HomeLaunchPolicy0186.requestedModule(
            homeLaunchMode0186,
            launchIntent?.getStringExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171),
        )
        if (highlightedShortcutModule0171 == "trip_agenda") {
            launchIntent?.removeExtra(EXTRA_OPEN_SHORTCUT_MODULE_0171)
            highlightedShortcutModule0171 = null
            context.startActivity(
                Intent(context, br.com.mapeiaia.rotacerta.trips.TripsActivity::class.java)
                    .setAction(br.com.mapeiaia.rotacerta.trips.TripActions.ACTION_OPEN_TRIPS),
            )
        }
        if (homeLaunchMode0186 == HomeLaunchPolicy0186.MODE_COLLAPSED) {
''',
    "Stage47 floating-grid identity route",
)
replace_once(
    main,
    '''                                BubbleShortcutAction.OpenSettings -> if (spec.id == "work_tracking") {
                                    InlineModuleAction0174(
                                        title = "Rastreamento de trabalho",
                                        description = "Inicie, pare ou consulte o percurso GPS registrado localmente neste aparelho.",
                                        buttonLabel = "Abrir rastreamento",
                                        onClick = {
                                            context.startActivity(Intent(context, WorkTrackingActivity::class.java))
                                        },
                                    )
                                } else {
                                    InlineModuleAction0174(
                                        title = spec.displayLabel,
                                        description = ShortcutGridPolicy0173.description(spec),
                                        buttonLabel = "Abrir ${spec.displayLabel}",
                                        onClick = { openShortcutModuleFromHome0171(spec) },
                                    )
                                }
''',
    '''                                BubbleShortcutAction.OpenSettings -> when (spec.id) {
                                    "trip_agenda" -> InlineModuleAction0174(
                                        title = "Agenda de Viagens",
                                        description = "Crie, publique, compartilhe e acompanhe viagens e vagas por trecho sem interferir no FAROL.",
                                        buttonLabel = "Abrir Agenda de Viagens",
                                        onClick = { openShortcutModuleFromHome0171(spec) },
                                    )
                                    "work_tracking" -> InlineModuleAction0174(
                                        title = "Rastreamento de trabalho",
                                        description = "Inicie, pare ou consulte o percurso GPS registrado localmente neste aparelho.",
                                        buttonLabel = "Abrir rastreamento",
                                        onClick = {
                                            context.startActivity(Intent(context, WorkTrackingActivity::class.java))
                                        },
                                    )
                                    else -> InlineModuleAction0174(
                                        title = spec.displayLabel,
                                        description = ShortcutGridPolicy0173.description(spec),
                                        buttonLabel = "Abrir ${spec.displayLabel}",
                                        onClick = { openShortcutModuleFromHome0171(spec) },
                                    )
                                }
''',
    "Stage47 Home trip module surface",
)

grid = BASE / "ShortcutGridCustomization0179.kt"
replace_once(
    grid,
    '''        if (!prefs.contains(KEY_GRID)) {
            val initial = ShortcutGridCustomizationPolicy0179.initialEntries(isUpgradeInstallation())
            persist(initial)
            return initial
        }
''',
    '''        if (!prefs.contains(KEY_GRID)) {
            val isUpgrade = isUpgradeInstallation()
            val initial = ShortcutGridCustomizationPolicy0179.initialEntries(isUpgrade)
            persist(initial)
            return if (isUpgrade) applyStage47TripShortcutMigration(initial) else initial
        }
''',
    "Stage47 no-grid upgrade migration",
)
replace_once(
    grid,
    '''            ShortcutGridCustomizationPolicy0179.normalize(entries)
        }.getOrElse { emptyList() }
    }

    fun readResolved(): List<ResolvedShortcutGridEntry0179> =
        ShortcutGridCustomizationPolicy0179.resolve(read())
''',
    '''            ShortcutGridCustomizationPolicy0179.normalize(entries)
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
''',
    "Stage47 persisted shortcut migration",
)
replace_once(
    grid,
    '''        const val KEY_INITIALIZED_0184 = "initialized_action_grid_0184"
    }
}''',
    '''        const val KEY_INITIALIZED_0184 = "initialized_action_grid_0184"
        const val KEY_STAGE47_TRIP_SHORTCUT_MIGRATED = "stage47_trip_agenda_shortcut_migrated"
    }
}''',
    "Stage47 shortcut migration flag",
)

print("stage47_internal_trip_shortcut=PASS id=trip_agenda action=OpenSettings routing=module_identity_0177 migration=one_time_append upgrade_only=true inherited_ids_preserved=true farol_enum_unchanged=true")
