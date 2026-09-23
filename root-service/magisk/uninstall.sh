#!/system/bin/sh

MODDIR="${0%/*}"

if [ -x "$MODDIR/tone3000-root-service.sh" ]; then
    "$MODDIR/tone3000-root-service.sh" stop
fi

settings put secure usb_audio_automatic_routing_disabled 0 2>/dev/null
