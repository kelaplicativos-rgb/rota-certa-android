# Stage47 — Agenda de Viagens & Reservas — 0.1.227 / 5520

## Objetivo

Criar no Rota Certa um subsistema independente para publicar viagens, controlar vagas por trecho, receber reservas, compartilhar uma agenda pública sanitizada e integrar motorista/passageiro com calendários sem expor a agenda pessoal do motorista.

## Autoridade funcional

- Rota Certa é a autoridade da viagem, paradas, capacidade e reservas.
- Google Calendar/iCalendar são superfícies de sincronização e conveniência, não a autoridade de capacidade.
- A agenda pessoal do motorista não é publicada.
- Passageiros não recebem a lista, e-mail, telefone ou identidade de outros passageiros.
- Uma vaga é consumida somente nos segmentos entre o embarque e o desembarque.
- Reservas concorrentes no backend atualizam a capacidade dentro de uma transação Firestore.

## Android

A Stage47 materializa o pacote `br.com.mapeiaia.rotacerta.trips` com:

- `TripDomain.kt`: `Trip`, `TripStop`, `Booking`, estados e `SeatAvailabilityEngine`;
- `TripStore.kt`: armazenamento local isolado e configuração online;
- `TripCalendar.kt`: ICS, compartilhamento, inserção via `CalendarContract` e link da agenda pública;
- `TripRemoteApi.kt`: cliente HTTPS para publicação/sincronização;
- `TripsActivity.kt`: criação, publicação, gestão, reservas manuais e configuração;
- `TripAndroidEntryPoints.kt`: atalhos Android, Quick Settings Tile e widget.

O módulo não altera `LiveRideAccessibilityService`, `DecisionEngine`, parsers, OCR, Google route stack ou qualquer outro fonte Kotlin herdado. O CI gera hashes antes/depois e exige identidade byte por byte de todos os fontes Kotlin preexistentes.

## Vagas por trecho

Para uma rota `A → B → C → D`, um passageiro `A → B` não impede que o mesmo assento seja vendido para outro passageiro `B → D`.

Para uma solicitação entre as paradas `i` e `j`, a disponibilidade é o mínimo de:

`capacidade - ocupação(segmento k)` para todo `k` em `[i, j)`.

Estados que consomem capacidade no motor Android:

- `CONFIRMED`;
- `HELD` enquanto não expirado.

Estados `REQUESTED`, `CANCELLED` e `EXPIRED` não consomem vagas.

`FULL` global só é produzido quando todos os segmentos estão sem vaga; um único segmento cheio não fecha trechos disjuntos que ainda tenham capacidade.

## Calendário

### Motorista

O botão `Google/Agenda` usa `CalendarContract.ACTION_INSERT`. Isso deixa o usuário escolher a agenda do aparelho sem conceder ao Rota Certa leitura indiscriminada da agenda pessoal.

### Passageiro

Após a reserva a página pública oferece:

- `Adicionar ao Google Agenda`;
- download `.ics` compatível com calendários que suportam iCalendar.

O evento individual contém somente os dados necessários da própria reserva.

### Agenda pública por assinatura

O backend expõe um feed sanitizado:

`https://SEU_HOST/calendar/SEU_TOKEN_PUBLICO.ics`

O feed contém somente viagens publicadas/ativas e pode ser assinado em clientes compatíveis, inclusive Google Calendar pela opção de adicionar calendário por URL. Não contém contatos ou reservas de passageiros.

## Página pública

`trip-platform/public/` implementa a experiência sem instalação do APK:

1. passageiro abre o link da viagem;
2. vê data, rota e paradas publicadas;
3. escolhe embarque e desembarque;
4. vê a capacidade real daquele trecho;
5. informa nome, contato opcional e lugares;
6. backend confirma transacionalmente ou rejeita por falta de vaga;
7. passageiro recebe ID e token privado de cancelamento;
8. pode adicionar a reserva ao Google Agenda ou baixar ICS.

A página aplica Content Security Policy e não acessa Firestore diretamente.

## Backend

`trip-platform/` usa Firebase Hosting, Cloud Functions e Firestore.

### Segurança

- regras Firestore: deny-by-default para clientes;
- mutações do motorista exigem `ROTA_CERTA_DRIVER_TOKEN` em Secret Manager;
- comparação do token do motorista em tempo constante;
- token público da agenda definido por `ROTA_CERTA_PUBLIC_CALENDAR_TOKEN`;
- identidade/contato do passageiro ficam na subcoleção privada de bookings;
- objeto público da viagem é montado por whitelist;
- confirmação da reserva e `segmentLoads` ocorrem na mesma transação;
- limite simples de tentativas por origem/minuto no endpoint de reservas.

## Ativação externa

A implementação e o APK não precisam de Firebase para operar localmente. Para publicação pública real é necessário um projeto Firebase/Google Cloud controlado pelo proprietário do Rota Certa. Essas credenciais não podem ser inventadas nem commitadas.

No ambiente administrativo com Firebase CLI autenticado:

```bash
cd trip-platform
firebase use SEU_PROJECT_ID
firebase functions:secrets:set ROTA_CERTA_DRIVER_TOKEN
firebase functions:secrets:set ROTA_CERTA_PUBLIC_CALENDAR_TOKEN
firebase deploy
```

O primeiro segredo deve ser uma credencial privada forte do motorista. O segundo deve ser um token aleatório longo que fará parte do endereço compartilhável do calendário.

Depois do deploy, na tela `Agenda de Viagens > Integração online` do APK, configurar:

- API HTTPS: URL do Hosting do projeto;
- URL pública: a mesma origem HTTPS do Hosting;
- chave privada do motorista: exatamente o valor de `ROTA_CERTA_DRIVER_TOKEN`;
- token público da agenda: exatamente o valor de `ROTA_CERTA_PUBLIC_CALENDAR_TOKEN`.

Nunca publicar a chave privada do motorista em WhatsApp, página web, agenda ou captura de tela.

## Atalhos Android

A Stage47 oferece três superfícies independentes:

1. atalho dinâmico/fixável `Nova viagem`;
2. Quick Settings Tile `Agenda de Viagens`;
3. widget `Próxima viagem` com situação de vagas e ação para nova viagem.

O esquema interno `rotacerta://trips` abre a Activity dedicada e permite integração posterior com outras superfícies internas sem acoplar o módulo ao FAROL.

## Validação automática

O workflow `stage47-trip-calendar-booking-0.1.227.yml`:

- reconstrói Stage46 R8 sobre a fonte canônica protegida;
- materializa Stage47 como 0.1.227 / 5520;
- compara todos os fontes Kotlin herdados antes/depois;
- exige 1.271 testes Kotlin (1.262 herdados + 9 Stage47);
- executa testes de contrato Node;
- valida sintaxe JS e resolução das dependências Firebase;
- executa `testDebugUnitTest`, `lintDebug` e `assembleDebug`;
- valida package/version/signatura/ZIP/DEX do APK;
- verifica marcadores Stage47 e marcadores herdados do FAROL;
- produz APK, hashes e pacote do backend como artifacts.

## Fora do escopo automático da Stage47

O CI não cria em nome do usuário:

- projeto Google Cloud/Firebase;
- domínio público;
- credenciais/segredos;
- deploy de produção;
- conta Google OAuth do motorista.

Isso é intencional: criar ou inventar essas identidades externas sem autorização real seria inseguro. O código está preparado para ativação quando os recursos forem fornecidos.

## Teste físico após CI verde

No Samsung físico:

1. instalar o APK Stage47;
2. confirmar que o FAROL mantém o comportamento da Stage46;
3. abrir `Agenda de Viagens` pelo atalho;
4. criar rota com três ou mais paradas;
5. publicar localmente;
6. criar reservas sobrepostas e disjuntas e conferir capacidade por trecho;
7. adicionar evento ao Google Agenda;
8. compartilhar ICS;
9. fixar atalho `Nova viagem`;
10. adicionar widget e Quick Settings Tile;
11. após Firebase configurado, publicar online em um ambiente de teste;
12. reservar de outro telefone/navegador;
13. confirmar que nenhum dado de outro passageiro aparece;
14. assinar o feed público `.ics` e confirmar que só viagens são exibidas.
