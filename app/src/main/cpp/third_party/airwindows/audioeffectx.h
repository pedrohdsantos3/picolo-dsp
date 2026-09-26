#pragma once

// Minimal host compatibility layer for compiling the upstream Airwindows DSP
// processors without bundling their desktop VST host/plugin interface.
#define __audioeffect__ 1

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

using VstInt32 = int32_t;
using audioMasterCallback = void*;
enum VstPlugCategory { kPlugCategEffect = 1 };

constexpr int kVstMaxProgNameLen = 24;
constexpr int kVstMaxParamStrLen = 8;
constexpr int kVstMaxProductStrLen = 64;
constexpr int kVstMaxVendorStrLen = 64;

inline void vst_strncpy(char* destination, const char* source, int length) {
    if (length <= 0) return;
    const auto sourceLength = std::strlen(source);
    const auto copyLength = std::min(sourceLength, static_cast<size_t>(length - 1));
    std::memcpy(destination, source, copyLength);
    destination[copyLength] = '\0';
}

inline void float2string(float value, char* destination, int length) {
    if (length <= 0) return;
    std::snprintf(destination, static_cast<size_t>(length), "%.4f", value);
}

class AudioEffect {
public:
    virtual ~AudioEffect() = default;
};

class AudioEffectX : public AudioEffect {
public:
    AudioEffectX(audioMasterCallback, int, int) {}
    void setNumInputs(int) {}
    void setNumOutputs(int) {}
    void setUniqueID(unsigned long) {}
    void canProcessReplacing() {}
    void canDoubleReplacing() {}
    void programsAreChunks(bool) {}
    void setSampleRate(float sampleRate) { sampleRate_ = sampleRate; }
    float getSampleRate() const { return sampleRate_; }

private:
    float sampleRate_ = 48000.0f;
};
