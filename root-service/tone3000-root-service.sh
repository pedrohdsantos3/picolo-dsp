#!/system/bin/sh

# Root companion for Tone3000M1.
# Run in foreground from a Magisk service.sh or use the "once" command.

PACKAGE="com.pedro.tone3000m1"
THREAD_NAME="Tone3000Audio"
FIFO_PRIORITY="2"
POLL_SECONDS="1"
PID_FILE="/data/local/tmp/tone3000-root-service.pid"
LOG_TAG="Tone3000Root"

log_message() {
    log -t "$LOG_TAG" "$*" 2>/dev/null || echo "$*"
}

find_evo_card() {
    awk '
        /EVO4|EVO 4/ {
            line = $0
            sub(/^[[:space:]]*/, "", line)
            split(line, fields, " ")
            print fields[1]
            exit
        }
    ' /proc/asound/cards 2>/dev/null
}

prepare_usb_audio() {
    settings put secure usb_audio_automatic_routing_disabled 1 2>/dev/null

    card="$(find_evo_card)"
    [ -n "$card" ] || return 0

    for node in \
        "/dev/snd/controlC${card}" \
        /dev/snd/pcmC"${card}"D*c \
        /dev/snd/pcmC"${card}"D*p
    do
        [ -e "$node" ] || continue
        chmod 0666 "$node"
    done
}

prepare_selinux() {
    # This is intentionally opt-in because permissive mode weakens security
    # for the entire Android system, not only for Tone3000.
    if [ "${TONE3000_SELINUX_PERMISSIVE:-0}" = "1" ]; then
        setenforce 0 2>/dev/null
    fi
}

tune_audio_thread() {
    app_pid="$(pidof "$PACKAGE" 2>/dev/null)"
    [ -n "$app_pid" ] || return 0

    for task in /proc/"$app_pid"/task/*
    do
        [ -r "$task/comm" ] || continue
        thread_name="$(cat "$task/comm" 2>/dev/null)"
        [ "$thread_name" = "$THREAD_NAME" ] || continue

        tid="${task##*/}"
        policy="$(chrt -p "$tid" 2>/dev/null | head -n 1)"

        case "$policy" in
            *SCHED_FIFO*) ;;
            *)
                # Android toybox expects PID before priority with -p.
                if chrt -f -p "$tid" "$FIFO_PRIORITY" 2>/dev/null; then
                    log_message "Applied SCHED_FIFO:$FIFO_PRIORITY to tid=$tid"
                else
                    log_message "Unable to apply SCHED_FIFO to tid=$tid"
                fi
                ;;
        esac
    done
}

show_status() {
    card="$(find_evo_card)"
    echo "package=$PACKAGE"
    echo "evo_card=${card:-not-found}"
    echo "selinux=$(getenforce 2>/dev/null)"

    app_pid="$(pidof "$PACKAGE" 2>/dev/null)"
    echo "app_pid=${app_pid:-not-running}"
    [ -n "$app_pid" ] || return 0

    for task in /proc/"$app_pid"/task/*
    do
        [ "$(cat "$task/comm" 2>/dev/null)" = "$THREAD_NAME" ] || continue
        tid="${task##*/}"
        echo "audio_tid=$tid"
        chrt -p "$tid" 2>/dev/null
        taskset -p "$tid" 2>/dev/null
    done
}

run_once() {
    prepare_selinux
    prepare_usb_audio
    tune_audio_thread
}

case "${1:-service}" in
    once)
        run_once
        ;;
    status)
        show_status
        ;;
    stop)
        if [ -r "$PID_FILE" ]; then
            service_pid="$(cat "$PID_FILE")"
            case "$service_pid" in
                ''|*[!0-9]*) ;;
                *) kill "$service_pid" 2>/dev/null ;;
            esac
            rm -f "$PID_FILE"
        fi
        ;;
    service)
        echo "$$" > "$PID_FILE"
        trap 'rm -f "$PID_FILE"; exit 0' INT TERM EXIT
        log_message "Root companion started"

        while true
        do
            run_once
            sleep "$POLL_SECONDS"
        done
        ;;
    *)
        echo "Usage: $0 [service|once|status|stop]" >&2
        exit 2
        ;;
esac
