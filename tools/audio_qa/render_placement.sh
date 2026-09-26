#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AUDIO_QA_BUILD_DIR="${AUDIO_QA_BUILD_DIR:-/tmp/tone3000m1-audioqa-build}"
AUDIO_QA_RENDER_DIR="${AUDIO_QA_RENDER_DIR:-${PROJECT_ROOT}/audio_qa/renders}"

cmake -S "${PROJECT_ROOT}/tools/audio_qa" -B "${AUDIO_QA_BUILD_DIR}" -DCMAKE_BUILD_TYPE=Release
cmake --build "${AUDIO_QA_BUILD_DIR}" --parallel 2
"${AUDIO_QA_BUILD_DIR}/tone3000_audio_qa" "${AUDIO_QA_RENDER_DIR}" placement

printf 'Placement comparison WAVs and report saved in: %s/placement\n' "${AUDIO_QA_RENDER_DIR}"
