#include "audioeffectx.h"

#define createEffectInstance createKPlate140EffectInstance
#include "kPlate140/kPlate140.cpp"
#include "kPlate140/kPlate140Proc.cpp"
#undef createEffectInstance

#include "AirwindowsAdapters.h"

#include <algorithm>
#include <array>
#include <cmath>

namespace picolo {
namespace {
struct KPlateAdapter {
    kPlate140 effect{nullptr};
    float parameters[5]{-1.0f, -1.0f, -1.0f, -1.0f, -1.0f};
    std::array<float, 64> inputLeft{}, inputRight{}, outputLeft{}, outputRight{};
    float* inputs[2]{inputLeft.data(), inputRight.data()};
    float* outputs[2]{outputLeft.data(), outputRight.data()};

    KPlateAdapter() {
        effect.setSampleRate(48000.0f);
        effect.setParameter(kParamA, 1.0f);
        effect.setParameter(kParamB, 0.62f);
        effect.setParameter(kParamC, 1.0f);
        effect.setParameter(kParamD, 0.0f);
        effect.setParameter(kParamE, 1.0f);
    }

    void set(int index, float value) {
        value = std::clamp(value, 0.0f, 1.0f);
        if (std::abs(parameters[index] - value) < 0.0001f) return;
        parameters[index] = value;
        effect.setParameter(index, value);
    }
};
} // namespace

void* createKPlate140() { return new KPlateAdapter(); }
void destroyKPlate140(void* instance) { delete static_cast<KPlateAdapter*>(instance); }
void processKPlate140(void* instance, const float* left, const float* right, int frames,
                      float decayMs, float predelayMs, float character,
                      float* outLeft, float* outRight) {
    auto& state = *static_cast<KPlateAdapter*>(instance);
    const float decay = std::clamp(decayMs, 500.0f, 8000.0f);
    const float targetRegen = std::clamp(std::exp(-3.0f * 0.15f / (decay * 0.001f)), 0.55f, 0.97f);
    const float regen = 1.0f - std::sqrt(1.0f - targetRegen);
    state.set(kParamB, regen);
    state.set(kParamC, std::clamp(character, 0.45f, 1.0f));
    state.set(kParamD, std::clamp(predelayMs, 0.0f, 300.0f) / 300.0f);
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
