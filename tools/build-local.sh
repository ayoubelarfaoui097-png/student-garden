#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/.local/share/student-garden-tools/jdk-17.0.20.1+1" ]; then
  export JAVA_HOME="$HOME/.local/share/student-garden-tools/jdk-17.0.20.1+1"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [ -d "$project_dir/.gradle-build-cache" ]; then
  default_cache="$project_dir/.gradle-build-cache"
else
  default_cache="$project_dir/.gradle-cache"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$default_cache}"
cd "$project_dir"
exec ./gradlew "$@"
