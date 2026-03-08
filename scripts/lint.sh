#!/usr/bin/env bash
# Run all lint checks locally (mirrors CI Code Quality workflow)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export JAVA_HOME

KTLINT_VERSION="1.5.0"
DETEKT_VERSION="1.23.7"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/serverdash-lint"
mkdir -p "$CACHE_DIR"

failed=0

# ── ktlint ────────────────────────────────────────────────────────
echo "=== ktlint $KTLINT_VERSION ==="
KTLINT="$CACHE_DIR/ktlint-$KTLINT_VERSION"
if [ ! -f "$KTLINT" ]; then
    curl -sSL "https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint" -o "$KTLINT"
    chmod +x "$KTLINT"
fi
if "$KTLINT" "app/src/**/*.kt" --relative; then
    echo "ktlint: PASS"
else
    echo "ktlint: FAIL (run '$0 --fix' to auto-format)"
    failed=1
fi

# ── detekt ────────────────────────────────────────────────────────
echo ""
echo "=== detekt $DETEKT_VERSION ==="
DETEKT_JAR="$CACHE_DIR/detekt-cli-$DETEKT_VERSION-all.jar"
if [ ! -f "$DETEKT_JAR" ]; then
    curl -sSL "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar" -o "$DETEKT_JAR"
fi
if java -jar "$DETEKT_JAR" \
    --input app/src \
    --config detekt.yml \
    --build-upon-default-config; then
    echo "detekt: PASS"
else
    echo "detekt: FAIL"
    failed=1
fi

# ── Android Lint ──────────────────────────────────────────────────
echo ""
echo "=== Android Lint ==="
if ./gradlew lintDebug --no-daemon -q; then
    echo "Android Lint: PASS"
else
    echo "Android Lint: FAIL (see app/build/reports/lint-results-debug.html)"
    failed=1
fi

echo ""
if [ "$failed" -eq 0 ]; then
    echo "All checks passed."
else
    echo "Some checks failed."
    exit 1
fi
