#!/usr/bin/env sh

set -eu

SERIAL="${1:-}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ -n "$SERIAL" ]; then
    adb_target="-s $SERIAL"
else
    adb_target=""
fi

# shellcheck disable=SC2086
adb $adb_target root
# shellcheck disable=SC2086
adb $adb_target wait-for-device
# shellcheck disable=SC2086
adb $adb_target remount

# shellcheck disable=SC2086
adb $adb_target push \
    "$SCRIPT_DIR/tone3000-root-service.sh" \
    /system/bin/tone3000-root-service.sh

# shellcheck disable=SC2086
adb $adb_target push \
    "$SCRIPT_DIR/init/tone3000-root.rc" \
    /system/etc/init/tone3000-root.rc

# shellcheck disable=SC2086
adb $adb_target shell chmod 0755 /system/bin/tone3000-root-service.sh
# shellcheck disable=SC2086
adb $adb_target shell chmod 0644 /system/etc/init/tone3000-root.rc

echo "Installed. Reboot Android to let init load tone3000-root.rc."
