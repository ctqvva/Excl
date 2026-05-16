#!/bin/sh
set -eu

: "${EXCLAVE_APPLICATION_ID:?Set EXCLAVE_APPLICATION_ID to the Android package name you want, e.g. com.example.camera}"

EXCLAVE_APP_LABEL="${EXCLAVE_APP_LABEL:-Camera}"
BUILD_TASK="${BUILD_TASK:-:app:assembleOssRelease}"

exec ./gradlew \
  "$BUILD_TASK" \
  -PstealthApplicationId="$EXCLAVE_APPLICATION_ID" \
  -PstealthAppLabel="$EXCLAVE_APP_LABEL"
