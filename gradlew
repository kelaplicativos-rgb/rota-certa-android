#!/usr/bin/env sh

set -e

APP_HOME=$0
while [ -h "$APP_HOME" ]; do
  ls=$(ls -ld "$APP_HOME")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    APP_HOME=$link
  else
    APP_HOME=$(dirname "$APP_HOME")/"$link"
  fi
done
APP_HOME=$(cd "$(dirname "$APP_HOME")" >/dev/null 2>&1 && pwd -P)

PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$PROPERTIES" ]; then
  echo "Missing $PROPERTIES" >&2
  exit 1
fi

read_property() {
  key=$1
  sed -n "s/^\${key}=//p" "$PROPERTIES" | tail -1
}

# gradle-wrapper.properties uses Java-properties escaping (for example https\\://).
distribution_url=$(read_property distributionUrl | sed 's/\\\\:/:/g; s/\\\\\\\\/\\\\/g')
distribution_sha256=$(read_property distributionSha256Sum)
network_timeout_ms=$(read_property networkTimeout)
validate_distribution_url=$(read_property validateDistributionUrl)

if [ -z "$distribution_url" ]; then
  echo "Missing distributionUrl in $PROPERTIES" >&2
  exit 1
fi
if [ -z "$distribution_sha256" ]; then
  echo "Missing distributionSha256Sum in $PROPERTIES" >&2
  exit 1
fi
case "$distribution_sha256" in
  *[!0-9a-fA-F]*|'')
    echo "Invalid distributionSha256Sum in $PROPERTIES" >&2
    exit 1
    ;;
esac
if [ "\${#distribution_sha256}" -ne 64 ]; then
  echo "Invalid distributionSha256Sum length in $PROPERTIES" >&2
  exit 1
fi

case "$network_timeout_ms" in
  ''|*[!0-9]*) network_timeout_ms=10000 ;;
esac
network_timeout_seconds=$(( (network_timeout_ms + 999) / 1000 ))
if [ "$network_timeout_seconds" -lt 1 ]; then
  network_timeout_seconds=1
fi

case "$distribution_url" in
  https://*) ;;
  *)
    echo "distributionUrl must use HTTPS: $distribution_url" >&2
    exit 1
    ;;
esac

distribution_file=$(basename "$distribution_url")
distribution_name=\${distribution_file%.zip}
gradle_version=$(printf "%s" "$distribution_name" | sed 's/^gradle-\(.*\)-bin$/\1/')
if [ -z "$gradle_version" ] || [ "$gradle_version" = "$distribution_name" ]; then
  echo "Unsupported Gradle distribution name: $distribution_file" >&2
  exit 1
fi

gradle_user_home=${GRADLE_USER_HOME:-"$HOME/.gradle"}
dist_base="$gradle_user_home/wrapper/dists/$distribution_name"
install_dir="$dist_base/gradle-$gradle_version"
gradle_bin="$install_dir/bin/gradle"

validate_url_with_curl() {
  curl --location --fail --silent --show-error --head \
    --connect-timeout "$network_timeout_seconds" \
    --max-time "$network_timeout_seconds" \
    "$distribution_url" >/dev/null
}

validate_url_with_wget() {
  wget --spider --timeout="$network_timeout_seconds" "$distribution_url" >/dev/null 2>&1
}

if [ ! -x "$gradle_bin" ]; then
  mkdir -p "$dist_base"
  tmp_zip="$dist_base/$distribution_file"
  tmp_part="$tmp_zip.part"
  rm -f "$tmp_part"

  echo "Downloading Gradle $gradle_version from $distribution_url..."
  if command -v curl >/dev/null 2>&1; then
    if [ "$validate_distribution_url" = "true" ]; then
      validate_url_with_curl
    fi
    curl --location --fail --show-error \
      --connect-timeout "$network_timeout_seconds" \
      --retry 3 --retry-all-errors \
      -o "$tmp_part" "$distribution_url"
  elif command -v wget >/dev/null 2>&1; then
    if [ "$validate_distribution_url" = "true" ]; then
      validate_url_with_wget
    fi
    wget --timeout="$network_timeout_seconds" -O "$tmp_part" "$distribution_url"
  else
    echo "Install curl or wget to download Gradle." >&2
    exit 1
  fi

  if ! command -v sha256sum >/dev/null 2>&1; then
    echo "Install sha256sum to verify the Gradle distribution." >&2
    rm -f "$tmp_part"
    exit 1
  fi
  actual_sha256=$(sha256sum "$tmp_part" | awk '{print $1}')
  if [ "$actual_sha256" != "$distribution_sha256" ]; then
    echo "Gradle distribution SHA-256 mismatch." >&2
    echo "Expected: $distribution_sha256" >&2
    echo "Actual:   $actual_sha256" >&2
    rm -f "$tmp_part"
    exit 1
  fi
  mv "$tmp_part" "$tmp_zip"
  echo "Verified Gradle distribution SHA-256: $actual_sha256"

  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$tmp_zip" -d "$dist_base"
  else
    echo "Install unzip to extract Gradle." >&2
    exit 1
  fi
fi

exec "$gradle_bin" "$@"
