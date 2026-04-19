#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

echo "Launching Blueprint in a PyCharm sandbox..."
echo "JDK: $JAVA_HOME"
echo "Project that will open: $(pwd)/examples/car_dealership"

./gradlew runIde --no-daemon
