# Rota Certa — contrato global de datas — 0.1.340

## Fonte de verdade

Este contrato existe para impedir novas entradas de data isoladas e divergentes entre telas.

### Android

Regra e modelo:
- `app/src/main/java/br/com/mapeiaia/rotacerta/date/RotaCertaDateContract.kt`

Componente visual reutilizável:
- `app/src/main/java/br/com/mapeiaia/rotacerta/ui/RotaCertaDatePicker.kt`

Consumidores atuais:
- Consulta Pública Android (`BlaBlaPublicSearchUi.kt`)
- Criação manual de viagem (`TripsActivity.kt`)

### Agenda Pública Web

Contrato equivalente:
- `trip-platform/public/date-selection.js`

Consumidor atual:
- Busca da Agenda Pública (`trip-platform/public/app.js`)

## Regras globais

- armazenamento técnico por dia: `YYYY-MM-DD`;
- o usuário não deve precisar digitar a data técnica;
- modos padronizados: `SINGLE`, `MULTIPLE`, `RANGE`, `MONTH`;
- múltiplas datas podem ser desmarcadas tocando novamente;
- intervalo inclui início e fim;
- seleção de mês respeita uma data mínima quando fornecida;
- telas que não autorizam passado devem fornecer hoje como data mínima;
- ausência de data só significa “começar hoje” nas telas cujo contrato de negócio prevê esse comportamento;
- data e horário são conceitos separados: telas que precisam de horário reutilizam o seletor global de data e tratam o horário separadamente.

## Regra para novas telas

Uma nova tela que peça data deve consumir este contrato em vez de criar:
- campo textual de `dd/MM/aaaa`;
- campo textual de `YYYY-MM-DD`;
- calendário paralelo com regras próprias.

A apresentação pode ser nativa por plataforma, mas seleção, modos, normalização e formato técnico devem permanecer equivalentes.
