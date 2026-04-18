#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

log="${BLUEPRINT_TRIAL_LOG:-$(pwd)/build/blueprint-live-product.log}"
pidfile="${BLUEPRINT_TRIAL_PIDFILE:-$(pwd)/build/blueprint-live-product.pid}"
sandbox_config="$(pwd)/build/idea-sandbox/config"

mkdir -p "$(dirname "$log")"

existing_sandbox_pid() {
    pgrep -f "idea.config.path=$sandbox_config" | head -1 || true
}

if [[ "${1:-}" != "--skip-smoke" ]]; then
    ./scripts/test-live-ai.sh
fi

running_pid="$(existing_sandbox_pid)"
if [[ -n "$running_pid" ]]; then
    echo "Blueprint sandbox is already running."
    echo "PID: $running_pid"
    echo "Open View -> Tool Windows -> Blueprint."
    exit 0
fi

echo "Launching Blueprint PyCharm sandbox..."
echo "Project: $(pwd)/examples/invite_project"
echo "Log: $log"

nohup ./gradlew --no-daemon --console=plain runIde >"$log" 2>&1 &
pid="$!"
echo "$pid" >"$pidfile"

sleep 5

running_pid="$(existing_sandbox_pid)"
if [[ -n "$running_pid" ]]; then
    echo "PID: $running_pid"
else
    echo "Launch command PID: $pid"
fi
echo "Open View -> Tool Windows -> Blueprint once PyCharm appears."
