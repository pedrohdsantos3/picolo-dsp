# FX DSP source notices

## Airwindows kPlate140 and TapeDelay2

- Upstream: https://github.com/airwindows/airwindows
- Source: `airwindows/kPlate140/` and `airwindows/TapeDelay2/`
- License: MIT; see [`airwindows/LICENSE`](airwindows/LICENSE).
- Copyright notices remain in the upstream source files. The upstream VST host
  wrapper is compiled against the small local `airwindows/audioeffectx.h`
  compatibility shim; processor state and DSP code are from the upstream
  processor sources.

## MVerb

- Upstream: https://github.com/martineastwood/mverb
- Source: `mverb/MVerb.h`
- License: GPL-3.0-or-later; see [`mverb/LICENSE`](mverb/LICENSE) and the
  copyright/license header in `MVerb.h`.
- Picolo integrates its sample processing API into the native stereo FX chain.
  Its delay-line capacity is specialized to 24,000 samples for the app's fixed
  48 kHz rate; this exceeds the longest delay this processor configures and
  reduces per-instance memory use.

## Chorus delay interpolation

- Upstream: https://github.com/Chowdhury-DSP/chowdsp_utils
- Source: `modules/dsp/chowdsp_dsp_utils/Delay/chowdsp_DelayLine.h`
- License: GPL-3.0; this Chorus reuses the already-vendored 5th-order
  Lagrange delay line. Delay buffers are prepared before audio starts; the
  callback only reads and writes preallocated state.
