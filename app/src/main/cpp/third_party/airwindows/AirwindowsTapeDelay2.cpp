#include "audioeffectx.h"

#define createEffectInstance createTapeDelay2EffectInstance
#include "TapeDelay2/TapeDelay2.cpp"
#include "TapeDelay2/TapeDelay2Proc.cpp"
#undef createEffectInstance

#include "AirwindowsAdapters.h"

#include <algorithm>
#include <array>
#include <cmath>

namespace picolo {
namespace {
struct TapeDelay2Adapter {
    TapeDelay2 effect{nullptr};
    float parameters[6]{-1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f};
    std::array<float, 64> inputLeft{}, inputRight{}, outputLeft{}, outputRight{};
    float* inputs[2]{inputLeft.data(), inputRight.data()};
    float* outputs[2]{outputLeft.data(), outputRight.data()};

    TapeDelay2Adapter() {
        effect.setSampleRate(48000.0f);
        for (int index = 0; index < 6; ++index) set(index, index == 0 ? 0.72f : 0.0f);
        set(kParamF, 1.0f);
    }

    void set(int index, float value) {
        value = std::clamp(value, 0.0f, 1.0f);
        if (std::abs(parameters[index] - value) < 0.0001f) return;
        parameters[index] = value;
        effect.setParameter(index, value);
    }
};
} // namespace

void* createTapeDelay2() { return new TapeDelay2Adapter(); }
void destroyTapeDelay2(void* instance) { delete static_cast<TapeDelay2Adapter*>(instance); }
void processTapeDelay2(void* instance, const float* left, const float* right, int frames,
                       float timeMs, float feedback, float toneHz,
                       float* outLeft, float* outRight) {
    auto& state = *static_cast<TapeDelay2Adapter*>(instance);
    const float safeTime = std::clamp(timeMs, 70.0f, 1800.0f);
    const float speed = std::clamp(88200.0f / (safeTime * 48.0f), 1.0f, 26.0f);
    const float timeControl = std::pow(std::clamp((speed - 1.0f) / 25.0f, 0.0f, 1.0f), 0.25f);
    // TapeDelay2's normalized frequency is 0..0.4 cycles/sample. Convert Hz
    // against the full sample rate so the requested tone maps to the same Hz.
    const float cutoffRatio = std::clamp(toneHz / (48000.0f * 0.4f), 0.0001f, 1.0f);
    state.set(kParamA, timeControl);
    state.set(kParamB, std::sqrt(std::clamp(feedback, 0.0f, 0.96f)));
    state.set(kParamC, std::cbrt(cutoffRatio));
    state.set(kParamD, 0.7f);
    state.set(kParamE, 0.12f);
    state.set(kParamF, 1.0f);
    int offset = 0;
    while (offset < frames) {
        const int count = std::min(frames - offset, static_cast<int>(state.inputLeft.size()));
        std::copy(left + offset, left + offset + count, state.inputLeft.begin());
        std::copy(right + offset, right + offset + count, state.inputRight.begin());
        state.effect.processReplacing(state.inputs, state.outputs, count);
        std::copy(state.outputLeft.begin(), state.outputLeft.begin() + count, outLeft + offset);
        std::copy(state.outputRight.begin(), state.outputRight.begin() + count, outRight + offset);
        offset += count;
    }
}
} // namespace picolo
