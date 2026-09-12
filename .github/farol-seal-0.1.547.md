# FAROL 0.1.547 — baseline selada

Baseline funcional: 535ad0217c38c7bd98c6eea7f839a94c64b5c2dd
APK SHA-256: e10abc87127aa95c19b5a288904f17cae26f0659bcaf7592320f3c54db82475b
Version: 0.1.547 (5839)
Package: br.com.mapeiaia.rotacerta
Dispositivo oficial para validação física: Samsung SM-S911B / Android 16

Comportamentos protegidos: Leitura sem dependência obrigatória de chave Google, OSM/Nominatim + OSRM como primário, Google apenas contingência, distância rodoviária para decisão, normalização resiliente de endereço, escolha do candidato geográfico coerente com destino configurado, proximidade independente do Modo Leitura e preservação dos contratos Stage 44/46.

LiveRideAccessibilityService.kt e os contratos ReadingProximity0545ContractTest, ReadingFreePrimary0546ContractTest e ReadingGeocodeResilience0547ContractTest devem permanecer preservados. Evolução futura deve criar uma nova baseline comprovada, sem reescrever esta baseline histórica.
