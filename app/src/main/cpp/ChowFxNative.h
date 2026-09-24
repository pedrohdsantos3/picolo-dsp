#pragma once

// FX algorithms are adapted from ChowDSP BYOD processors (GPL-3.0) and use
// chowdsp_utils' GPL DSP modules. Host/UI wrappers are replaced with this
// allocation-free, sample-based adapter for Picolo's stereo post chain.
#include <chowdsp_dsp_utils/chowdsp_dsp_utils.h>
#include <chowdsp_reverb/chowdsp_reverb.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <memory>
#include <numeric>

namespace picolo {

using ChowCleanDelay = chowdsp::DelayLine<float, chowdsp::DelayLineInterpolationTypes::Lagrange5th>;
using ChowBbdDelay = chowdsp::BBD::BBDDelayWrapper<4 * 16384>;

struct ChowDelayState {
    ChowCleanDelay clean{1 << 17};
    ChowBbdDelay lofi;
    chowdsp::SVFLowpass<float, 2> matrixLpf;
    juce::dsp::ProcessSpec stereoSpec{48000.0, 64, 2};
    std::array<float, 2> feedbackState{};
    float currentMatrixDelayMs = -1.0f;
    float currentBbdDelayMs = -1.0f;

    void prepare() {
        clean.prepare(stereoSpec);
        lofi.prepare(stereoSpec);
        matrixLpf.prepare(stereoSpec);
        matrixLpf.setCutoffFrequency(8000.0f);
    }

    void reset() {
        clean.reset();
        lofi.reset();
        matrixLpf.reset();
        feedbackState.fill(0.0f);
        currentMatrixDelayMs = currentBbdDelayMs = -1.0f;
    }

    // ChowMatrix's DelayProc feedback topology, using its ChowDSP delay/filter
    // primitives without importing the JUCE plugin host or UI layer.
    std::array<float, 2> processMatrix(float left, float right, float timeMs, float feedback) {
        const float safeTimeMs = std::clamp(timeMs, 20.0f, 2000.0f);
        if (safeTimeMs != currentMatrixDelayMs) {
            clean.setDelay(safeTimeMs * 48.0f);
            currentMatrixDelayMs = safeTimeMs;
        }
        const float input[2]{left, right};
        std::array<float, 2> output{};
        for (int channel = 0; channel < 2; ++channel) {
            const auto ch = static_cast<size_t>(channel);
            clean.pushSample(channel, matrixLpf.processSample(channel, input[channel] + feedbackState[ch]));
            const float delayed = clean.popSample(channel);
            feedbackState[ch] = delayed * std::clamp(feedback, 0.0f, 0.94f);
            output[ch] = delayed;
        }
        return output;
    }

    // BYOD's lo-fi delay uses the original Chow BBD delay line.
    std::array<float, 2> processByodBbd(float left, float right, float timeMs, float feedback) {
        const float safeTimeMs = std::clamp(timeMs, 20.0f, 2000.0f);
        if (safeTimeMs != currentBbdDelayMs) {
            lofi.setDelay(safeTimeMs * 48.0f);
            lofi.setFilterFreq(4000.0f);
            currentBbdDelayMs = safeTimeMs;
        }
        const float input[2]{left, right};
        std::array<float, 2> output{};
        auto& line = static_cast<chowdsp::DelayLineBase<float>&>(lofi);
        for (int channel = 0; channel < 2; ++channel) {
            const float delayed = line.popSample(channel);
            line.pushSample(channel, input[channel] + delayed * std::clamp(feedback, 0.0f, 0.94f));
            output[static_cast<size_t>(channel)] = delayed;
        }
        return output;
    }
};

constexpr int kChowDiffuserChannels = 8;
constexpr int kChowFdnChannels = 12;
constexpr int kChowReverbDelaySize = 1 << 15;

using ChowSmoothDiffuser = chowdsp::Reverb::Diffuser<float, kChowDiffuserChannels,
        chowdsp::DelayLineInterpolationTypes::None, kChowReverbDelaySize>;
using ChowSmoothFdn = chowdsp::Reverb::FDN<
        chowdsp::Reverb::DefaultFDNConfig<float, kChowFdnChannels>,
        chowdsp::DelayLineInterpolationTypes::None, kChowReverbDelaySize>;

struct ChowSmoothState {
    using DiffuserChain = chowdsp::Reverb::DiffuserChain<4, ChowSmoothDiffuser>;
    DiffuserChain diffuser;
    ChowSmoothFdn fdn;
    ChowCleanDelay preDelayLeft{1 << 15};
    ChowCleanDelay preDelayRight{1 << 15};
    chowdsp::LevelDetector<float> envelope;
    juce::dsp::ProcessSpec monoSpec{48000.0, 64, 1};
    float currentDecayMs = -1.0f;

    void prepare() {
        diffuser.prepare(48000.0);
        fdn.prepare(48000.0);
        preDelayLeft.prepare(monoSpec);
        preDelayRight.prepare(monoSpec);
        preDelayLeft.setDelay(43.0f * 48.0f);
        preDelayRight.setDelay(77.0f * 48.0f);
        envelope.prepare(monoSpec);
        envelope.setParameters(20.0f, 2000.0f);
    }

    void reset() {
        diffuser.reset();
        fdn.reset();
        preDelayLeft.reset();
        preDelayRight.reset();
        envelope.reset();
        currentDecayMs = -1.0f;
    }

    std::array<float, 2> process(float left, float right, float decayMs, float relax) {
        decayMs = std::clamp(decayMs, 500.0f, 5000.0f);
        if (decayMs != currentDecayMs) {
            diffuser.setDiffusionTimeMs(std::pow(decayMs * 0.005f, 0.75f));
            fdn.setDelayTimeMs(std::pow(decayMs * 0.2f, 0.95f));
            fdn.getFDNConfig().setDecayTimeMs(fdn, decayMs * 1.25f, decayMs * 0.5f, 750.0f);
            currentDecayMs = decayMs;
        }

        const float level = envelope.processSample((left + right) * (left + right));
        const float factor = 1.0f + 2.5f * std::pow(decayMs / 5000.0f, 1.25f) * std::clamp(relax, 0.0f, 1.0f) * level;
        preDelayLeft.setDelay(43.0f * 48.0f * factor);
        preDelayRight.setDelay(77.0f * 48.0f * factor);
        const float reflectionsL = preDelayLeft.popSample(0);
        const float reflectionsR = preDelayRight.popSample(0);
        preDelayLeft.pushSample(0, std::tanh(left));
        preDelayRight.pushSample(0, std::tanh(right));

        std::array<float, kChowDiffuserChannels> diffuserIn{};
        diffuserIn.fill(0.0f);
        std::fill(diffuserIn.begin(), diffuserIn.begin() + kChowDiffuserChannels / 2, std::tanh(left));
        std::fill(diffuserIn.begin() + kChowDiffuserChannels / 2, diffuserIn.end(), std::tanh(right));
        const auto* diffused = diffuser.process(diffuserIn.data());

        std::array<float, kChowFdnChannels> fdnIn{};
        fdnIn[0] = 0.5f * reflectionsL;
        fdnIn[1] = 0.5f * reflectionsR;
        fdnIn[2] = std::tanh(left);
        fdnIn[3] = std::tanh(right);
        for (int i = 4; i < kChowFdnChannels; ++i) fdnIn[static_cast<size_t>(i)] = 0.25f * diffused[i % kChowDiffuserChannels];
        const auto* fdnOut = fdn.process(fdnIn.data());
        float fdnLeft = 0.0f, fdnRight = 0.0f;
        for (int i = 0; i < kChowFdnChannels; i += 2) {
            fdnLeft += fdnOut[i];
            fdnRight += fdnOut[i + 1];
        }
        return {0.2f * reflectionsL + 0.3f * diffused[0] + 0.45f * fdnLeft,
                0.2f * reflectionsR + 0.3f * diffused[1] + 0.45f * fdnRight};
    }
};

struct ChowShimmerFdnConfig : chowdsp::Reverb::DefaultFDNConfig<float, kChowFdnChannels> {
    using Base = chowdsp::Reverb::DefaultFDNConfig<float, kChowFdnChannels>;
    chowdsp::PitchShifter<float, chowdsp::DelayLineInterpolationTypes::Linear> shifter{1 << 15, 2048};
    void prepare(double sampleRate) {
        shifter.prepare({sampleRate, 64, 1});
        Base::prepare(sampleRate);
    }
    void reset() {
        shifter.reset();
        Base::reset();
    }
    static const float* doFeedbackProcess(ChowShimmerFdnConfig& config, const float* data) {
        auto* feedback = config.fbData.data();
        feedback[kChowFdnChannels - 1] = config.shifter.processSample(0, data[kChowFdnChannels - 1]);
        std::copy(data, data + kChowFdnChannels - 1, feedback);
        return Base::doFeedbackProcess(config, feedback);
    }
};

using ChowShimmerFdn = chowdsp::Reverb::FDN<ChowShimmerFdnConfig,
        chowdsp::DelayLineInterpolationTypes::Linear, kChowReverbDelaySize>;

struct ChowShimmerState {
    std::array<ChowShimmerFdn, 2> fdns;
    float lfoValues[2]{1.0f, 1.0f};
    float currentSizeMs = -1.0f;
    float currentDecayMs = -1.0f;
    float currentShiftSemitones = 1000.0f;

    void prepare() { for (auto& fdn : fdns) fdn.prepare(48000.0); }
    void reset() {
        for (auto& fdn : fdns) fdn.reset();
        currentSizeMs = currentDecayMs = -1.0f;
        currentShiftSemitones = 1000.0f;
    }

    std::array<float, 2> process(float left, float right, float sizeMs, float decayMs, float shiftSemitones) {
        const float input[2]{left, right};
        std::array<float, 2> result{};
        for (int channel = 0; channel < 2; ++channel) {
            auto& fdn = fdns[static_cast<size_t>(channel)];
            const float safeSizeMs = std::clamp(sizeMs, 50.0f, 250.0f);
            const float safeDecayMs = std::clamp(decayMs, 1000.0f, 10000.0f);
            const float safeShift = std::clamp(shiftSemitones, -12.0f, 12.0f);
            if (safeSizeMs != currentSizeMs) {
                fdn.setDelayTimeMsWithModulators<2>(safeSizeMs, lfoValues);
                if (channel == 1) currentSizeMs = safeSizeMs;
            }
            if (safeDecayMs != currentDecayMs) {
                fdn.getFDNConfig().setDecayTimeMs(fdn, safeDecayMs, safeDecayMs * 0.75f, 800.0f);
                if (channel == 1) currentDecayMs = safeDecayMs;
            }
            if (safeShift != currentShiftSemitones) {
                fdn.getFDNConfig().shifter.setShiftSemitones(safeShift);
                if (channel == 1) currentShiftSemitones = safeShift;
            }
            std::array<float, kChowFdnChannels> inputVector{};
            inputVector.fill(input[channel]);
            const auto* output = fdn.process(inputVector.data());
            result[static_cast<size_t>(channel)] = std::accumulate(output, output + kChowFdnChannels, 0.0f) / kChowFdnChannels;
        }
        return result;
    }
};

struct FxNativeProcessor {
    explicit FxNativeProcessor(int selectedType) : type(std::clamp(selectedType, 0, 3)) { prepare(); }
    int type = 0;
    std::unique_ptr<ChowDelayState> delay;
    std::unique_ptr<ChowSmoothState> smooth;
    std::unique_ptr<ChowShimmerState> shimmer;

    void prepare() {
        if (type < 2) { delay = std::make_unique<ChowDelayState>(); delay->prepare(); }
        else if (type == 2) { smooth = std::make_unique<ChowSmoothState>(); smooth->prepare(); }
        else { shimmer = std::make_unique<ChowShimmerState>(); shimmer->prepare(); }
    }
    void reset() {
        if (delay) delay->reset();
        if (smooth) smooth->reset();
        if (shimmer) shimmer->reset();
    }
    std::array<float, 2> processStereo(float left, float right, float p1, float p2, float p3) {
        if (type == 0) return delay->processMatrix(left, right, p1, p2);
        if (type == 1) return delay->processByodBbd(left, right, p1, p2);
        if (type == 2) return smooth->process(left, right, p1, p2);
        return shimmer->process(left, right, p1, p2, p3);
    }
};

} // namespace picolo
