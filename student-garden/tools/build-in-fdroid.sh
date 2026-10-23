#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
sdk_dir=${1:?Pass the absolute Android SDK path (platform 36, build-tools 35.0.0)}
docker run --rm --user "$(id -u):$(id -g)" \
  -v "$project_dir:/project" -v "$sdk_dir:/sdk" \
  -e ANDROID_HOME=/sdk -e GRADLE_USER_HOME=/project/.gradle-cache -w /project \
  registry.gitlab.com/fdroid/fdroidserver@sha256:9cb68105642ca4e7b295f0ceab10f069f5b3247dc18fa7c36046e9d81aa469a8 \
  ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
