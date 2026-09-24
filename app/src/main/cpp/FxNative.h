#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <vector>

namespace picolo {

// Compact mono real-time processors used by FXNative. The algorithms follow
// the delay/reverb structures used by ChowDSP BYOD and ChowMatrix, adapted to
// the app's existing float/mono callback (no JUCE dependency or RT allocation).
struct FxNativeProcessor {
    static constexpr int kRate = 48000;
    static constexpr int kMaxDelay = kRate * 2;
    std::array<std::array<float, kMaxDelay>, 2> delay{};
    std::array<std::array<std::array<float, 8192>, 4>, 2> comb{};
    std::array<std::array<std::size_t, 4>, 2> combWrite{};
    std::array<float, 2> lowpass{};
    std::array<std::size_t, 2> write{};
    float shimmerPhase = 0.0f;

    void reset() {
        for (auto& channel : delay) channel.fill(0.0f);
        for (auto& channel : comb) for (auto& line : channel) line.fill(0.0f);
        for (auto& channel : combWrite) channel.fill(0);
        write.fill(0);
        lowpass.fill(0.0f);
        shimmerPhase = 0.0f;
    }

    std::array<float, 2> processStereo(float inputLeft, float inputRight, int type, float p1, float p2) {
        // p1: time/size, p2: feedback/decay. Wet/dry is applied by caller.
        if (type == 0 || type == 1) {
            const float time = std::clamp(p1, 40.0f, 1200.0f);
            const auto lengthLeft = static_cast<std::size_t>(std::clamp(time * 48.0f, 1.0f, static_cast<float>(kMaxDelay - 1)));
            const auto lengthRight = std::min(lengthLeft + 337, static_cast<std::size_t>(kMaxDelay - 1));
            const auto readLeft = (write[0] + kMaxDelay - lengthLeft) % kMaxDelay;
            const auto readRight = (write[1] + kMaxDelay - lengthRight) % kMaxDelay;
            const float delayedLeft = delay[0][readLeft];
            const float delayedRight = delay[1][readRight];
            const float feedback = std::clamp(p2, 0.0f, 0.88f);
            if (type == 1) {
                // Lo-fi/BBD character: bandwidth-limited feedback and gentle saturation.
                lowpass[0] += 0.16f * (delayedLeft - lowpass[0]);
                lowpass[1] += 0.16f * (delayedRight - lowpass[1]);
            }
            const float feedbackLeft = type == 1 ? std::tanh(lowpass[0] * 1.35f) : delayedLeft;
            const float feedbackRight = type == 1 ? std::tanh(lowpass[1] * 1.35f) : delayedRight;
            // Cross-feedback creates a stereo ping-pong field from the mono NAM bus.
            delay[0][write[0]] = std::clamp(inputLeft + feedback * (0.78f * feedbackLeft + 0.22f * feedbackRight), -3.0f, 3.0f);
            delay[1][write[1]] = std::clamp(inputRight + feedback * (0.78f * feedbackRight + 0.22f * feedbackLeft), -3.0f, 3.0f);
            write[0] = (write[0] + 1) % kMaxDelay;
            write[1] = (write[1] + 1) % kMaxDelay;
            return {delayedLeft, delayedRight};
        }

        // Four mutually-prime comb delays form a small FDN-like diffuse tail.
        constexpr std::array<int, 4> lengths{1493, 1601, 1747, 1867};
        const float decay = std::clamp(p2, 0.0f, 0.94f);
        const float size = std::clamp(p1, 0.2f, 1.0f);
        std::array<float, 2> outputs{};
        for (std::size_t channel = 0; channel < 2; ++channel) {
            const float input = channel == 0 ? inputLeft : inputRight;
            float sum = 0.0f;
            std::array<float, 4> feedbackValues{};
            for (std::size_t i = 0; i < 4; ++i) {
                const auto len = std::min(static_cast<std::size_t>(std::max(1.0f, lengths[i] * size)), comb[channel][i].size() - 1);
                const auto read = (combWrite[channel][i] + comb[channel][i].size() - len) % comb[channel][i].size();
                feedbackValues[i] = comb[channel][i][read];
                sum += feedbackValues[i];
            }
            const float diffuse = sum * 0.25f;
            for (std::size_t i = 0; i < 4; ++i) {
                float excitation = input;
                if (type == 3) {
                    shimmerPhase += 0.00019f;
                    if (shimmerPhase >= 6.2831853f) shimmerPhase -= 6.2831853f;
                    const float halo = std::sin(shimmerPhase + static_cast<float>(i + channel * 2) * 1.5707963f);
                    excitation += 0.12f * halo * std::abs(feedbackValues[(i + 1) % 4]);
                }
                const float v = std::clamp(excitation * 0.20f + decay * (feedbackValues[i] - diffuse * 0.45f), -3.0f, 3.0f);
                comb[channel][i][combWrite[channel][i]] = v;
                combWrite[channel][i] = (combWrite[channel][i] + 1) % comb[channel][i].size();
            }
            outputs[channel] = diffuse * (type == 3 ? 1.35f : 1.0f);
        }
        return outputs;
    }
};

} // namespace picolo
