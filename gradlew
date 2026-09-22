#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.9
DIST="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin.zip"
DIR="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
if [ ! -x "$DIR/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  mkdir -p "$DIR"
  if [ ! -f "$DIST" ]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    mkdir -p "$(dirname "$DIST")"
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$DIST"
  fi
  rm -rf "$DIR/gradle-$GRADLE_VERSION"
  unzip -q "$DIST" -d "$DIR"
fi
exec "$DIR/gradle-$GRADLE_VERSION/bin/gradle" "$@"
