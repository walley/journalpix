#!/usr/bin/env bash
# Generated Gradle wrapper bootstrapper: minimal wrapper script for CI when gradle is not installed locally
# This is a simplified shell wrapper that downloads a specified Gradle distribution and runs it.
# NOTE: Use only as a temporary measure. Prefer committing the official gradle wrapper files.

set -euo pipefail

GRADLE_VERSION="8.4"
GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
CACHE_DIR=".gradle-wrapper-cache"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}" 

if [ -x "./gradlew" ]; then
  exec ./gradlew "$@"
fi

if [ -d "${GRADLE_HOME}" ]; then
  echo "Using cached Gradle at ${GRADLE_HOME}"
else
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  mkdir -p "${CACHE_DIR}"
  curl -sSLo "${CACHE_DIR}/gradle.zip" "${GRADLE_DIST_URL}"
  unzip -q "${CACHE_DIR}/gradle.zip" -d "${CACHE_DIR}"
  rm "${CACHE_DIR}/gradle.zip"
fi

# Execute the gradle wrapper from the downloaded distribution
"${GRADLE_HOME}/bin/gradle" "$@"
