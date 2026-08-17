#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PYTHONPATH="$ROOT/compiler:$ROOT/mcp"
cd "$ROOT/logic" && ./gradlew test --no-daemon
cd "$ROOT" && python3 -m pytest compiler/tests mcp/tests -q
echo "PrintCore tests passed."
