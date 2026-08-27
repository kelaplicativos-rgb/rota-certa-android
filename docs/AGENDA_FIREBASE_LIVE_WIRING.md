# Agenda — Firebase live wiring

Continuidade direta do PR #118 e da infraestrutura Stage47 existente. Este checkpoint não cria outro motor de reservas, vagas, Agenda ou Timeline.

## Projeto Firebase real

- projectId: `rota-certa-7ccc8`
- project number: `353336964879`
- Android package: `br.com.mapeiaia.rotacerta`
- Web App ID: `1:353336964879:web:e7e6924d865a221d99f07a`
- Hosting: `rota-certa-7ccc8.web.app` e `rota-certa-7ccc8.firebaseapp.com`
- Authentication: provedor anônimo habilitado no console
- App Check Web: reCAPTCHA Enterprise registrado
- Firestore: acesso direto continua deny-by-default

A chave de site reCAPTCHA Enterprise usada pelo App Check é pública por natureza e fica no JavaScript do navegador. Nenhuma API key Firebase, service account, token do motorista ou segredo de deploy é commitado.

## Integração Web

O portal público usa os URLs reservados do Firebase Hosting:

- `/__/firebase/8.10.1/firebase-app.js`
- `/__/firebase/8.10.1/firebase-app-check.js`
- `/__/firebase/8.10.1/firebase-auth.js`
- `/__/firebase/init.js`

Assim, a configuração do app Web vem do próprio site Firebase associado ao projeto, evitando duplicar ou hardcodar a API key no repositório.

Antes de qualquer mutação pública:

1. App Check é ativado com reCAPTCHA Enterprise e auto-refresh;
2. o navegador abre sessão Firebase anônima;
3. o portal obtém ID token e App Check token;
4. reserva/cancelamento enviam `Authorization: Bearer ...` e `X-Firebase-AppCheck`.

Consultar agenda e viagem permanece público por token/link. Criar ou cancelar reserva exige a dupla validação.

## Backend

`tripApi` valida nos endpoints públicos mutáveis:

- Firebase Authentication via Admin SDK;
- Firebase App Check via Admin SDK;
- App Check pertencente exatamente ao app Web `1:353336964879:web:e7e6924d865a221d99f07a`.

Depois dessa camada continuam valendo os controles já existentes: rate limit, idempotência, WhatsApp brasileiro, token de cancelamento, transação Firestore por segmento e bloqueio de overbooking.

## CSP

Hosting e HTML permitem somente as origens necessárias ao Auth/App Check/reCAPTCHA. A política continua bloqueando leitura Firestore direta e não libera scripts arbitrários.

## Android

O App Check do Android permanece propositalmente não registrado/enforced neste checkpoint. O APK continua falando com o backend HTTPS existente; ativar Play Integrity será uma etapa separada para não bloquear builds físicos de teste.

## O que ainda não foi feito

- nenhum deploy de produção;
- nenhum merge/release/latest;
- nenhum segredo foi criado ou enviado ao GitHub;
- `ROTA_CERTA_DRIVER_TOKEN` ainda precisa existir no Secret Manager antes do primeiro deploy;
- GitHub/Firebase ainda precisam de uma credencial real de deploy (service account, Workload Identity ou login CLI autorizado);
- teste físico WhatsApp/Chrome/Samsung continua pendente até existir URL implantada.

## Próxima fronteira real

Validar CI desta branch. Depois, configurar credencial de deploy e o segredo do backend sem expor valores no repositório, implantar Hosting + Functions + rules no projeto `rota-certa-7ccc8`, testar a URL real e somente então considerar enforcement mais amplo do App Check.
