<!-- DECISION_PROXIMITY_NO_DIRECTION_0191_START -->
## 08/08/2026 — aproximação por distância substitui autoridade de sentido apenas em alertas/radares

- **Decisão:** na 0.1.191, alertas manuais e radares importados não dependem de `headingDegrees`, de `isTargetAhead(...)` nem de `radarDirectionMatches(...)` para liberar o aviso. A autoridade passa a ser GPS recente/preciso, limite de distância e tendência consistente de aproximação.
- **Passagem:** o ponto é considerado ultrapassado pelo aumento consistente da distância depois do menor valor observado, preservando amostras mínimas e o contrato visual pós-passagem.
- **Compatibilidade:** a assinatura antiga de `DirectionalAlertPolicy.hasPassed(...)` permanece disponível, mas delega à decisão por distância; isso evita quebrar consumidores materializados anteriores sem manter decisão por heading.
- **Materialização fail-closed:** padrões repetidos em alerta salvo e radar nunca podem ser corrigidos por “primeira/segunda ocorrência” global. O transformador deve usar contexto semântico de cada fluxo, exigir correspondência única dentro desse contexto e abortar se houver sobra das chamadas antigas.
- **Regressão obrigatória:** a árvore final deve conter exatamente dois usos `runtime.hasPassed(distance)`, um por fluxo, nenhuma chamada antiga com heading e nenhum gate `isTargetAhead`/`radarDirectionMatches` no motor de proximidade.
- **Fronteira:** esta decisão é exclusiva do subsistema de alertas/radares de proximidade. Não altera o núcleo universal do farol, `DecisionEngine`, confirmação de card, destino final, rota real, Casa/Alfinete, OCR, Manifest ou permissões.
- **Supersessão limitada:** esta decisão substitui somente a parte direcional da decisão registrada em 03/08/2026 para alertas/radares da 0.1.184. Catálogo de atalhos, Home, segurança das ações e demais contratos daquela versão continuam vigentes.
- **Risco conhecido:** sem gate direcional, um ponto em via paralela ou sentido oposto pode avisar quando a distância realmente diminuir dentro do contrato. Não reintroduzir filtro de sentido sem pedido explícito; validar esse comportamento em aparelho real.
- **Evidência atual:** correção do materializador publicada no commit `9d812fe81cd2d0bfa3b17c665a660a804c7dc81a` do PR #69. Testes focados do transformador e sintaxe Kotlin foram aprovados antes do commit; Android Lint/build/APK da 0.1.191 ainda não foram executados e não devem ser declarados aprovados.
<!-- DECISION_PROXIMITY_NO_DIRECTION_0191_END -->
