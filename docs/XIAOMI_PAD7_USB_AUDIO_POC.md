# Xiaomi Pad 7 USB audio proof of concept (paused)

The current validation target is the Samsung Galaxy S20 FE. This Pad 7
experiment is retained as a record and is not the active development target.
Although the app's input and output meters moved, no audio was heard from the
EVO4 monitors, so end-to-end playback was not validated.

This branch tests no-root EVO 4 access on the Xiaomi Pad 7. The app now falls
back to Android's native AAudio USB route when TinyALSA cannot see a USB PCM
device. The separate libusb probe remains a descriptor/access diagnostic; it
does not stream audio.

## Run the probe

1. Connect the EVO 4 to the Xiaomi Pad 7 in USB host/OTG mode. The EVO 4 is bus
   powered; use a powered USB-C hub if the tablet does not provide stable power.
2. Open **Settings** in PicoloDSP and tap **SCAN USB**.
3. Grant the Android USB access prompt for the audio device.
4. Read the **Direct libusb probe** section in the result dialog.

The probe prints USB descriptors, claims an AudioStreaming interface, selects
an alternate setting, and submits one short isochronous IN transfer. On the
EVO4, the first such endpoint is the 4-byte feedback endpoint, not captured
audio samples. A completed transfer therefore proves USB access only. The
TinyALSA scan is included separately for comparison with the existing backend.

## Audio route

When TinyALSA cannot find a USB PCM card, the native engine asks AAudio for
48 kHz input and output streams using the EVO4 device IDs reported by Android.
It tries exclusive mode first and shared mode second. DSP stays in C++ with a
64-frame processing block. On the tested Pad 7, Android opened the exact EVO4
input and output IDs; AAudio reported an output burst of 3844 frames, so the
route is active but end-to-end latency still needs listening tests.

## Interpret the result

- **No USB Audio interface**: Android's USB host service did not expose the
  connected device as an audio-class device to this app.
- **libusb open/claim failed**: Android granted device permission, but the
  application could not take the USB handle or streaming interface.
- **Isochronous submit failed**: interface setup succeeded, but the USB stack
  rejected the real-time transfer request.
- **Isochronous IN completed**: the app submitted and completed a USB transfer
  without root. This does not prove that the payload contains audio samples.

The AAudio backend opened streams through Android's USB route, but audible
playback and end-to-end full-duplex behavior were not validated. The direct
libusb probe is not the audio backend. Further Pad 7 testing is paused while
the S20 FE is the active target.
