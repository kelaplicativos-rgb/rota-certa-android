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
    '''    OpenAuthorizedAppsAndCards,
    OpenTextCorrection,
}''',
    '''    OpenAuthorizedAppsAndCards,
    OpenTrips,
    OpenTextCorrection,
}''',
    "Stage47 shortcut enum",
)
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
        action = BubbleShortcutAction.OpenTrips,
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

focus = BASE / "ShortcutModuleFocusPolicy0177.kt"
replace_once(
    focus,
    '''        BubbleShortcutAction.OpenSettings,
        BubbleShortcutAction.OpenTextCorrection,
''',
    '''        BubbleShortcutAction.OpenSettings,
        BubbleShortcutAction.OpenTrips,
        BubbleShortcutAction.OpenTextCorrection,
''',
    "Stage47 focus policy",
)

main = BASE / "MainActivity.kt"
replace_once(
    main,
    '''            BubbleShortcutAction.OpenQuickLinks -> context.startActivity(Intent(context, QuickLinksActivity::class.java))
            BubbleShortcutAction.OpenMessageTemplates,
''',
    '''            BubbleShortcutAction.OpenQuickLinks -> context.startActivity(Intent(context, QuickLinksActivity::class.java))
            BubbleShortcutAction.OpenTrips -> context.startActivity(
                Intent(context, br.com.mapeiaia.rotacerta.trips.TripsActivity::class.java)
                    .setAction(br.com.mapeiaia.rotacerta.trips.TripActions.ACTION_OPEN_TRIPS),
            )
            BubbleShortcutAction.OpenMessageTemplates,
''',
    "Stage47 Home trip activity dispatch",
)
replace_once(
    main,
    '''                                BubbleShortcutAction.OpenFinance -> InlineModuleAction0174(
                                    title = "Controle financeiro",
                                    description = "Receitas, despesas e resumo financeiro continuam em uma tela dedicada para evitar uma lista pesada dentro da Home.",
                                    buttonLabel = "Abrir controle financeiro",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenTextCorrection -> TextCorrectionModule0186(''',
    '''                                BubbleShortcutAction.OpenFinance -> InlineModuleAction0174(
                                    title = "Controle financeiro",
                                    description = "Receitas, despesas e resumo financeiro continuam em uma tela dedicada para evitar uma lista pesada dentro da Home.",
                                    buttonLabel = "Abrir controle financeiro",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenTrips -> InlineModuleAction0174(
                                    title = "Agenda de Viagens",
                                    description = "Crie, publique, compartilhe e acompanhe viagens e vagas por trecho sem interferir no FAROL.",
                                    buttonLabel = "Abrir Agenda de Viagens",
                                    onClick = { openShortcutModuleFromHome0171(spec) },
                                )

                                BubbleShortcutAction.OpenTextCorrection -> TextCorrectionModule0186(''',
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

print("stage47_internal_trip_shortcut=PASS id=trip_agenda action=OpenTrips migration=one_time_append upgrade_only=true inherited_ids_preserved=true")
