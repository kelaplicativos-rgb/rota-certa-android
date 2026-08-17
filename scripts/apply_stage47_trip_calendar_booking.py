#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd().resolve()
STAGE = ROOT / "stage47/android"
APP = ROOT / "app"


def copy_tree(source: Path, destination: Path) -> None:
    if not source.is_dir():
        raise SystemExit(f"missing Stage47 source directory: {source}")
    for path in source.rglob("*"):
        if path.is_dir():
            continue
        target = destination / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"{label}: expected one marker, got {text.count(old)}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


copy_tree(STAGE / "main", APP / "src/main/java")
copy_tree(STAGE / "test", APP / "src/test/java")
copy_tree(STAGE / "res", APP / "src/main/res")

# Correct global FULL semantics: a route is globally full only when every
# segment has zero availability. A single full segment must not block a
# disjoint segment that still has a seat.
domain = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt"
replace_once(
    domain,
    '''        return if (remainingSeatsForWholeTrip(trip, bookings, nowMillis) == 0) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
''',
    '''        val loads = segmentLoads(trip, bookings, nowMillis)
        return if (loads.isNotEmpty() && loads.all { it.availableSeats == 0 }) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
''',
    "Stage47 global FULL semantics",
)

# kotlinx.serialization generic decode extension is intentionally explicit so
# this remains compiler-stable across serialization library versions.
for relative in ("TripStore.kt", "TripRemoteApi.kt"):
    path = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips" / relative
    text = path.read_text(encoding="utf-8")
    if "import kotlinx.serialization.decodeFromString" not in text:
        anchor = "import kotlinx.serialization.encodeToString\n"
        if text.count(anchor) != 1:
            raise SystemExit(f"{relative}: serialization import anchor missing")
        path.write_text(text.replace(anchor, "import kotlinx.serialization.decodeFromString\n" + anchor, 1), encoding="utf-8")

# Public URL must point at Hosting, not at the raw Cloud Functions host.
remote = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt"
replace_once(
    remote,
    '            setRequestProperty("Content-Type", "application/json; charset=utf-8")\n',
    '''            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (settings.publicBaseUrl.startsWith("https://")) {
                setRequestProperty("X-Rota-Certa-Public-Base-Url", settings.publicBaseUrl)
            }
''',
    "Stage47 public hosting header",
)

# Avoid shadowing kotlin.error inside the Compose editor.
activity = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
replace_once(
    activity,
    '                val seats = capacity.toIntOrNull()?.coerceIn(1, 8) ?: error("Informe uma quantidade de vagas válida.")\n',
    '                val seats = capacity.toIntOrNull()?.coerceIn(1, 8) ?: throw IllegalArgumentException("Informe uma quantidade de vagas válida.")\n',
    "Stage47 editor capacity validation",
)

# Tile.subtitle arrived after the module minimum SDK; guard the call even
# though the physical test device is Android 16.
entry = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripAndroidEntryPoints.kt"
replace_once(
    entry,
    '''            subtitle = next?.title?.take(28) ?: "Rota Certa"
            updateTile()
''',
    '''            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = next?.title?.take(28) ?: "Rota Certa"
            }
            updateTile()
''',
    "Stage47 Quick Settings API guard",
)

manifest = APP / "src/main/AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
marker = "STAGE47_TRIP_CALENDAR_BOOKING"
if marker in s:
    raise SystemExit("Stage47 manifest block already present")
block = '''
        <!-- STAGE47_TRIP_CALENDAR_BOOKING: isolated from FAROL/accessibility pipeline. -->
        <activity
            android:name=".trips.TripsActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="rotacerta" android:host="trips" />
            </intent-filter>
        </activity>
        <service
            android:name=".trips.TripQuickTileService"
            android:exported="true"
            android:label="Agenda de Viagens"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>
        <receiver
            android:name=".trips.TripWidgetProvider"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/trip_widget_info_stage47" />
        </receiver>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.tripfiles"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/trip_file_paths_stage47" />
        </provider>
'''
if s.count("</application>") != 1:
    raise SystemExit("AndroidManifest.xml must contain one </application>")
manifest.write_text(s.replace("</application>", block + "    </application>", 1), encoding="utf-8")

build = APP / "build.gradle.kts"
s = build.read_text(encoding="utf-8")
if s.count("versionCode = 5510") != 1 or s.count('versionName = "0.1.226"') != 1:
    raise SystemExit("Stage47 expected materialized Stage46 R8 version 0.1.226/5510")
s = s.replace("versionCode = 5510", "versionCode = 5520", 1)
s = s.replace('versionName = "0.1.226"', 'versionName = "0.1.227"', 1)
build.write_text(s, encoding="utf-8")

print("stage47_trip_calendar_booking=PASS versionName=0.1.227 versionCode=5520")
