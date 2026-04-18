#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export BLUEPRINT_LIVE_AI_SMOKE=1

log="$(mktemp -t blueprint-live-ai.XXXXXX.log)"

echo "Running live AI smoke test..."
echo "This checks: .env key -> OpenAI -> UML -> plan -> execution -> review"

if ./gradlew --no-daemon --console=plain --quiet \
    test --tests com.blueprint.service.BlueprintLiveAiSmokeTest >"$log" 2>&1; then
    rm -f "$log"
    echo "PASS live AI e2e"
else
    echo "FAIL live AI e2e"
    echo
    echo "Last log lines:"
    tail -80 "$log"
    echo
    echo "Full log: $log"
    exit 1
fi
