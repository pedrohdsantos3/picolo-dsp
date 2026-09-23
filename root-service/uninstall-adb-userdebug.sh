#!/usr/bin/env sh

set -eu

SERIAL="${1:-}"

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
adb $adb_target shell stop tone3000_root 2>/dev/null || true
# shellcheck disable=SC2086
adb $adb_target shell rm -f \
    /system/bin/tone3000-root-service.sh \
    /system/etc/init/tone3000-root.rc
# shellcheck disable=SC2086
adb $adb_target shell settings put secure usb_audio_automatic_routing_disabled 0

echo "Removed. Reboot Android to complete cleanup."
