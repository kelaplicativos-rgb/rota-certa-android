# BlaBlaCar capability and command matrix — Rota Certa 0.1.413

## Evidence boundary

Reference APK investigated for this command registry:

- application: BlaBlaCar
- versionName: 6.26.0
- versionCode: 340000589
- SHA-256: 2dd5d9b2cfb99f73542414568bdee8d33a32319cbfc0b160fb3f8ac081340743

This document does **not** claim that strings, classes, screens, or routes equal executable commands.
It inventories the operations discovered in the investigated APK and maps them to the Rota Certa runtime.
An operation is enabled for the OpenAI interpreter only when the Rota Certa side has a typed contract and a proven execution/readback path.

Strong identity for an existing BlaBlaCar publication remains:

`tenantId + accountId + profileUuid + tripId`

The OpenAI model never resolves or invents those identifiers.

## Coverage meanings

- **VERIFIED** — executor and readback/verification are proven in the current Rota Certa runtime.
- **IMPLEMENTED** — typed/local capability exists and is usable, while external write verification may not apply.
- **BLOCKED** — operation was discovered in the APK, but current-runtime write + readback is not proven. The interpreter cannot execute it.
- **NOT_AUTOMATABLE** — intentionally not exposed as an operational write.
- **NOT_APPLICABLE** — discovered surface is outside the driver command scope.

## Command matrix

| Action | Discovered operation | Coverage in 0.1.413 | Rota Certa authority / evidence |
|---|---|---:|---|
| CREATE_TRIPS | Publish one or multiple rides | IMPLEMENTED | AgendaBatchPublisherPlanner → Store → Activity; duplicate validation before enqueue; central sync verifies resulting publications |
| LIST_TRIPS | List canonical trips | VERIFIED | TripStore |
| READ_TRIP | Read one canonical trip | VERIFIED | TripStore + strong identity |
| REVERIFY_TRIP | Re-read one BlaBlaCar card/publication | VERIFIED | AgendaBackgroundSync0392.enqueueTripReverify0407 + command status/readback |
| CHECK_SYNC | Inspect canonical/sync state | VERIFIED | TripStore + projection integrity |
| LIST_UNRESOLVED_TRIPS | Find trips without confirmed external identity | VERIFIED | TripStore |
| LIST_FULL_TRIPS | Find full trips | VERIFIED | Canonical trip/capacity state |
| OPEN_TRIP | Open exact publication | VERIFIED | Canonical verified URL/tripId |
| SHARE_TRIP | Share passenger-facing publication URL | VERIFIED | Canonical public URL only; admin URL is never exposed |
| GET_TRIP_PRICE | Read observed price | VERIFIED | Canonical external snapshot |
| SET_TRIP_SEATS | Change published seats | VERIFIED | BlaBlaSeatBrowserController → save → SEAT_OPTIONS readback → BlaBlaPublicationSeatSyncStateStore |
| READ_BOOKINGS | Read reservations for a trip | IMPLEMENTED | Canonical bookings / TripRemoteApi |
| READ_PASSENGERS | Read passenger roster | IMPLEMENTED | Canonical bookings and collector passenger evidence |
| READ_PASSENGER | Read one passenger occurrence | IMPLEMENTED | TripStore booking identity |
| READ_PROFILE | Read authenticated driver profile | IMPLEMENTED | BlaBla collector profile snapshot |
| READ_VEHICLE | Read vehicle information | IMPLEMENTED | Authenticated profile snapshot |
| PUBLIC_SEARCH | Auditable public search by driver name, route and date/period | VERIFIED | Existing BlaBlaPublicSearchActivity + BlaBlaPublicSearchStore; only COMPLETE/validated coverage can prove absence |
| SET_TRIP_DATE | Publication edit: date | BLOCKED | APK edit flow discovered; current write/readback not proven |
| SET_TRIP_TIME | Publication edit: departure time | BLOCKED | APK itinerary/time flow discovered; current write/readback not proven |
| SET_TRIP_ORIGIN | Publication edit: departure | BLOCKED | APK departure autocomplete/edit flow discovered |
| SET_TRIP_DESTINATION | Publication edit: arrival | BLOCKED | APK arrival autocomplete/edit flow discovered |
| SET_TRIP_ROUTE | Publication edit: itinerary | BLOCKED | APK publication edit flow discovered |
| SET_TRIP_STOPOVERS | Publication edit: stopovers | BLOCKED | APK stopover flow discovered |
| SET_MEETING_POINT | Edit meeting points | BLOCKED | APK PublicationEditMeetingPoints surface discovered |
| SET_TRIP_PRICE | Edit price | BLOCKED | APK /rides/offer/edit/prices surface discovered |
| SET_TRIP_BOOST | Enable/disable Boost | BLOCKED | Boost capability discovered in ride/booking surfaces; verified write unavailable |
| SET_SMART_STOPOVERS | Smart stopovers | BLOCKED | APK /rides/offer/edit/smartstopovers discovered |
| SET_INSTANT_BOOKING | Instant Booking | BLOCKED | APK publication setting discovered |
| SET_TWO_MAX_IN_BACK | Two passengers max in rear seat | BLOCKED | APK comfort preference discovered |
| SET_WOMEN_ONLY | Women-only preference | BLOCKED | APK publication preference discovered |
| SET_TRIP_VEHICLE | Change publication vehicle | BLOCKED | APK publication vehicle selection discovered |
| SET_TRIP_COMMENT | Change publication comment | BLOCKED | APK publication comment surface discovered |
| DUPLICATE_TRIP | Duplicate an existing ride | BLOCKED | APK ride-plan duplication flow discovered |
| CREATE_RETURN_TRIP | Create return ride | BLOCKED | APK smart publication return flow discovered |
| CANCEL_TRIP | Cancel publication | BLOCKED | APK cancellation flow discovered; destructive and unverified |
| ACCEPT_BOOKING | Accept booking request | BLOCKED | Driver booking request surface discovered; external write/readback not proven |
| DECLINE_BOOKING | Decline booking request | BLOCKED | Driver request/refusal surface discovered; destructive and unverified |
| CANCEL_BOOKING | Cancel booking | BLOCKED | Booking cancellation flow discovered; destructive and unverified |
| CONTACT_PASSENGER | Open/contact passenger | BLOCKED | Contact-passenger surface discovered; exact automated transport not verified |
| READ_MESSAGES | Read message thread | BLOCKED | DOM/message collection exists, but complete authoritative thread coverage is not yet proven |
| SEND_MESSAGE | Send message | BLOCKED | Messaging input surface discovered; verified write/readback not implemented |

## Conversational read behavior in 0.1.413

Natural questions are routed to the existing canonical/read-only surfaces before any answer is rendered. Common read-only questions are interpreted locally first, so they do not depend on OpenAI availability or quota:

- trip existence, dates and departure times → LIST_TRIPS with deterministic date/time/route filtering;
- passenger roster → READ_PASSENGERS with date/time/route trip resolution and direct WhatsApp shortcuts when canonical contact data exists;
- occupancy/fullness → LIST_FULL_TRIPS using the canonical operational inventory and occupancy engine, not only the visual status label;
- public driver/route discovery → PUBLIC_SEARCH through the existing auditable public collector. Partial coverage never proves absence.

## JSON input in 0.1.413

The existing Assistant field accepts natural language, pasted JSON, fenced ```json blocks, and local .json files up to 64 KB.

JSON is parsed on-device and is not sent to OpenAI. The compatibility adapter accepts the current typed command shape plus the previous CREATE_TRIPS script shape (`dates`, `roundTrip`, `route.outbound/return`) and `CREATE_ROUND_TRIP` with root `date`, `outbound` and `return`. The latter maps to the existing verified CREATE_TRIPS executor rather than creating a parallel action.

Separate outbound/return departure times are preserved. Imported JSON cannot expand the Command Registry, enable a BLOCKED action, disable calendar validation/fail-closed policy, or bypass high-impact confirmation. `mode: "SIMULATION"` validates and prepares the plan without executing it. Requirements such as AUTO_RECONCILE, checkAllDriverProfiles, runPublicCollector or unproven schedule-conflict checks fail closed rather than being silently ignored.

## OpenAI boundary

The backend endpoint `POST /v1/assistant/interpret` uses a deterministic read-only fast path for common operational questions and the OpenAI Responses API with strict JSON Schema Structured Outputs for requests that need model interpretation. HTTP 429 from OpenAI is retried once with bounded backoff; if it persists, it is mapped to `openai_rate_limited` without exposing the upstream body.

The request contains:

- natural-language instruction;
- timezone and locale;
- a runtime-generated **allowlist** of currently executable action names.

It deliberately does not send:

- BlaBlaCar cookies;
- browser sessions;
- driver token;
- OpenAI API key;
- passwords;
- raw WebView HTML;
- arbitrary JavaScript;
- arbitrary executor names.

The model returns intent parameters only. Rota Certa then independently:

1. validates temporal data;
2. resolves canonical entities;
3. requires unique strong identity where applicable;
4. rejects ambiguous or stale plans;
5. applies risk policy;
6. routes to the existing authoritative executor;
7. rereads/verifies external or canonical state;
8. records idempotency/audit evidence.

A model output cannot create a new action name because the schema action enum is built from the runtime Command Registry allowlist.

## Execution policy

Read-only and low-risk verified commands may execute after deterministic validation.

High-impact and destructive commands require explicit confirmation before execution. In 0.1.413, CREATE_TRIPS and SET_TRIP_SEATS are treated as high-impact. Static-only APK capabilities remain blocked regardless of what the model requests.

## Invalid and ambiguous requests

Examples that must fail closed:

- `dia 32`;
- `31/02`;
- `29/02/2026`;
- `25:00`;
- weekday/date conflicts;
- more than one matching trip;
- more than one matching passenger;
- missing required tripId;
- canonical revision changed between planning and confirmation;
- action omitted from the runtime allowlist;
- prompt injection asking the model to invent or run a non-registered action.

## Remaining external dependency

The Android APK never contains an OpenAI key. The backend requires `OPENAI_API_KEY` to be configured server-side before live natural-language interpretation can succeed. Without that secret, the endpoint fails closed and no operational action is executed.
