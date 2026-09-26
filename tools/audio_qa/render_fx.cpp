#include "ChowFxNative.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {
constexpr int kSampleRate = 48000;
constexpr int kBlockSize = 64;
constexpr float kMix = 0.35f;
constexpr float kPi = 3.14159265358979323846f;
constexpr double kGuitarSignalSeconds = 3.4;
constexpr double kImpulsePreRollSeconds = 0.1;
constexpr double kRenderSeconds = 12.0;
constexpr std::size_t kRenderFrames = static_cast<std::size_t>(kSampleRate * kRenderSeconds);
constexpr std::size_t kTailStartFrame = static_cast<std::size_t>(kSampleRate * 3.5);

struct Note {
    double startSeconds;
    double durationSeconds;
    float frequency;
    float gain;
};

constexpr std::array<Note, 8> kProbeNotes{{
        {0.16, 0.48, 82.4069f, 0.70f},
        {0.72, 0.48, 110.0000f, 0.68f},
        {1.28, 0.48, 146.8324f, 0.66f},
        {1.84, 0.48, 196.0000f, 0.64f},
        {2.45, 0.84, 82.4069f, 0.38f},
        {2.45, 0.84, 123.4708f, 0.34f},
        {2.45, 0.84, 164.8138f, 0.30f},
        {2.45, 0.84, 207.6523f, 0.26f},
}};

struct EffectCase {
    int type;
    const char* name;
    const char* filename;
    float param1;
    float param2;
    float param3;
};

constexpr std::array<EffectCase, 3> kEffects{{
        {7, "Airwindows kPlate140", "kplate140", 3000.0f, 40.0f, 0.90f},
        {8, "MVerb", "mverb", 5000.0f, 8500.0f, 0.72f},
        {9, "Airwindows Tape Delay 2", "tape_delay2", 450.0f, 0.55f, 900.0f},
}};

constexpr std::array<EffectCase, 12> kPlacementEffects{{
        {0, "ChowMatrix Delay", "chowmatrix_delay", 350.0f, 0.35f, 12.0f},
        {1, "BYOD BBD Delay", "byod_bbd_delay", 350.0f, 0.35f, 12.0f},
        {2, "BYOD Smooth Reverb", "byod_smooth_reverb", 2000.0f, 0.5f, 12.0f},
        {3, "BYOD Shimmer Reverb", "byod_shimmer_reverb", 150.0f, 5000.0f, 0.0f},
        {4, "Spring Reverb", "spring_reverb", 2000.0f, 0.5f, 6500.0f},
        {5, "Ping-Pong Delay", "ping_pong_delay", 350.0f, 0.35f, 0.72f},
        {6, "Plate Reverb", "plate_reverb", 3000.0f, 8500.0f, 0.72f},
        {7, "Airwindows kPlate140", "kplate140", 3000.0f, 40.0f, 0.90f},
        {8, "MVerb Reverb", "mverb", 5000.0f, 8500.0f, 0.72f},
        {9, "Airwindows Tape Delay 2", "tape_delay2", 450.0f, 0.55f, 900.0f},
        {10, "Dual Delay", "dual_delay", 350.0f, 0.35f, 525.0f},
        {11, "Chorus", "chorus", 8.0f, 0.8f, 16000.0f},
}};

float noteSample(double timeSeconds, const Note& note) {
    const double elapsed = timeSeconds - note.startSeconds;
    if (elapsed < 0.0 || elapsed >= note.durationSeconds) return 0.0f;

    const float attack = static_cast<float>(1.0 - std::exp(-elapsed * 110.0));
    const float envelope = attack * static_cast<float>(std::exp(-elapsed * 4.8));
    float sample = 0.0f;
    for (int harmonic = 1; harmonic <= 9; ++harmonic) {
        const float harmonicGain = 1.0f / std::pow(static_cast<float>(harmonic), 1.25f);
        const float inharmonicity = 1.0f + 0.00015f * static_cast<float>(harmonic * harmonic);
        const float phase = 2.0f * kPi * note.frequency * static_cast<float>(harmonic) * inharmonicity * static_cast<float>(elapsed);
        sample += harmonicGain * std::sin(phase);
    }
    return 0.22f * note.gain * envelope * sample;
}

float guitarProbe(double timeSeconds) {
    float result = 0.0f;
    for (const auto& note : kProbeNotes) result += noteSample(timeSeconds, note);
    return std::clamp(result, -0.85f, 0.85f);
}

std::vector<float> makeInput(bool impulse) {
    std::vector<float> input(kRenderFrames, 0.0f);
    if (impulse) {
        input[static_cast<std::size_t>(kSampleRate * kImpulsePreRollSeconds)] = 0.5f;
        return input;
    }
    for (std::size_t frame = 0; frame < input.size(); ++frame) {
        const double timeSeconds = static_cast<double>(frame) / kSampleRate;
        if (timeSeconds < kGuitarSignalSeconds) input[frame] = guitarProbe(timeSeconds);
    }
    return input;
}

void writeU16(std::ofstream& stream, std::uint16_t value) {
    const char bytes[2]{static_cast<char>(value & 0xff), static_cast<char>((value >> 8) & 0xff)};
    stream.write(bytes, sizeof(bytes));
}

void writeU32(std::ofstream& stream, std::uint32_t value) {
    const char bytes[4]{
            static_cast<char>(value & 0xff),
            static_cast<char>((value >> 8) & 0xff),
            static_cast<char>((value >> 16) & 0xff),
            static_cast<char>((value >> 24) & 0xff),
    };
    stream.write(bytes, sizeof(bytes));
}

struct Stats {
    double peak = 0.0;
    double sumSquares = 0.0;
    double tailSquares = 0.0;
    double sideSquares = 0.0;
    std::array<double, 12> secondSquares{};
    std::array<std::uint64_t, 12> secondSampleCounts{};
    std::int64_t firstAboveThresholdFrame = -1;
    std::uint64_t clippedSamples = 0;
    std::uint64_t nonFiniteSamples = 0;
};

struct StereoBuffer {
    std::vector<float> left;
    std::vector<float> right;
};

void writeWav(const std::filesystem::path& path, const std::vector<float>& left,
              const std::vector<float>& right, Stats& stats) {
    constexpr std::uint16_t channels = 2;
    constexpr std::uint16_t bitsPerSample = 16;
    constexpr std::uint16_t blockAlign = channels * (bitsPerSample / 8);
    const auto frames = std::min(left.size(), right.size());
    const auto dataBytes = static_cast<std::uint32_t>(frames * blockAlign);
    std::ofstream stream(path, std::ios::binary);
    if (!stream) throw std::runtime_error("Could not create " + path.string());
    stream.write("RIFF", 4);
    writeU32(stream, 36 + dataBytes);
    stream.write("WAVEfmt ", 8);
    writeU32(stream, 16);
    writeU16(stream, 1);
    writeU16(stream, channels);
    writeU32(stream, kSampleRate);
    writeU32(stream, kSampleRate * blockAlign);
    writeU16(stream, blockAlign);
    writeU16(stream, bitsPerSample);
    stream.write("data", 4);
    writeU32(stream, dataBytes);

    for (std::size_t frame = 0; frame < frames; ++frame) {
        const float samples[2]{left[frame], right[frame]};
        float safeSamples[2]{samples[0], samples[1]};
        for (float& sample : safeSamples) {
            if (!std::isfinite(sample)) {
                ++stats.nonFiniteSamples;
                sample = 0.0f;
            }
        }
        const float side = 0.5f * (safeSamples[0] - safeSamples[1]);
        stats.sideSquares += static_cast<double>(side) * side;
        const auto second = std::min<std::size_t>(frame / kSampleRate, stats.secondSquares.size() - 1);
        if (stats.firstAboveThresholdFrame < 0 &&
            std::max(std::abs(safeSamples[0]), std::abs(safeSamples[1])) >= 0.001f) {
            stats.firstAboveThresholdFrame = static_cast<std::int64_t>(frame);
        }
        for (float raw : safeSamples) {
            stats.peak = std::max(stats.peak, std::abs(static_cast<double>(raw)));
            stats.sumSquares += static_cast<double>(raw) * raw;
            if (frame >= kTailStartFrame) stats.tailSquares += static_cast<double>(raw) * raw;
            stats.secondSquares[second] += static_cast<double>(raw) * raw;
            ++stats.secondSampleCounts[second];
            if (std::abs(raw) >= 0.999f) ++stats.clippedSamples;
            const float value = std::clamp(raw, -1.0f, 1.0f);
            const auto pcm = static_cast<std::int16_t>(std::lrint(value * 32767.0f));
            writeU16(stream, static_cast<std::uint16_t>(pcm));
        }
    }
    if (!stream) throw std::runtime_error("Could not finish " + path.string());
}

StereoBuffer renderEffect(const std::vector<float>& input, const EffectCase& effect, bool impulse) {
    std::srand(1); // Airwindows initializes its tiny denormal dither state from rand().
    picolo::FxNativeProcessor processor(effect.type);
    StereoBuffer output{std::vector<float>(input.size()), std::vector<float>(input.size())};
    for (std::size_t frame = 0; frame < input.size(); ++frame) {
        output.left[frame] = input[frame];
        output.right[frame] = input[frame];
    }
    for (std::size_t offset = 0; offset < input.size(); offset += kBlockSize) {
        const auto frames = static_cast<unsigned int>(std::min<std::size_t>(kBlockSize, input.size() - offset));
        processor.processStereoBlock(output.left.data() + offset, output.right.data() + offset, frames,
                                     effect.param1, effect.param2, effect.param3, impulse ? 1.0f : kMix);
    }
    return output;
}

double rms(double sumSquares, std::size_t sampleCount) {
    return sampleCount == 0 ? 0.0 : std::sqrt(sumSquares / static_cast<double>(sampleCount));
}

void writeReportLine(std::ofstream& report, const std::string& label, const Stats& stats,
                     double signalStartSeconds = 0.0) {
    const auto totalSamples = kRenderFrames * 2;
    const auto tailSamples = (kRenderFrames - kTailStartFrame) * 2;
    report << label << "\n"
           << "  peak: " << stats.peak << " (" << (stats.peak > 0.0 ? 20.0 * std::log10(stats.peak) : -120.0) << " dBFS)\n"
           << "  rms: " << rms(stats.sumSquares, totalSamples) << "\n"
           << "  tail_rms_after_3.5s: " << rms(stats.tailSquares, tailSamples) << "\n"
           << "  stereo_side_rms: " << rms(stats.sideSquares, kRenderFrames) << "\n"
           << "  first_sample_above_-60dBFS_ms: "
           << (stats.firstAboveThresholdFrame < 0 ? -1.0 : 1000.0 * (stats.firstAboveThresholdFrame / static_cast<double>(kSampleRate) - signalStartSeconds)) << "\n"
           << "  samples_at_or_above_-0.009dBFS: " << stats.clippedSamples << "\n"
           << "  non_finite_samples: " << stats.nonFiniteSamples << "\n"
           << "  rms_by_second:";
    for (std::size_t second = 0; second < stats.secondSquares.size(); ++second) {
        report << " " << second << "s=" << rms(stats.secondSquares[second], stats.secondSampleCounts[second]);
    }
    report << "\n\n";
}

Stats writeEffectRender(const std::filesystem::path& outputDir, const std::vector<float>& input,
                        const EffectCase& effect, bool impulse) {
    auto audio = renderEffect(input, effect, impulse);
    Stats stats;
    const std::string prefix = impulse ? "impulse_" : "guitar_";
    writeWav(outputDir / (prefix + effect.filename + ".wav"), audio.left, audio.right, stats);
    return stats;
}

float syntheticAmp(float sample) {
    return 0.7f * std::tanh(3.5f * sample);
}

StereoBuffer renderPlacement(const std::vector<float>& input, const EffectCase& effect, bool preAmp) {
    std::srand(1);
    picolo::FxNativeProcessor processor(effect.type);
    StereoBuffer output{std::vector<float>(input.size()), std::vector<float>(input.size())};

    if (preAmp) {
        std::vector<float> mono(input);
        for (std::size_t offset = 0; offset < mono.size(); offset += kBlockSize) {
            const auto frames = static_cast<unsigned int>(
                    std::min<std::size_t>(kBlockSize, mono.size() - offset));
            processor.processMonoBlock(mono.data() + offset, frames,
                                       effect.param1, effect.param2, effect.param3, kMix);
            for (unsigned int frame = 0; frame < frames; ++frame)
                mono[offset + frame] = syntheticAmp(mono[offset + frame]);
        }
        output.left = mono;
        output.right = std::move(mono);
        return output;
    }

    for (std::size_t frame = 0; frame < input.size(); ++frame) {
        const float ampOutput = syntheticAmp(input[frame]);
        output.left[frame] = ampOutput;
        output.right[frame] = ampOutput;
    }
    for (std::size_t offset = 0; offset < input.size(); offset += kBlockSize) {
        const auto frames = static_cast<unsigned int>(
                std::min<std::size_t>(kBlockSize, input.size() - offset));
        processor.processStereoBlock(output.left.data() + offset, output.right.data() + offset, frames,
                                     effect.param1, effect.param2, effect.param3, kMix);
    }
    return output;
}

void renderPlacementSet(const std::filesystem::path& outputDir) {
    const auto placementDir = outputDir / "placement";
    std::filesystem::create_directories(placementDir);
    const auto guitar = makeInput(false);
    std::ofstream report(placementDir / "report.txt");
    if (!report) throw std::runtime_error("Could not create placement/report.txt");
    report << "Tone3000M1 FXNative placement comparison\n"
           << "Rate: 48000 Hz | block: 64 frames | stereo PCM16 WAV\n"
           << "Input: deterministic synthetic guitar phrase; 35% parallel wet mix\n"
           << "PRE: FXNative folded to mono before a synthetic amp saturation stage.\n"
           << "POST: the same amp output feeds FXNative after the amp in stereo.\n"
           << "The amp stage is a repeatable saturator, not a NAM capture.\n"
           << "No phone, audio device, microphone, or Android audio path is used.\n\n";

    std::vector<float> ampReference(guitar.size());
    for (std::size_t frame = 0; frame < guitar.size(); ++frame) {
        ampReference[frame] = syntheticAmp(guitar[frame]);
    }
    Stats referenceStats;
    writeWav(placementDir / "amp_reference.wav", ampReference, ampReference, referenceStats);
    writeReportLine(report, "amp_reference.wav", referenceStats);

    for (const auto& effect : kPlacementEffects) {
        report << "Effect: " << effect.name << " (type " << effect.type << ")\n"
               << "  parameters: " << effect.param1 << ", " << effect.param2 << ", " << effect.param3
               << " | mix: " << kMix << "\n";
        for (const bool preAmp : {true, false}) {
            auto audio = renderPlacement(guitar, effect, preAmp);
            const std::string placementName = preAmp ? "pre_amp_" : "post_amp_";
            const std::string filename = placementName + effect.filename + ".wav";
            Stats stats;
            writeWav(placementDir / filename, audio.left, audio.right, stats);
            writeReportLine(report, filename, stats);
        }
    }
}
} // namespace

int main(int argc, char** argv) {
    try {
        if (argc != 2 && argc != 3) {
            std::cerr << "Usage: tone3000_audio_qa <output-directory> [placement]\n";
            return 2;
        }
        const std::filesystem::path outputDir(argv[1]);
        std::filesystem::create_directories(outputDir);
        if (argc == 3 && std::string(argv[2]) == "placement") {
            renderPlacementSet(outputDir);
            std::cout << "Placement comparison WAVs and report saved to " << outputDir / "placement" << "\n";
            return 0;
        }
        if (argc == 3) {
            std::cerr << "Unknown render mode: " << argv[2] << "\n";
            return 2;
        }
        std::ofstream report(outputDir / "report.txt");
        if (!report) throw std::runtime_error("Could not create report.txt");
        report << "Tone3000M1 offline FX render\n"
               << "Rate: 48000 Hz | block: 64 frames | output: stereo PCM16\n"
               << "Signal: deterministic synthetic guitar DI and impulse; no device I/O\n"
               << "Guitar wet mix: " << kMix << " | impulse wet mix: 1.0 | render: " << kRenderSeconds << " seconds\n"
               << "Random seed: 1 (Airwindows denormal dither initialization)\n\n";

        const auto guitar = makeInput(false);
        std::vector<float> reference(guitar);
        std::vector<float> referenceRight(reference);
        Stats dryStats;
        writeWav(outputDir / "dry_reference.wav", reference, referenceRight, dryStats);
        writeReportLine(report, "dry_reference.wav", dryStats);

        for (const auto& effect : kEffects) {
            report << "Effect: " << effect.name << " (type " << effect.type << ")\n"
                   << "  parameters: " << effect.param1 << ", " << effect.param2 << ", " << effect.param3 << "\n";
            writeReportLine(report, "guitar_" + std::string(effect.filename) + ".wav",
                            writeEffectRender(outputDir, guitar, effect, false));
            report << "Effect: " << effect.name << " impulse response\n";
            const auto impulse = makeInput(true);
            writeReportLine(report, "impulse_" + std::string(effect.filename) + ".wav",
                            writeEffectRender(outputDir, impulse, effect, true), kImpulsePreRollSeconds);
        }

        std::cout << "Rendered WAVs to " << outputDir << "\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "audio QA render failed: " << error.what() << "\n";
        return 1;
    }
}
