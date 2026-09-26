# Offline audio renders

This tool renders the production `picolo::FxNativeProcessor` directly on the
development computer. It does not open Android audio, a USB/audio interface,
or a microphone. The DSP implementation and parameter values are shared with
the app's native effect chain.

Run from the repository root:

```sh
./tools/audio_qa/render_offline.sh
```

The script writes 48 kHz, stereo, 16-bit PCM WAV files and `report.txt` to
`audio_qa/renders/`. That generated-output folder is ignored by Git. Each run
replaces the current render set.

The fixed guitar-like probe is generated in code, followed by silence so the
effect tail is audible. A separate impulse render makes onset and decay easy
to inspect. `dry_reference.wav` contains the same probe without an effect.

The report includes effect parameters, peak and RMS levels, tail RMS, clipping
and non-finite sample counts. These are repeatable signal and safety checks;
they do not grade musical preference. Listen to the WAVs on the development
computer for the subjective comparison.

Play the guitar set in order (dry reference, then each effect):

```sh
./tools/audio_qa/play_renders.sh guitar
```

Play the impulse set with:

```sh
./tools/audio_qa/play_renders.sh impulse
```

Render every built-in FXNative both before and after an amp-like stage:

```sh
./tools/audio_qa/render_placement.sh
```

The files are written to `audio_qa/renders/placement/`. `pre_amp_*.wav`
processes the effect as mono before a deterministic synthetic amp saturator;
`post_amp_*.wav` runs the effect in stereo after that same amp output. The
amp stage is a synthetic comparison fixture, not a NAM capture. Play the set
sequentially with:

```sh
./tools/audio_qa/play_renders.sh placement
```

Playback uses the computer's ALSA output (`aplay`), never the Android device.

Render a saved two-NAM chain, cabinet IR, and the current native effects on the
development computer with:

```sh
cmake --build /tmp/tone3000m1-audioqa-build --target tone3000_chain_qa --parallel 2
/tmp/tone3000m1-audioqa-build/tone3000_chain_qa pedal.nam amp.nam cabinet.wav audio_qa/renders/current_chain
```

This writes a full-chain WAV and a NAM-plus-IR reference WAV. The tool uses the
production NAM core, cabinet convolver, and FXNative processors. Its input is a
repeatable guitar-like probe, not a recording of a player.
