#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RENDER_DIR="${AUDIO_QA_RENDER_DIR:-${PROJECT_ROOT}/audio_qa/renders}"
GROUP="${1:-guitar}"

if ! command -v aplay >/dev/null 2>&1; then
    printf 'aplay is required to play the WAVs on this development computer.\n' >&2
    exit 1
fi

case "${GROUP}" in
    guitar)
        FILES=(dry_reference.wav guitar_kplate140.wav guitar_mverb.wav guitar_tape_delay2.wav)
        ;;
    impulse)
        FILES=(impulse_kplate140.wav impulse_mverb.wav impulse_tape_delay2.wav)
        ;;
    placement)
        RENDER_DIR="${RENDER_DIR}/placement"
        FILES=(amp_reference.wav
            pre_amp_chowmatrix_delay.wav post_amp_chowmatrix_delay.wav
            pre_amp_byod_bbd_delay.wav post_amp_byod_bbd_delay.wav
            pre_amp_byod_smooth_reverb.wav post_amp_byod_smooth_reverb.wav
            pre_amp_byod_shimmer_reverb.wav post_amp_byod_shimmer_reverb.wav
            pre_amp_spring_reverb.wav post_amp_spring_reverb.wav
            pre_amp_ping_pong_delay.wav post_amp_ping_pong_delay.wav
            pre_amp_plate_reverb.wav post_amp_plate_reverb.wav
            pre_amp_kplate140.wav post_amp_kplate140.wav
            pre_amp_mverb.wav post_amp_mverb.wav
            pre_amp_tape_delay2.wav post_amp_tape_delay2.wav
            pre_amp_dual_delay.wav post_amp_dual_delay.wav
            pre_amp_chorus.wav post_amp_chorus.wav)
        ;;
    *)
        printf 'Usage: %s [guitar|impulse|placement]\n' "$0" >&2
        exit 2
        ;;
esac

for FILE in "${FILES[@]}"; do
    if [[ ! -f "${RENDER_DIR}/${FILE}" ]]; then
        printf 'Missing %s; run tools/audio_qa/render_offline.sh first.\n' "${RENDER_DIR}/${FILE}" >&2
        exit 1
    fi
    printf 'Playing %s\n' "${FILE}"
    aplay -q "${RENDER_DIR}/${FILE}"
done
