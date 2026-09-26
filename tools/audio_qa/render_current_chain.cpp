#include "ChowFxNative.h"
#include "NAM/get_dsp.h"
#include "NAM/slimmable.h"
#include "ImpulseResponse.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

namespace {
constexpr int kSampleRate = 48000;
constexpr int kBlockSize = 64;
constexpr float kPi = 3.14159265358979323846f;
constexpr std::size_t kFrames = kSampleRate * 12;
constexpr float kNamGainDb = -10.0f;
constexpr float kAmpGainDb = -15.0f;
constexpr float kOutputGainDb = -10.0f;
constexpr float kFxMix = 0.35f;

struct Note {
    double start;
    double duration;
    float frequency;
    float gain;
};

constexpr std::array<Note, 8> kNotes{{
        {0.16, 0.48, 82.4069f, 0.70f},
        {0.72, 0.48, 110.0000f, 0.68f},
        {1.28, 0.48, 146.8324f, 0.66f},
        {1.84, 0.48, 196.0000f, 0.64f},
        {2.45, 0.84, 82.4069f, 0.38f},
        {2.45, 0.84, 123.4708f, 0.34f},
        {2.45, 0.84, 164.8138f, 0.30f},
        {2.45, 0.84, 207.6523f, 0.26f},
}};

float inputSample(double time) {
    float sum = 0.0f;
    for (const auto& note : kNotes) {
        const double elapsed = time - note.start;
        if (elapsed < 0.0 || elapsed >= note.duration) continue;
        const float attack = static_cast<float>(1.0 - std::exp(-elapsed * 110.0));
        const float envelope = attack * static_cast<float>(std::exp(-elapsed * 4.8));
        float tone = 0.0f;
        for (int harmonic = 1; harmonic <= 9; ++harmonic) {
            const float harmonicGain = 1.0f / std::pow(static_cast<float>(harmonic), 1.25f);
            const float inharmonicity = 1.0f + 0.00015f * static_cast<float>(harmonic * harmonic);
            tone += harmonicGain * std::sin(2.0f * kPi * note.frequency * harmonic * inharmonicity *
                                            static_cast<float>(elapsed));
        }
        sum += 0.22f * note.gain * envelope * tone;
    }
    return std::clamp(sum, -0.85f, 0.85f);
}

void writeU16(std::ofstream& out, std::uint16_t value) {
    const char bytes[2]{static_cast<char>(value & 0xff), static_cast<char>((value >> 8) & 0xff)};
    out.write(bytes, sizeof(bytes));
}

void writeU32(std::ofstream& out, std::uint32_t value) {
    const char bytes[4]{static_cast<char>(value & 0xff), static_cast<char>((value >> 8) & 0xff),
                        static_cast<char>((value >> 16) & 0xff), static_cast<char>((value >> 24) & 0xff)};
    out.write(bytes, sizeof(bytes));
}

struct Stats { double peak = 0.0; double squares = 0.0; std::uint64_t clipped = 0; };

Stats writeStereoWav(const std::filesystem::path& path, const std::vector<float>& left,
                     const std::vector<float>& right) {
    constexpr std::uint16_t channels = 2;
    constexpr std::uint16_t blockAlign = channels * sizeof(std::int16_t);
    const auto frames = std::min(left.size(), right.size());
    const auto bytes = static_cast<std::uint32_t>(frames * blockAlign);
    std::ofstream out(path, std::ios::binary);
    if (!out) throw std::runtime_error("Unable to write " + path.string());
    out.write("RIFF", 4); writeU32(out, 36 + bytes); out.write("WAVEfmt ", 8); writeU32(out, 16);
    writeU16(out, 1); writeU16(out, channels); writeU32(out, kSampleRate);
    writeU32(out, kSampleRate * blockAlign); writeU16(out, blockAlign); writeU16(out, 16);
    out.write("data", 4); writeU32(out, bytes);
    Stats stats;
    for (std::size_t i = 0; i < frames; ++i) {
        for (float sample : {left[i], right[i]}) {
            const float finite = std::isfinite(sample) ? sample : 0.0f;
            stats.peak = std::max(stats.peak, std::abs(static_cast<double>(finite)));
            stats.squares += static_cast<double>(finite) * finite;
            if (std::abs(finite) >= 0.999f) ++stats.clipped;
            writeU16(out, static_cast<std::uint16_t>(static_cast<std::int16_t>(
                    std::lrint(std::clamp(finite, -1.0f, 1.0f) * 32767.0f))));
        }
    }
    if (!out) throw std::runtime_error("Failed while writing " + path.string());
    return stats;
}

std::vector<float> processNam(const std::vector<float>& input, const std::filesystem::path& path,
                              float gainDb, bool normalize, bool fullQuality) {
    auto model = nam::get_dsp(path);
    if (!model) throw std::runtime_error("NAM load failed: " + path.string());
    if (std::abs(model->GetExpectedSampleRate() - kSampleRate) > 0.5) {
        throw std::runtime_error("NAM sample rate is not 48000 Hz: " + path.string());
    }
    if (fullQuality) {
        if (auto* slimmable = dynamic_cast<nam::SlimmableModel*>(model.get())) slimmable->SetSlimmableSize(1.0);
    }
    if (model->NumInputChannels() != 1 || model->NumOutputChannels() < 1) {
        throw std::runtime_error("Only mono NAM models are supported by this chain renderer");
    }
    model->Reset(kSampleRate, kBlockSize);
    std::vector<float> output(input.size());
    std::array<NAM_SAMPLE, kBlockSize> in{};
    std::array<NAM_SAMPLE, kBlockSize> out{};
    NAM_SAMPLE* inPtr = in.data();
    NAM_SAMPLE* outPtr = out.data();
    const float gain = std::pow(10.0f, gainDb / 20.0f);
    float normalizeGain = 1.0f;
    for (std::size_t offset = 0; offset < input.size(); offset += kBlockSize) {
        const auto count = static_cast<int>(std::min<std::size_t>(kBlockSize, input.size() - offset));
        for (int frame = 0; frame < count; ++frame) in[static_cast<std::size_t>(frame)] = input[offset + frame];
        model->process(&inPtr, &outPtr, count);
        float blockPeak = 0.0f;
        for (int frame = 0; frame < count; ++frame) {
            const float value = static_cast<float>(out[static_cast<std::size_t>(frame)]) * gain * normalizeGain;
            output[offset + static_cast<std::size_t>(frame)] = value;
            blockPeak = std::max(blockPeak, std::abs(value));
        }
        if (normalize && blockPeak > 0.0001f) {
            const float target = std::clamp(0.65f / blockPeak, 0.25f, 4.0f);
            normalizeGain = normalizeGain * 0.98f + target * 0.02f;
        } else if (!normalize) {
            normalizeGain = 1.0f;
        }
    }
    return output;
}

void processChorusMono(std::vector<float>& samples) {
    picolo::FxNativeProcessor chorus(11);
    for (std::size_t offset = 0; offset < samples.size(); offset += kBlockSize) {
        const auto count = static_cast<unsigned int>(std::min<std::size_t>(kBlockSize, samples.size() - offset));
        chorus.processMonoBlock(samples.data() + offset, count, 8.0f, 0.8f, 16000.0f, kFxMix, 0.0f);
    }
}

void processCabinetIr(std::vector<float>& samples, const std::filesystem::path& path) {
    dsp::ImpulseResponse ir(path.c_str(), kSampleRate);
    if (ir.GetWavState() != dsp::wav::LoadReturnCode::SUCCESS) {
        throw std::runtime_error("Cabinet IR load failed: " + path.string());
    }
    std::array<double, kBlockSize> in{};
    double* inPtr = in.data();
    double** inputPtrs = &inPtr;
    for (std::size_t offset = 0; offset < samples.size(); offset += kBlockSize) {
        const auto count = std::min<std::size_t>(kBlockSize, samples.size() - offset);
        for (std::size_t frame = 0; frame < count; ++frame) in[frame] = samples[offset + frame];
        double** result = ir.Process(inputPtrs, 1, count);
        for (std::size_t frame = 0; frame < count; ++frame) samples[offset + frame] = static_cast<float>(result[0][frame]);
    }
}

struct Rendered {
    std::vector<float> left;
    std::vector<float> right;
};

Rendered makeChain(const std::vector<float>& pedal, const std::filesystem::path& ampPath,
                   const std::filesystem::path& irPath, bool withFx) {
    auto afterChorus = pedal;
    if (withFx) processChorusMono(afterChorus);
    auto amp = processNam(afterChorus, ampPath, kAmpGainDb, true, true);
    processCabinetIr(amp, irPath);
    Rendered result{amp, amp};
    if (withFx) {
        std::srand(1);
        picolo::FxNativeProcessor tape(9);
        picolo::FxNativeProcessor plate(7);
        for (std::size_t offset = 0; offset < result.left.size(); offset += kBlockSize) {
            const auto count = static_cast<unsigned int>(std::min<std::size_t>(kBlockSize, result.left.size() - offset));
            tape.processStereoBlock(result.left.data() + offset, result.right.data() + offset, count,
                                    450.0f, 0.55f, 900.0f, kFxMix, 0.0f);
            plate.processStereoBlock(result.left.data() + offset, result.right.data() + offset, count,
                                     3000.0f, 40.0f, 0.9f, kFxMix, 0.0f);
        }
    }
    const float outputGain = std::pow(10.0f, kOutputGainDb / 20.0f);
    for (float& sample : result.left) sample *= outputGain;
    for (float& sample : result.right) sample *= outputGain;
    return result;
}

void writeStats(std::ofstream& report, const std::string& name, const Stats& stats) {
    const double samples = static_cast<double>(kFrames) * 2.0;
    report << name << "\n"
           << "  sample_rate_hz: " << kSampleRate << "\n"
           << "  channels: 2\n"
           << "  duration_seconds: " << static_cast<double>(kFrames) / kSampleRate << "\n"
           << "  peak_dbfs: " << (stats.peak > 0.0 ? 20.0 * std::log10(stats.peak) : -120.0) << "\n"
           << "  rms: " << std::sqrt(stats.squares / samples) << "\n"
           << "  clipped_samples: " << stats.clipped << "\n\n";
}
} // namespace

int main(int argc, char** argv) {
    if (argc != 5) {
        std::cerr << "Usage: tone3000_chain_qa <pedal.nam> <amp.nam> <cabinet.wav> <output-dir>\n";
        return 2;
    }
    try {
        const std::filesystem::path pedalPath(argv[1]);
        const std::filesystem::path ampPath(argv[2]);
        const std::filesystem::path irPath(argv[3]);
        const std::filesystem::path outputDir(argv[4]);
        std::filesystem::create_directories(outputDir);
        std::vector<float> input(kFrames);
        for (std::size_t frame = 0; frame < input.size(); ++frame) {
            input[frame] = 0.8f * inputSample(static_cast<double>(frame) / kSampleRate);
        }
        std::cout << "Rendering pedal NAM: " << pedalPath.filename() << "\n";
        auto pedal = processNam(input, pedalPath, kNamGainDb, false, false);
        auto full = makeChain(pedal, ampPath, irPath, true);
        auto reference = makeChain(pedal, ampPath, irPath, false);
        const auto fullStats = writeStereoWav(outputDir / "current_chain_full.wav", full.left, full.right);
        const auto referenceStats = writeStereoWav(outputDir / "current_chain_nam_ir_reference.wav", reference.left, reference.right);
        std::ofstream report(outputDir / "report.txt");
        report << "Tone3000M1 active-chain offline render\n"
               << "Input: deterministic guitar-like probe, 48 kHz\n"
               << "Order: Fortin Modded TS-9 NAM -> Chorus mono -> EVH 5150 III NAM (A2 Full) -> Mesa Oversized SM57 and VR2 5150 Power IR -> Tape Delay 2 -> kPlate140\n"
               << "NAM gains: pedal -10 dB, amp -15 dB with block normalization; output -10 dB\n"
               << "FXNative mix: 35%; chorus 8 ms / 0.8 Hz / 16 kHz; tape delay 450 ms / 0.55 / 900 Hz; kPlate140 3000 ms / 40 Hz / 0.9\n"
               << "IR placement: after amp; same fixed -18 dB convolution trim as the app; cabinet mix 100%\n\n";
        writeStats(report, "current_chain_full.wav", fullStats);
        writeStats(report, "current_chain_nam_ir_reference.wav", referenceStats);
        if (!report) throw std::runtime_error("Unable to write report");
        std::cout << "Wrote current_chain_full.wav and current_chain_nam_ir_reference.wav to " << outputDir << "\n";
    } catch (const std::exception& error) {
        std::cerr << "Render failed: " << error.what() << "\n";
        return 1;
    }
    return 0;
}
