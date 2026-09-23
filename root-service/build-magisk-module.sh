#!/usr/bin/env sh

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PROJECT_DIR="$(dirname -- "$SCRIPT_DIR")"
STAGING_DIR="${TMPDIR:-/tmp}/tone3000-magisk-module.$$"
OUTPUT_FILE="$PROJECT_DIR/Tone3000-Root-Companion-v0.1.0.zip"

cleanup() {
    rm -rf "$STAGING_DIR"
}

trap cleanup EXIT INT TERM
mkdir -p "$STAGING_DIR"

cp "$SCRIPT_DIR/magisk/module.prop" "$STAGING_DIR/module.prop"
cp "$SCRIPT_DIR/magisk/service.sh" "$STAGING_DIR/service.sh"
cp "$SCRIPT_DIR/magisk/customize.sh" "$STAGING_DIR/customize.sh"
cp "$SCRIPT_DIR/magisk/uninstall.sh" "$STAGING_DIR/uninstall.sh"
cp "$SCRIPT_DIR/tone3000-root-service.sh" "$STAGING_DIR/tone3000-root-service.sh"

chmod 0755 \
    "$STAGING_DIR/service.sh" \
    "$STAGING_DIR/customize.sh" \
    "$STAGING_DIR/uninstall.sh" \
    "$STAGING_DIR/tone3000-root-service.sh"

rm -f "$OUTPUT_FILE"
(
    cd "$STAGING_DIR"
    zip -q -9 "$OUTPUT_FILE" \
        module.prop \
        service.sh \
        customize.sh \
        uninstall.sh \
        tone3000-root-service.sh
)

echo "$OUTPUT_FILE"
