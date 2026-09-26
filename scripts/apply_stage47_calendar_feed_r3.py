#!/usr/bin/env python3
from pathlib import Path
import sys

PATCHES = Path(sys.argv[1]).resolve()
CAL = PATCHES / "trip-platform/calendar-functions/index.js"


def once(old: str, new: str, label: str) -> None:
    text = CAL.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one marker, got {count}")
    CAL.write_text(text.replace(old, new, 1), encoding="utf-8")

once(
'''function safeEqual(a, b) {
''',
'''function sha256Hex(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function normalizeUsername(value) {
  return String(value || "")
    .normalize("NFD").replace(/[\\u0300-\\u036f]/g, "")
    .toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 32);
}

function safeEqual(a, b) {
''',
"calendar token helpers",
)

once(
'''    updatedAtMillis: trip.updatedAtMillis || null,
  };
''',
'''    driverUsername: trip.driverUsername || "",
    driverDisplayName: trip.driverDisplayName || "",
    updatedAtMillis: trip.updatedAtMillis || null,
  };
''',
"calendar public driver identity",
)

once(
'''async function upcomingTrips() {
  const snapshot = await db.collection("trips")
    .where("departureAtMillis", ">=", Date.now() - 6 * 60 * 60 * 1000)
    .orderBy("departureAtMillis", "asc")
    .limit(100)
    .get();
  return snapshot.docs.filter((doc) => allowedStatuses.has(doc.data().status));
}
''',
'''async function upcomingTrips(username = "") {
  const cutoff = Date.now() - 6 * 60 * 60 * 1000;
  if (username) {
    const snapshot = await db.collection("trips").where("driverUsername", "==", username).limit(200).get();
    return snapshot.docs
      .filter((doc) => allowedStatuses.has(doc.data().status) && Number(doc.data().departureAtMillis) >= cutoff)
      .sort((a, b) => Number(a.data().departureAtMillis) - Number(b.data().departureAtMillis))
      .slice(0, 100);
  }
  const snapshot = await db.collection("trips")
    .where("departureAtMillis", ">=", cutoff)
    .orderBy("departureAtMillis", "asc")
    .limit(100)
    .get();
  return snapshot.docs.filter((doc) => allowedStatuses.has(doc.data().status));
}
''',
"per-driver upcoming trips",
)

once(
'''    const path = (req.path || req.url || "").split("?")[0];
    const match = path.match(/\\/calendar\\/([A-Za-z0-9_-]{16,120})\\.(ics|json)$/);
    const supplied = match ? match[1] : "";
    const format = match ? match[2] : "";
    if (!safeEqual(supplied, publicCalendarToken.value())) {
      return res.status(404).set("Cache-Control", "no-store").send("Not found");
    }
    try {
      const documents = await upcomingTrips();
''',
'''    const path = (req.path || req.url || "").split("?")[0];
    const driverMatch = path.match(/\\/calendar\\/([a-z0-9][a-z0-9-]{1,30}[a-z0-9])\\/([A-Za-z0-9_-]{16,120})\\.(ics|json)$/);
    const legacyMatch = path.match(/\\/calendar\\/([A-Za-z0-9_-]{16,120})\\.(ics|json)$/);
    let username = "";
    let displayName = "";
    let format = "";
    if (driverMatch) {
      username = normalizeUsername(driverMatch[1]);
      const supplied = driverMatch[2];
      format = driverMatch[3];
      const driverSnap = await db.collection("tripDrivers").doc(username).get();
      if (!driverSnap.exists || !safeEqual(sha256Hex(supplied), driverSnap.data().agendaTokenHash || "")) {
        return res.status(404).set("Cache-Control", "no-store").send("Not found");
      }
      displayName = String(driverSnap.data().displayName || username).slice(0, 120);
    } else if (legacyMatch) {
      const supplied = legacyMatch[1];
      format = legacyMatch[2];
      if (!safeEqual(supplied, publicCalendarToken.value())) {
        return res.status(404).set("Cache-Control", "no-store").send("Not found");
      }
    } else {
      return res.status(404).set("Cache-Control", "no-store").send("Not found");
    }
    try {
      const documents = await upcomingTrips(username);
''',
"per-driver calendar route",
)

once(
'''        "X-WR-CALNAME:Rota Certa — Viagens",
''',
'''        `X-WR-CALNAME:${escapeIcs(displayName ? `Rota Certa — ${displayName}` : "Rota Certa — Viagens")}`,
''',
"driver calendar name",
)

print("stage47_calendar_feed_r3=PASS per_driver_json=true per_driver_ics=true legacy_calendar_preserved=true")
