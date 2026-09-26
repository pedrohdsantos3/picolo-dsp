# PicoloDSP

PicoloDSP is an experimental native Android audio project written in Kotlin
and C++. It combines NAM models with guitar effects processing and external
audio hardware.

The original motivation was to port the Tone3000 plugin to native Android and
validate how its experience could work outside a desktop host. As development
progressed, the idea grew into a **Neural Amp Modeler (NAM)** player, including
NAM A2 support, integrated with a guitar-effects DSP engine. The app brings
together a capture browser, NAM blocks, cabinet/IR, pedals, and native effects.
The audio engine uses TinyALSA and C++; the main UI is native Android, built
with Jetpack Compose.

NAM remains mono. FXNative blocks placed before the amp process mono; blocks
placed after the amp process stereo. This project was built primarily to
validate knowledge of Android, C++, and DSP, and is not yet a product intended
for general use. Real-time performance depends on the model, signal chain,
audio interface, buffer, drivers, and device firmware.

## Running consistently on Android

The app can start without root, but direct USB audio access and thread priority
may be restricted by some firmware. For live TinyALSA use, the currently tested
setup uses a rooted Android device with a persistent Magisk service (or an
equivalent `init` integration on `userdebug` firmware). The companion prepares
the EVO4 ALSA nodes, disables Android's automatic USB audio routing, and applies
`SCHED_FIFO:2` to the `Tone3000Audio` thread, periodically checking the setup
again. It does not pin the thread to a CPU.

Build the Magisk module with:

```bash
./root-service/build-magisk-module.sh
```

Install the generated ZIP through Magisk and reboot. The companion script is
[`root-service/tone3000-root-service.sh`](root-service/tone3000-root-service.sh);
experimental methods for `userdebug` firmware are in [`root-service/`](root-service/).

**Security warning:** the current `service.sh` starts the companion with SELinux
permissive, weakening security for the entire system. This is a development
workaround and is not recommended for daily use or distribution. Root/Magisk,
firmware changes, USB routing, and ALSA permissions require care. Review the
scripts and understand how to remove the module before proceeding. Validate
stability with the actual interface, measuring xruns, latency, and artifacts.

## Build

Requirements: Android Studio/SDK, the NDK and CMake configured for this project,
a JDK compatible with the Gradle Wrapper, and Git with submodule support.

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Root service and Magisk

The service in [`root-service/tone3000-root-service.sh`](root-service/tone3000-root-service.sh)
is experimental. It looks for an Audient EVO4 in `/proc/asound/cards`, prepares
the ALSA audio nodes, disables Android's automatic USB routing, and applies
`SCHED_FIFO:2` only to the `Tone3000Audio` thread in the
`com.pedro.tone3000m1` process. It checks again every second; the `once`,
`status`, and `stop` commands are available for manual operation and
diagnostics.

Generate the persistent companion ZIP with
`./root-service/build-magisk-module.sh` and install it through Magisk. The
current startup script sets `TONE3000_SELINUX_PERMISSIVE=1`, disabling SELinux
protection system-wide. This is a temporary development compromise and a real
security risk. Do not distribute or use the module as a daily setup without
replacing this with a minimal, device-specific SELinux policy.

On `userdebug` firmware,
`root-service/install-adb-userdebug.sh SERIAL_ADB` provides an experimental
`init` integration. It modifies `/system` or its overlay, requires a reboot,
and also enables permissive SELinux. Remove it with
`root-service/uninstall-adb-userdebug.sh`. ADB root for a single session is not
by itself a persistent boot service.

## Signal-chain architecture

The app maintains an ordered chain of typed blocks. NAM and Cabinet/IR blocks
can be moved in the chain; FXNative blocks are processed after the NAM/CAB
stages and do not make the NAM engine stereo. Presets persist the chain and its
corresponding controls.

Each NAM has In Gain, Mix, Out Gain, normalization, A2 Lite/Full, bypass, and a
six-band parametric EQ with PRE/POST selection. The Cabinet has gain/mix
controls, bypass, a six-band PRE/POST EQ, removal, and chain positioning. The
native engine honors the selected position; it does not add a global
convolution when the Cabinet is placed elsewhere in the chain.

### Guitar blocks and FXNative

`AMP` loads NAM models, `DRIVE` loads pedal captures, and `CAB/IR` processes
cabinet impulse responses. FXNative contains modulation, delay, and reverb
effects, shown in the chain as `MOD`, `DELAY`, and `REV`. The modulation group
currently includes Chorus; the delay group includes ChowMatrix, BBD, Ping Pong,
tape, and Dual Delay; the reverb group includes Smooth, Shimmer, Spring, MVerb,
and Plate variants. The UI uses distinct category colors and block icons to
make the signal chain easier to scan.

FXNative can be placed before or after NAM/DRIVE and CAB. In a pre-amp position
it processes mono; after the amp it processes stereo. Delay time can be set in
milliseconds or synchronized to the global tap-tempo BPM using rhythmic
divisions. Chorus speed can also follow the global tempo. Output level controls
start at unity so adding an effect does not automatically reduce the chain's
level.

The implementation uses open source DSP modules and native algorithms adapted
for the Android host. Sources and license notices are documented in
[`app/src/main/cpp/third_party/`](app/src/main/cpp/third_party/).

### Offline audio checks

The `tools/audio_qa` utilities render effects and signal chains on the
development computer without connecting the app or audio interface. They save
48 kHz WAV samples under the ignored `audio_qa/renders/` directory, including
pre-amp mono and post-amp stereo comparisons. See
[`tools/audio_qa/README.md`](tools/audio_qa/README.md) for render and playback
commands.

Before relying on live use, test with the USB interface connected. Xruns,
artifacts, and processing time should be measured with the actual hardware and
intended signal chain.

For up to two NAMs, the TinyALSA profile prioritizes 128 frames at 48 kHz (a
nominal DSP deadline of 2.67 ms) and automatically falls back to 256 frames if
the interface does not accept the smaller period. Confirm actual latency on
the hardware, as firmware and drivers may impose larger periods.

### Reference measurement

During development on a Samsung SM-G781B with an Audient EVO4, two NAMs, and
the root companion active, one observed measurement was: 128-frame blocks,
1.126 ms average processing time, 2.123 ms maximum, a 2.667 ms budget, no
over-budget blocks, and no capture/playback errors. The Magisk service had
reapplied `SCHED_FIFO:2` to the audio thread.

## Credits

Thanks to **Tone3000** for the original plugin that inspired this project and
for the capture-browsing experience. PicoloDSP is an independent project and
does not imply endorsement by Tone3000.

### About me

I have been a **programmer and guitarist since my teens**, and I am an
enthusiast of technology applied to music. The motivation for this project was
purely to validate my knowledge by bringing together native Android, C++,
audio processing.

Contact: [pedrohdsantos3@gmail.com](mailto:pedrohdsantos3@gmail.com)
