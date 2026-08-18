#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
ACTIVITY = ROOT / "app/src/main/java/br/com/mapeiaia/rotacerta/trips/TripsActivity.kt"
text = ACTIVITY.read_text(encoding="utf-8")
old = '''                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
'''
new = '''                    }) { Text(if (trip.remoteId == null) "Publicar online" else "Sincronizar online") }
                    if (trip.remoteId != null) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    TripRemoteApi(settings).listBookings(trip.remoteId).bookings
                                }.onSuccess { remoteBookings ->
                                    remoteBookings.forEach { remote ->
                                        store.saveBooking(remote.toLocalBooking(trip.id))
                                    }
                                    onChanged("Reservas online atualizadas: ${remoteBookings.size}.")
                                }.onFailure {
                                    onChanged("Falha ao atualizar reservas: ${it.message}")
                                }
                            }
                        }) { Text("Atualizar reservas online") }
                    }
'''
if text.count(old) != 1:
    raise SystemExit(f"remote booking UI marker expected once, got {text.count(old)}")
ACTIVITY.write_text(text.replace(old, new, 1), encoding="utf-8")
print("stage47_remote_booking_ui=PASS")
