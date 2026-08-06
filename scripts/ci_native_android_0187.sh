#!/usr/bin/env bash
set -Eeuo pipefail

PROTECTED_SOURCE_SHA="${1:?protected source SHA required}"
EXPECTED_PATCH_SHA="${2:?patch SHA required}"
PATCH_REPOSITORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_ROOT="${RUNNER_TEMP:-/tmp}/rota-certa-native-${GITHUB_RUN_ID:-manual}-${GITHUB_RUN_ATTEMPT:-1}"
SOURCE_REPOSITORY="$WORK_ROOT/source"
LOG_FILE="$WORK_ROOT/build-0.1.187-native.log"
CURRENT_STAGE="bootstrap"

mkdir -p "$WORK_ROOT"
touch "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1

summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"
  fi
}

retry() {
  local attempts="$1"
  local delay="$2"
  shift 2
  local n=1
  until "$@"; do
    local status=$?
    if (( n >= attempts )); then
      echo "command_failed_after_${attempts}_attempts status=$status command=$*" >&2
      return "$status"
    fi
    echo "retry=$n/$attempts status=$status command=$*" >&2
    sleep "$delay"
    n=$((n + 1))
  done
}

comment_pr() {
  local body="$1"
  if [[ -n "${PR_NUMBER:-}" && -n "${GH_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1; then
    gh api "repos/${GITHUB_REPOSITORY}/issues/${PR_NUMBER}/comments" --method POST -f body="$body" >/dev/null 2>&1 || true
  fi
}

on_error() {
  local line="$1"
  local status="$2"
  set +e
  echo "native_ci_failure stage=$CURRENT_STAGE line=$line status=$status" >&2
  summary "## Rota Certa 0.1.187 — falha na validação nativa"
  summary "- Etapa: \`$CURRENT_STAGE\`"
  summary "- Linha: \`$line\`"
  summary "- Código: \`$status\`"
  summary "- Commit: \`$EXPECTED_PATCH_SHA\`"
  tail -n 80 "$LOG_FILE" > "$WORK_ROOT/failure-tail.txt" 2>/dev/null || true
  comment_pr "### Validação nativa 0.1.187 falhou\n\n- Etapa: \`${CURRENT_STAGE}\`\n- Commit: \`${EXPECTED_PATCH_SHA}\`\n- Run: \`${GITHUB_RUN_ID:-desconhecido}\`\n\nO código de saída foi \`${status}\`. Consulte o log da execução; o link permanente não foi alterado."
  exit "$status"
}
trap 'on_error "$LINENO" "$?"' ERR

CURRENT_STAGE="verify_patch_checkout"
test "$(git -C "$PATCH_REPOSITORY" rev-parse HEAD)" = "$EXPECTED_PATCH_SHA"
git -C "$PATCH_REPOSITORY" diff --check
bash -n "$PATCH_REPOSITORY/scripts/build_rota_certa_0187.sh"

CURRENT_STAGE="toolchain_preflight"
command -v git
command -v curl
command -v unzip
command -v zip
command -v python3
command -v gh
python3 - <<'PY'
import sys
assert sys.version_info >= (3, 10), sys.version
print(sys.version)
PY

select_java17() {
  local candidates=(
    "${JAVA_HOME_17_X64:-}"
    "/usr/lib/jvm/temurin-17-jdk-amd64"
    "/usr/lib/jvm/java-17-openjdk-amd64"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
  java -version 2>&1 | tee "$WORK_ROOT/java-version.txt"
  java -version 2>&1 | grep -Eq 'version "17\.|openjdk version "17\.'
}
select_java17

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
test -d "$ANDROID_SDK_ROOT"
SDKMANAGER=""
for candidate in \
  "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
  "$ANDROID_SDK_ROOT/cmdline-tools/bin/sdkmanager" \
  "$(command -v sdkmanager 2>/dev/null || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    SDKMANAGER="$candidate"
    break
  fi
done
test -n "$SDKMANAGER"
export PATH="$(dirname "$SDKMANAGER"):$ANDROID_SDK_ROOT/platform-tools:$PATH"
"$SDKMANAGER" --version

install_sdk_package() {
  local package="$1"
  sdk_install_once() {
    local requested="$1"
    set +o pipefail
    yes | "$SDKMANAGER" "$requested"
    local status="${PIPESTATUS[1]}"
    set -o pipefail
    return "$status"
  }
  retry 5 20 sdk_install_once "$package"
}
if [[ ! -d "$ANDROID_SDK_ROOT/platforms/android-35" ]]; then
  CURRENT_STAGE="install_android_platform_35"
  install_sdk_package "platforms;android-35"
fi
if [[ ! -d "$ANDROID_SDK_ROOT/build-tools/35.0.0" ]]; then
  CURRENT_STAGE="install_android_build_tools_35"
  install_sdk_package "build-tools;35.0.0"
fi
if [[ ! -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
  CURRENT_STAGE="install_android_platform_tools"
  install_sdk_package "platform-tools"
fi

CURRENT_STAGE="checkout_protected_source"
rm -rf "$SOURCE_REPOSITORY"
git init "$SOURCE_REPOSITORY"
git -C "$SOURCE_REPOSITORY" remote add origin "https://github.com/${GITHUB_REPOSITORY}.git"
retry 5 20 git -C "$SOURCE_REPOSITORY" -c http.version=HTTP/1.1 fetch --force --no-tags origin "$PROTECTED_SOURCE_SHA"
git -C "$SOURCE_REPOSITORY" checkout --detach FETCH_HEAD
test "$(git -C "$SOURCE_REPOSITORY" rev-parse HEAD)" = "$PROTECTED_SOURCE_SHA"
git -C "$SOURCE_REPOSITORY" diff --check

CURRENT_STAGE="gradle_bootstrap"
export GRADLE_USER_HOME="$WORK_ROOT/gradle-home"
mkdir -p "$GRADLE_USER_HOME"
sed -i 's/^networkTimeout=.*/networkTimeout=120000/' "$SOURCE_REPOSITORY/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$SOURCE_REPOSITORY/gradlew"
cd "$SOURCE_REPOSITORY"
retry 5 20 ./gradlew --version --no-daemon

CURRENT_STAGE="materialize_test_lint_assemble"
set -o pipefail
bash "$PATCH_REPOSITORY/scripts/build_rota_certa_0187.sh" "$PATCH_REPOSITORY" 2>&1 | tee -a "$LOG_FILE"

CURRENT_STAGE="validate_output_contract"
OUTPUT_DIR="$SOURCE_REPOSITORY/artifact-0.1.187"
APK="$OUTPUT_DIR/rota-certa-0.1.187-farol-runtime-validado.apk"
test -s "$APK"
test -s "$OUTPUT_DIR/sha256.txt"
test -s "$OUTPUT_DIR/test-count.txt"
test -s "$OUTPUT_DIR/validation.txt"
unzip -t "$APK" >/dev/null
EXPECTED_SHA="$(awk 'NR==1 {print $1}' "$OUTPUT_DIR/sha256.txt")"
ACTUAL_SHA="$(sha256sum "$APK" | awk '{print $1}')"
test "$EXPECTED_SHA" = "$ACTUAL_SHA"
grep -Fq 'tests=' "$OUTPUT_DIR/test-count.txt"
grep -Fq 'failures=0' "$OUTPUT_DIR/test-count.txt"

BUILD_TOOLS_DIR="$(find "$ANDROID_SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
test -x "$BUILD_TOOLS_DIR/aapt"
test -x "$BUILD_TOOLS_DIR/apksigner"
"$BUILD_TOOLS_DIR/aapt" dump badging "$APK" | tee "$WORK_ROOT/badging-independent.txt"
grep -Fq "package: name='br.com.mapeiaia.rotacerta' versionCode='5471' versionName='0.1.187'" "$WORK_ROOT/badging-independent.txt"
"$BUILD_TOOLS_DIR/apksigner" verify --verbose --print-certs "$APK" | tee "$WORK_ROOT/signature-independent.txt"
grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$WORK_ROOT/signature-independent.txt"

CURRENT_STAGE="package_validation_bundle"
cp "$WORK_ROOT/badging-independent.txt" "$OUTPUT_DIR/badging-independent.txt"
cp "$WORK_ROOT/signature-independent.txt" "$OUTPUT_DIR/signature-independent.txt"
cp "$WORK_ROOT/java-version.txt" "$OUTPUT_DIR/java-version.txt"
cp "$LOG_FILE" "$OUTPUT_DIR/build-native.log"
VALIDATION_ZIP="$WORK_ROOT/rota-certa-0.1.187-fase4-validation.zip"
(
  cd "$OUTPUT_DIR"
  zip -q -r "$VALIDATION_ZIP" .
)
test -s "$VALIDATION_ZIP"

CURRENT_STAGE="publish_permanent_release"
test -n "${GH_TOKEN:-}"
cp "$APK" "$WORK_ROOT/rota-certa-latest.apk"
cp "$APK" "$WORK_ROOT/rota-certa-0.1.187-fase4.apk"
cat > "$WORK_ROOT/release-notes.md" <<NOTES
Rota Certa 0.1.187 (5471) — fase 4 validada

- Resultado de rota vinculado a pacote, sessão, janela, gerações, tela e destino.
- Resultado atrasado descartado mesmo se a chamada de rede ignorar o cancelamento.
- Cancelamento centralizado de rota, análise, OCR e confirmação parcial.
- Pipeline nativo sem Marketplace Actions no caminho crítico.
- Testes unitários e de contrato, Android Lint e clean assembleDebug aprovados.
- Pacote: br.com.mapeiaia.rotacerta.
- SHA-256: ${ACTUAL_SHA}.
- Validação física no Samsung SM-S911B/Android 16 ainda necessária.
NOTES

if gh release view latest --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  retry 5 15 gh release edit latest --repo "$GITHUB_REPOSITORY" --title "Rota Certa — versão mais recente (0.1.187)" --notes-file "$WORK_ROOT/release-notes.md"
else
  retry 5 15 gh release create latest --repo "$GITHUB_REPOSITORY" --target "$EXPECTED_PATCH_SHA" --title "Rota Certa — versão mais recente (0.1.187)" --notes-file "$WORK_ROOT/release-notes.md"
fi
retry 5 15 gh release upload latest \
  "$WORK_ROOT/rota-certa-latest.apk" \
  "$WORK_ROOT/rota-certa-0.1.187-fase4.apk" \
  "$VALIDATION_ZIP" \
  --repo "$GITHUB_REPOSITORY" --clobber

CURRENT_STAGE="verify_permanent_download"
PERMANENT_URL="https://github.com/${GITHUB_REPOSITORY}/releases/download/latest/rota-certa-latest.apk"
retry 5 20 curl --fail --location --retry 3 --retry-all-errors --connect-timeout 30 --max-time 900 --output "$WORK_ROOT/downloaded-latest.apk" "$PERMANENT_URL"
test "$(sha256sum "$WORK_ROOT/downloaded-latest.apk" | awk '{print $1}')" = "$ACTUAL_SHA"

CURRENT_STAGE="completed"
summary "## Rota Certa 0.1.187 — validação nativa aprovada"
summary "- Commit: \`$EXPECTED_PATCH_SHA\`"
summary "- Pacote: \`br.com.mapeiaia.rotacerta\`"
summary "- Versão: \`0.1.187 (5471)\`"
summary "- SHA-256: \`$ACTUAL_SHA\`"
summary "- Link permanente verificado: $PERMANENT_URL"
comment_pr "### Validação nativa 0.1.187 aprovada\n\n- Commit: \`${EXPECTED_PATCH_SHA}\`\n- Pacote: \`br.com.mapeiaia.rotacerta\`\n- Versão: \`0.1.187 (5471)\`\n- SHA-256: \`${ACTUAL_SHA}\`\n- Testes, Lint, build, assinatura e download permanente foram verificados.\n- A validação física no Samsung SM-S911B/Android 16 continua pendente."

echo "native_ci_success=true"
echo "sha256=$ACTUAL_SHA"
echo "permanent_url=$PERMANENT_URL"
