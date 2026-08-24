# Agenda de Viagens — contrato global de rastreio forense

Versão de introdução: **0.1.273 / 5566**.

## Objetivo

Toda interação observável executada dentro do módulo Agenda deve deixar evidência cronológica no mesmo relatório unificado já utilizado pelo Rota Certa. O rastreio é infraestrutura permanente da Agenda, não uma instrumentação temporária de um defeito.

## Cobertura automática

`AgendaTraceProvider` é inicializado pelo Android antes das telas e registra `ActivityLifecycleCallbacks` no processo. Toda `Activity` cujo nome pertença a `br.com.mapeiaia.rotacerta.trips.*` é automaticamente coberta, incluindo `TripsActivity`, sessões BlaBlaCar, colheita MHTML e automação confiável de vagas.

O callback de `Window` é encapsulado sem consumir eventos. Cada `MotionEvent.ACTION_UP` gera `AGENDA_INTERACTION` com:

- sequência monotônica da sessão;
- classe da tela;
- tipo de interação;
- posição aproximada em baldes de 5% da janela;
- quantidade de ponteiros.

Mudanças de ciclo de vida geram `AGENDA_SCREEN` (`created`, `started`, `resumed`, `paused`, `stopped`, `state_saved`, `destroyed`).

Esse gancho cobre automaticamente novas `Activity` adicionadas futuramente ao pacote `trips`, sem exigir instalação manual do rastreador em cada tela.

## Camada semântica

`ResponsiveTripActions` registra `AGENDA_ACTION` antes de executar o callback original. A ação pode fornecer um `traceKey` estático e não pessoal. Quando não houver `traceKey`, o texto visual não é persistido: somente um hash SHA-256 curto da label entra no evento.

A camada global não substitui os eventos causais já existentes. Eventos do coletor network-first, identidade, passageiros, Timeline, MHTML e reconciliação de vagas continuam sendo a evidência detalhada da execução interna.

## Privacidade e segurança

O rastreio global não deve persistir texto digitado, nome de passageiro, telefone, endereço, conteúdo de campo, cookie, token, cabeçalho de autorização, extras de Intent ou URL bruta. Coordenadas são reduzidas a baldes de 5% para correlação visual sem registrar posição exata.

Falha do mecanismo diagnóstico é fail-open para a UI: `UnifiedDebugEventStore.record` é protegido e o `Window.Callback` original sempre recebe o evento. O rastreador não altera ocupação, sincronização, navegação ou regras de negócio.

## Contrato para recursos futuros

1. Nova tela da Agenda deve permanecer no pacote `br.com.mapeiaia.rotacerta.trips` para herdar a cobertura global automaticamente.
2. Se uma tela de Agenda precisar existir fora desse pacote, ela deve ser explicitamente incluída no predicado de cobertura no mesmo commit.
3. Novas ações renderizadas por `ResponsiveTripActions` devem preferir `traceKey` estático e sem dados pessoais.
4. Fluxos com efeito de negócio ou rede devem continuar emitindo eventos causais próprios de início, resultado, pendência e erro; o toque global é a rede de segurança, não substituto da causalidade.
5. O teste `AgendaGlobalTrace0273Test` e o source-contract do workflow direct-source devem continuar passando. Alteração que remova o gancho global deve bloquear o APK de validação.

## Uso no relatório físico

Com vídeo e relatório de depuração gerados na mesma sessão, a correlação desejada é:

`tempo do vídeo → AGENDA_SCREEN → AGENDA_INTERACTION/AGENDA_ACTION → evento causal do recurso → resposta/estado → efeito na Timeline/tela → próximo evento`.

O contrato é considerado tecnicamente materializado somente quando testes, build direct-source, proteção do FAROL e artifact APK passarem. A aprovação funcional continua dependendo do teste físico no Samsung SM-S911B / Android 16.
