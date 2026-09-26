#pragma once

// FX algorithms are adapted from ChowDSP BYOD processors (GPL-3.0) and use
// chowdsp_utils' GPL DSP modules. Host/UI wrappers are replaced with this
// allocation-free, sample-based adapter for Picolo's stereo post chain.
#include <chowdsp_dsp_utils/chowdsp_dsp_utils.h>
#include <chowdsp_reverb/chowdsp_reverb.h>
#include "third_party/airwindows/AirwindowsAdapters.h"
#include "third_party/mverb/MVerb.h"

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

// Two independent stereo delay taps with cross-feedback. The fixed buffers
// keep all state allocation and cleanup outside the audio callback.
struct DualDelayState {
    static constexpr size_t kBufferSize = 96000; // 2 seconds at 48 kHz
    std::array<std::array<float, kBufferSize>, 2> buffer{};
    size_t writeIndex = 0;

    void reset() {
        for (auto& channel : buffer) channel.fill(0.0f);
        writeIndex = 0;
    }

    std::array<float, 2> process(float left, float right, float leftTimeMs,
                                 float feedback, float rightTimeMs) {
        const auto delaySamples = [](float timeMs) {
            const float safeTime = std::clamp(timeMs, 20.0f, 2000.0f);
            return std::clamp(static_cast<size_t>(safeTime * 48.0f), size_t{1}, kBufferSize - 1);
        };
        const size_t leftRead = (writeIndex + kBufferSize - delaySamples(leftTimeMs)) % kBufferSize;
        const size_t rightRead = (writeIndex + kBufferSize - delaySamples(rightTimeMs)) % kBufferSize;
        const float delayedLeft = buffer[0][leftRead];
        const float delayedRight = buffer[1][rightRead];
        const float safeFeedback = std::clamp(feedback, 0.0f, 0.96f);
        buffer[0][writeIndex] = std::clamp(left + delayedRight * safeFeedback, -4.0f, 4.0f);
        buffer[1][writeIndex] = std::clamp(right + delayedLeft * safeFeedback, -4.0f, 4.0f);
        if (++writeIndex == kBufferSize) writeIndex = 0;
        return {delayedLeft, delayedRight};
    }
};

struct ChorusState {
    ChowCleanDelay delay{1 << 12};
    juce::dsp::ProcessSpec stereoSpec{48000.0, 64, 2};
    float phase = 0.0f;
    float currentDepthMs = 8.0f;
    float currentRateHz = 0.8f;
    float filterLeft = 0.0f;
    float filterRight = 0.0f;

    void prepare() { delay.prepare(stereoSpec); }

    void reset() {
        delay.reset();
        phase = 0.0f;
        currentDepthMs = 8.0f;
        currentRateHz = 0.8f;
        filterLeft = filterRight = 0.0f;
    }

    void process(float* left, float* right, unsigned int frames, float depthMs,
                 float rateHz, float toneHz, float mix, float outputGainDb) {
        const float targetDepthMs = std::clamp(depthMs, 0.0f, 20.0f);
        const float targetRateHz = std::clamp(rateHz, 0.1f, 8.0f);
        const float toneCutoff = std::clamp(toneHz, 1000.0f, 20000.0f);
        const float toneCoefficient = 1.0f - std::exp(-2.0f * 3.14159265358979323846f * toneCutoff / 48000.0f);
        const float wet = std::clamp(mix, 0.0f, 1.0f);
        const float dryLevel = std::cos(wet * 1.57079632679489661923f);
        const float wetLevel = std::sin(wet * 1.57079632679489661923f);
        const float outputGain = std::pow(10.0f, std::clamp(outputGainDb, -12.0f, 12.0f) / 20.0f);
        constexpr float kTwoPi = 6.28318530717958647692f;
        constexpr float kSmoothing = 0.001f;

        for (unsigned int frame = 0; frame < frames; ++frame) {
            currentDepthMs += (targetDepthMs - currentDepthMs) * kSmoothing;
            currentRateHz += (targetRateHz - currentRateHz) * kSmoothing;
            const float inputLeft = left[frame];
            const float inputRight = right[frame];

            delay.pushSample(0, inputLeft);
            delay.pushSample(1, inputRight);
            float chorusLeft = inputLeft;
            float chorusRight = inputRight;
            if (targetDepthMs > 0.001f) {
                const float leftLfo = 0.5f + 0.5f * std::sin(phase * kTwoPi);
                const float rightLfo = 0.5f + 0.5f * std::sin((phase + 0.25f) * kTwoPi);
                const float leftDelay = std::max(1.0f, currentDepthMs * 48.0f * leftLfo);
                const float rightDelay = std::max(1.0f, currentDepthMs * 48.0f * rightLfo);
                chorusLeft = delay.popSample(0, leftDelay, true);
                chorusRight = delay.popSample(1, rightDelay, true);
            } else {
                // Keep read pointers advancing when depth is zero so re-enabling
                // modulation cannot expose stale samples from the delay buffer.
                delay.popSample(0, 1.0f, true);
                delay.popSample(1, 1.0f, true);
            }

            if (targetDepthMs <= 0.001f) {
                filterLeft = inputLeft;
                filterRight = inputRight;
                left[frame] = inputLeft * outputGain;
                right[frame] = inputRight * outputGain;
            } else {
                filterLeft += toneCoefficient * (chorusLeft - filterLeft);
                filterRight += toneCoefficient * (chorusRight - filterRight);
                left[frame] = (inputLeft * dryLevel + filterLeft * wetLevel) * outputGain;
                right[frame] = (inputRight * dryLevel + filterRight * wetLevel) * outputGain;
            }

            phase += currentRateHz / 48000.0f;
            if (phase >= 1.0f) phase -= 1.0f;
        }
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

/** Cross-feedback stereo delay with alternating left/right repeats. */
struct ChowPingPongState {
    ChowCleanDelay left{1 << 17};
    ChowCleanDelay right{1 << 17};
    juce::dsp::ProcessSpec monoSpec{48000.0, 64, 1};
    float currentTimeMs = -1.0f;

    void prepare() {
        left.prepare(monoSpec);
        right.prepare(monoSpec);
    }
    void reset() {
        left.reset();
        right.reset();
        currentTimeMs = -1.0f;
    }
    std::array<float, 2> process(float inputLeft, float inputRight, float timeMs, float feedback, float width) {
        const float safeTime = std::clamp(timeMs, 20.0f, 2000.0f);
        if (safeTime != currentTimeMs) {
            left.setDelay(safeTime * 48.0f);
            right.setDelay(safeTime * 48.0f);
            currentTimeMs = safeTime;
        }
        const float delayedLeft = left.popSample(0);
        const float delayedRight = right.popSample(0);
        const float monoInput = 0.5f * (inputLeft + inputRight);
        const float safeFeedback = std::clamp(feedback, 0.0f, 0.94f);
        const float safeWidth = std::clamp(width, 0.0f, 1.0f);
        left.pushSample(0, monoInput * (1.0f - 0.35f * safeWidth) + delayedRight * safeFeedback);
        right.pushSample(0, delayedLeft * safeFeedback);
        return {delayedLeft, delayedRight};
    }
};

/** Spring-like coupled waveguide with dispersive all-pass sections and damped feedback. */
struct ChowSpringState {
    ChowCleanDelay left{1 << 15};
    ChowCleanDelay right{1 << 15};
    juce::dsp::ProcessSpec monoSpec{48000.0, 64, 1};
    std::array<float, 2> dampingState{};
    std::array<float, 2> dispersionState1{};
    std::array<float, 2> dispersionState2{};
    float toneAlpha = 0.2f;
    float currentToneHz = -1.0f;

    static float allpass(float input, float coefficient, float& state) {
        const float output = -coefficient * input + state;
        state = input + coefficient * output;
        return output;
    }
    void prepare() {
        left.prepare(monoSpec);
        right.prepare(monoSpec);
        left.setDelay(37.0f * 48.0f);
        right.setDelay(53.0f * 48.0f);
    }
    void reset() {
        left.reset();
        right.reset();
        dampingState.fill(0.0f);
        dispersionState1.fill(0.0f);
        dispersionState2.fill(0.0f);
        currentToneHz = -1.0f;
    }
    std::array<float, 2> process(float inputLeft, float inputRight, float decayMs, float dwell, float toneHz) {
        const float safeDecay = std::clamp(decayMs, 500.0f, 5000.0f);
        const float safeDwell = std::clamp(dwell, 0.0f, 1.0f);
        const float safeTone = std::clamp(toneHz, 500.0f, 12000.0f);
        if (safeTone != currentToneHz) {
            toneAlpha = std::exp(-2.0f * 3.14159265358979323846f * safeTone / 48000.0f);
            currentToneHz = safeTone;
        }
        const float feedback = std::clamp(std::exp(-3.0f * 0.053f / (safeDecay * 0.001f)), 0.62f, 0.96f);
        const float delayed[2]{left.popSample(0), right.popSample(0)};
        const float input = std::tanh(0.5f * (inputLeft + inputRight) * (0.2f + 0.8f * safeDwell));
        std::array<float, 2> output{};
        for (int channel = 0; channel < 2; ++channel) {
            const auto index = static_cast<size_t>(channel);
            const float damped = (1.0f - toneAlpha) * delayed[index] + toneAlpha * dampingState[index];
            dampingState[index] = damped;
            const float dispersed = allpass(allpass(damped, 0.72f, dispersionState1[index]), 0.48f, dispersionState2[index]);
            const float excitation = channel == 0 ? input : -0.72f * input;
            const float cross = delayed[1 - channel] * 0.11f;
            const float next = std::tanh(excitation * 0.42f + dispersed * feedback + cross);
            (channel == 0 ? left : right).pushSample(0, next);
            output[index] = 0.68f * (delayed[index] + 0.22f * dispersed);
        }
        return output;
    }
};

/** Dattorro-style plate network using the vendored ChowDSP lattice/tank primitives. */
struct ChowPlateState {
    using InputNetwork = chowdsp::Reverb::Dattorro::InputNetwork<>;
    using TankNetwork = chowdsp::Reverb::Dattorro::TankNetwork<>;
    std::array<InputNetwork, 2> inputDiffusion;
    TankNetwork tank;
    float currentDecayMs = -1.0f;
    float currentDampingHz = -1.0f;
    float currentDiffusion = -1.0f;

    void prepare() {
        for (auto& network : inputDiffusion) network.prepare(48000.0f);
        tank.prepare(48000.0f);
        setDiffusion(0.72f);
        chowdsp::Reverb::Dattorro::DefaultTankNetworkConfig<>::setDecayDiffusion1Parameters(tank, 0.68f);
        tank.setDecayAmount(0.82f);
        tank.setDampingFrequency(8500.0f);
    }
    void reset() {
        for (auto& network : inputDiffusion) network.reset();
        tank.reset();
        currentDecayMs = currentDampingHz = currentDiffusion = -1.0f;
    }
    void setDiffusion(float amount) {
        const float safe = std::clamp(amount, 0.0f, 1.0f);
        if (safe == currentDiffusion) return;
        for (auto& network : inputDiffusion) {
            chowdsp::Reverb::Dattorro::DefaultInputNetworkConfig<>::setInputDiffusionParameters(
                network, 0.45f + 0.42f * safe, 0.38f + 0.42f * safe);
        }
        currentDiffusion = safe;
    }
    std::array<float, 2> process(float left, float right, float decayMs, float dampingHz, float diffusion) {
        const float safeDecay = std::clamp(decayMs, 500.0f, 8000.0f);
        const float safeDamping = std::clamp(dampingHz, 1000.0f, 12000.0f);
        if (safeDecay != currentDecayMs) {
            const float decayGain = std::clamp(std::exp(-3.0f * 0.15f / (safeDecay * 0.001f)), 0.35f, 0.96f);
            tank.setDecayAmount(decayGain);
            currentDecayMs = safeDecay;
        }
        if (safeDamping != currentDampingHz) {
            tank.setDampingFrequency(safeDamping);
            currentDampingHz = safeDamping;
        }
        setDiffusion(diffusion);
        const float diffusedLeft = inputDiffusion[0].processSample(std::tanh(left));
        const float diffusedRight = inputDiffusion[1].processSample(std::tanh(right));
        const auto result = tank.processSample(diffusedLeft, diffusedRight);
        return {result.first, result.second};
    }
};

struct AirwindowsPlateState {
    void* instance = createKPlate140();
    AirwindowsPlateState() = default;
    ~AirwindowsPlateState() { destroyKPlate140(instance); }
    AirwindowsPlateState(const AirwindowsPlateState&) = delete;
    AirwindowsPlateState& operator=(const AirwindowsPlateState&) = delete;
    void process(const float* left, const float* right, int frames, float decayMs,
                 float predelayMs, float character, float* outLeft, float* outRight) {
        processKPlate140(instance, left, right, frames, decayMs, predelayMs, character, outLeft, outRight);
    }
};

struct AirwindowsTapeDelayState {
    void* instance = createTapeDelay2();
    AirwindowsTapeDelayState() = default;
    ~AirwindowsTapeDelayState() { destroyTapeDelay2(instance); }
    AirwindowsTapeDelayState(const AirwindowsTapeDelayState&) = delete;
    AirwindowsTapeDelayState& operator=(const AirwindowsTapeDelayState&) = delete;
    void process(const float* left, const float* right, int frames, float timeMs,
                 float feedback, float toneHz, float* outLeft, float* outRight) {
        processTapeDelay2(instance, left, right, frames, timeMs, feedback, toneHz, outLeft, outRight);
    }
};

struct MVerbState {
    MVerb<float> reverb;
    float currentDecay = -1.0f;
    float currentDamping = -1.0f;
    float currentDensity = -1.0f;
    std::array<float, 64> inputLeft{}, inputRight{}, outputLeft{}, outputRight{};
    float* inputs[2]{inputLeft.data(), inputRight.data()};
    float* outputs[2]{outputLeft.data(), outputRight.data()};

    explicit MVerbState(bool plateVoicing = false) {
        reverb.setSampleRate(48000.0f);
        reverb.setParameter(MVerb<float>::SIZE, plateVoicing ? 0.92f : 0.8f);
        reverb.setParameter(MVerb<float>::PREDELAY, plateVoicing ? 0.025f : 0.08f);
        reverb.setParameter(MVerb<float>::DENSITY, plateVoicing ? 0.92f : 0.75f);
        reverb.setParameter(MVerb<float>::BANDWIDTHFREQ, plateVoicing ? 0.82f : 0.9f);
        reverb.setParameter(MVerb<float>::GAIN, 1.0f);
        reverb.setParameter(MVerb<float>::MIX, 1.0f);
        reverb.setParameter(MVerb<float>::EARLYMIX, plateVoicing ? 0.25f : 0.5f);
    }

    void process(const float* left, const float* right, int frames, float decayMs,
                 float dampingHz, float density, float* outLeft, float* outRight) {
        const float safeDecay = std::clamp(decayMs, 500.0f, 10000.0f);
        const float safeDamping = std::clamp(dampingHz, 100.0f, 18500.0f);
        const float safeDensity = std::clamp(density, 0.0f, 1.0f);
        if (safeDecay != currentDecay) {
            reverb.setParameter(MVerb<float>::DECAY, (safeDecay - 500.0f) / 9500.0f);
            currentDecay = safeDecay;
        }
        if (safeDamping != currentDamping) {
            const float normalized = (safeDamping - 100.0f) / 18400.0f;
            reverb.setParameter(MVerb<float>::DAMPINGFREQ, 1.0f - normalized);
            currentDamping = safeDamping;
        }
        if (safeDensity != currentDensity) {
            reverb.setParameter(MVerb<float>::DENSITY, safeDensity);
            currentDensity = safeDensity;
        }
        int offset = 0;
        while (offset < frames) {
            const int count = std::min(frames - offset, static_cast<int>(inputLeft.size()));
            std::copy(left + offset, left + offset + count, inputLeft.begin());
            std::copy(right + offset, right + offset + count, inputRight.begin());
            reverb.process(inputs, outputs, count);
            std::copy(outputLeft.begin(), outputLeft.begin() + count, outLeft + offset);
            std::copy(outputRight.begin(), outputRight.begin() + count, outRight + offset);
            offset += count;
        }
    }
};

struct FxNativeProcessor {
    explicit FxNativeProcessor(int selectedType) : type(std::clamp(selectedType, 0, 11)) { prepare(); }
    int type = 0;
    std::unique_ptr<ChowDelayState> delay;
    std::unique_ptr<ChowSmoothState> smooth;
    std::unique_ptr<ChowShimmerState> shimmer;
    std::unique_ptr<ChowSpringState> spring;
    std::unique_ptr<ChowPingPongState> pingPong;
    std::unique_ptr<AirwindowsPlateState> airwindowsPlate;
    std::unique_ptr<MVerbState> mverb;
    std::unique_ptr<AirwindowsTapeDelayState> tapeDelay;
    std::unique_ptr<DualDelayState> dualDelay;
    std::unique_ptr<ChorusState> chorus;

    void prepare() {
        if (type < 2) { delay = std::make_unique<ChowDelayState>(); delay->prepare(); }
        else if (type == 2) { smooth = std::make_unique<ChowSmoothState>(); smooth->prepare(); }
        else if (type == 3) { shimmer = std::make_unique<ChowShimmerState>(); shimmer->prepare(); }
        else if (type == 4) { spring = std::make_unique<ChowSpringState>(); spring->prepare(); }
        else if (type == 5) { pingPong = std::make_unique<ChowPingPongState>(); pingPong->prepare(); }
        else if (type == 6) mverb = std::make_unique<MVerbState>(true);
        else if (type == 7) airwindowsPlate = std::make_unique<AirwindowsPlateState>();
        else if (type == 8) mverb = std::make_unique<MVerbState>();
        else if (type == 9) tapeDelay = std::make_unique<AirwindowsTapeDelayState>();
        else if (type == 10) dualDelay = std::make_unique<DualDelayState>();
        else { chorus = std::make_unique<ChorusState>(); chorus->prepare(); }
    }
    void reset() {
        if (delay) delay->reset();
        if (smooth) smooth->reset();
        if (shimmer) shimmer->reset();
        if (spring) spring->reset();
        if (pingPong) pingPong->reset();
        if (dualDelay) dualDelay->reset();
        if (chorus) chorus->reset();
    }
    std::array<float, 2> processStereo(float left, float right, float p1, float p2, float p3) {
        if (type == 0) return delay->processMatrix(left, right, p1, p2);
        if (type == 1) return delay->processByodBbd(left, right, p1, p2);
        if (type == 2) return smooth->process(left, right, p1, p2);
        if (type == 3) return shimmer->process(left, right, p1, p2, p3);
        if (type == 4) return spring->process(left, right, p1, p2, p3);
        if (type == 5) return pingPong->process(left, right, p1, p2, p3);
        if (type == 10) return dualDelay->process(left, right, p1, p2, p3);
        return {left, right};
    }

    void processStereoBlock(float* left, float* right, unsigned int frames,
                            float p1, float p2, float p3, float mix, float outputGainDb = 0.0f) {
        // Keep the dry path at unity and use MIX as a parallel wet send.
        // Per-block loudness normalization would pump during notes and tails.
        const float wetLevel = std::clamp(mix, 0.0f, 1.0f);
        if (type == 11) {
            chorus->process(left, right, frames, p1, p2, p3, wetLevel, outputGainDb);
            return;
        }
        if (type == 6) {
            std::array<float, 64> dryLeft{}, dryRight{}, wetLeft{}, wetRight{};
            unsigned int offset = 0;
            while (offset < frames) {
                const unsigned int count = std::min(frames - offset, static_cast<unsigned int>(dryLeft.size()));
                std::copy(left + offset, left + offset + count, dryLeft.begin());
                std::copy(right + offset, right + offset + count, dryRight.begin());
                mverb->process(dryLeft.data(), dryRight.data(), static_cast<int>(count),
                               p1, p2, p3, wetLeft.data(), wetRight.data());
                for (unsigned int frame = 0; frame < count; ++frame) {
                    left[offset + frame] = dryLeft[frame] + wetLeft[frame] * wetLevel;
                    right[offset + frame] = dryRight[frame] + wetRight[frame] * wetLevel;
                }
                offset += count;
            }
            return;
        }
        if (type < 7 || type == 10) {
            for (unsigned int frame = 0; frame < frames; ++frame) {
                const float dryLeft = left[frame];
                const float dryRight = right[frame];
                const auto wet = processStereo(dryLeft, dryRight, p1, p2, p3);
                left[frame] = dryLeft + wet[0] * wetLevel;
                right[frame] = dryRight + wet[1] * wetLevel;
            }
            return;
        }

        std::array<float, 64> dryLeft{}, dryRight{}, wetLeft{}, wetRight{};
        unsigned int offset = 0;
        while (offset < frames) {
            const unsigned int count = std::min(frames - offset, static_cast<unsigned int>(dryLeft.size()));
            std::copy(left + offset, left + offset + count, dryLeft.begin());
            std::copy(right + offset, right + offset + count, dryRight.begin());
            if (type == 7) airwindowsPlate->process(dryLeft.data(), dryRight.data(), static_cast<int>(count), p1, p2, p3, wetLeft.data(), wetRight.data());
            else if (type == 8) mverb->process(dryLeft.data(), dryRight.data(), static_cast<int>(count), p1, p2, p3, wetLeft.data(), wetRight.data());
            else tapeDelay->process(dryLeft.data(), dryRight.data(), static_cast<int>(count), p1, p2, p3, wetLeft.data(), wetRight.data());
            for (unsigned int frame = 0; frame < count; ++frame) {
                const auto index = static_cast<size_t>(frame);
                left[offset + frame] = dryLeft[index] + wetLeft[index] * wetLevel;
                right[offset + frame] = dryRight[index] + wetRight[index] * wetLevel;
            }
            offset += count;
        }
    }

    void processMonoBlock(float* mono, unsigned int frames,
                          float p1, float p2, float p3, float mix, float outputGainDb = 0.0f) {
        std::array<float, 64> left{}, right{};
        unsigned int offset = 0;
        while (offset < frames) {
            const unsigned int count = std::min(frames - offset, static_cast<unsigned int>(left.size()));
            for (unsigned int frame = 0; frame < count; ++frame) {
                left[frame] = mono[offset + frame];
                right[frame] = mono[offset + frame];
            }
            processStereoBlock(left.data(), right.data(), count, p1, p2, p3, mix, outputGainDb);
            for (unsigned int frame = 0; frame < count; ++frame) {
                mono[offset + frame] = 0.5f * (left[frame] + right[frame]);
            }
            offset += count;
        }
    }
};

} // namespace picolo
