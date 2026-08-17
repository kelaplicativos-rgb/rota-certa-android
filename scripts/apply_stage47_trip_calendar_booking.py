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


copy_tree(STAGE / "main", APP / "src/main/java")
copy_tree(STAGE / "test", APP / "src/test/java")
copy_tree(STAGE / "res", APP / "src/main/res")

# Correct global FULL semantics: a route is globally full only when every
# segment has zero availability. A single full segment must not block a
# disjoint segment that still has a seat.
domain = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripDomain.kt"
s = domain.read_text(encoding="utf-8")
old = '''        return if (remainingSeatsForWholeTrip(trip, bookings, nowMillis) == 0) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
'''
new = '''        val loads = segmentLoads(trip, bookings, nowMillis)
        return if (loads.isNotEmpty() && loads.all { it.availableSeats == 0 }) {
            TripStatus.FULL
        } else {
            TripStatus.PUBLISHED
        }
'''
if s.count(old) != 1:
    raise SystemExit("Stage47 TripDomain global FULL marker not found exactly once")
domain.write_text(s.replace(old, new, 1), encoding="utf-8")

# Public URL must point at Hosting, not at the raw Cloud Functions host.
remote = APP / "src/main/java/br/com/mapeiaia/rotacerta/trips/TripRemoteApi.kt"
s = remote.read_text(encoding="utf-8")
old = '            setRequestProperty("Content-Type", "application/json; charset=utf-8")\n'
new = old + '''            if (settings.publicBaseUrl.startsWith("https://")) {
                setRequestProperty("X-Rota-Certa-Public-Base-Url", settings.publicBaseUrl)
            }
'''
if s.count(old) != 1:
    raise SystemExit("Stage47 TripRemoteApi header marker not found exactly once")
remote.write_text(s.replace(old, new, 1), encoding="utf-8")

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
