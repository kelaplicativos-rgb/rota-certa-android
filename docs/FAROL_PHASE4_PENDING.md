# Rota Certa 0.1.187 — fase 4 implementada, validação Android pendente

Data: 06/08/2026

## Referências

- Repositório: `kelaplicativos-rgb/rota-certa-android`
- Branch principal: `agent/fix-farol-runtime-0.1.187`
- PR principal: #59
- Base real: `agent/shortcut-audio-links-text-correction-0.1.186`
- Commit funcional da fase 4: `e818395276965ba59a6598e83affc63c42694721`
- Versão: `0.1.187`
- versionCode: `5471`
- Pacote: `br.com.mapeiaia.rotacerta`

## Pedido

Continuar a correção estrutural do farol após a fase 3, garantindo que resultados atrasados de rota, OCR ou análise não consigam substituir uma leitura nova, mesmo quando uma chamada de rede ignore o cancelamento da coroutine.

## Situação anterior

A fase 3 passou a preservar corretamente o visual confirmado quando um snapshot transitório era rejeitado. Porém, a verificação final do resultado de rota ainda considerava somente geração da tela, hash e assinatura do endereço. A geração da sessão e a geração da janela não participavam do vínculo final.

Uma chamada de rede que terminasse depois de uma troca de sessão ou janela poderia ignorar o cancelamento e ainda tentar aplicar o resultado antigo caso tela, hash e endereço coincidissem.

## Causa

O cancelamento era de melhor esforço e estava distribuído em vários pontos. O resultado assíncrono não carregava uma identidade completa e indivisível da leitura que o originou.

## Correção implementada

- Criado `FarolDecisionBinding0187Phase4` com pacote, geração da sessão, janela, geração da tela, geração da janela, hash da tela e assinatura do destino.
- Criada `FarolDecisionBindingPolicy0187Phase4` para exigir igualdade completa antes e depois de qualquer suspensão.
- Rotas normais, cacheadas e de recuperação passam a carregar o vínculo completo.
- Resultado atrasado registra `BUBBLE_ROUTE_RESULT_DISCARDED_0187_PHASE4` e não altera destino, quilômetros ou cor.
- Criado cancelamento centralizado `invalidateFarolAsyncWork0187Phase4` para rota, análise, OCR, screenshot de fallback e confirmação parcial.
- Troca de sessão, rejeição de janela e limpeza avançam gerações e invalidam trabalho assíncrono sem depender apenas de `Job.cancel()`.
- A fase 3 permanece preservada: rejeição transitória não apaga decisão válida; amarelo e cinza continuam exigindo evidência positiva coerente.

## Arquivos principais

- `FarolRuntimeSafety0187.kt`
- `LiveRideAccessibilityService.kt`
- `FarolRuntimeSafety0187Test.kt`
- `FarolRuntimeFix0187ContractTest.kt`
- `tests/test_farol_trace_phase4.py`
- `patches/farol-runtime-0187-phase4.patch.gz.b64`
- `scripts/inject_build_rota_certa_0187_phase4.py`
- `scripts/build_rota_certa_0187.sh`

SHA-256 do patch decodificado:

`f1cd8fbee993b93da81f97e313143f96ddd0a821e8c1e1ce7176e3b2e524843f`

## Fronteira preservada

Não foram alterados `DecisionEngine`, cálculo de rota, raio, Casa/Alfinete, Manifest, permissões, parser universal, confirmação do destino final, radares, alertas ou autoridade dos adaptadores. Adaptadores continuam proibidos de decidir a cor.

## Verificações concluídas

- `git diff --check`: aprovado na árvore materializada.
- Compilação isolada de `FarolRuntimeSafety0187.kt` com `kotlinc`: aprovada.
- Replay Python da fase 4: dois testes aprovados.
- Cenário comprovado: uma rota pertencente à sessão/janela anterior falha na política de frescor e não pode repintar depois da invalidação.

## Validação ainda não concluída

Não foi possível concluir nesta fase:

- testes Android completos;
- Android Lint;
- `clean assembleDebug`;
- verificação do Manifest compilado, pacote, versão e versionCode;
- assinatura APK;
- artifact do GitHub Actions;
- SHA-256 de um APK da fase 4;
- publicação e conferência do download permanente.

Motivo: incidente oficial de indisponibilidade do GitHub Actions em 06/08/2026. O GitHub informou que workflows estavam falhando ou demorando para iniciar, jobs poderiam permanecer enfileirados por período prolongado ou expirar e algumas requisições da API de Actions retornavam erros.

## Artifact e APK

- Artifact da fase 4: **não gerado/comprovado**.
- APK da fase 4: **não entregue**.
- SHA-256 do APK da fase 4: **não disponível**.
- O APK da fase 3 não deve ser apresentado como se contivesse esta correção.

## Riscos e validações pendentes

Depois da recuperação do GitHub Actions, executar obrigatoriamente a cadeia completa e instalar o APK resultante no Samsung SM-S911B/Android 16. Validar troca rápida de cards, fechamento real, SystemUI, teclado, bolinha, retorno ao feed, saída do aplicativo, rota antiga concluindo depois da troca de janela e ausência de pisca ou quilômetros antigos.
