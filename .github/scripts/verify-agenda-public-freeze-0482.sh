#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

MANIFEST=".github/validation/agenda-public-0482-freeze.manifest"
CONTRACT=".github/validation/agenda-date-year-0482-contract.txt"
CANDIDATE=".github/validation/agenda-public-0484-segment-candidate.contract"
APPROVED_SHA="25f056a1b9ba3dc63249a2df3e62a644e05f2923"

fail() {
  printf 'AGENDA_PUBLIC_FREEZE=FAIL: %s\n' "$*" >&2
  exit 1
}

test -f "$MANIFEST" || fail "baseline manifest missing"
test -f "$CONTRACT" || fail "physical approval contract missing"
grep -Fqx "approvedPublicSourceSha=$APPROVED_SHA" "$MANIFEST" || fail "approved source SHA changed"
grep -Fqx 'approvedVersion=0.1.482/5775' "$MANIFEST" || fail "approved version changed"
grep -Fqx 'anonymousWindow=APPROVED_BY_USER' "$MANIFEST" || fail "anonymous-window approval missing"
grep -Fqx 'publicSurface=READ_ONLY' "$MANIFEST" || fail "public read-only baseline changed"
grep -Fqx 'changePolicy=EXPLICIT_USER_REQUEST_REQUIRED' "$MANIFEST" || fail "explicit-request policy missing"
grep -Fqx "physicalApprovedSha=$APPROVED_SHA" "$CONTRACT" || fail "physical approval SHA mismatch"
grep -Fqx 'physicalApprovedVersion=0.1.482/5775' "$CONTRACT" || fail "physical approval version mismatch"
grep -Fqx 'physicalStatus=APPROVED_BY_USER' "$CONTRACT" || fail "physical approval status missing"
grep -Fqx 'anonymousWindow=APPROVED_BY_USER' "$CONTRACT" || fail "anonymous validation missing"
grep -Fqx 'publicSurface=READ_ONLY' "$CONTRACT" || fail "read-only contract missing"
grep -Fqx 'publicFreeze=ENFORCED' "$CONTRACT" || fail "freeze contract missing"

candidate_mode=false
if test -f "$CANDIDATE"; then
  grep -Fqx 'candidateId=agenda-public-segment-availability-0484' "$CANDIDATE" || fail "unknown candidate"
  grep -Fqx "candidateBaseApprovedSha=$APPROVED_SHA" "$CANDIDATE" || fail "candidate baseline mismatch"
  grep -Fqx 'candidateBaseApprovedVersion=0.1.482/5775' "$CANDIDATE" || fail "candidate baseline version mismatch"
  grep -Fqx 'candidatePublicBundleVersion=0.1.484' "$CANDIDATE" || fail "candidate bundle version mismatch"
  grep -Fqx 'candidatePhysicalStatus=PENDING' "$CANDIDATE" || fail "candidate must remain physically pending"
  grep -Fqx 'authorization=EXPLICIT_USER_REQUEST' "$CANDIDATE" || fail "candidate authorization missing"
  candidate_mode=true
fi

expected_paths="$(mktemp)"
actual_paths="$(mktemp)"
changed_public="$(mktemp)"
trap 'rm -f "$expected_paths" "$actual_paths" "$changed_public"' EXIT

awk '$1 == "public-file" { print $4 }' "$MANIFEST" | LC_ALL=C sort > "$expected_paths"
git ls-files trip-platform/public | LC_ALL=C sort > "$actual_paths"
diff -u "$expected_paths" "$actual_paths" || fail "public file set changed (addition/removal/rename)"

candidate_blob() {
  local path="$1"
  if ! $candidate_mode; then return 0; fi
  awk -v target="$path" '$1 == "candidate-public-file" && $4 == target { print $3 }' "$CANDIDATE"
}

while read -r kind mode expected_blob path; do
  case "$kind" in
    public-file|config-file) ;;
    *) continue ;;
  esac
  test -f "$path" || fail "$path missing"
  actual_mode="$(git ls-files -s -- "$path" | awk 'NR == 1 { print $1 }')"
  actual_blob="$(git hash-object "$path")"
  candidate_expected="$(candidate_blob "$path" || true)"
  effective_expected="${candidate_expected:-$expected_blob}"
  test "$actual_mode" = "$mode" || fail "$path mode changed: $actual_mode != $mode"
  test "$actual_blob" = "$effective_expected" || fail "$path content changed outside approved/candidate contract"
done < "$MANIFEST"

if $candidate_mode; then
  git diff --name-only "$APPROVED_SHA"...HEAD -- trip-platform/public trip-platform/firebase.json trip-platform/firestore.rules | LC_ALL=C sort > "$changed_public"
  while IFS= read -r path; do
    test -z "$path" && continue
    case "$path" in
      trip-platform/public/app.js|trip-platform/public/index.html) ;;
      *) fail "candidate changed protected public/config path: $path" ;;
    esac
  done < "$changed_public"
  grep -Fxq 'trip-platform/public/app.js' "$changed_public" || fail "candidate app.js change missing"
  grep -Fxq 'trip-platform/public/index.html' "$changed_public" || fail "candidate index.html change missing"
fi

while IFS= read -r js; do
  node --check "$js"
done < <(git ls-files 'trip-platform/public/*.js' | LC_ALL=C sort)

if $candidate_mode; then
  grep -Fq 'app.js?v=0.1.484' trip-platform/public/index.html || fail "candidate public bundle version changed"
  grep -Fq 'Vagas por trecho' trip-platform/public/app.js || fail "candidate segment availability surface missing"
else
  grep -Fq 'app.js?v=0.1.482' trip-platform/public/index.html || fail "public bundle version changed"
fi

! grep -Eiq 'admin-0417\.js|Minha Área|Administração da Agenda|Administrar esta viagem|accessGate|privateAuth|passengerPortal' trip-platform/public/index.html ||
  fail "administrative surface was wired into public HTML"
! grep -Eiq '/v1/admin/|passengerSession|privateAuth|public-visibility|Administrar esta viagem|startBooking' trip-platform/public/app.js ||
  fail "write/admin capability was wired into public browser bundle"
grep -Fq '"public": "public"' trip-platform/firebase.json || fail "Hosting public root changed"
grep -Fq '"source": "/v1/**"' trip-platform/firebase.json || fail "public API rewrite missing"
grep -Fq '"functionId": "tripApi"' trip-platform/firebase.json || fail "public API target changed"
grep -Fq 'allow read, write: if false;' trip-platform/firestore.rules || fail "Firestore browser deny-all changed"

if $candidate_mode; then
  printf 'AGENDA_PUBLIC_FREEZE_0484=CANDIDATE_PASS base_approved_sha=%s physical_status=PENDING\n' "$APPROVED_SHA"
else
  printf 'AGENDA_PUBLIC_FREEZE_0482=PASS approved_sha=%s\n' "$APPROVED_SHA"
fi
