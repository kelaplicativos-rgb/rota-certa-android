<!-- PROXIMITY_NO_DIRECTION_0191_STAGE6_START -->
## 08/08/2026 — 0.1.191: reparo do materializador publicado; validação Android final pendente

- **Branch:** `agent/proximity-alerts-no-direction-0.1.191`; **PR:** #69; **base:** `agent/alert-popup-lifecycle-0.1.190`.
- **Commit funcional do reparo:** `9d812fe81cd2d0bfa3b17c665a660a804c7dc81a`.
- **Versão alvo:** `0.1.191`; **versionCode:** `5475`; **pacote:** `br.com.mapeiaia.rotacerta`.
- **Pedido:** alertas manuais e radares importados devem avisar pela proximidade/tendência de distância, sem exigir heading, alvo à frente ou coincidência com a direção cadastrada do radar.
- **Falha anterior:** workflow `31204485271`, job `92952148552`, parou antes da validação 0.1.191 porque o transformador exigia uma única ocorrência global de um gate que existe legitimamente nos fluxos de alerta salvo e radar.
- **Causa:** duas substituições globais eram ambíguas: `DirectionalAlertPolicy.isTargetAhead(...)` e `runtime.hasPassed(fix.headingDegrees, bearingToTarget, distance)`.
- **Correção:** o transformador agora usa contexto semântico distinto por fluxo, mantém falha fechada e exige exatamente dois caminhos finais `runtime.hasPassed(distance)`, sem sobras dos gates antigos.
- **Regressões preparadas:** contrato gerado exige os dois fluxos por distância, ausência das chamadas antigas, preservação de precisão/velocidade GPS, compatibilidade de `hasPassed` e contrato visual de 3 segundos/fechamento manual.
- **Testes já executados antes do commit:** `python -m py_compile` aprovado; quatro regressões focadas do materializador aprovadas; teste Kotlin gerado compilado isoladamente sem erro de sintaxe.
- **Lint/build:** Lint da base 0.1.190 aprovado; Lint e build Android da **0.1.191 corrigida ainda pendentes**. O commit foi enviado com `[skip ci]`, portanto nenhum APK/artifact 0.1.191 foi gerado nesta etapa.
- **Fronteiras preservadas:** nenhuma mudança direta em Manifest, `DecisionEngine`, rota/Google Maps, OCR, parser, `RadarImport`, overlay direcional ou `LiveRideAccessibilityService`.
- **Risco/pêndencia física:** sem gate de direção, ponto de via paralela/sentido oposto pode avisar se a distância realmente caracterizar aproximação. Validar no aparelho sem reintroduzir filtro global de sentido.
- **Próxima validação:** somente com autorização da etapa final executar pipeline Android completo, assinatura, artifact, SHA-256 e teste físico.
<!-- PROXIMITY_NO_DIRECTION_0191_STAGE6_END -->
