# Stage28 CI provenance

- Exact Stage26 predecessor HEAD: `6ae4550c849107875a1356b86125e6c043585df6`
- Stage18 materialized snapshot SHA-256: `b6a81198842bdba9aa38fc6ec8643e4ec24bce4449e01ecc28f16940c4c977a7`
- Stage28 versionName: `0.1.205`
- Stage28 versionCode: `5489`
- Required package: `br.com.mapeiaia.rotacerta`
- Materialization chain: Stage18 → Stage19 → Stage20 → Stage21 → Stage23 → Stage26 → Stage28
- Stage28 deterministic tests: 50
- Preserved predecessor test inventory: 682
- Required final unit-test inventory: 732
- Temporary compile diagnostic workflow and obsolete v3 wrapper were removed before final CI.
- Final CI must be executed from this clean Stage28 HEAD and must pass the complete Stage28 workflow before an APK is delivered.
- This document records CI provenance only. Samsung SM-S911B physical behavior is reserved for Stage29.
