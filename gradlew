#!/bin/sh
# Gradle wrapper script — invokes the locally cached Gradle 8.9 distribution.
# If GRADLE_HOME is set and points to a valid Gradle, it will be used.
# Falls back to the user's cached Gradle wrapper distribution.

set -e

find_cached_gradle() {
  local cached
  cached=$(find "$HOME/.gradle/wrapper/dists" -name "gradle" -path "*/bin/gradle" -type f 2>/dev/null | sort -V | tail -1)
  echo "$cached"
}

if [ -n "$GRADLE_HOME" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  GRADLE_CMD="$GRADLE_HOME/bin/gradle"
else
  GRADLE_CMD=$(find_cached_gradle)
fi

if [ -z "$GRADLE_CMD" ]; then
  echo "ERROR: Gradle not found. Install via: brew install gradle" >&2
  exit 1
fi

exec "$GRADLE_CMD" -p "$(dirname "$0")" "$@"
