# ChowDSP third-party DSP notices

FXNative links the original `chowdsp_utils` DSP modules and adapts sample-processing structures from ChowMatrix and BYOD. The plugin host and UI layers are not included.

- ChowMatrix: <https://github.com/Chowdhury-DSP/ChowMatrix>, revision `40d8e0ef1f752a6843099ff3dfc3d99132b332eb`. Its `DelayProc` feedback topology is adapted in FXNative. ChowMatrix is BSD-3-Clause; `CHOWMATRIX_LICENSE` retains the required notice and terms.

- `chowdsp_utils`: <https://github.com/Chowdhury-DSP/chowdsp_utils>, revision `e97b826ef3de0b0fd92b15cb2e286076f678d8b9`. Per-module licenses are documented in `chowdsp_utils/modules/*/*`; the modules used by FXNative include GPLv3 DSP modules. The upstream `LICENSE.md` is retained in that directory.
- BYOD: <https://github.com/Chowdhury-DSP/BYOD>, revision `1cf22b6ac802b9dc33cfc9f8dd6af5b3c3e40bc9`. `BYOD_LICENSE` retains its GPLv3 license text. The FXNative adapter does not include the BYOD plugin host/UI classes.

The app's adapter is in `../ChowFxNative.h`; the signal-chain and parameter/UI integration are app code. The ChowDSP module tree is vendored at `chowdsp_utils/`.
