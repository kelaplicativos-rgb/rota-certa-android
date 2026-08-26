# Agenda 0.1.296 — Link público de reservas

Checkpoint de trabalho da Agenda. Esta implementação continua a infraestrutura Stage47 já existente; não cria um segundo motor de vagas.

## Autoridade

- capacidade física: Rota Certa;
- ocupação: Booking + SeatAvailabilityEngine/SegmentLoad;
- reserva pública: nova porta de entrada `BookingSource.ROTA_CERTA`;
- BlaBlaCar: sincronização externa posterior pelo sincronizador de estado desejado já existente;
- publicação pública: opt-in por viagem, desligada por padrão.

## Backend existente reutilizado

- Firebase Hosting;
- Cloud Functions HTTPS;
- Firestore deny-by-default;
- identidade por motorista e token público;
- reconciliação transacional de capacidade por segmento;
- cancelamento transacional.

## Lacunas tratadas neste checkpoint

- idempotência por intenção de reserva;
- proteção contra duplo toque/retry;
- WhatsApp brasileiro obrigatório e normalizado;
- viagem passada/privada recusada no backend;
- seleção de lugares limitada pela disponibilidade do trecho;
- revisão explícita antes de confirmar;
- importação automática servidor → app ao abrir a Agenda;
- tentativa de enfileirar a sincronização BlaBlaCar usando o motor de vagas existente e somente quando a associação local for inequívoca;
- compartilhamento simples do link da viagem.

## Limite de validação

CI pode provar código, contratos, testes, lint e APK. Uma URL pública real exige um projeto Firebase/Google Cloud e credenciais/segredos externos reais. O deploy de produção e o teste físico pelo WhatsApp/Chrome/Samsung não são declarados concluídos sem essa evidência.
