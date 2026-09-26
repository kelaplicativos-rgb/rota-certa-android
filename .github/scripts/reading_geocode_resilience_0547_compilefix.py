from pathlib import Path

path = Path(__file__).resolve().parents[2] / "app/src/main/java/br/com/mapeiaia/rotacerta/GoogleMapsService.kt"
text = path.read_text(encoding="utf-8")
old = '''                val selected = when (val result = platformGeocoder.geocode(query)) {
                    is Result.Success -> result.data
                    is Result.Failure -> null
                }
'''
new = '''                val selected = platformGeocoder.geocode(
                    query = query,
                    region = DeviceRegion(city = "", country = ""),
                )
'''
if text.count(old) != 1:
    raise SystemExit("Android geocoder fallback anchor mismatch")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("aligned Android GeocodingService 0.1.547 signature")
