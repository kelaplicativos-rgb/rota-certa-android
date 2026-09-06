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

distribution_url=$(sed -n 's/^distributionUrl=//p' "$PROPERTIES" | tail -1)
if [ -z "$distribution_url" ]; then
  echo "Missing distributionUrl in $PROPERTIES" >&2
  exit 1
fi

distribution_name=$(basename "$distribution_url" .zip)
gradle_version=$(printf "%s" "$distribution_name" | sed 's/^gradle-\(.*\)-bin$/\1/')

gradle_user_home=${GRADLE_USER_HOME:-"$HOME/.gradle"}
dist_base="$gradle_user_home/wrapper/dists/$distribution_name"
install_dir="$dist_base/gradle-$gradle_version"
gradle_bin="$install_dir/bin/gradle"

if [ ! -x "$gradle_bin" ]; then
  mkdir -p "$dist_base"
  tmp_zip="$dist_base/$distribution_name.zip"
  echo "Downloading Gradle $gradle_version..."
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --connect-timeout 20 --retry 3 -o "$tmp_zip" "$distribution_url"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$tmp_zip" "$distribution_url"
  else
    echo "Install curl or wget to download Gradle." >&2
    exit 1
  fi

  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$tmp_zip" -d "$dist_base"
  else
    echo "Install unzip to extract Gradle." >&2
    exit 1
  fi
fi

exec "$gradle_bin" "$@"
