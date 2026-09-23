#!/system/bin/sh

ui_print "- Installing Tone3000 Root Companion"
ui_print "- Package: com.pedro.tone3000m1"
ui_print "- EVO4 TinyALSA + SCHED_FIFO:2"

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/tone3000-root-service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
