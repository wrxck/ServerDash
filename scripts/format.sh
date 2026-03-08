#!/usr/bin/env bash
# Auto-format Kotlin source files with ktlint
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

KTLINT_VERSION="1.5.0"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/serverdash-lint"
mkdir -p "$CACHE_DIR"

KTLINT="$CACHE_DIR/ktlint-$KTLINT_VERSION"
if [ ! -f "$KTLINT" ]; then
    echo "Downloading ktlint $KTLINT_VERSION..."
    curl -sSL "https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint" -o "$KTLINT"
    chmod +x "$KTLINT"
fi

echo "Formatting app/src/**/*.kt ..."
"$KTLINT" --format "app/src/**/*.kt" --relative
echo "Done."
