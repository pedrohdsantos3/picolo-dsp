#!/system/bin/sh

MODDIR="${0%/*}"
export TONE3000_SELINUX_PERMISSIVE=1
exec "$MODDIR/tone3000-root-service.sh" service
