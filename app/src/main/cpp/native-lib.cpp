#include <jni.h>
#include <android/log.h>

#include <tinyalsa/pcm.h>

#include <NAM/activations.h>
#include <NAM/get_dsp.h>
#include <NAM/slimmable.h>
#include "ImpulseResponse.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <map>
#include <memory>
#include <mutex>
#include <pthread.h>
#include <sched.h>
#include <set>
#include <sstream>
#include <string>
#include <sys/resource.h>
#include <thread>
#include <vector>

#define LOG_TAG "Tone3000Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


namespace {

    constexpr unsigned int TARGET_RATE = 48000;
    constexpr unsigned int DEFAULT_BLOCK_SIZE = 64;

    /*
     * Keep the realtime processing quantum at 64 frames (1.333 ms),
     * but give ALSA more ring-buffer headroom for rare Android/USB
     * scheduling stalls.
     *
     * 8 x 64 @ 48 kHz = 512 frames = 10.667 ms of ring capacity.
     *
     * We explicitly keep avail_min/start_threshold at one period below,
     * so the processing cadence remains 64 frames instead of waiting for
     * a large part of the ring to fill.
     */
    constexpr unsigned int DEFAULT_PERIOD_COUNT = 8;

    /*
     * Gapless preset change:
     * old and new NAM run in parallel for two 64-frame blocks.
     *
     * 128 samples @ 48 kHz = 2.667 ms.
     */
    constexpr unsigned int PRESET_CROSSFADE_SAMPLES = 128;

    /* All NAM slots are peers in one serial signal chain. */
    constexpr unsigned int MAX_NAM_BLOCKS = 4;

    /*
     * A2 / SlimmableContainer quality.
     *
     * A2 stores Full and Lite as slim points in the same model.
     * NeuralAmpModelerCore maps the lowest slim value to the
     * smallest/lowest-compute tier.
     *
     * For this validation build we force A2-Lite on every slimmable NAM.
     */
    constexpr double A2_LITE_SLIM_SIZE = 0.0;

    struct UsbCard {
        unsigned int card = 0;
        std::string name;
    };

    struct PcmEndpoint {
        unsigned int card = 0;
        unsigned int device = 0;
        bool capture = false;
        bool playback = false;
        std::string cardName;
    };

    static std::string trim(const std::string& value) {
        const auto begin = value.find_first_not_of(" \t\r\n");

        if (begin == std::string::npos) {
            return "";
        }

        const auto end = value.find_last_not_of(" \t\r\n");

        return value.substr(
                begin,
                end - begin + 1
        );
    }

    static std::map<unsigned int, std::string> findUsbCards() {
        std::map<unsigned int, std::string> result;

        std::ifstream file(
                "/proc/asound/cards"
        );

        if (!file.is_open()) {
            return result;
        }

        std::string line;

        while (std::getline(file, line)) {
            if (
                    line.find("USB-Audio") ==
                    std::string::npos
                    ) {
                continue;
            }

            std::istringstream stream(
                    line
            );

            unsigned int card = 0;

            if (!(stream >> card)) {
                continue;
            }

            std::string name =
                    "USB Audio";

            const auto separator =
                    line.find(" - ");

            if (
                    separator !=
                    std::string::npos
                    ) {
                name =
                        trim(
                                line.substr(
                                        separator + 3
                                )
                        );
            }

            result[card] =
                    name;
        }

        return result;
    }

    static std::vector<PcmEndpoint> findUsbPcmEndpoints() {
        std::vector<PcmEndpoint> result;

        const auto usbCards =
                findUsbCards();

        if (usbCards.empty()) {
            return result;
        }

        std::ifstream file(
                "/proc/asound/pcm"
        );

        if (!file.is_open()) {
            return result;
        }

        std::string line;

        while (std::getline(file, line)) {
            unsigned int card = 0;
            unsigned int device = 0;

            if (
                    std::sscanf(
                            line.c_str(),
                            " %u-%u:",
                            &card,
                            &device
                    ) != 2
                    ) {
                continue;
            }

            const auto cardIt =
                    usbCards.find(
                            card
                    );

            if (
                    cardIt ==
                    usbCards.end()
                    ) {
                continue;
            }

            PcmEndpoint endpoint;

            endpoint.card =
                    card;

            endpoint.device =
                    device;

            endpoint.capture =
                    line.find("capture") !=
                    std::string::npos;

            endpoint.playback =
                    line.find("playback") !=
                    std::string::npos;

            endpoint.cardName =
                    cardIt->second;

            result.push_back(
                    endpoint
            );
        }

        return result;
    }

    static const char* formatName(
            pcm_format format
    ) {
        switch (format) {
            case PCM_FORMAT_S16_LE:
                return "S16_LE";

            case PCM_FORMAT_S24_LE:
                return "S24_LE";

            case PCM_FORMAT_S24_3LE:
                return "S24_3LE";

            case PCM_FORMAT_S32_LE:
                return "S32_LE";

            case PCM_FORMAT_FLOAT_LE:
                return "FLOAT_LE";

            default:
                return "UNKNOWN";
        }
    }

    static size_t bytesPerSample(
            pcm_format format
    ) {
        switch (format) {
            case PCM_FORMAT_S16_LE:
                return 2;

            case PCM_FORMAT_S24_3LE:
                return 3;

            case PCM_FORMAT_S24_LE:
            case PCM_FORMAT_S32_LE:
            case PCM_FORMAT_FLOAT_LE:
                return 4;

            default:
                return 0;
        }
    }

    static std::vector<pcm_format> supportedFormats(
            pcm_params* params
    ) {
        std::vector<pcm_format> result;

        /*
         * Preferimos 32-bit primeiro.
         *
         * A EVO4 usa S32_LE, mas não há nenhuma
         * dependência específica dela aqui.
         */
        const std::array<pcm_format, 5> preferred = {
                PCM_FORMAT_S32_LE,
                PCM_FORMAT_S24_LE,
                PCM_FORMAT_S24_3LE,
                PCM_FORMAT_S16_LE,
                PCM_FORMAT_FLOAT_LE
        };

        for (
            const auto format :
                preferred
                ) {
            if (
                    pcm_params_format_test(
                            params,
                            format
                    )
                    ) {
                result.push_back(
                        format
                );
            }
        }

        return result;
    }

    static void addUnique(
            std::vector<unsigned int>& values,
            unsigned int value
    ) {
        if (
                std::find(
                        values.begin(),
                        values.end(),
                        value
                ) ==
                values.end()
                ) {
            values.push_back(
                    value
            );
        }
    }

    static std::vector<unsigned int> channelCandidates(
            pcm_params* params
    ) {
        const unsigned int minChannels =
                pcm_params_get_min(
                        params,
                        PCM_PARAM_CHANNELS
                );

        const unsigned int maxChannels =
                pcm_params_get_max(
                        params,
                        PCM_PARAM_CHANNELS
                );

        std::vector<unsigned int> result;

        /*
         * Preferência típica para interfaces USB.
         */
        const std::array<unsigned int, 5> preferred = {
                2,
                1,
                4,
                6,
                8
        };

        for (
            const auto channels :
                preferred
                ) {
            if (
                    channels >= minChannels &&
                    channels <= maxChannels
                    ) {
                addUnique(
                        result,
                        channels
                );
            }
        }

        if (minChannels > 0) {
            addUnique(
                    result,
                    minChannels
            );
        }

        if (maxChannels > 0) {
            addUnique(
                    result,
                    maxChannels
            );
        }

        return result;
    }

    static bool supportsValue(
            pcm_params* params,
            pcm_param parameter,
            unsigned int value
    ) {
        const unsigned int min =
                pcm_params_get_min(
                        params,
                        parameter
                );

        const unsigned int max =
                pcm_params_get_max(
                        params,
                        parameter
                );

        return
                value >= min &&
                value <= max;
    }

    static unsigned int choosePeriodCount(
            pcm_params* params,
            unsigned int preferred
    ) {
        const unsigned int min =
                pcm_params_get_min(
                        params,
                        PCM_PARAM_PERIODS
                );

        const unsigned int max =
                pcm_params_get_max(
                        params,
                        PCM_PARAM_PERIODS
                );

        if (
                preferred >= min &&
                preferred <= max
                ) {
            return preferred;
        }

        if (
                DEFAULT_PERIOD_COUNT >= min &&
                DEFAULT_PERIOD_COUNT <= max
                ) {
            return DEFAULT_PERIOD_COUNT;
        }

        if (min > 0) {
            return min;
        }

        return preferred > 0
               ? preferred
               : DEFAULT_PERIOD_COUNT;
    }

    static float clampSample(
            float value
    ) {
        return std::clamp(
                value,
                -1.0f,
                0.99999994f
        );
    }


    static uint64_t fnv1aUpdate(
            uint64_t hash,
            const void* data,
            size_t size
    ) {
        const auto* bytes =
                static_cast<const uint8_t*>(
                        data
                );

        for (size_t i = 0; i < size; ++i) {
            hash ^=
                    static_cast<uint64_t>(
                            bytes[i]
                    );

            hash *=
                    1099511628211ULL;
        }

        return hash;
    }

    static uint64_t hashFileFNV1a(
            const std::string& path
    ) {
        constexpr uint64_t OFFSET =
                1469598103934665603ULL;

        std::ifstream file(
                path,
                std::ios::binary
        );

        if (!file.is_open()) {
            return 0;
        }

        uint64_t hash =
                OFFSET;

        std::array<char, 8192> buffer{};

        while (file.good()) {
            file.read(
                    buffer.data(),
                    static_cast<std::streamsize>(
                            buffer.size()
                    )
            );

            const auto count =
                    file.gcount();

            if (count <= 0) {
                break;
            }

            hash =
                    fnv1aUpdate(
                            hash,
                            buffer.data(),
                            static_cast<size_t>(
                                    count
                            )
                    );
        }

        return hash;
    }

    static std::string hex64(
            uint64_t value
    ) {
        std::ostringstream out;

        out
                << std::hex
                << std::uppercase
                << std::setfill('0')
                << std::setw(16)
                << value;

        return out.str();
    }

    static std::string cpuMaskToString(
            uint64_t mask
    ) {
        std::ostringstream out;

        bool first =
                true;

        for (
                int cpu = 0;
                cpu < 64;
                ++cpu
                ) {
            if (
                    (
                            mask &
                            (
                                    1ULL <<
                                         cpu
                            )
                    ) ==
                    0
                    ) {
                continue;
            }

            if (!first) {
                out << ",";
            }

            out << cpu;

            first =
                    false;
        }

        if (first) {
            return "none";
        }

        return out.str();
    }


    static bool readUnsignedFile(
            const std::string& path,
            uint64_t& value
    ) {
        std::ifstream file(
                path
        );

        if (!file.is_open()) {
            return false;
        }

        uint64_t parsed =
                0;

        if (!(file >> parsed)) {
            return false;
        }

        value =
                parsed;

        return true;
    }


    static std::string currentCpuFrequenciesString(
            uint64_t mask
    ) {
        if (mask == 0) {
            return "none";
        }

        std::ostringstream out;

        bool first =
                true;

        for (
                int cpu = 0;
                cpu < 64;
                ++cpu
                ) {
            if (
                    (
                            mask &
                            (
                                    1ULL <<
                                         cpu
                            )
                    ) ==
                    0
                    ) {
                continue;
            }

            uint64_t frequency =
                    0;

            const std::string scalingCurPath =
                    "/sys/devices/system/cpu/cpu" +
                    std::to_string(
                            cpu
                    ) +
                    "/cpufreq/scaling_cur_freq";

            const bool found =
                    readUnsignedFile(
                            scalingCurPath,
                            frequency
                    );

            if (!first) {
                out << " ";
            }

            out
                    << "cpu"
                    << cpu
                    << "=";

            if (found) {
                out
                        << frequency
                        << "kHz";
            } else {
                out
                        << "?";
            }

            first =
                    false;
        }

        return out.str();
    }


    static double linearToDbFS(
            float value
    ) {
        const double magnitude =
                std::max(
                        static_cast<double>(
                                std::abs(value)
                        ),
                        1.0e-12
                );

        return
                20.0 *
                std::log10(
                        magnitude
                );
    }

    static int32_t signExtend24(
            uint32_t value
    ) {
        value &=
                0x00FFFFFFu;

        if (
                value &
                0x00800000u
                ) {
            value |=
                    0xFF000000u;
        }

        return static_cast<int32_t>(
                value
        );
    }

    static float readSample(
            const uint8_t* buffer,
            unsigned int frame,
            unsigned int channel,
            unsigned int channels,
            pcm_format format
    ) {
        const size_t sampleBytes =
                bytesPerSample(
                        format
                );

        const size_t offset =
                (
                        static_cast<size_t>(frame) *
                        channels +
                        channel
                ) *
                sampleBytes;

        const uint8_t* ptr =
                buffer + offset;

        switch (format) {
            case PCM_FORMAT_S16_LE: {
                int16_t value = 0;

                std::memcpy(
                        &value,
                        ptr,
                        sizeof(value)
                );

                return
                        static_cast<float>(value) /
                        32768.0f;
            }

            case PCM_FORMAT_S32_LE: {
                int32_t value = 0;

                std::memcpy(
                        &value,
                        ptr,
                        sizeof(value)
                );

                return
                        static_cast<float>(
                                static_cast<double>(value) /
                                2147483648.0
                        );
            }

            case PCM_FORMAT_S24_LE: {
                uint32_t raw = 0;

                std::memcpy(
                        &raw,
                        ptr,
                        sizeof(raw)
                );

                const int32_t value =
                        signExtend24(
                                raw
                        );

                return
                        static_cast<float>(value) /
                        8388608.0f;
            }

            case PCM_FORMAT_S24_3LE: {
                uint32_t raw =
                        static_cast<uint32_t>(ptr[0]) |
                        (
                                static_cast<uint32_t>(ptr[1])
                                        << 8u
                        ) |
                        (
                                static_cast<uint32_t>(ptr[2])
                                        << 16u
                        );

                const int32_t value =
                        signExtend24(
                                raw
                        );

                return
                        static_cast<float>(value) /
                        8388608.0f;
            }

            case PCM_FORMAT_FLOAT_LE: {
                float value = 0.0f;

                std::memcpy(
                        &value,
                        ptr,
                        sizeof(value)
                );

                return value;
            }

            default:
                return 0.0f;
        }
    }

    static void writeSample(
            uint8_t* buffer,
            unsigned int frame,
            unsigned int channel,
            unsigned int channels,
            pcm_format format,
            float sample
    ) {
        sample =
                clampSample(
                        sample
                );

        const size_t sampleBytes =
                bytesPerSample(
                        format
                );

        const size_t offset =
                (
                        static_cast<size_t>(frame) *
                        channels +
                        channel
                ) *
                sampleBytes;

        uint8_t* ptr =
                buffer + offset;

        switch (format) {
            case PCM_FORMAT_S16_LE: {
                const int16_t value =
                        static_cast<int16_t>(
                                std::lround(
                                        sample *
                                        32767.0f
                                )
                        );

                std::memcpy(
                        ptr,
                        &value,
                        sizeof(value)
                );

                break;
            }

            case PCM_FORMAT_S32_LE: {
                const int32_t value =
                        static_cast<int32_t>(
                                std::llround(
                                        static_cast<double>(sample) *
                                        2147483647.0
                                )
                        );

                std::memcpy(
                        ptr,
                        &value,
                        sizeof(value)
                );

                break;
            }

            case PCM_FORMAT_S24_LE: {
                const int32_t value =
                        static_cast<int32_t>(
                                std::lround(
                                        sample *
                                        8388607.0f
                                )
                        );

                std::memcpy(
                        ptr,
                        &value,
                        sizeof(value)
                );

                break;
            }

            case PCM_FORMAT_S24_3LE: {
                const int32_t signedValue =
                        static_cast<int32_t>(
                                std::lround(
                                        sample *
                                        8388607.0f
                                )
                        );

                const uint32_t value =
                        static_cast<uint32_t>(
                                signedValue
                        );

                ptr[0] =
                        static_cast<uint8_t>(
                                value & 0xFFu
                        );

                ptr[1] =
                        static_cast<uint8_t>(
                                (value >> 8u) &
                                0xFFu
                        );

                ptr[2] =
                        static_cast<uint8_t>(
                                (value >> 16u) &
                                0xFFu
                        );

                break;
            }

            case PCM_FORMAT_FLOAT_LE: {
                std::memcpy(
                        ptr,
                        &sample,
                        sizeof(sample)
                );

                break;
            }

            default:
                break;
        }
    }


    struct Biquad {
        double b0 = 1.0;
        double b1 = 0.0;
        double b2 = 0.0;
        double a1 = 0.0;
        double a2 = 0.0;

        double z1 = 0.0;
        double z2 = 0.0;


        void reset() {
            z1 =
                    0.0;

            z2 =
                    0.0;
        }


        float process(
                float input
        ) {
            const double x =
                    static_cast<double>(
                            input
                    );

            const double output =
                    b0 *
                    x +
                    z1;

            z1 =
                    b1 *
                    x -
                    a1 *
                    output +
                    z2;

            z2 =
                    b2 *
                    x -
                    a2 *
                    output;

            return static_cast<float>(
                    output
            );
        }


        void setPeaking(
                double sampleRate,
                double frequency,
                double q,
                double gainDb
        ) {
            const double a =
                    std::pow(
                            10.0,
                            gainDb /
                            40.0
                    );

            const double w0 =
                    2.0 *
                    3.14159265358979323846 *
                    frequency /
                    sampleRate;

            const double cosW0 =
                    std::cos(
                            w0
                    );

            const double sinW0 =
                    std::sin(
                            w0
                    );

            const double alpha =
                    sinW0 /
                    (
                            2.0 *
                            q
                    );


            const double rawB0 =
                    1.0 +
                    alpha *
                    a;

            const double rawB1 =
                    -2.0 *
                    cosW0;

            const double rawB2 =
                    1.0 -
                    alpha *
                    a;

            const double rawA0 =
                    1.0 +
                    alpha /
                    a;

            const double rawA1 =
                    -2.0 *
                    cosW0;

            const double rawA2 =
                    1.0 -
                    alpha /
                    a;


            b0 =
                    rawB0 /
                    rawA0;

            b1 =
                    rawB1 /
                    rawA0;

            b2 =
                    rawB2 /
                    rawA0;

            a1 =
                    rawA1 /
                    rawA0;

            a2 =
                    rawA2 /
                    rawA0;
        }


        void setLowShelf(
                double sampleRate,
                double frequency,
                double gainDb
        ) {
            const double a =
                    std::pow(
                            10.0,
                            gainDb /
                            40.0
                    );

            const double w0 =
                    2.0 *
                    3.14159265358979323846 *
                    frequency /
                    sampleRate;

            const double cosW0 =
                    std::cos(
                            w0
                    );

            const double sinW0 =
                    std::sin(
                            w0
                    );

            const double alpha =
                    sinW0 /
                    2.0 *
                    std::sqrt(
                            2.0
                    );

            const double twoSqrtAAlpha =
                    2.0 *
                    std::sqrt(
                            a
                    ) *
                    alpha;


            const double rawB0 =
                    a *
                    (
                            (
                                    a +
                                    1.0
                            ) -
                            (
                                    a -
                                    1.0
                            ) *
                            cosW0 +
                            twoSqrtAAlpha
                    );

            const double rawB1 =
                    2.0 *
                    a *
                    (
                            (
                                    a -
                                    1.0
                            ) -
                            (
                                    a +
                                    1.0
                            ) *
                            cosW0
                    );

            const double rawB2 =
                    a *
                    (
                            (
                                    a +
                                    1.0
                            ) -
                            (
                                    a -
                                    1.0
                            ) *
                            cosW0 -
                            twoSqrtAAlpha
                    );

            const double rawA0 =
                    (
                            a +
                            1.0
                    ) +
                    (
                            a -
                            1.0
                    ) *
                    cosW0 +
                    twoSqrtAAlpha;

            const double rawA1 =
                    -2.0 *
                    (
                            (
                                    a -
                                    1.0
                            ) +
                            (
                                    a +
                                    1.0
                            ) *
                            cosW0
                    );

            const double rawA2 =
                    (
                            a +
                            1.0
                    ) +
                    (
                            a -
                            1.0
                    ) *
                    cosW0 -
                    twoSqrtAAlpha;


            b0 =
                    rawB0 /
                    rawA0;

            b1 =
                    rawB1 /
                    rawA0;

            b2 =
                    rawB2 /
                    rawA0;

            a1 =
                    rawA1 /
                    rawA0;

            a2 =
                    rawA2 /
                    rawA0;
        }


        void setHighShelf(
                double sampleRate,
                double frequency,
                double gainDb
        ) {
            const double a =
                    std::pow(
                            10.0,
                            gainDb /
                            40.0
                    );

            const double w0 =
                    2.0 *
                    3.14159265358979323846 *
                    frequency /
                    sampleRate;

            const double cosW0 =
                    std::cos(
                            w0
                    );

            const double sinW0 =
                    std::sin(
                            w0
                    );

            const double alpha =
                    sinW0 /
                    2.0 *
                    std::sqrt(
                            2.0
                    );

            const double twoSqrtAAlpha =
                    2.0 *
                    std::sqrt(
                            a
                    ) *
                    alpha;


            const double rawB0 =
                    a *
                    (
                            (
                                    a +
                                    1.0
                            ) +
                            (
                                    a -
                                    1.0
                            ) *
                            cosW0 +
                            twoSqrtAAlpha
                    );

            const double rawB1 =
                    -2.0 *
                    a *
                    (
                            (
                                    a -
                                    1.0
                            ) +
                            (
                                    a +
                                    1.0
                            ) *
                            cosW0
                    );

            const double rawB2 =
                    a *
                    (
                            (
                                    a +
                                    1.0
                            ) +
                            (
                                    a -
                                    1.0
                            ) *
                            cosW0 -
                            twoSqrtAAlpha
                    );

            const double rawA0 =
                    (
                            a +
                            1.0
                    ) -
                    (
                            a -
                            1.0
                    ) *
                    cosW0 +
                    twoSqrtAAlpha;

            const double rawA1 =
                    2.0 *
                    (
                            (
                                    a -
                                    1.0
                            ) -
                            (
                                    a +
                                    1.0
                            ) *
                            cosW0
                    );

            const double rawA2 =
                    (
                            a +
                            1.0
                    ) -
                    (
                            a -
                            1.0
                    ) *
                    cosW0 -
                    twoSqrtAAlpha;


            b0 =
                    rawB0 /
                    rawA0;

            b1 =
                    rawB1 /
                    rawA0;

            b2 =
                    rawB2 /
                    rawA0;

            a1 =
                    rawA1 /
                    rawA0;

            a2 =
                    rawA2 /
                    rawA0;
        }
    };


    class AudioEngine {

    public:

        AudioEngine() {
            for (unsigned int slot = 0; slot < MAX_NAM_BLOCKS; ++slot) {
                mNamGainDb[slot].store(0.0f);
                mNamInGainDb[slot].store(0.0f);
                mNamMix[slot].store(1.0f);
                mNamNormalize[slot].store(true);
                mNamNormalizeGain[slot].store(1.0f);
                mNamEqLowDb[slot].store(0.0f);
                mNamEqMidDb[slot].store(0.0f);
                mNamEqHighDb[slot].store(0.0f);
                mNamEqBand3Db[slot].store(0.0f);
                mNamEqBand4Db[slot].store(0.0f);
                mNamEqBand5Db[slot].store(0.0f);
            }
        }

        ~AudioEngine() {
            stop();
        }

        std::string loadModel(
                const std::string& path
        ) {
            stop();

            try {
                nam::activations::Activation::enable_fast_tanh();

                nam::DspLoadOptions options;

                /*
                 * Não fazemos prewarm durante get_dsp().
                 */
                options.prewarm =
                        false;

                auto candidate =
                        nam::get_dsp(
                                std::filesystem::path(
                                        path
                                ),
                                options
                        );

                if (!candidate) {
                    return
                            "MODEL LOAD FAILED\n"
                            "get_dsp returned null";
                }

                if (
                        candidate->NumInputChannels() !=
                        1
                        ) {
                    return
                            "MODEL LOAD FAILED\n"
                            "NAM must have 1 input channel";
                }

                if (
                        candidate->NumOutputChannels() !=
                        1
                        ) {
                    return
                            "MODEL LOAD FAILED\n"
                            "NAM must have 1 output channel";
                }

                const double expectedRate =
                        candidate->GetExpectedSampleRate();

                if (
                        expectedRate > 0.0 &&
                        std::abs(
                                expectedRate -
                                static_cast<double>(
                                        TARGET_RATE
                                )
                        ) > 1.0
                        ) {
                    std::ostringstream out;

                    out
                            << "MODEL LOAD FAILED\n"
                            << "Model expects "
                            << expectedRate
                            << " Hz\n"
                            << "Engine currently requires "
                            << TARGET_RATE
                            << " Hz";

                    return out.str();
                }

                candidate->Reset(
                        TARGET_RATE,
                        DEFAULT_BLOCK_SIZE
                );

                /*
                 * A2 models can be loaded as a SlimmableContainer.
                 * If we do not choose a size explicitly, the container
                 * may remain on its smallest/cheapest tier.
                 *
                 * Force the maximum-quality tier for this test.
                 * SetSlimmableSize() is not real-time safe, so this is
                 * intentionally done while audio is stopped.
                 */
                applyA2LiteSlimmableQuality(
                        candidate.get()
                );

                const uint64_t fileFingerprint =
                        hashFileFNV1a(
                                path
                        );

                const uint64_t probeFingerprint =
                        probeModelFingerprint(
                                candidate.get()
                        );

                /*
                 * Restore the normal initial block-size state after
                 * the deterministic probe.
                 */
                candidate->Reset(
                        TARGET_RATE,
                        DEFAULT_BLOCK_SIZE
                );

                applyA2LiteSlimmableQuality(
                        candidate.get()
                );

                mNamModels[0] =
                        std::move(
                                candidate
                        );

                mModelFileFingerprint.store(
                        fileFingerprint
                );

                mModelProbeFingerprint.store(
                        probeFingerprint
                );

                mModelGeneration.fetch_add(
                        1
                );

                mNamBypass[0].store(
                        false
                );

                LOGI(
                        "Loaded model generation=%llu fileHash=%s probeHash=%s path=%s",
                        static_cast<unsigned long long>(
                                mModelGeneration.load()
                        ),
                        hex64(
                                fileFingerprint
                        ).c_str(),
                        hex64(
                                probeFingerprint
                        ).c_str(),
                        path.c_str()
                );

                std::ostringstream out;

                out
                        << "MODEL LOADED\n"
                        << path
                        << "\n"
                        << "NAM ACTIVE";

                if (mIsSlimmable.load()) {
                    out
                            << "\n"
                            << "A2 SLIMMABLE: LITE ("
                            << mSlimSize.load()
                            << ")";
                } else {
                    out
                            << "\n"
                            << "SLIMMABLE: NO";
                }

                out
                        << "\n"
                        << "generation="
                        << mModelGeneration.load()
                        << "\n"
                        << "fileHash="
                        << hex64(
                                mModelFileFingerprint.load()
                        )
                        << "\n"
                        << "probeHash="
                        << hex64(
                                mModelProbeFingerprint.load()
                        );

                return out.str();

            } catch (
                    const std::exception& e
            ) {
                return
                        std::string(
                                "MODEL LOAD FAILED\n"
                        ) +
                        e.what();
            }
        }

        std::string loadImpulseResponse(const std::string& path) {
            stop();
            auto candidate = std::make_unique<dsp::ImpulseResponse>(path.c_str(), TARGET_RATE);
            if (candidate->GetWavState() != dsp::wav::LoadReturnCode::SUCCESS) {
                return "IR LOAD FAILED\nInvalid or unsupported WAV file";
            }
            mOutputIr = std::move(candidate);
            return "IR LOADED\nCabinet convolution active";
        }

        void setImpulseResponseBypass(bool bypass) {
            mOutputIrBypass.store(bypass, std::memory_order_relaxed);
        }

        void clearImpulseResponse() {
            stop();
            mOutputIr.reset();
            mOutputIrBypass.store(false, std::memory_order_relaxed);
            mOutputIrPosition.store(static_cast<int>(MAX_NAM_BLOCKS), std::memory_order_relaxed);
        }

        void setImpulseResponsePosition(int namBlocksBefore) {
            mOutputIrPosition.store(
                    std::clamp(namBlocksBefore, 0, static_cast<int>(MAX_NAM_BLOCKS)),
                    std::memory_order_relaxed
            );
        }

        void setImpulseResponseInGainDb(float db) {
            mOutputIrInGainDb.store(std::clamp(db, -24.0f, 24.0f), std::memory_order_relaxed);
        }

        void setImpulseResponseOutGainDb(float db) {
            mOutputIrOutGainDb.store(std::clamp(db, -24.0f, 12.0f), std::memory_order_relaxed);
        }

        void setImpulseResponseMix(float mix) {
            mOutputIrMix.store(std::clamp(mix, 0.0f, 1.0f), std::memory_order_relaxed);
        }

        void setImpulseResponseEqDb(int band, float db) {
            if (band >= 0 && band < 6) {
                mOutputIrEqDb[static_cast<size_t>(band)].store(
                        std::clamp(db, -12.0f, 12.0f), std::memory_order_relaxed);
            }
        }

        void setImpulseResponseEqPre(bool pre) {
            mOutputIrEqPre.store(pre, std::memory_order_relaxed);
        }

        std::string switchPresetGapless(
                const std::string& path,
                float inputGainDb,
                float outputGainDb,
                int inputChannel,
                int outputPair,
                bool gateEnabled,
                float gateThresholdDb,
                float eqLowDb,
                float eqMidDb,
                float eqHighDb
        ) {
            if (!mRunning.load()) {
                const std::string result =
                        loadModel(
                                path
                        );

                if (
                        result.rfind(
                                "MODEL LOADED",
                                0
                        ) != 0
                        ) {
                    return result;
                }

                setInputGainDb(
                        inputGainDb
                );

                setOutputGainDb(
                        outputGainDb
                );

                setInputChannel(
                        inputChannel
                );

                setOutputPair(
                        outputPair
                );

                setGateEnabled(
                        gateEnabled
                );

                setGateThresholdDb(
                        gateThresholdDb
                );

                setEqLowDb(
                        eqLowDb
                );

                setEqMidDb(
                        eqMidDb
                );

                setEqHighDb(
                        eqHighDb
                );

                return
                        "PRESET LOADED\n"
                        "Audio is stopped; preset is ready.";
            }


            std::unique_ptr<nam::DSP> retiredModel;

            {
                std::lock_guard<std::mutex> lock(
                        mModelSwapMutex
                );

                if (mPresetSwitchPending.load()) {
                    return
                            "PRESET SWITCH BUSY\n"
                            "Another preset is already queued.";
                }

                if (
                        !mCrossfadeActive.load(
                                std::memory_order_acquire
                        ) &&
                        mCrossfadeOldModel
                        ) {
                    retiredModel =
                            std::move(
                                    mCrossfadeOldModel
                            );
                }
            }

            /*
             * Destroy only on this loader thread, never on audio.
             */
            retiredModel.reset();


            try {
                nam::activations::Activation::enable_fast_tanh();

                nam::DspLoadOptions options;

                options.prewarm =
                        false;


                auto candidate =
                        nam::get_dsp(
                                std::filesystem::path(
                                        path
                                ),
                                options
                        );


                if (!candidate) {
                    return
                            "PRESET SWITCH FAILED\n"
                            "get_dsp returned null";
                }


                if (
                        candidate->NumInputChannels() !=
                        1
                        ) {
                    return
                            "PRESET SWITCH FAILED\n"
                            "NAM must have 1 input channel";
                }


                if (
                        candidate->NumOutputChannels() !=
                        1
                        ) {
                    return
                            "PRESET SWITCH FAILED\n"
                            "NAM must have 1 output channel";
                }


                const double expectedRate =
                        candidate->GetExpectedSampleRate();


                if (
                        expectedRate > 0.0 &&
                        std::abs(
                                expectedRate -
                                static_cast<double>(
                                        TARGET_RATE
                                )
                        ) > 1.0
                        ) {
                    std::ostringstream out;

                    out
                            << "PRESET SWITCH FAILED\n"
                            << "Model expects "
                            << expectedRate
                            << " Hz";

                    return out.str();
                }


                const unsigned int targetBlockSize =
                        mBlockSize > 0
                        ? mBlockSize
                        : DEFAULT_BLOCK_SIZE;


                candidate->Reset(
                        TARGET_RATE,
                        static_cast<int>(
                                targetBlockSize
                        )
                );


                bool candidateIsSlimmable =
                        false;

                float candidateSlimSize =
                        0.0f;


                if (
                        auto* slimmable =
                                dynamic_cast<nam::SlimmableModel*>(
                                        candidate.get()
                                )
                        ) {
                    slimmable->SetSlimmableSize(
                            A2_LITE_SLIM_SIZE
                    );

                    candidateIsSlimmable =
                            true;

                    candidateSlimSize =
                            static_cast<float>(
                                    A2_LITE_SLIM_SIZE
                            );
                }


                const uint64_t fileFingerprint =
                        hashFileFNV1a(
                                path
                        );


                const bool activeSlimmable =
                        mIsSlimmable.load();

                const float activeSlimSize =
                        mSlimSize.load();


                const uint64_t probeFingerprint =
                        probeModelFingerprint(
                                candidate.get()
                        );


                /*
                 * probeModelFingerprint() publishes slimmable diagnostics.
                 * Restore the currently active model's public values.
                 */
                mIsSlimmable.store(
                        activeSlimmable
                );

                mSlimSize.store(
                        activeSlimSize
                );


                candidate->Reset(
                        TARGET_RATE,
                        static_cast<int>(
                                targetBlockSize
                        )
                );


                if (candidateIsSlimmable) {
                    auto* slimmable =
                            dynamic_cast<nam::SlimmableModel*>(
                                    candidate.get()
                            );

                    if (slimmable) {
                        slimmable->SetSlimmableSize(
                                A2_LITE_SLIM_SIZE
                        );
                    }
                }


                inputGainDb =
                        std::clamp(
                                inputGainDb,
                                -24.0f,
                                24.0f
                        );

                outputGainDb =
                        std::clamp(
                                outputGainDb,
                                -24.0f,
                                12.0f
                        );

                gateThresholdDb =
                        std::clamp(
                                gateThresholdDb,
                                -90.0f,
                                -20.0f
                        );

                eqLowDb =
                        std::clamp(
                                eqLowDb,
                                -12.0f,
                                12.0f
                        );

                eqMidDb =
                        std::clamp(
                                eqMidDb,
                                -12.0f,
                                12.0f
                        );

                eqHighDb =
                        std::clamp(
                                eqHighDb,
                                -12.0f,
                                12.0f
                        );


                /*
                 * Normally the previous 2.7 ms crossfade is long finished by
                 * the time the new NAM has loaded. If not, wait here rather
                 * than blocking the realtime thread.
                 */
                for (
                        int attempt = 0;
                        attempt < 50 &&
                        mCrossfadeActive.load(
                                std::memory_order_acquire
                        );
                        ++attempt
                        ) {
                    std::this_thread::sleep_for(
                            std::chrono::milliseconds(
                                    1
                            )
                    );
                }


                if (
                        mCrossfadeActive.load(
                                std::memory_order_acquire
                        )
                        ) {
                    return
                            "PRESET SWITCH BUSY\n"
                            "Previous crossfade has not finished.";
                }


                std::unique_ptr<nam::DSP> staleCrossfadeModel;

                {
                    std::lock_guard<std::mutex> lock(
                            mModelSwapMutex
                    );


                    if (mPresetSwitchPending.load()) {
                        return
                                "PRESET SWITCH BUSY\n"
                                "Another preset is already queued.";
                    }


                    if (mCrossfadeOldModel) {
                        staleCrossfadeModel =
                                std::move(
                                        mCrossfadeOldModel
                                );
                    }


                    mPendingModel =
                            std::move(
                                    candidate
                            );


                    mPendingInputGainDb =
                            inputGainDb;

                    mPendingInputGainLinear =
                            std::pow(
                                    10.0f,
                                    inputGainDb /
                                    20.0f
                            );

                    mPendingOutputGainDb =
                            outputGainDb;

                    mPendingOutputGainLinear =
                            std::pow(
                                    10.0f,
                                    outputGainDb /
                                    20.0f
                            );

                    mPendingInputChannel =
                            static_cast<unsigned int>(
                                    std::max(
                                            inputChannel,
                                            0
                                    )
                            );

                    mPendingOutputPair =
                            static_cast<unsigned int>(
                                    std::max(
                                            outputPair,
                                            0
                                    )
                            );

                    mPendingGateEnabled =
                            gateEnabled;

                    mPendingGateThresholdDb =
                            gateThresholdDb;

                    mPendingGateThresholdLinear =
                            std::pow(
                                    10.0f,
                                    gateThresholdDb /
                                    20.0f
                            );

                    mPendingEqLowDb =
                            eqLowDb;

                    mPendingEqMidDb =
                            eqMidDb;

                    mPendingEqHighDb =
                            eqHighDb;

                    mPendingFileFingerprint =
                            fileFingerprint;

                    mPendingProbeFingerprint =
                            probeFingerprint;

                    mPendingIsSlimmable =
                            candidateIsSlimmable;

                    mPendingSlimSize =
                            candidateSlimSize;


                    mPresetSwitchPending.store(
                            true,
                            std::memory_order_release
                    );
                }


                staleCrossfadeModel.reset();


                std::ostringstream out;

                out
                        << "PRESET SWITCH QUEUED\n"
                        << "crossfade="
                        << PRESET_CROSSFADE_SAMPLES
                        << " samples ("
                        << (
                                static_cast<double>(
                                        PRESET_CROSSFADE_SAMPLES
                                ) /
                                static_cast<double>(
                                        TARGET_RATE
                                ) *
                                1000.0
                        )
                        << " ms)";

                return out.str();


            } catch (
                    const std::exception& e
            ) {
                return
                        std::string(
                                "PRESET SWITCH FAILED\n"
                        ) +
                        e.what();
            }
        }


        std::string addChainModel(
                const std::string& path
        ) {
            /*
             * The current TONE3000 selection flow leaves the Activity and
             * already stops realtime audio. Keep this first multi-NAM
             * implementation deliberately strict: mutate the model chain only
             * while the audio thread is stopped.
             *
             * The important validation here is the realtime serial processing
             * of multiple full NAM A2 models.
             */
            stop();


            unsigned int freeSlot =
                    MAX_NAM_BLOCKS;


            for (
                    unsigned int slot = 0;
                    slot < MAX_NAM_BLOCKS;
                    ++slot
                    ) {
                if (!mNamModels[slot]) {
                    freeSlot =
                            slot;

                    break;
                }
            }


            if (
                    freeSlot >=
                    MAX_NAM_BLOCKS
                    ) {
                return
                        "CHAIN NAM ADD FAILED\n"
                        "Maximum of 4 NAM blocks reached.";
            }


            try {
                nam::activations::Activation::enable_fast_tanh();

                nam::DspLoadOptions options;

                options.prewarm =
                        false;


                auto candidate =
                        nam::get_dsp(
                                std::filesystem::path(
                                        path
                                ),
                                options
                        );


                if (!candidate) {
                    return
                            "CHAIN NAM ADD FAILED\n"
                            "get_dsp returned null";
                }


                if (
                        candidate->NumInputChannels() !=
                        1
                        ) {
                    return
                            "CHAIN NAM ADD FAILED\n"
                            "NAM must have 1 input channel";
                }


                if (
                        candidate->NumOutputChannels() !=
                        1
                        ) {
                    return
                            "CHAIN NAM ADD FAILED\n"
                            "NAM must have 1 output channel";
                }


                const double expectedRate =
                        candidate->GetExpectedSampleRate();


                if (
                        expectedRate > 0.0 &&
                        std::abs(
                                expectedRate -
                                static_cast<double>(
                                        TARGET_RATE
                                )
                        ) > 1.0
                        ) {
                    std::ostringstream out;

                    out
                            << "CHAIN NAM ADD FAILED\n"
                            << "Model expects "
                            << expectedRate
                            << " Hz";

                    return out.str();
                }


                const unsigned int blockSize =
                        mBlockSize > 0
                        ? mBlockSize
                        : DEFAULT_BLOCK_SIZE;


                candidate->Reset(
                        TARGET_RATE,
                        static_cast<int>(
                                blockSize
                        )
                );


                if (
                        auto* slimmable =
                                dynamic_cast<nam::SlimmableModel*>(
                                        candidate.get()
                                )
                        ) {
                    slimmable->SetSlimmableSize(
                            A2_LITE_SLIM_SIZE
                    );
                }


                mNamModels[freeSlot] =
                        std::move(
                                candidate
                        );


                mNamBypass[freeSlot].store(
                        false
                );


                std::ostringstream out;

                out
                        << "CHAIN NAM ADDED\n"
                        << "chainIndex="
                        << (
                                freeSlot
                        )
                        << "\n"
                        << "namBlocks="
                        << namBlockCount();

                return out.str();


            } catch (
                    const std::exception& e
            ) {
                return
                        std::string(
                                "CHAIN NAM ADD FAILED\n"
                        ) +
                        e.what();
            }
        }


        std::string clearExtraChainModels() {
            stop();


            for (
                    unsigned int slot = 0;
                    slot < MAX_NAM_BLOCKS;
                    ++slot
                    ) {
                if (slot == 0) {
                    continue;
                }

                mNamModels[slot].reset();

                mNamBypass[slot].store(
                        false
                );
            }


            return
                    "EXTRA NAM BLOCKS CLEARED";
        }


        std::string clearChainModels() {
            stop();

            for (unsigned int slot = 0; slot < MAX_NAM_BLOCKS; ++slot) {
                mNamModels[slot].reset();
                mNamBypass[slot].store(false);
            }

            mPendingModel.reset();
            mCrossfadeOldModel.reset();
            mPresetSwitchPending.store(false);
            mCrossfadeActive.store(false);

            return "NAM CHAIN CLEARED";
        }


        void setChainNamBypass(
                int chainIndex,
                bool bypass
        ) {
            if (chainIndex < 0) {
                return;
            }

            const unsigned int slot =
                    static_cast<unsigned int>(
                            chainIndex
                    );


            if (
                    slot >=
                    MAX_NAM_BLOCKS
                    ) {
                return;
            }


            mNamBypass[slot].store(
                    bypass
            );
        }

        std::string setChainNamQuality(int chainIndex, bool full) {
            if (chainIndex < 0 || chainIndex >= static_cast<int>(MAX_NAM_BLOCKS)) {
                return "A2 QUALITY FAILED\nInvalid block";
            }
            auto* model = mNamModels[static_cast<unsigned int>(chainIndex)].get();
            auto* slimmable = dynamic_cast<nam::SlimmableModel*>(model);
            if (!slimmable) {
                return "A2 QUALITY UNAVAILABLE\nThis NAM is not slimmable";
            }
            const bool wasRunning = isRunning();
            stop();
            slimmable->SetSlimmableSize(full ? 1.0 : A2_LITE_SLIM_SIZE);
            if (wasRunning) {
                const auto result = start();
                if (!result.starts_with("AUDIO ACTIVE")) return result;
            }
            return full ? "A2 FULL ACTIVE" : "A2 LITE ACTIVE";
        }

        void setChainNamGainDb(int chainIndex, float db) {
            if (chainIndex >= 0 && chainIndex < static_cast<int>(MAX_NAM_BLOCKS)) {
                mNamGainDb[static_cast<unsigned int>(chainIndex)].store(
                        std::clamp(db, -24.0f, 12.0f), std::memory_order_relaxed);
            }
        }

        void setChainNamInGainDb(int chainIndex, float db) {
            if (chainIndex >= 0 && chainIndex < static_cast<int>(MAX_NAM_BLOCKS)) {
                mNamInGainDb[static_cast<unsigned int>(chainIndex)].store(
                        std::clamp(db, -24.0f, 24.0f), std::memory_order_relaxed);
            }
        }

        void setChainNamMix(int chainIndex, float mix) {
            if (chainIndex >= 0 && chainIndex < static_cast<int>(MAX_NAM_BLOCKS)) {
                mNamMix[static_cast<unsigned int>(chainIndex)].store(
                        std::clamp(mix, 0.0f, 1.0f), std::memory_order_relaxed);
            }
        }

        void setChainNamNormalize(int chainIndex, bool enabled) {
            if (chainIndex >= 0 && chainIndex < static_cast<int>(MAX_NAM_BLOCKS)) {
                mNamNormalize[static_cast<unsigned int>(chainIndex)].store(enabled, std::memory_order_relaxed);
            }
        }

        void setChainNamEqDb(int chainIndex, int band, float db) {
            if (chainIndex < 0 || chainIndex >= static_cast<int>(MAX_NAM_BLOCKS)) {
                return;
            }
            auto value = std::clamp(db, -12.0f, 12.0f);
            const auto slot = static_cast<unsigned int>(chainIndex);
            if (band == 0) mNamEqLowDb[slot].store(value, std::memory_order_relaxed);
            if (band == 1) mNamEqMidDb[slot].store(value, std::memory_order_relaxed);
            if (band == 2) mNamEqHighDb[slot].store(value, std::memory_order_relaxed);
            if (band == 3) mNamEqBand3Db[slot].store(value, std::memory_order_relaxed);
            if (band == 4) mNamEqBand4Db[slot].store(value, std::memory_order_relaxed);
            if (band == 5) mNamEqBand5Db[slot].store(value, std::memory_order_relaxed);
        }

        void setChainNamEqPre(int chainIndex, bool pre) {
            if (chainIndex >= 0 && chainIndex < static_cast<int>(MAX_NAM_BLOCKS)) {
                mNamEqPre[static_cast<unsigned int>(chainIndex)].store(pre, std::memory_order_relaxed);
            }
        }


        int namBlockCount() const {
            int count = 0;

            for (const auto& model : mNamModels) {
                if (model) {
                    ++count;
                }
            }

            return count;
        }


        std::string start() {
            stop();

            if (namBlockCount() == 0) {
                return
                        "AUDIO START FAILED\n"
                        "No NAM model loaded";
            }

            std::string openError;

            if (
                    !openCompatibleUsbInterface(
                            openError
                    )
                    ) {
                return
                        "AUDIO START FAILED\n" +
                        openError;
            }

            for (
                    unsigned int slot = 0;
                    slot < MAX_NAM_BLOCKS;
                    ++slot
                    ) {
                auto* model =
                        mNamModels[slot].get();


                if (!model) {
                    continue;
                }


                model->Reset(
                        TARGET_RATE,
                        static_cast<int>(
                                mBlockSize
                        )
                );


                if (
                        auto* slimmable =
                                dynamic_cast<nam::SlimmableModel*>(
                                        model
                                )
                        ) {
                    slimmable->SetSlimmableSize(
                            A2_LITE_SLIM_SIZE
                    );
                }
            }


            resetStats();

            mRunning.store(
                    true
            );

            mThread =
                    std::thread(
                            &AudioEngine::audioLoop,
                            this
                    );

            std::ostringstream out;

            out
                    << "AUDIO ACTIVE\n"
                    << mDeviceName
                    << "\n"
                    << "card="
                    << mCard
                    << " device="
                    << mDevice
                    << "\n"
                    << TARGET_RATE
                    << " Hz / "
                    << mBlockSize
                    << " frames\n"
                    << "realtimeProfile="
                    << (
                            namBlockCount() >=
                            3
                            ? "A2_LITE_HEAVY_CHAIN_256X4_SINGLE_FASTEST_CORE"
                            : (
                                    namBlockCount() ==
                                    2
                                    ? "A2_LITE_MULTI_NAM_256X4_SINGLE_FASTEST_CORE"
                                    : "A2_LITE_LOW_LATENCY_128X4_SINGLE_FASTEST_CORE"
                            )
                    )
                    << "\n"
                    << "NAM blocks="
                    << namBlockCount()
                    << "\n"
                    << "capture: "
                    << mCaptureChannels
                    << "ch "
                    << formatName(
                            mCaptureFormat
                    )
                    << "\n"
                    << "playback: "
                    << mPlaybackChannels
                    << "ch "
                    << formatName(
                            mPlaybackFormat
                    );

            return out.str();
        }

        void stop() {
            mRunning.store(
                    false
            );

            if (
                    mThread.joinable()
                    ) {
                mThread.join();
            }

            closePcm();


            std::unique_ptr<nam::DSP> pendingCleanup;
            std::unique_ptr<nam::DSP> crossfadeCleanup;

            {
                std::lock_guard<std::mutex> lock(
                        mModelSwapMutex
                );

                pendingCleanup =
                        std::move(
                                mPendingModel
                        );

                crossfadeCleanup =
                        std::move(
                                mCrossfadeOldModel
                        );

                mPresetSwitchPending.store(
                        false
                );

                mCrossfadeActive.store(
                        false
                );

                mCrossfadePositionSamples =
                        0;
            }

            pendingCleanup.reset();
            crossfadeCleanup.reset();
        }

        bool isRunning() const {
            return mRunning.load(
                    std::memory_order_acquire
            );
        }


        void setBypass(
                bool bypass
        ) {
            mNamBypass[0].store(
                    bypass
            );
        }

        void setInputGainDb(
                float db
        ) {
            db =
                    std::clamp(
                            db,
                            -24.0f,
                            24.0f
                    );

            mInputGainDb.store(
                    db
            );

            mInputGainLinear.store(
                    std::pow(
                            10.0f,
                            db / 20.0f
                    )
            );
        }

        void setOutputGainDb(
                float db
        ) {
            db =
                    std::clamp(
                            db,
                            -24.0f,
                            12.0f
                    );

            mOutputGainDb.store(
                    db
            );

            mOutputGainLinear.store(
                    std::pow(
                            10.0f,
                            db / 20.0f
                    )
            );
        }

        void setGateEnabled(
                bool enabled
        ) {
            mGateEnabled.store(
                    enabled
            );
        }


        void setGateThresholdDb(
                float db
        ) {
            db =
                    std::clamp(
                            db,
                            -90.0f,
                            -20.0f
                    );

            mGateThresholdDb.store(
                    db
            );

            mGateThresholdLinear.store(
                    std::pow(
                            10.0f,
                            db /
                            20.0f
                    )
            );
        }


        void setEqLowDb(
                float db
        ) {
            mEqLowDb.store(
                    std::clamp(
                            db,
                            -12.0f,
                            12.0f
                    )
            );
        }


        void setEqMidDb(
                float db
        ) {
            mEqMidDb.store(
                    std::clamp(
                            db,
                            -12.0f,
                            12.0f
                    )
            );
        }


        void setEqHighDb(
                float db
        ) {
            mEqHighDb.store(
                    std::clamp(
                            db,
                            -12.0f,
                            12.0f
                    )
            );
        }


        std::string dspChainInfo() const {
            std::ostringstream out;

            out
                    << "DSP CHAIN\\n"
                    << "Gate: "
                    << (
                            mGateEnabled.load()
                            ? "ON"
                            : "OFF"
                    )
                    << " threshold="
                    << mGateThresholdDb.load()
                    << " dB\\n"
                    << "NAM blocks: "
                    << namBlockCount()
                    << " serial\\n"
                    << "EQ Low(120Hz): "
                    << mEqLowDb.load()
                    << " dB\\n"
                    << "EQ Mid(750Hz): "
                    << mEqMidDb.load()
                    << " dB\\n"
                    << "EQ High(4kHz): "
                    << mEqHighDb.load()
                    << " dB";

            return out.str();
        }


        void setInputChannel(
                int channel
        ) {
            if (channel < 0) {
                channel =
                        0;
            }

            mSelectedInputChannel.store(
                    static_cast<unsigned int>(
                            channel
                    )
            );
        }


        void setOutputPair(
                int pairIndex
        ) {
            if (pairIndex < 0) {
                pairIndex =
                        0;
            }

            mSelectedOutputPair.store(
                    static_cast<unsigned int>(
                            pairIndex
                    )
            );
        }


        int cycleInputChannel() {
            const unsigned int channels =
                    mCaptureChannels;

            if (channels == 0) {
                return static_cast<int>(
                        mSelectedInputChannel.load()
                );
            }

            const unsigned int next =
                    (
                            mSelectedInputChannel.load() +
                            1
                    ) %
                    channels;

            mSelectedInputChannel.store(
                    next
            );

            return static_cast<int>(
                    next
            );
        }


        int cycleOutputPair() {
            const unsigned int channels =
                    mPlaybackChannels;

            const unsigned int pairCount =
                    channels == 0
                    ? 0
                    : (
                              channels +
                              1
                      ) /
                      2;

            if (pairCount == 0) {
                return static_cast<int>(
                        mSelectedOutputPair.load()
                );
            }

            const unsigned int next =
                    (
                            mSelectedOutputPair.load() +
                            1
                    ) %
                    pairCount;

            mSelectedOutputPair.store(
                    next
            );

            return static_cast<int>(
                    next
            );
        }


        std::string routingInfo() const {
            const unsigned int requestedInput =
                    mSelectedInputChannel.load();

            const unsigned int requestedPair =
                    mSelectedOutputPair.load();


            const unsigned int effectiveInput =
                    mCaptureChannels > 0
                    ? std::min(
                            requestedInput,
                            mCaptureChannels -
                            1
                    )
                    : requestedInput;


            const unsigned int pairCount =
                    mPlaybackChannels > 0
                    ? (
                              mPlaybackChannels +
                              1
                      ) /
                      2
                    : 0;


            const unsigned int effectivePair =
                    pairCount > 0
                    ? std::min(
                            requestedPair,
                            pairCount -
                            1
                    )
                    : requestedPair;


            const unsigned int outputLeft =
                    effectivePair *
                    2;

            const unsigned int outputRight =
                    outputLeft +
                    1;


            std::ostringstream out;

            out
                    << "ROUTING\n"
                    << "Input: "
                    << (
                            effectiveInput +
                            1
                    );

            if (mCaptureChannels > 0) {
                out
                        << " / "
                        << mCaptureChannels;
            } else {
                out
                        << " (pending)";
            }


            out
                    << "\n"
                    << "Output: "
                    << (
                            outputLeft +
                            1
                    );

            if (
                    mPlaybackChannels == 0 ||
                    outputRight <
                    mPlaybackChannels
                    ) {
                out
                        << "-"
                        << (
                                outputRight +
                                1
                        );
            }


            if (mPlaybackChannels > 0) {
                out
                        << " / "
                        << mPlaybackChannels
                        << " ch";
            } else {
                out
                        << " (pending)";
            }


            return out.str();
        }


        std::string scanUsbAudio() {
            const auto endpoints =
                    findUsbPcmEndpoints();

            if (endpoints.empty()) {
                return
                        "NO USB AUDIO INTERFACES FOUND";
            }

            std::ostringstream out;

            out
                    << "USB AUDIO INTERFACES\n";

            bool foundFullDuplex =
                    false;

            for (
                const auto& endpoint :
                    endpoints
                    ) {
                if (
                        !endpoint.capture ||
                        !endpoint.playback
                        ) {
                    continue;
                }

                foundFullDuplex =
                        true;

                out
                        << "\n"
                        << endpoint.cardName
                        << "\n"
                        << "card="
                        << endpoint.card
                        << " device="
                        << endpoint.device;

                pcm_params* captureParams =
                        pcm_params_get(
                                endpoint.card,
                                endpoint.device,
                                PCM_IN
                        );

                pcm_params* playbackParams =
                        pcm_params_get(
                                endpoint.card,
                                endpoint.device,
                                PCM_OUT
                        );

                if (
                        captureParams &&
                        playbackParams
                        ) {
                    out
                            << "\n"
                            << "capture channels: "
                            << pcm_params_get_min(
                                    captureParams,
                                    PCM_PARAM_CHANNELS
                            )
                            << "-"
                            << pcm_params_get_max(
                                    captureParams,
                                    PCM_PARAM_CHANNELS
                            );

                    out
                            << "\n"
                            << "playback channels: "
                            << pcm_params_get_min(
                                    playbackParams,
                                    PCM_PARAM_CHANNELS
                            )
                            << "-"
                            << pcm_params_get_max(
                                    playbackParams,
                                    PCM_PARAM_CHANNELS
                            );

                    const bool rate48 =
                            supportsValue(
                                    captureParams,
                                    PCM_PARAM_RATE,
                                    TARGET_RATE
                            ) &&
                            supportsValue(
                                    playbackParams,
                                    PCM_PARAM_RATE,
                                    TARGET_RATE
                            );

                    out
                            << "\n48 kHz: "
                            << (
                                    rate48
                                    ? "YES"
                                    : "NO"
                            );
                } else {
                    out
                            << "\n"
                            << "Unable to inspect PCM parameters.";
                }

                if (captureParams) {
                    pcm_params_free(
                            captureParams
                    );
                }

                if (playbackParams) {
                    pcm_params_free(
                            playbackParams
                    );
                }

                out
                        << "\n";
            }

            if (!foundFullDuplex) {
                return
                        "USB AUDIO FOUND,\n"
                        "BUT NO FULL-DUPLEX PCM WAS FOUND.";
            }

            return out.str();
        }

        std::string deviceInfo() const {
            if (
                    mDeviceName.empty()
                    ) {
                return
                        "Audio interface: not opened";
            }

            std::ostringstream out;

            out
                    << "Audio interface: "
                    << mDeviceName
                    << "\n"
                    << "card="
                    << mCard
                    << " device="
                    << mDevice
                    << "\n"
                    << TARGET_RATE
                    << " Hz / "
                    << mBlockSize
                    << " frames"
                    << "\n"
                    << "capture periods="
                    << mCapturePeriodCount
                    << " buffer="
                    << (
                            mBlockSize *
                            mCapturePeriodCount
                    )
                    << " frames ("
                    << (
                            (
                                    static_cast<double>(
                                            mBlockSize *
                                            mCapturePeriodCount
                                    ) /
                                    static_cast<double>(
                                            TARGET_RATE
                                    )
                            ) *
                            1000.0
                    )
                    << " ms)"
                    << "\n"
                    << "routing input="
                    << (
                            std::min(
                                    mSelectedInputChannel.load(),
                                    mCaptureChannels > 0
                                    ? mCaptureChannels - 1
                                    : 0u
                            ) +
                            1
                    )
                    << " output="
                    << (
                            std::min(
                                    mSelectedOutputPair.load(),
                                    mPlaybackChannels > 0
                                    ? (
                                              (
                                                      mPlaybackChannels +
                                                      1
                                              ) /
                                              2
                                      ) -
                                      1
                                    : 0u
                            ) *
                            2 +
                            1
                    )
                    << "-"
                    << (
                            std::min(
                                    (
                                            std::min(
                                                    mSelectedOutputPair.load(),
                                                    mPlaybackChannels > 0
                                                    ? (
                                                              (
                                                                      mPlaybackChannels +
                                                                      1
                                                              ) /
                                                              2
                                                      ) -
                                                      1
                                                    : 0u
                                            ) *
                                            2
                                    ) +
                                    2,
                                    mPlaybackChannels > 0
                                    ? mPlaybackChannels
                                    : 2u
                            )
                    )
                    << "\n"
                    << "playback periods="
                    << mPlaybackPeriodCount
                    << " buffer="
                    << (
                            mBlockSize *
                            mPlaybackPeriodCount
                    )
                    << " frames ("
                    << (
                            (
                                    static_cast<double>(
                                            mBlockSize *
                                            mPlaybackPeriodCount
                                    ) /
                                    static_cast<double>(
                                            TARGET_RATE
                                    )
                            ) *
                            1000.0
                    )
                    << " ms)";

            return out.str();
        }

        std::string stats() const {
            const uint64_t blocks =
                    mBlocks.load();

            const uint64_t totalNs =
                    mTotalProcessNs.load();

            const uint64_t maxNs =
                    mMaxProcessNs.load();

            const uint64_t overBudget =
                    mOverBudget.load();

            const uint64_t captureErrors =
                    mCaptureErrors.load();

            const uint64_t playbackErrors =
                    mPlaybackErrors.load();

            const double averageUs =
                    blocks > 0
                    ? (
                              static_cast<double>(
                                      totalNs
                              ) /
                              static_cast<double>(
                                      blocks
                              )
                      ) /
                      1000.0
                    : 0.0;

            const double maxUs =
                    static_cast<double>(
                            maxNs
                    ) /
                    1000.0;

            const double budgetUs =
                    (
                            static_cast<double>(
                                    mBlockSize
                            ) /
                            static_cast<double>(
                                    TARGET_RATE
                            )
                    ) *
                    1000000.0;

            const double cpuBudget =
                    budgetUs > 0.0
                    ? (
                              averageUs /
                              budgetUs
                      ) *
                      100.0
                    : 0.0;

            std::ostringstream out;

            out
                    << "NAM PERFORMANCE\n\n"
                    << "blocks="
                    << blocks
                    << "\n"
                    << "avgProcess="
                    << averageUs
                    << " us\n"
                    << "maxProcess="
                    << maxUs
                    << " us\n"
                    << "budget="
                    << budgetUs
                    << " us\n"
                    << "overBudget="
                    << overBudget
                    << "\n"
                    << "CPU budget used(avg)="
                    << cpuBudget
                    << "%\n\n"
                    << "captureErrors="
                    << captureErrors
                    << "\n"
                    << "playbackErrors="
                    << playbackErrors
                    << "\n\n"
                    << "inputGain="
                    << mInputGainDb.load()
                    << " dB\n"
                    << "outputGain="
                    << mOutputGainDb.load()
                    << " dB\n\n"
                    << "slimmable="
                    << (
                            mIsSlimmable.load()
                            ? "yes"
                            : "no"
                    )
                    << "\n"
                    << "slimSize="
                    << mSlimSize.load()
                    << "\n\n"
                    << "modelGeneration="
                    << mModelGeneration.load()
                    << "\n"
                    << "modelFileHash="
                    << hex64(
                            mModelFileFingerprint.load()
                    )
                    << "\n"
                    << "modelProbeHash="
                    << hex64(
                            mModelProbeFingerprint.load()
                    )
                    << "\n\n"
                    << "capturePeak="
                    << mCapturePeak.load()
                    << " ("
                    << linearToDbFS(
                            mCapturePeak.load()
                    )
                    << " dBFS)\n"
                    << "namInputPeak="
                    << mNamInputPeak.load()
                    << " ("
                    << linearToDbFS(
                            mNamInputPeak.load()
                    )
                    << " dBFS)\n"
                    << "namOutputPeak="
                    << mNamOutputPeak.load()
                    << " ("
                    << linearToDbFS(
                            mNamOutputPeak.load()
                    )
                    << " dBFS)\n"
                    << "postEqPeak="
                    << mPostEqPeak.load()
                    << " ("
                    << linearToDbFS(
                            mPostEqPeak.load()
                    )
                    << " dBFS)\n"
                    << "gateGain="
                    << mGateGainMonitor.load()
                    << "\n\n"
                    << dspChainInfo()
                    << "\n\n"
                    << routingInfo()
                    << "\n\n"
                    << "NAM CHAIN\n"
                    << "realtimeProfile="
                    << (
                            namBlockCount() >=
                            3
                            ? "A2_LITE_HEAVY_CHAIN_256X4_SINGLE_FASTEST_CORE"
                            : (
                                    namBlockCount() ==
                                    2
                                    ? "A2_LITE_MULTI_NAM_256X4_SINGLE_FASTEST_CORE"
                                    : "A2_LITE_LOW_LATENCY_128X4_SINGLE_FASTEST_CORE"
                            )
                    )
                    << "\n"
                    << "namBlocks="
                    << namBlockCount()
                    << " / "
                    << MAX_NAM_BLOCKS
                    << "\n"
                    << "NAM slot 0 bypass="
                    << (
                            mNamBypass[0].load()
                            ? "yes"
                            : "no"
                    )
                    << "\n";

            for (
                    unsigned int slot = 1;
                    slot < MAX_NAM_BLOCKS;
                    ++slot
                    ) {
                if (!mNamModels[slot]) {
                    continue;
                }

                out
                        << "NAM slot "
                        << slot
                        << " bypass="
                        << (
                                mNamBypass[slot].load()
                                ? "yes"
                                : "no"
                        )
                        << "\n";
            }

            out
                    << "\n"
                    << "PRESET SWITCHING\n"
                    << "gaplessSwitches="
                    << mGaplessPresetSwitches.load()
                    << "\n"
                    << "switchPending="
                    << (
                            mPresetSwitchPending.load()
                            ? "yes"
                            : "no"
                    )
                    << "\n"
                    << "crossfadeActive="
                    << (
                            mCrossfadeActive.load()
                            ? "yes"
                            : "no"
                    )
                    << "\n"
                    << "crossfadeLength="
                    << PRESET_CROSSFADE_SAMPLES
                    << " samples\n"
                    << "crossfadeOverBudget="
                    << mCrossfadeOverBudget.load()
                    << "\n\n";

            const double maxCaptureGapUs =
                    static_cast<double>(
                            mMaxCaptureGapNs.load()
                    ) /
                    1000.0;

            const double maxLoopWorkUs =
                    static_cast<double>(
                            mMaxLoopWorkNs.load()
                    ) /
                    1000.0;

            const double maxReadWaitUs =
                    static_cast<double>(
                            mMaxReadWaitNs.load()
                    ) /
                    1000.0;

            const double maxWriteWaitUs =
                    static_cast<double>(
                            mMaxWriteWaitNs.load()
                    ) /
                    1000.0;

            const double maxNonIoWorkUs =
                    static_cast<double>(
                            mMaxNonIoWorkNs.load()
                    ) /
                    1000.0;

            const double lateGapThresholdUs =
                    budgetUs *
                    1.5;

            const double veryLateGapThresholdUs =
                    budgetUs *
                    2.0;


            out
                    << "SCHEDULER / LOOP JITTER\n"
                    << "captureGapMax="
                    << maxCaptureGapUs
                    << " us\n"
                    << "lateGaps(>1.5x)="
                    << mLateCaptureGaps.load()
                    << "\n"
                    << "veryLateGaps(>2x)="
                    << mVeryLateCaptureGaps.load()
                    << "\n"
                    << "lateThreshold="
                    << lateGapThresholdUs
                    << " us\n"
                    << "veryLateThreshold="
                    << veryLateGapThresholdUs
                    << " us\n"
                    << "loopWallMax(incl IO)="
                    << maxLoopWorkUs
                    << " us\n"
                    << "\n"
                    << "I/O TIMING\n"
                    << "readWaitMax="
                    << maxReadWaitUs
                    << " us\n"
                    << "readWaits(>2x)="
                    << mLateReadWaits.load()
                    << "\n"
                    << "writeWaitMax="
                    << maxWriteWaitUs
                    << " us\n"
                    << "writeWaits(>2x)="
                    << mLateWriteWaits.load()
                    << "\n"
                    << "nonIoWorkMax="
                    << maxNonIoWorkUs
                    << " us\n"
                    << "nonIoOverBudget="
                    << mNonIoOverBudget.load()
                    << "\n\n"
                    << "AUDIO THREAD\n"
                    << "niceTarget=-16\n"
                    << "niceSet="
                    << (
                            mAudioThreadNiceSetResult.load() == 0
                            ? "OK"
                            : "FAILED"
                    )
                    << " errno="
                    << mAudioThreadNiceErrno.load()
                    << "\n"
                    << "niceActual="
                    << mAudioThreadNiceActual.load()
                    << "\n"
                    << "fifoTarget=2\n"
                    << "fifoSet="
                    << (
                            mAudioThreadFifoSetResult.load() == 0
                            ? "OK"
                            : "FAILED"
                    )
                    << " error="
                    << mAudioThreadFifoError.load()
                    << "\n"
                    << "policy="
                    << schedulerPolicyName(
                            mAudioThreadPolicy.load()
                    )
                    << "\n"
                    << "schedulerPriority="
                    << mAudioThreadPriority.load()
                    << "\n"
                    << "affinitySet="
                    << (
                            mAudioThreadAffinitySetResult.load() == 0
                            ? "OK"
                            : (
                                    mAudioThreadAffinitySetResult.load() == 1
                                    ? "UNCHANGED"
                                    : "FAILED"
                            )
                    )
                    << " error="
                    << mAudioThreadAffinityError.load()
                    << "\n"
                    << "allowedCPUs="
                    << cpuMaskToString(
                            mAudioThreadAllowedCpuMask.load()
                    )
                    << "\n"
                    << "selectedCPUs="
                    << cpuMaskToString(
                            mAudioThreadSelectedCpuMask.load()
                    )
                    << "\n"
                    << "maxCpuFreq="
                    << mAudioThreadAffinityMaxFreqKhz.load()
                    << " kHz\n"
                    << "currentCpuFreqs="
                    << currentCpuFrequenciesString(
                            mAudioThreadSelectedCpuMask.load()
                    )
                    << "\n\n"
                    << "BUFFER HEADROOM\n"
                    << "period="
                    << budgetUs
                    << " us\n"
                    << "captureRing="
                    << (
                            static_cast<double>(
                                    mBlockSize *
                                    mCapturePeriodCount
                            ) /
                            static_cast<double>(
                                    TARGET_RATE
                            ) *
                            1000.0
                    )
                    << " ms\n"
                    << "playbackRing="
                    << (
                            static_cast<double>(
                                    mBlockSize *
                                    mPlaybackPeriodCount
                            ) /
                            static_cast<double>(
                                    TARGET_RATE
                            ) *
                            1000.0
                    )
                    << " ms\n\n"
                    << deviceInfo();

            return out.str();
        }

    private:

        /*
         * Force a slimmable A2 model onto its minimum-compute slim point.
         * This is intentionally done outside the realtime processing path.
         */
        bool applyA2LiteSlimmableQuality(
                nam::DSP* model
        ) {
            if (!model) {
                mIsSlimmable.store(
                        false
                );

                mSlimSize.store(
                        0.0f
                );

                return false;
            }

            auto* slimmable =
                    dynamic_cast<nam::SlimmableModel*>(
                            model
                    );

            if (!slimmable) {
                mIsSlimmable.store(
                        false
                );

                mSlimSize.store(
                        0.0f
                );

                return false;
            }

            slimmable->SetSlimmableSize(
                    A2_LITE_SLIM_SIZE
            );

            mIsSlimmable.store(
                    true
            );

            mSlimSize.store(
                    static_cast<float>(
                            A2_LITE_SLIM_SIZE
                    )
            );

            LOGI(
                    "Slimmable NAM detected: forced size=%.2f (LITE)",
                    A2_LITE_SLIM_SIZE
            );

            return true;
        }


        uint64_t probeModelFingerprint(
                nam::DSP* model
        ) {
            if (!model) {
                return 0;
            }

            constexpr unsigned int PROBE_BLOCK =
                    DEFAULT_BLOCK_SIZE;

            constexpr unsigned int PROBE_BLOCKS =
                    64;

            constexpr uint64_t OFFSET =
                    1469598103934665603ULL;

            model->Reset(
                    TARGET_RATE,
                    PROBE_BLOCK
            );

            applyA2LiteSlimmableQuality(
                    model
            );

            std::vector<NAM_SAMPLE> input(
                    PROBE_BLOCK
            );

            std::vector<NAM_SAMPLE> output(
                    PROBE_BLOCK
            );

            NAM_SAMPLE* inputPtr =
            input.data();

            NAM_SAMPLE* outputPtr =
            output.data();

            uint64_t hash =
                    OFFSET;

            uint32_t state =
                    0x12345678u;

            for (
                    unsigned int block = 0;
                    block < PROBE_BLOCKS;
                    ++block
                    ) {
                for (
                        unsigned int frame = 0;
                        frame < PROBE_BLOCK;
                        ++frame
                        ) {
                    /*
                     * Deterministic broadband probe.
                     *
                     * The exact same input is fed to every model.
                     * Different models should therefore produce
                     * different output fingerprints.
                     */
                    state =
                            state *
                            1664525u +
                            1013904223u;

                    const int32_t signedNoise =
                            static_cast<int32_t>(
                                    state >> 8u
                            ) -
                            0x00800000;

                    const float noise =
                            static_cast<float>(
                                    signedNoise
                            ) /
                            8388608.0f;

                    const unsigned int sampleIndex =
                            block *
                            PROBE_BLOCK +
                            frame;

                    const float sine =
                            std::sin(
                                    2.0 *
                                    3.14159265358979323846 *
                                    997.0 *
                                    static_cast<double>(
                                            sampleIndex
                                    ) /
                                    static_cast<double>(
                                            TARGET_RATE
                                    )
                            );

                    input[frame] =
                            static_cast<NAM_SAMPLE>(
                                    noise *
                                    0.12f +
                                    sine *
                                    0.08f
                            );

                    output[frame] =
                            static_cast<NAM_SAMPLE>(
                                    0
                            );
                }

                model->process(
                        &inputPtr,
                        &outputPtr,
                        static_cast<int>(
                                PROBE_BLOCK
                        )
                );

                for (
                        unsigned int frame = 0;
                        frame < PROBE_BLOCK;
                        ++frame
                        ) {
                    const float sample =
                            static_cast<float>(
                                    output[frame]
                            );

                    uint32_t bits =
                            0;

                    static_assert(
                            sizeof(bits) ==
                            sizeof(sample)
                    );

                    std::memcpy(
                            &bits,
                            &sample,
                            sizeof(bits)
                    );

                    hash =
                            fnv1aUpdate(
                                    hash,
                                    &bits,
                                    sizeof(bits)
                            );
                }
            }

            /*
             * The probe intentionally changes DSP state.
             * Reset again so live audio starts from a clean state.
             */
            model->Reset(
                    TARGET_RATE,
                    DEFAULT_BLOCK_SIZE
            );

            applyA2LiteSlimmableQuality(
                    model
            );

            return hash;
        }

        void updatePeak(
                std::atomic<float>& target,
                float value
        ) {
            value =
                    std::abs(
                            value
                    );

            float current =
                    target.load();

            while (
                    value > current &&
                    !target.compare_exchange_weak(
                            current,
                            value
                    )
                    ) {
            }
        }

        bool openCompatibleUsbInterface(
                std::string& error
        ) {
            const auto endpoints =
                    findUsbPcmEndpoints();

            if (endpoints.empty()) {
                error =
                        "No USB Audio card found.\n"
                        "Connect a class-compliant USB audio interface.";

                return false;
            }

            std::ostringstream attempts;

            for (
                const auto& endpoint :
                    endpoints
                    ) {
                if (
                        !endpoint.capture ||
                        !endpoint.playback
                        ) {
                    continue;
                }

                pcm_params* captureParams =
                        pcm_params_get(
                                endpoint.card,
                                endpoint.device,
                                PCM_IN
                        );

                pcm_params* playbackParams =
                        pcm_params_get(
                                endpoint.card,
                                endpoint.device,
                                PCM_OUT
                        );

                if (
                        !captureParams ||
                        !playbackParams
                        ) {
                    attempts
                            << endpoint.cardName
                            << ": cannot read PCM parameters\n";

                    if (captureParams) {
                        pcm_params_free(
                                captureParams
                        );
                    }

                    if (playbackParams) {
                        pcm_params_free(
                                playbackParams
                        );
                    }

                    continue;
                }

                const bool rateSupported =
                        supportsValue(
                                captureParams,
                                PCM_PARAM_RATE,
                                TARGET_RATE
                        ) &&
                        supportsValue(
                                playbackParams,
                                PCM_PARAM_RATE,
                                TARGET_RATE
                        );

                if (!rateSupported) {
                    attempts
                            << endpoint.cardName
                            << ": 48 kHz unsupported\n";

                    pcm_params_free(
                            captureParams
                    );

                    pcm_params_free(
                            playbackParams
                    );

                    continue;
                }

                /*
                 * Realtime profile selection.
                 *
                 * 1 NAM:
                 *   128 frames x 4 periods
                 *   DSP deadline ~= 2.67 ms
                 *   nominal ring ~= 10.67 ms / direction
                 *
                 * 2 NAMs:
                 *   prefer 128 frames x 4 periods (DSP deadline ~= 2.67 ms)
                 *   fall back to 256 frames only when the USB PCM rejects it
                 *
                 * 3-4 NAMs:
                 *   256 frames x 4 periods
                 *   DSP deadline ~= 5.33 ms
                 *   nominal ring remains ~= 21.33 ms / direction
                 *
                 * The heavy-chain profile intentionally increases the DSP
                 * quantum without doubling total ALSA ring depth again.
                 * It is meant to absorb sustained multi-NAM execution-time
                 * spikes while keeping roughly the same total ring size as
                 * the multi-NAM stability profile.
                 */
                const unsigned int activeNamBlocks =
                        namBlockCount();

                std::vector<unsigned int> blockCandidates;

                unsigned int preferredPeriodCount =
                        DEFAULT_PERIOD_COUNT;

                if (
                        activeNamBlocks >=
                        3
                        ) {
                    blockCandidates = {
                            256,
                            128,
                            96,
                            64
                    };

                    preferredPeriodCount =
                            4;

                } else if (
                        activeNamBlocks ==
                        2
                        ) {
                    blockCandidates = {
                            128,
                            96,
                            64,
                            256
                    };

                    preferredPeriodCount =
                            4;

                } else {
                    blockCandidates = {
                            128,
                            96,
                            64,
                            256
                    };

                    preferredPeriodCount =
                            4;
                }

                const auto captureFormats =
                        supportedFormats(
                                captureParams
                        );

                const auto playbackFormats =
                        supportedFormats(
                                playbackParams
                        );

                const auto captureChannels =
                        channelCandidates(
                                captureParams
                        );

                const auto playbackChannels =
                        channelCandidates(
                                playbackParams
                        );

                const unsigned int capturePeriods =
                        choosePeriodCount(
                                captureParams,
                                preferredPeriodCount
                        );

                const unsigned int playbackPeriods =
                        choosePeriodCount(
                                playbackParams,
                                preferredPeriodCount
                        );

                bool opened =
                        false;

                for (
                    const auto blockSize :
                        blockCandidates
                        ) {
                    if (
                            !supportsValue(
                                    captureParams,
                                    PCM_PARAM_PERIOD_SIZE,
                                    blockSize
                            ) ||
                            !supportsValue(
                                    playbackParams,
                                    PCM_PARAM_PERIOD_SIZE,
                                    blockSize
                            )
                            ) {
                        continue;
                    }

                    for (
                        const auto captureFormat :
                            captureFormats
                            ) {
                        for (
                            const auto playbackFormat :
                                playbackFormats
                                ) {
                            for (
                                const auto captureChannelCount :
                                    captureChannels
                                    ) {
                                for (
                                    const auto playbackChannelCount :
                                        playbackChannels
                                        ) {
                                    pcm_config captureConfig{};

                                    captureConfig.channels =
                                            captureChannelCount;

                                    captureConfig.rate =
                                            TARGET_RATE;

                                    captureConfig.period_size =
                                            blockSize;

                                    captureConfig.period_count =
                                            capturePeriods;

                                    captureConfig.format =
                                            captureFormat;

                                    captureConfig.start_threshold =
                                            blockSize;

                                    captureConfig.stop_threshold =
                                            blockSize *
                                            capturePeriods;

                                    captureConfig.avail_min =
                                            blockSize;


                                    pcm* capture =
                                            pcm_open(
                                                    endpoint.card,
                                                    endpoint.device,
                                                    PCM_IN,
                                                    &captureConfig
                                            );

                                    if (
                                            !capture ||
                                            !pcm_is_ready(
                                                    capture
                                            )
                                            ) {
                                        if (capture) {
                                            pcm_close(
                                                    capture
                                            );
                                        }

                                        continue;
                                    }


                                    pcm_config playbackConfig{};

                                    playbackConfig.channels =
                                            playbackChannelCount;

                                    playbackConfig.rate =
                                            TARGET_RATE;

                                    playbackConfig.period_size =
                                            blockSize;

                                    playbackConfig.period_count =
                                            playbackPeriods;

                                    playbackConfig.format =
                                            playbackFormat;

                                    playbackConfig.start_threshold =
                                            blockSize;

                                    playbackConfig.stop_threshold =
                                            blockSize *
                                            playbackPeriods;

                                    playbackConfig.avail_min =
                                            blockSize;


                                    pcm* playback =
                                            pcm_open(
                                                    endpoint.card,
                                                    endpoint.device,
                                                    PCM_OUT,
                                                    &playbackConfig
                                            );

                                    if (
                                            !playback ||
                                            !pcm_is_ready(
                                                    playback
                                            )
                                            ) {
                                        if (playback) {
                                            pcm_close(
                                                    playback
                                            );
                                        }

                                        pcm_close(
                                                capture
                                        );

                                        continue;
                                    }

                                    mCapture =
                                            capture;

                                    mPlayback =
                                            playback;

                                    mCard =
                                            endpoint.card;

                                    mDevice =
                                            endpoint.device;

                                    mDeviceName =
                                            endpoint.cardName;

                                    mCaptureChannels =
                                            pcm_get_channels(
                                                    capture
                                            );

                                    mPlaybackChannels =
                                            pcm_get_channels(
                                                    playback
                                            );

                                    mCaptureFormat =
                                            pcm_get_format(
                                                    capture
                                            );

                                    mPlaybackFormat =
                                            pcm_get_format(
                                                    playback
                                            );

                                    mBlockSize =
                                            blockSize;

                                    mCapturePeriodCount =
                                            capturePeriods;

                                    mPlaybackPeriodCount =
                                            playbackPeriods;

                                    opened =
                                            true;

                                    break;
                                }

                                if (opened) {
                                    break;
                                }
                            }

                            if (opened) {
                                break;
                            }
                        }

                        if (opened) {
                            break;
                        }
                    }

                    if (opened) {
                        break;
                    }
                }

                pcm_params_free(
                        captureParams
                );

                pcm_params_free(
                        playbackParams
                );

                if (opened) {
                    LOGI(
                            "Opened USB audio: %s card=%u device=%u capture=%uch/%s periods=%u playback=%uch/%s periods=%u block=%u",
                            mDeviceName.c_str(),
                            mCard,
                            mDevice,
                            mCaptureChannels,
                            formatName(
                                    mCaptureFormat
                            ),
                            mCapturePeriodCount,
                            mPlaybackChannels,
                            formatName(
                                    mPlaybackFormat
                            ),
                            mPlaybackPeriodCount,
                            mBlockSize
                    );

                    return true;
                }

                attempts
                        << endpoint.cardName
                        << ": compatible PCM configuration not found\n";
            }

            error =
                    "No compatible full-duplex USB Audio interface.\n\n"
                    "Requirements for this milestone:\n"
                    "- USB Audio / class compliant\n"
                    "- capture + playback\n"
                    "- 48 kHz\n"
                    "- supported PCM format\n\n" +
                    attempts.str();

            return false;
        }

        void closePcm() {
            if (mCapture) {
                pcm_close(
                        mCapture
                );

                mCapture =
                        nullptr;
            }

            if (mPlayback) {
                pcm_close(
                        mPlayback
                );

                mPlayback =
                        nullptr;
            }
        }

        void resetStats() {
            mBlocks.store(
                    0
            );

            mTotalProcessNs.store(
                    0
            );

            mMaxProcessNs.store(
                    0
            );

            mOverBudget.store(
                    0
            );

            mCaptureErrors.store(
                    0
            );

            mPlaybackErrors.store(
                    0
            );

            mCapturePeak.store(
                    0.0f
            );

            mNamInputPeak.store(
                    0.0f
            );

            mNamOutputPeak.store(
                    0.0f
            );

            mPostEqPeak.store(
                    0.0f
            );

            mGateGainMonitor.store(
                    1.0f
            );

            mGaplessPresetSwitches.store(
                    0
            );

            mCrossfadeOverBudget.store(
                    0
            );

            mMaxCaptureGapNs.store(
                    0
            );

            mLateCaptureGaps.store(
                    0
            );

            mVeryLateCaptureGaps.store(
                    0
            );

            mMaxLoopWorkNs.store(
                    0
            );

            mMaxReadWaitNs.store(
                    0
            );

            mMaxWriteWaitNs.store(
                    0
            );

            mMaxNonIoWorkNs.store(
                    0
            );

            mLateReadWaits.store(
                    0
            );

            mLateWriteWaits.store(
                    0
            );

            mNonIoOverBudget.store(
                    0
            );

            mAudioThreadNiceSetResult.store(
                    -9999
            );

            mAudioThreadNiceErrno.store(
                    0
            );

            mAudioThreadNiceActual.store(
                    0
            );

            mAudioThreadFifoSetResult.store(
                    -9999
            );

            mAudioThreadFifoError.store(
                    0
            );

            mAudioThreadPolicy.store(
                    SCHED_OTHER
            );

            mAudioThreadPriority.store(
                    0
            );

            mAudioThreadAffinitySetResult.store(
                    -9999
            );

            mAudioThreadAffinityError.store(
                    0
            );

            mAudioThreadAllowedCpuMask.store(
                    0
            );

            mAudioThreadSelectedCpuMask.store(
                    0
            );

            mAudioThreadAffinityMaxFreqKhz.store(
                    0
            );
        }


        void updateMaxAtomic(
                std::atomic<uint64_t>& target,
                uint64_t value
        ) {
            uint64_t current =
                    target.load(
                            std::memory_order_relaxed
                    );

            while (
                    value > current &&
                    !target.compare_exchange_weak(
                            current,
                            value,
                            std::memory_order_relaxed
                    )
                    ) {
            }
        }


        void updateMaxProcessTime(
                uint64_t value
        ) {
            updateMaxAtomic(
                    mMaxProcessNs,
                    value
            );
        }


        static const char* schedulerPolicyName(
                int policy
        ) {
            switch (policy) {
                case SCHED_FIFO:
                    return "SCHED_FIFO";

                case SCHED_RR:
                    return "SCHED_RR";

                case SCHED_OTHER:
                    return "SCHED_OTHER";

#ifdef SCHED_BATCH
                case SCHED_BATCH:
                    return "SCHED_BATCH";
#endif

#ifdef SCHED_IDLE
                case SCHED_IDLE:
                    return "SCHED_IDLE";
#endif

                default:
                    return "UNKNOWN";
            }
        }


        void configurePerformanceCoreAffinity() {
            cpu_set_t allowedSet;

            CPU_ZERO(
                    &allowedSet
            );

            /*
             * sched_getaffinity(pid=0, ...) targets the calling audio thread.
             * We use the actual set currently allowed by Android/cpuset and
             * never assume a hard-coded CPU number.
             */
            const int getAffinityResult =
                    sched_getaffinity(
                            0,
                            sizeof(allowedSet),
                            &allowedSet
                    );


            if (
                    getAffinityResult !=
                    0
                    ) {
                mAudioThreadAffinitySetResult.store(
                        -1
                );

                const int affinityError =
                        errno;

                mAudioThreadAffinityError.store(
                        affinityError
                );

                LOGE(
                        "Unable to read current CPU affinity: errno=%d",
                        affinityError
                );

                return;
            }


            uint64_t allowedMask =
                    0;

            uint64_t maxFreqKhz =
                    0;

            int fastestCpu =
                    -1;


            for (
                    int cpu = 0;
                    cpu < CPU_SETSIZE &&
                    cpu < 64;
                    ++cpu
                    ) {
                if (!CPU_ISSET(cpu, &allowedSet)) {
                    continue;
                }


                allowedMask |=
                        (
                                1ULL <<
                                     cpu
                        );


                uint64_t frequency =
                        0;


                const std::string maxFreqPath =
                        "/sys/devices/system/cpu/cpu" +
                        std::to_string(
                                cpu
                        ) +
                        "/cpufreq/cpuinfo_max_freq";


                if (
                        !readUnsignedFile(
                                maxFreqPath,
                                frequency
                        )
                        ) {
                    const std::string scalingMaxPath =
                            "/sys/devices/system/cpu/cpu" +
                            std::to_string(
                                    cpu
                            ) +
                            "/cpufreq/scaling_max_freq";


                    readUnsignedFile(
                            scalingMaxPath,
                            frequency
                    );
                }


                /*
                 * Select exactly ONE fastest allowed CPU.
                 *
                 * The NAM chain is serial inside one realtime thread, so
                 * giving the thread multiple CPUs does not make one block
                 * execute in parallel. It only permits migration.
                 *
                 * On equal-frequency CPUs, prefer the higher CPU index.
                 * On the Snapdragon 865 performance cluster this is a useful
                 * deterministic tie-break and eliminates inter-core migration
                 * and the associated cache/scheduler jitter.
                 */
                if (
                        frequency >
                        maxFreqKhz ||
                        (
                                frequency ==
                                maxFreqKhz &&
                                cpu >
                                fastestCpu
                        )
                        ) {
                    maxFreqKhz =
                            frequency;

                    fastestCpu =
                            cpu;
                }
            }


            mAudioThreadAllowedCpuMask.store(
                    allowedMask
            );

            mAudioThreadAffinityMaxFreqKhz.store(
                    maxFreqKhz
            );


            if (
                    allowedMask ==
                    0
                    ) {
                mAudioThreadAffinitySetResult.store(
                        -1
                );

                mAudioThreadAffinityError.store(
                        EINVAL
                );

                LOGE(
                        "CPU affinity: current allowed set is empty"
                );

                return;
            }


            if (
                    fastestCpu <
                    0 ||
                    maxFreqKhz ==
                    0
                    ) {
                mAudioThreadAffinitySetResult.store(
                        1
                );

                mAudioThreadAffinityError.store(
                        0
                );

                mAudioThreadSelectedCpuMask.store(
                        allowedMask
                );

                LOGI(
                        "CPU affinity unchanged: cpufreq max values unavailable; allowed CPUs=%s",
                        cpuMaskToString(
                                allowedMask
                        ).c_str()
                );

                return;
            }


            cpu_set_t selectedSet;

            CPU_ZERO(
                    &selectedSet
            );

            CPU_SET(
                    fastestCpu,
                    &selectedSet
            );


            const uint64_t selectedMask =
                    (
                            1ULL <<
                                 fastestCpu
                    );


            /*
             * One fixed performance CPU for the serial DSP chain.
             * No syscall is made from the per-block hot path.
             */
            const int setAffinityResult =
                    sched_setaffinity(
                            0,
                            sizeof(selectedSet),
                            &selectedSet
                    );


            const int setAffinityError =
                    setAffinityResult == 0
                    ? 0
                    : errno;


            mAudioThreadAffinitySetResult.store(
                    setAffinityResult == 0
                    ? 0
                    : -1
            );

            mAudioThreadAffinityError.store(
                    setAffinityError
            );

            mAudioThreadSelectedCpuMask.store(
                    selectedMask
            );


            LOGI(
                    "Audio CPU affinity: allowed=%s selected=%s fastestCpu=%d maxFreq=%llu kHz result=%d errno=%d",
                    cpuMaskToString(
                            allowedMask
                    ).c_str(),
                    cpuMaskToString(
                            selectedMask
                    ).c_str(),
                    fastestCpu,
                    static_cast<unsigned long long>(
                            maxFreqKhz
                    ),
                    setAffinityResult,
                    setAffinityError
            );
        }


        void configureAudioThreadScheduling() {
            /*
             * All of this runs exactly once, before entering the realtime
             * audio loop. No scheduling syscalls are performed per block.
             *
             * Android may reject negative nice values and/or SCHED_FIFO for
             * an ordinary app UID. That is expected; this is intentionally
             * best-effort and the result is exposed in NAM PERFORMANCE.
             */
            pthread_setname_np(
                    pthread_self(),
                    "Tone3000Audio"
            );


            configurePerformanceCoreAffinity();


            constexpr int targetNice =
                    -16;


            errno =
                    0;

            const int niceResult =
                    setpriority(
                            PRIO_PROCESS,
                            0,
                            targetNice
                    );

            const int niceError =
                    niceResult == 0
                    ? 0
                    : errno;


            errno =
                    0;

            const int actualNice =
                    getpriority(
                            PRIO_PROCESS,
                            0
                    );


            int fifoMin =
                    sched_get_priority_min(
                            SCHED_FIFO
                    );

            int fifoMax =
                    sched_get_priority_max(
                            SCHED_FIFO
                    );

            if (fifoMin < 1) {
                fifoMin =
                        1;
            }

            if (fifoMax < fifoMin) {
                fifoMax =
                        fifoMin;
            }

            const int targetFifoPriority =
                    std::clamp(
                            2,
                            fifoMin,
                            fifoMax
                    );


            sched_param requested{};

            requested.sched_priority =
                    targetFifoPriority;


            const int fifoResult =
                    pthread_setschedparam(
                            pthread_self(),
                            SCHED_FIFO,
                            &requested
                    );


            sched_param actualParams{};

            int actualPolicy =
                    SCHED_OTHER;

            const int getSchedResult =
                    pthread_getschedparam(
                            pthread_self(),
                            &actualPolicy,
                            &actualParams
                    );


            if (getSchedResult != 0) {
                actualPolicy =
                        SCHED_OTHER;

                actualParams.sched_priority =
                        0;
            }


            mAudioThreadNiceSetResult.store(
                    niceResult
            );

            mAudioThreadNiceErrno.store(
                    niceError
            );

            mAudioThreadNiceActual.store(
                    actualNice
            );

            mAudioThreadFifoSetResult.store(
                    fifoResult == 0
                    ? 0
                    : -1
            );

            mAudioThreadFifoError.store(
                    fifoResult
            );

            mAudioThreadPolicy.store(
                    actualPolicy
            );

            mAudioThreadPriority.store(
                    actualParams.sched_priority
            );


            LOGI(
                    "Audio thread scheduling: nice target=%d setResult=%d errno=%d actualNice=%d fifoPriority=%d fifoResult=%d actualPolicy=%s actualPriority=%d",
                    targetNice,
                    niceResult,
                    niceError,
                    actualNice,
                    targetFifoPriority,
                    fifoResult,
                    schedulerPolicyName(
                            actualPolicy
                    ),
                    actualParams.sched_priority
            );
        }

        void applyPendingPresetSwitchAtBlockBoundary() {
            if (
                    !mPresetSwitchPending.load(
                            std::memory_order_acquire
                    )
                    ) {
                return;
            }


            if (!mModelSwapMutex.try_lock()) {
                return;
            }


            if (
                    !mPresetSwitchPending.load(
                            std::memory_order_relaxed
                    ) ||
                    !mPendingModel
                    ) {
                mModelSwapMutex.unlock();

                return;
            }


            /*
             * Ownership moves only. No allocation and no model destruction
             * occurs in the realtime thread.
             */
            mCrossfadeOldModel =
                    std::move(
                            mNamModels[0]
                    );

            mNamModels[0] =
                    std::move(
                            mPendingModel
                    );


            mInputGainDb.store(
                    mPendingInputGainDb,
                    std::memory_order_relaxed
            );

            mInputGainLinear.store(
                    mPendingInputGainLinear,
                    std::memory_order_relaxed
            );

            mOutputGainDb.store(
                    mPendingOutputGainDb,
                    std::memory_order_relaxed
            );

            mOutputGainLinear.store(
                    mPendingOutputGainLinear,
                    std::memory_order_relaxed
            );

            mSelectedInputChannel.store(
                    mPendingInputChannel,
                    std::memory_order_relaxed
            );

            mSelectedOutputPair.store(
                    mPendingOutputPair,
                    std::memory_order_relaxed
            );

            mGateEnabled.store(
                    mPendingGateEnabled,
                    std::memory_order_relaxed
            );

            mGateThresholdDb.store(
                    mPendingGateThresholdDb,
                    std::memory_order_relaxed
            );

            mGateThresholdLinear.store(
                    mPendingGateThresholdLinear,
                    std::memory_order_relaxed
            );

            mEqLowDb.store(
                    mPendingEqLowDb,
                    std::memory_order_relaxed
            );

            mEqMidDb.store(
                    mPendingEqMidDb,
                    std::memory_order_relaxed
            );

            mEqHighDb.store(
                    mPendingEqHighDb,
                    std::memory_order_relaxed
            );


            mModelFileFingerprint.store(
                    mPendingFileFingerprint,
                    std::memory_order_relaxed
            );

            mModelProbeFingerprint.store(
                    mPendingProbeFingerprint,
                    std::memory_order_relaxed
            );

            mIsSlimmable.store(
                    mPendingIsSlimmable,
                    std::memory_order_relaxed
            );

            mSlimSize.store(
                    mPendingSlimSize,
                    std::memory_order_relaxed
            );

            mModelGeneration.fetch_add(
                    1,
                    std::memory_order_relaxed
            );

            mNamBypass[0].store(
                    false,
                    std::memory_order_relaxed
            );


            mCrossfadePositionSamples =
                    0;


            mCrossfadeActive.store(
                    mCrossfadeOldModel !=
                    nullptr,
                    std::memory_order_release
            );


            mPresetSwitchPending.store(
                    false,
                    std::memory_order_release
            );


            mGaplessPresetSwitches.fetch_add(
                    1,
                    std::memory_order_relaxed
            );


            mModelSwapMutex.unlock();
        }


        void audioLoop() {
            const unsigned int frames =
                    mBlockSize;

            const size_t captureBufferSize =
                    static_cast<size_t>(
                            frames
                    ) *
                    mCaptureChannels *
                    bytesPerSample(
                            mCaptureFormat
                    );

            const size_t playbackBufferSize =
                    static_cast<size_t>(
                            frames
                    ) *
                    mPlaybackChannels *
                    bytesPerSample(
                            mPlaybackFormat
                    );

            std::vector<uint8_t> captureBuffer(
                    captureBufferSize
            );

            std::vector<uint8_t> playbackBuffer(
                    playbackBufferSize
            );

            std::vector<NAM_SAMPLE> namInput(
                    frames
            );

            std::vector<NAM_SAMPLE> namOutput(
                    frames
            );

            std::vector<NAM_SAMPLE> namOldOutput(
                    frames
            );

            std::vector<NAM_SAMPLE> namChainA(
                    frames
            );

            std::vector<NAM_SAMPLE> namChainB(
                    frames
            );

            std::vector<NAM_SAMPLE> namDry(
                    frames
            );

            std::vector<float> postEqBuffer(frames);
            std::vector<float> irDry(frames);
            std::vector<double> irInput(frames);
            double* irInputPointers[1] = {irInput.data()};
            std::array<Biquad, 6> irEq;
            std::array<float, 6> currentIrEqDb;
            currentIrEqDb.fill(999.0f);

            NAM_SAMPLE* namInputPtr =
            namInput.data();

            NAM_SAMPLE* namOutputPtr =
            namOutput.data();

            NAM_SAMPLE* namOldOutputPtr =
            namOldOutput.data();

            const uint64_t budgetNs =
                    static_cast<uint64_t>(
                            (
                                    static_cast<double>(
                                            frames
                                    ) /
                                    static_cast<double>(
                                            TARGET_RATE
                                    )
                            ) *
                            1000000000.0
                    );


            const uint64_t lateGapThresholdNs =
                    budgetNs +
                    budgetNs / 2;

            const uint64_t veryLateGapThresholdNs =
                    budgetNs *
                    2;


            configureAudioThreadScheduling();


            /*
             * Ignore the first few successful blocks for gap statistics.
             * USB PCM startup can legitimately contain one-off timing
             * transients that are not representative of steady-state audio.
             */
            constexpr uint64_t JITTER_WARMUP_BLOCKS =
                    32;


            uint64_t successfulLoopBlocks =
                    0;

            bool havePreviousCaptureWake =
                    false;

            std::chrono::steady_clock::time_point previousCaptureWake;

            /*
             * M2.3a realtime DSP state.
             *
             * Gate runs before NAM.
             * 3-band EQ runs after NAM.
             *
             * Filter coefficients are rebuilt only when the UI changes
             * a band gain, never for every individual sample.
             */
            float gateEnvelope =
                    0.0f;

            float gateGain =
                    1.0f;

            bool gateOpen =
                    true;


            const float gateEnvelopeAttackCoeff =
                    std::exp(
                            -1.0f /
                            (
                                    0.0015f *
                                    static_cast<float>(
                                            TARGET_RATE
                                    )
                            )
                    );

            const float gateEnvelopeReleaseCoeff =
                    std::exp(
                            -1.0f /
                            (
                                    0.060f *
                                    static_cast<float>(
                                            TARGET_RATE
                                    )
                            )
                    );

            const float gateGainAttackCoeff =
                    std::exp(
                            -1.0f /
                            (
                                    0.003f *
                                    static_cast<float>(
                                            TARGET_RATE
                                    )
                            )
                    );

            const float gateGainReleaseCoeff =
                    std::exp(
                            -1.0f /
                            (
                                    0.080f *
                                    static_cast<float>(
                                            TARGET_RATE
                                    )
                            )
                    );


            Biquad lowEq;
            Biquad midEq;
            Biquad highEq;

            std::array<Biquad, MAX_NAM_BLOCKS> namLowEq;
            std::array<Biquad, MAX_NAM_BLOCKS> namMidEq;
            std::array<Biquad, MAX_NAM_BLOCKS> namHighEq;
            std::array<Biquad, MAX_NAM_BLOCKS> namBand3Eq;
            std::array<Biquad, MAX_NAM_BLOCKS> namBand4Eq;
            std::array<Biquad, MAX_NAM_BLOCKS> namBand5Eq;

            std::array<float, MAX_NAM_BLOCKS> currentNamLowDb;
            std::array<float, MAX_NAM_BLOCKS> currentNamMidDb;
            std::array<float, MAX_NAM_BLOCKS> currentNamHighDb;
            std::array<float, MAX_NAM_BLOCKS> currentNamBand3Db;
            std::array<float, MAX_NAM_BLOCKS> currentNamBand4Db;
            std::array<float, MAX_NAM_BLOCKS> currentNamBand5Db;
            currentNamLowDb.fill(999.0f);
            currentNamMidDb.fill(999.0f);
            currentNamHighDb.fill(999.0f);
            currentNamBand3Db.fill(999.0f);
            currentNamBand4Db.fill(999.0f);
            currentNamBand5Db.fill(999.0f);

            auto processNamControls = [&](unsigned int slot, NAM_SAMPLE* buffer, const NAM_SAMPLE* dry) {
                const float lowDb = mNamEqLowDb[slot].load(std::memory_order_relaxed);
                const float midDb = mNamEqMidDb[slot].load(std::memory_order_relaxed);
                const float highDb = mNamEqHighDb[slot].load(std::memory_order_relaxed);
                const float band3Db = mNamEqBand3Db[slot].load(std::memory_order_relaxed);
                const float band4Db = mNamEqBand4Db[slot].load(std::memory_order_relaxed);
                const float band5Db = mNamEqBand5Db[slot].load(std::memory_order_relaxed);
                if (std::abs(lowDb - currentNamLowDb[slot]) > 0.001f) {
                    namLowEq[slot].setLowShelf(TARGET_RATE, 120.0, lowDb);
                    currentNamLowDb[slot] = lowDb;
                }
                if (std::abs(midDb - currentNamMidDb[slot]) > 0.001f) {
                    namMidEq[slot].setPeaking(TARGET_RATE, 750.0, 0.8, midDb);
                    currentNamMidDb[slot] = midDb;
                }
                if (std::abs(highDb - currentNamHighDb[slot]) > 0.001f) {
                    namHighEq[slot].setHighShelf(TARGET_RATE, 4000.0, highDb);
                    currentNamHighDb[slot] = highDb;
                }
                if (std::abs(band3Db - currentNamBand3Db[slot]) > 0.001f) {
                    namBand3Eq[slot].setPeaking(TARGET_RATE, 1800.0, 0.8, band3Db);
                    currentNamBand3Db[slot] = band3Db;
                }
                if (std::abs(band4Db - currentNamBand4Db[slot]) > 0.001f) {
                    namBand4Eq[slot].setPeaking(TARGET_RATE, 3500.0, 0.8, band4Db);
                    currentNamBand4Db[slot] = band4Db;
                }
                if (std::abs(band5Db - currentNamBand5Db[slot]) > 0.001f) {
                    namBand5Eq[slot].setHighShelf(TARGET_RATE, 8000.0, band5Db);
                    currentNamBand5Db[slot] = band5Db;
                }
                const float gain = std::pow(10.0f,
                                            mNamGainDb[slot].load(std::memory_order_relaxed) / 20.0f);
                const float mix = mNamMix[slot].load(std::memory_order_relaxed);
                const bool normalize = mNamNormalize[slot].load(std::memory_order_relaxed);
                const float normalizeGain = mNamNormalizeGain[slot].load(std::memory_order_relaxed);
                float blockPeak = 0.0f;
                for (unsigned int frame = 0; frame < frames; ++frame) {
                    float value = static_cast<float>(buffer[frame]);
                    if (!mNamEqPre[slot].load(std::memory_order_relaxed)) {
                        value = namLowEq[slot].process(value);
                        value = namMidEq[slot].process(value);
                        value = namHighEq[slot].process(value);
                        value = namBand3Eq[slot].process(value);
                        value = namBand4Eq[slot].process(value);
                        value = namBand5Eq[slot].process(value);
                    }
                    value = (static_cast<float>(dry[frame]) * (1.0f - mix) + value * mix) * gain * normalizeGain;
                    blockPeak = std::max(blockPeak, std::abs(value));
                    buffer[frame] = static_cast<NAM_SAMPLE>(value);
                }
                if (normalize && blockPeak > 0.0001f) {
                    const float targetGain = std::clamp(0.65f / blockPeak, 0.25f, 4.0f);
                    const float smoothed = normalizeGain * 0.98f + targetGain * 0.02f;
                    mNamNormalizeGain[slot].store(smoothed, std::memory_order_relaxed);
                }
            };

            auto processNamPreEq = [&](unsigned int slot, NAM_SAMPLE* buffer) {
                if (!mNamEqPre[slot].load(std::memory_order_relaxed)) return;
                for (unsigned int frame = 0; frame < frames; ++frame) {
                    float value = static_cast<float>(buffer[frame]);
                    value = namLowEq[slot].process(value);
                    value = namMidEq[slot].process(value);
                    value = namHighEq[slot].process(value);
                    value = namBand3Eq[slot].process(value);
                    value = namBand4Eq[slot].process(value);
                    value = namBand5Eq[slot].process(value);
                    buffer[frame] = static_cast<NAM_SAMPLE>(value);
                }
            };

            auto processOutputIr = [&](NAM_SAMPLE* buffer) {
                if (!mOutputIr || mOutputIrBypass.load(std::memory_order_relaxed)) return;
                const float inGain = std::pow(10.0f, mOutputIrInGainDb.load(std::memory_order_relaxed) / 20.0f);
                const float outGain = std::pow(10.0f, mOutputIrOutGainDb.load(std::memory_order_relaxed) / 20.0f);
                const float mix = mOutputIrMix.load(std::memory_order_relaxed);
                const float eqValues[6] = {
                        mOutputIrEqDb[0].load(std::memory_order_relaxed),
                        mOutputIrEqDb[1].load(std::memory_order_relaxed),
                        mOutputIrEqDb[2].load(std::memory_order_relaxed),
                        mOutputIrEqDb[3].load(std::memory_order_relaxed),
                        mOutputIrEqDb[4].load(std::memory_order_relaxed),
                        mOutputIrEqDb[5].load(std::memory_order_relaxed)
                };
                if (eqValues[0] != currentIrEqDb[0]) { irEq[0].setLowShelf(TARGET_RATE, 120.0, eqValues[0]); currentIrEqDb[0] = eqValues[0]; }
                if (eqValues[1] != currentIrEqDb[1]) { irEq[1].setPeaking(TARGET_RATE, 750.0, 0.8, eqValues[1]); currentIrEqDb[1] = eqValues[1]; }
                if (eqValues[2] != currentIrEqDb[2]) { irEq[2].setHighShelf(TARGET_RATE, 4000.0, eqValues[2]); currentIrEqDb[2] = eqValues[2]; }
                if (eqValues[3] != currentIrEqDb[3]) { irEq[3].setPeaking(TARGET_RATE, 1800.0, 0.8, eqValues[3]); currentIrEqDb[3] = eqValues[3]; }
                if (eqValues[4] != currentIrEqDb[4]) { irEq[4].setPeaking(TARGET_RATE, 3500.0, 0.8, eqValues[4]); currentIrEqDb[4] = eqValues[4]; }
                if (eqValues[5] != currentIrEqDb[5]) { irEq[5].setHighShelf(TARGET_RATE, 8000.0, eqValues[5]); currentIrEqDb[5] = eqValues[5]; }
                const bool eqPre = mOutputIrEqPre.load(std::memory_order_relaxed);
                for (unsigned int frame = 0; frame < frames; ++frame) {
                    irDry[frame] = static_cast<float>(buffer[frame]);
                    float value = irDry[frame] * inGain;
                    if (eqPre) {
                        for (auto& filter : irEq) value = filter.process(value);
                    }
                    irInput[frame] = static_cast<double>(value);
                }
                double** irOutput = mOutputIr->Process(irInputPointers, 1, frames);
                for (unsigned int frame = 0; frame < frames; ++frame) {
                    const float wet = static_cast<float>(irOutput[0][frame]) * outGain;
                    float wetValue = wet;
                    if (!eqPre) {
                        for (auto& filter : irEq) wetValue = filter.process(wetValue);
                    }
                    buffer[frame] = static_cast<NAM_SAMPLE>(irDry[frame] * (1.0f - mix) + wetValue * mix);
                }
            };


            float currentLowDb =
                    999.0f;

            float currentMidDb =
                    999.0f;

            float currentHighDb =
                    999.0f;


            while (
                    mRunning.load()
                    ) {
                const auto readStart =
                        std::chrono::steady_clock::now();


                const int readResult =
                        pcm_readi(
                                mCapture,
                                captureBuffer.data(),
                                frames
                        );


                const auto readEnd =
                        std::chrono::steady_clock::now();


                const uint64_t readWaitNs =
                        static_cast<uint64_t>(
                                std::chrono::duration_cast<
                                        std::chrono::nanoseconds
                                >(
                                        readEnd -
                                        readStart
                                ).count()
                        );


                if (readResult < 0) {
                    mCaptureErrors.fetch_add(
                            1
                    );

                    std::this_thread::sleep_for(
                            std::chrono::milliseconds(
                                    1
                            )
                    );

                    havePreviousCaptureWake =
                            false;

                    continue;
                }


                const auto captureWake =
                        readEnd;


                if (
                        havePreviousCaptureWake &&
                        successfulLoopBlocks >=
                        JITTER_WARMUP_BLOCKS
                        ) {
                    const uint64_t captureGapNs =
                            static_cast<uint64_t>(
                                    std::chrono::duration_cast<
                                            std::chrono::nanoseconds
                                    >(
                                            captureWake -
                                            previousCaptureWake
                                    ).count()
                            );


                    updateMaxAtomic(
                            mMaxCaptureGapNs,
                            captureGapNs
                    );


                    if (
                            captureGapNs >
                            lateGapThresholdNs
                            ) {
                        mLateCaptureGaps.fetch_add(
                                1
                        );
                    }


                    if (
                            captureGapNs >
                            veryLateGapThresholdNs
                            ) {
                        mVeryLateCaptureGaps.fetch_add(
                                1
                        );
                    }
                }


                previousCaptureWake =
                        captureWake;

                havePreviousCaptureWake =
                        true;

                ++successfulLoopBlocks;


                applyPendingPresetSwitchAtBlockBoundary();


                if (
                        successfulLoopBlocks >
                        JITTER_WARMUP_BLOCKS
                        ) {
                    updateMaxAtomic(
                            mMaxReadWaitNs,
                            readWaitNs
                    );


                    if (
                            readWaitNs >
                            veryLateGapThresholdNs
                            ) {
                        mLateReadWaits.fetch_add(
                                1
                        );
                    }
                }


                const auto loopWorkStart =
                        captureWake;


                std::fill(
                        playbackBuffer.begin(),
                        playbackBuffer.end(),
                        0
                );

                const float inputGain =
                        mInputGainLinear.load();

                const float outputGain =
                        mOutputGainLinear.load();

                const bool gateEnabled =
                        mGateEnabled.load();

                const float gateThreshold =
                        mGateThresholdLinear.load();

                const float lowDb =
                        mEqLowDb.load();

                const float midDb =
                        mEqMidDb.load();

                const float highDb =
                        mEqHighDb.load();


                if (
                        std::abs(
                                lowDb -
                                currentLowDb
                        ) >
                        0.001f
                        ) {
                    lowEq.setLowShelf(
                            TARGET_RATE,
                            120.0,
                            lowDb
                    );

                    currentLowDb =
                            lowDb;
                }


                if (
                        std::abs(
                                midDb -
                                currentMidDb
                        ) >
                        0.001f
                        ) {
                    midEq.setPeaking(
                            TARGET_RATE,
                            750.0,
                            0.8,
                            midDb
                    );

                    currentMidDb =
                            midDb;
                }


                if (
                        std::abs(
                                highDb -
                                currentHighDb
                        ) >
                        0.001f
                        ) {
                    highEq.setHighShelf(
                            TARGET_RATE,
                            4000.0,
                            highDb
                    );

                    currentHighDb =
                            highDb;
                }


                const unsigned int inputChannel =
                        mCaptureChannels > 0
                        ? std::min(
                                mSelectedInputChannel.load(),
                                mCaptureChannels -
                                1
                        )
                        : 0;


                const unsigned int outputPairCount =
                        mPlaybackChannels > 0
                        ? (
                                  mPlaybackChannels +
                                  1
                          ) /
                          2
                        : 0;


                const unsigned int outputPair =
                        outputPairCount > 0
                        ? std::min(
                                mSelectedOutputPair.load(),
                                outputPairCount -
                                1
                        )
                        : 0;


                const unsigned int outputLeftChannel =
                        outputPair *
                        2;


                const unsigned int outputRightChannel =
                        outputLeftChannel +
                        1;


                float captureBlockPeak =
                        0.0f;

                float namInputBlockPeak =
                        0.0f;

                for (
                        unsigned int frame = 0;
                        frame < frames;
                        ++frame
                        ) {
                    const float captureSample =
                            readSample(
                                    captureBuffer.data(),
                                    frame,
                                    inputChannel,
                                    mCaptureChannels,
                                    mCaptureFormat
                            );

                    captureBlockPeak =
                            std::max(
                                    captureBlockPeak,
                                    std::abs(
                                            captureSample
                                    )
                            );

                    float modelInput =
                            captureSample *
                            inputGain;


                    if (gateEnabled) {
                        const float detector =
                                std::abs(
                                        modelInput
                                );


                        const float envelopeCoeff =
                                detector >
                                gateEnvelope
                                ? gateEnvelopeAttackCoeff
                                : gateEnvelopeReleaseCoeff;


                        gateEnvelope =
                                envelopeCoeff *
                                gateEnvelope +
                                (
                                        1.0f -
                                        envelopeCoeff
                                ) *
                                detector;


                        const float closeThreshold =
                                gateThreshold *
                                0.7079458f;


                        if (
                                !gateOpen &&
                                gateEnvelope >=
                                gateThreshold
                                ) {
                            gateOpen =
                                    true;
                        } else if (
                                gateOpen &&
                                gateEnvelope <=
                                closeThreshold
                                ) {
                            gateOpen =
                                    false;
                        }


                        const float targetGateGain =
                                gateOpen
                                ? 1.0f
                                : 0.0f;


                        const float gainCoeff =
                                targetGateGain >
                                gateGain
                                ? gateGainAttackCoeff
                                : gateGainReleaseCoeff;


                        gateGain =
                                gainCoeff *
                                gateGain +
                                (
                                        1.0f -
                                        gainCoeff
                                ) *
                                targetGateGain;


                        modelInput *=
                                gateGain;

                    } else {
                        gateEnvelope =
                                0.0f;

                        gateGain =
                                1.0f;

                        gateOpen =
                                true;
                    }


                    namInputBlockPeak =
                            std::max(
                                    namInputBlockPeak,
                                    std::abs(
                                            modelInput
                                    )
                            );

                    namInput[frame] =
                            static_cast<NAM_SAMPLE>(
                                    modelInput
                            );
                }

                updatePeak(
                        mCapturePeak,
                        captureBlockPeak
                );

                updatePeak(
                        mNamInputPeak,
                        namInputBlockPeak
                );


                /* Slot 0 uses the same block-local bypass as every slot. */
                const bool firstSlotBypassed =
                        mNamBypass[0].load(
                                std::memory_order_relaxed
                        );


                /*
                 * If slot 0 is bypassed during an in-flight preset crossfade,
                 * cancel the crossfade state only. Old-model destruction
                 * remains outside the realtime thread.
                 */
                if (
                        firstSlotBypassed &&
                        mCrossfadeActive.load(
                                std::memory_order_acquire
                        )
                        ) {
                    mCrossfadeActive.store(
                            false,
                            std::memory_order_release
                    );

                    mCrossfadePositionSamples =
                            PRESET_CROSSFADE_SAMPLES;
                }


                const bool crossfadeThisBlock =
                        !firstSlotBypassed &&
                        mCrossfadeActive.load(
                                std::memory_order_acquire
                        ) &&
                        mCrossfadeOldModel;

                if (mOutputIrPosition.load(std::memory_order_relaxed) == 0) {
                    processOutputIr(namInput.data());
                }


                const auto start =
                        std::chrono::steady_clock::now();


                if (firstSlotBypassed) {
                    /*
                     * Block-local bypass:
                     * the gate output is forwarded to the next NAM block.
                     */
                    std::copy(
                            namInput.begin(),
                            namInput.end(),
                            namChainA.begin()
                    );

                } else {
                    std::copy(namInput.begin(), namInput.end(), namDry.begin());
                    const float inGain = std::pow(10.0f,
                            mNamInGainDb[0].load(std::memory_order_relaxed) / 20.0f);
                    for (auto& sample : namInput) sample = static_cast<NAM_SAMPLE>(sample * inGain);
                    processNamPreEq(0, namInput.data());
                    if (crossfadeThisBlock) {
                        mCrossfadeOldModel->process(
                                &namInputPtr,
                                &namOldOutputPtr,
                                static_cast<int>(
                                        frames
                                )
                        );
                    }


                    mNamModels[0]->process(
                            &namInputPtr,
                            &namOutputPtr,
                            static_cast<int>(
                                    frames
                            )
                    );


                    /*
                     * Produce one concrete buffer for slot 0 output.
                     * During a gapless preset switch this is the old/new
                     * crossfade. Otherwise it is the current slot 0 model.
                     */
                    for (
                            unsigned int frame = 0;
                            frame < frames;
                            ++frame
                            ) {
                        const float newNamOutput =
                                static_cast<float>(
                                        namOutput[frame]
                                );


                        float firstSlotOutput =
                                newNamOutput;


                        if (crossfadeThisBlock) {
                            const float oldNamOutput =
                                    static_cast<float>(
                                            namOldOutput[frame]
                                    );


                            const unsigned int absoluteCrossfadeSample =
                                    std::min(
                                            mCrossfadePositionSamples +
                                            frame +
                                            1,
                                            PRESET_CROSSFADE_SAMPLES
                                    );


                            const float mix =
                                    static_cast<float>(
                                            absoluteCrossfadeSample
                                    ) /
                                    static_cast<float>(
                                            PRESET_CROSSFADE_SAMPLES
                                    );


                            firstSlotOutput =
                                    oldNamOutput *
                                    (
                                            1.0f -
                                            mix
                                    ) +
                                    newNamOutput *
                                    mix;
                        }


                        namChainA[frame] =
                                static_cast<NAM_SAMPLE>(
                                        firstSlotOutput
                                );
                    }

                    processNamControls(0, namChainA.data(), namDry.data());
                }

                if (mOutputIrPosition.load(std::memory_order_relaxed) == 1) {
                    processOutputIr(namChainA.data());
                }


                NAM_SAMPLE* chainCurrentPtr =
                namChainA.data();

                NAM_SAMPLE* chainScratchPtr =
                namChainB.data();


                for (
                        unsigned int slot = 1;
                        slot < MAX_NAM_BLOCKS;
                        ++slot
                        ) {
                    auto* model =
                            mNamModels[slot].get();


                    if (
                            !model ||
                            mNamBypass[slot].load(
                                    std::memory_order_relaxed
                            )
                            ) {
                        continue;
                    }


                    std::copy(chainCurrentPtr, chainCurrentPtr + frames, namDry.begin());
                    const float inGain = std::pow(10.0f,
                            mNamInGainDb[slot].load(std::memory_order_relaxed) / 20.0f);
                    for (unsigned int frame = 0; frame < frames; ++frame) {
                        chainCurrentPtr[frame] = static_cast<NAM_SAMPLE>(chainCurrentPtr[frame] * inGain);
                    }
                    processNamPreEq(slot, chainCurrentPtr);

                    model->process(
                            &chainCurrentPtr,
                            &chainScratchPtr,
                            static_cast<int>(
                                    frames
                            )
                    );

                    processNamControls(slot, chainScratchPtr, namDry.data());


                    std::swap(
                            chainCurrentPtr,
                            chainScratchPtr
                    );

                    if (mOutputIrPosition.load(std::memory_order_relaxed) == static_cast<int>(slot + 1)) {
                        processOutputIr(chainCurrentPtr);
                    }
                }


                const auto end =
                        std::chrono::steady_clock::now();

                const uint64_t elapsedNs =
                        static_cast<uint64_t>(
                                std::chrono::duration_cast<
                                        std::chrono::nanoseconds
                                >(
                                        end -
                                        start
                                ).count()
                        );

                mBlocks.fetch_add(
                        1
                );

                mTotalProcessNs.fetch_add(
                        elapsedNs
                );

                updateMaxProcessTime(
                        elapsedNs
                );

                if (
                        elapsedNs >
                        budgetNs
                        ) {
                    mOverBudget.fetch_add(
                            1
                    );

                    if (crossfadeThisBlock) {
                        mCrossfadeOverBudget.fetch_add(
                                1
                        );
                    }
                }

                float namOutputBlockPeak =
                        0.0f;

                float postEqBlockPeak =
                        0.0f;

                for (
                        unsigned int frame = 0;
                        frame < frames;
                        ++frame
                        ) {
                    const float rawNamOutput =
                            static_cast<float>(
                                    chainCurrentPtr[frame]
                            );


                    namOutputBlockPeak =
                            std::max(
                                    namOutputBlockPeak,
                                    std::abs(
                                            rawNamOutput
                                    )
                            );

                    float postEq =
                            lowEq.process(
                                    rawNamOutput
                            );

                    postEq =
                            midEq.process(
                                    postEq
                            );

                    postEq =
                            highEq.process(
                                    postEq
                            );


                    postEqBlockPeak =
                            std::max(
                                    postEqBlockPeak,
                                    std::abs(
                                            postEq
                                    )
                            );


                    postEqBuffer[frame] = postEq;
                }

                if (mOutputIrPosition.load(std::memory_order_relaxed) == static_cast<int>(MAX_NAM_BLOCKS)) {
                    processOutputIr(reinterpret_cast<NAM_SAMPLE*>(postEqBuffer.data()));
                }

                for (unsigned int frame = 0; frame < frames; ++frame) {
                    const float output = postEqBuffer[frame] * outputGain;
                    if (outputLeftChannel < mPlaybackChannels) {
                        writeSample(playbackBuffer.data(), frame, outputLeftChannel,
                                    mPlaybackChannels, mPlaybackFormat, output);
                    }
                    if (outputRightChannel < mPlaybackChannels) {
                        writeSample(playbackBuffer.data(), frame, outputRightChannel,
                                    mPlaybackChannels, mPlaybackFormat, output);
                    }
                }

                if (crossfadeThisBlock) {
                    mCrossfadePositionSamples =
                            std::min(
                                    mCrossfadePositionSamples +
                                    frames,
                                    PRESET_CROSSFADE_SAMPLES
                            );


                    if (
                            mCrossfadePositionSamples >=
                            PRESET_CROSSFADE_SAMPLES
                            ) {
                        /*
                         * Do not destroy the old NAM here.
                         * The next background loader reclaims it.
                         */
                        mCrossfadeActive.store(
                                false,
                                std::memory_order_release
                        );
                    }
                }


                updatePeak(
                        mNamOutputPeak,
                        namOutputBlockPeak
                );

                updatePeak(
                        mPostEqPeak,
                        postEqBlockPeak
                );

                mGateGainMonitor.store(
                        gateGain,
                        std::memory_order_relaxed
                );

                const auto writeStart =
                        std::chrono::steady_clock::now();


                const uint64_t nonIoWorkNs =
                        static_cast<uint64_t>(
                                std::chrono::duration_cast<
                                        std::chrono::nanoseconds
                                >(
                                        writeStart -
                                        loopWorkStart
                                ).count()
                        );


                const int writeResult =
                        pcm_writei(
                                mPlayback,
                                playbackBuffer.data(),
                                frames
                        );


                const auto writeEnd =
                        std::chrono::steady_clock::now();


                const uint64_t writeWaitNs =
                        static_cast<uint64_t>(
                                std::chrono::duration_cast<
                                        std::chrono::nanoseconds
                                >(
                                        writeEnd -
                                        writeStart
                                ).count()
                        );


                if (
                        writeResult < 0
                        ) {
                    mPlaybackErrors.fetch_add(
                            1
                    );
                }


                const auto loopWorkEnd =
                        writeEnd;

                const uint64_t loopWorkNs =
                        static_cast<uint64_t>(
                                std::chrono::duration_cast<
                                        std::chrono::nanoseconds
                                >(
                                        loopWorkEnd -
                                        loopWorkStart
                                ).count()
                        );


                if (
                        successfulLoopBlocks >
                        JITTER_WARMUP_BLOCKS
                        ) {
                    updateMaxAtomic(
                            mMaxLoopWorkNs,
                            loopWorkNs
                    );


                    updateMaxAtomic(
                            mMaxWriteWaitNs,
                            writeWaitNs
                    );


                    updateMaxAtomic(
                            mMaxNonIoWorkNs,
                            nonIoWorkNs
                    );


                    if (
                            writeWaitNs >
                            veryLateGapThresholdNs
                            ) {
                        mLateWriteWaits.fetch_add(
                                1
                        );
                    }


                    if (
                            nonIoWorkNs >
                            budgetNs
                            ) {
                        mNonIoOverBudget.fetch_add(
                                1
                        );
                    }
                }
            }
        }


        std::array<
                std::unique_ptr<nam::DSP>,
                MAX_NAM_BLOCKS
        > mNamModels;

        std::array<
                std::atomic<bool>,
                MAX_NAM_BLOCKS
        > mNamBypass{};

        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamGainDb{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamInGainDb{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamMix{};
        std::array<std::atomic<bool>, MAX_NAM_BLOCKS> mNamNormalize{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamNormalizeGain{};

        std::unique_ptr<dsp::ImpulseResponse> mOutputIr;
        std::atomic<bool> mOutputIrBypass{false};
        std::atomic<int> mOutputIrPosition{static_cast<int>(MAX_NAM_BLOCKS)};
        std::atomic<float> mOutputIrInGainDb{0.0f};
        std::atomic<float> mOutputIrOutGainDb{0.0f};
        std::atomic<float> mOutputIrMix{1.0f};
        std::array<std::atomic<float>, 6> mOutputIrEqDb{};
        std::atomic<bool> mOutputIrEqPre{false};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqLowDb{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqMidDb{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqHighDb{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqBand3Db{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqBand4Db{};
        std::array<std::atomic<float>, MAX_NAM_BLOCKS> mNamEqBand5Db{};
        std::array<std::atomic<bool>, MAX_NAM_BLOCKS> mNamEqPre{};

        std::mutex mModelSwapMutex;

        std::unique_ptr<nam::DSP> mPendingModel;

        std::unique_ptr<nam::DSP> mCrossfadeOldModel;

        std::atomic<bool> mPresetSwitchPending{
                false
        };

        std::atomic<bool> mCrossfadeActive{
                false
        };

        unsigned int mCrossfadePositionSamples =
                0;

        std::atomic<uint64_t> mGaplessPresetSwitches{
                0
        };

        std::atomic<uint64_t> mCrossfadeOverBudget{
                0
        };


        float mPendingInputGainDb =
                0.0f;

        float mPendingInputGainLinear =
                1.0f;

        float mPendingOutputGainDb =
                0.0f;

        float mPendingOutputGainLinear =
                1.0f;

        unsigned int mPendingInputChannel =
                0;

        unsigned int mPendingOutputPair =
                0;

        bool mPendingGateEnabled =
                false;

        float mPendingGateThresholdDb =
                -65.0f;

        float mPendingGateThresholdLinear =
                0.0005623413f;

        float mPendingEqLowDb =
                0.0f;

        float mPendingEqMidDb =
                0.0f;

        float mPendingEqHighDb =
                0.0f;

        uint64_t mPendingFileFingerprint =
                0;

        uint64_t mPendingProbeFingerprint =
                0;

        bool mPendingIsSlimmable =
                false;

        float mPendingSlimSize =
                0.0f;


        pcm* mCapture =
                nullptr;

        pcm* mPlayback =
                nullptr;

        std::thread mThread;

        std::atomic<bool> mRunning{
                false
        };

        std::atomic<bool> mIsSlimmable{
                false
        };

        std::atomic<float> mSlimSize{
                0.0f
        };

        std::atomic<uint64_t> mModelGeneration{
                0
        };

        std::atomic<uint64_t> mModelFileFingerprint{
                0
        };

        std::atomic<uint64_t> mModelProbeFingerprint{
                0
        };

        std::atomic<float> mCapturePeak{
                0.0f
        };

        std::atomic<float> mNamInputPeak{
                0.0f
        };

        std::atomic<float> mNamOutputPeak{
                0.0f
        };

        std::atomic<uint64_t> mMaxCaptureGapNs{
                0
        };

        std::atomic<uint64_t> mLateCaptureGaps{
                0
        };

        std::atomic<uint64_t> mVeryLateCaptureGaps{
                0
        };

        std::atomic<uint64_t> mMaxLoopWorkNs{
                0
        };

        std::atomic<uint64_t> mMaxReadWaitNs{
                0
        };

        std::atomic<uint64_t> mMaxWriteWaitNs{
                0
        };

        std::atomic<uint64_t> mMaxNonIoWorkNs{
                0
        };

        std::atomic<uint64_t> mLateReadWaits{
                0
        };

        std::atomic<uint64_t> mLateWriteWaits{
                0
        };

        std::atomic<uint64_t> mNonIoOverBudget{
                0
        };


        std::atomic<int> mAudioThreadNiceSetResult{
                -9999
        };

        std::atomic<int> mAudioThreadNiceErrno{
                0
        };

        std::atomic<int> mAudioThreadNiceActual{
                0
        };

        std::atomic<int> mAudioThreadFifoSetResult{
                -9999
        };

        std::atomic<int> mAudioThreadFifoError{
                0
        };

        std::atomic<int> mAudioThreadPolicy{
                SCHED_OTHER
        };

        std::atomic<int> mAudioThreadPriority{
                0
        };

        std::atomic<int> mAudioThreadAffinitySetResult{
                -9999
        };

        std::atomic<int> mAudioThreadAffinityError{
                0
        };

        std::atomic<uint64_t> mAudioThreadAllowedCpuMask{
                0
        };

        std::atomic<uint64_t> mAudioThreadSelectedCpuMask{
                0
        };

        std::atomic<uint64_t> mAudioThreadAffinityMaxFreqKhz{
                0
        };


        unsigned int mCard =
                0;

        unsigned int mDevice =
                0;

        unsigned int mBlockSize =
                DEFAULT_BLOCK_SIZE;

        unsigned int mCapturePeriodCount =
                DEFAULT_PERIOD_COUNT;

        unsigned int mPlaybackPeriodCount =
                DEFAULT_PERIOD_COUNT;

        unsigned int mCaptureChannels =
                0;

        unsigned int mPlaybackChannels =
                0;

        pcm_format mCaptureFormat =
                PCM_FORMAT_INVALID;

        pcm_format mPlaybackFormat =
                PCM_FORMAT_INVALID;

        std::string mDeviceName;


        std::atomic<unsigned int> mSelectedInputChannel{
                0
        };

        std::atomic<unsigned int> mSelectedOutputPair{
                0
        };


        std::atomic<float> mInputGainDb{
                0.0f
        };

        std::atomic<float> mOutputGainDb{
                0.0f
        };

        std::atomic<float> mInputGainLinear{
                1.0f
        };

        std::atomic<float> mOutputGainLinear{
                1.0f
        };


        std::atomic<bool> mGateEnabled{
                false
        };

        std::atomic<float> mGateThresholdDb{
                -65.0f
        };

        std::atomic<float> mGateThresholdLinear{
                0.0005623413f
        };

        std::atomic<float> mEqLowDb{
                0.0f
        };

        std::atomic<float> mEqMidDb{
                0.0f
        };

        std::atomic<float> mEqHighDb{
                0.0f
        };

        std::atomic<float> mGateGainMonitor{
                1.0f
        };

        std::atomic<float> mPostEqPeak{
                0.0f
        };


        std::atomic<uint64_t> mBlocks{
                0
        };

        std::atomic<uint64_t> mTotalProcessNs{
                0
        };

        std::atomic<uint64_t> mMaxProcessNs{
                0
        };

        std::atomic<uint64_t> mOverBudget{
                0
        };

        std::atomic<uint64_t> mCaptureErrors{
                0
        };

        std::atomic<uint64_t> mPlaybackErrors{
                0
        };
    };


    AudioEngine gEngine;


    static jstring makeJString(
            JNIEnv* env,
            const std::string& value
    ) {
        return env->NewStringUTF(
                value.c_str()
        );
    }

} // namespace


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeLoadModel(
        JNIEnv* env,
        jobject,
        jstring path
) {
    const char* chars =
            env->GetStringUTFChars(
                    path,
                    nullptr
            );

    const std::string result =
            gEngine.loadModel(
                    chars
            );

    env->ReleaseStringUTFChars(
            path,
            chars
    );

    return makeJString(
            env,
            result
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeLoadImpulseResponse(
        JNIEnv* env, jobject, jstring path
) {
    const char* chars = env->GetStringUTFChars(path, nullptr);
    const std::string result = gEngine.loadImpulseResponse(chars);
    env->ReleaseStringUTFChars(path, chars);
    return makeJString(env, result);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseBypass(
        JNIEnv*, jobject, jboolean bypass
) {
    gEngine.setImpulseResponseBypass(bypass == JNI_TRUE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeClearImpulseResponse(
        JNIEnv*, jobject
) {
    gEngine.clearImpulseResponse();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponsePosition(
        JNIEnv*, jobject, jint namBlocksBefore
) {
    gEngine.setImpulseResponsePosition(static_cast<int>(namBlocksBefore));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseInGainDb(
        JNIEnv*, jobject, jfloat db
) {
    gEngine.setImpulseResponseInGainDb(db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseOutGainDb(
        JNIEnv*, jobject, jfloat db
) {
    gEngine.setImpulseResponseOutGainDb(db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseMix(
        JNIEnv*, jobject, jfloat mix
) {
    gEngine.setImpulseResponseMix(mix);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseEqDb(
        JNIEnv*, jobject, jint band, jfloat db
) {
    gEngine.setImpulseResponseEqDb(static_cast<int>(band), db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetImpulseResponseEqPre(
        JNIEnv*, jobject, jboolean pre
) {
    gEngine.setImpulseResponseEqPre(pre == JNI_TRUE);
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeAddChainModel(
        JNIEnv* env,
        jobject,
        jstring path
) {
    const char* chars =
            env->GetStringUTFChars(
                    path,
                    nullptr
            );


    const std::string result =
            gEngine.addChainModel(
                    chars
            );


    env->ReleaseStringUTFChars(
            path,
            chars
    );


    return makeJString(
            env,
            result
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeClearExtraNamBlocks(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.clearExtraChainModels()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeClearNamChain(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.clearChainModels()
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamBypass(
        JNIEnv*,
        jobject,
        jint chainIndex,
        jboolean bypass
) {
    gEngine.setChainNamBypass(
            static_cast<int>(
                    chainIndex
            ),
            bypass ==
            JNI_TRUE
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamQuality(
        JNIEnv* env, jobject, jint chainIndex, jboolean full
) {
    return makeJString(env, gEngine.setChainNamQuality(
            static_cast<int>(chainIndex), full == JNI_TRUE));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamGainDb(
        JNIEnv*, jobject, jint chainIndex, jfloat db
) {
    gEngine.setChainNamGainDb(static_cast<int>(chainIndex), db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamInGainDb(
        JNIEnv*, jobject, jint chainIndex, jfloat db
) {
    gEngine.setChainNamInGainDb(static_cast<int>(chainIndex), db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamMix(
        JNIEnv*, jobject, jint chainIndex, jfloat mix
) {
    gEngine.setChainNamMix(static_cast<int>(chainIndex), mix);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamNormalize(
        JNIEnv*, jobject, jint chainIndex, jboolean enabled
) {
    gEngine.setChainNamNormalize(static_cast<int>(chainIndex), enabled == JNI_TRUE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamEqDb(
        JNIEnv*, jobject, jint chainIndex, jint band, jfloat db
) {
    gEngine.setChainNamEqDb(static_cast<int>(chainIndex), static_cast<int>(band), db);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetChainNamEqPre(
        JNIEnv*, jobject, jint chainIndex, jboolean pre
) {
    gEngine.setChainNamEqPre(static_cast<int>(chainIndex), pre == JNI_TRUE);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeGetNamBlockCount(
        JNIEnv*,
        jobject
) {
    return static_cast<jint>(
            gEngine.namBlockCount()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSwitchPresetGapless(
        JNIEnv* env,
        jobject,
        jstring path,
        jfloat inputGainDb,
        jfloat outputGainDb,
        jint inputChannel,
        jint outputPair,
        jboolean gateEnabled,
        jfloat gateThresholdDb,
        jfloat eqLowDb,
        jfloat eqMidDb,
        jfloat eqHighDb
) {
    const char* chars =
            env->GetStringUTFChars(
                    path,
                    nullptr
            );


    const std::string result =
            gEngine.switchPresetGapless(
                    chars,
                    inputGainDb,
                    outputGainDb,
                    static_cast<int>(
                            inputChannel
                    ),
                    static_cast<int>(
                            outputPair
                    ),
                    gateEnabled ==
                    JNI_TRUE,
                    gateThresholdDb,
                    eqLowDb,
                    eqMidDb,
                    eqHighDb
            );


    env->ReleaseStringUTFChars(
            path,
            chars
    );


    return makeJString(
            env,
            result
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeStart(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.start()
    );
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeIsRunning(
        JNIEnv*,
        jobject
) {
    return gEngine.isRunning()
           ? JNI_TRUE
           : JNI_FALSE;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeStop(
        JNIEnv*,
        jobject
) {
    gEngine.stop();
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetBypass(
        JNIEnv*,
        jobject,
        jboolean bypass
) {
    gEngine.setBypass(
            bypass == JNI_TRUE
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetInputGainDb(
        JNIEnv*,
        jobject,
        jfloat gainDb
) {
    gEngine.setInputGainDb(
            gainDb
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetOutputGainDb(
        JNIEnv*,
        jobject,
        jfloat gainDb
) {
    gEngine.setOutputGainDb(
            gainDb
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetGateEnabled(
        JNIEnv*,
        jobject,
        jboolean enabled
) {
    gEngine.setGateEnabled(
            enabled ==
            JNI_TRUE
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetGateThresholdDb(
        JNIEnv*,
        jobject,
        jfloat db
) {
    gEngine.setGateThresholdDb(
            db
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetEqLowDb(
        JNIEnv*,
        jobject,
        jfloat db
) {
    gEngine.setEqLowDb(
            db
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetEqMidDb(
        JNIEnv*,
        jobject,
        jfloat db
) {
    gEngine.setEqMidDb(
            db
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetEqHighDb(
        JNIEnv*,
        jobject,
        jfloat db
) {
    gEngine.setEqHighDb(
            db
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeGetDspChainInfo(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.dspChainInfo()
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetInputChannel(
        JNIEnv*,
        jobject,
        jint channel
) {
    gEngine.setInputChannel(
            static_cast<int>(
                    channel
            )
    );
}


extern "C"
JNIEXPORT void JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeSetOutputPair(
        JNIEnv*,
        jobject,
        jint pairIndex
) {
    gEngine.setOutputPair(
            static_cast<int>(
                    pairIndex
            )
    );
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeCycleInputChannel(
        JNIEnv*,
        jobject
) {
    return static_cast<jint>(
            gEngine.cycleInputChannel()
    );
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeCycleOutputPair(
        JNIEnv*,
        jobject
) {
    return static_cast<jint>(
            gEngine.cycleOutputPair()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeGetRoutingInfo(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.routingInfo()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeScanUsbAudio(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.scanUsbAudio()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeGetAudioDeviceInfo(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.deviceInfo()
    );
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_MainActivity_nativeGetStats(
        JNIEnv* env,
        jobject
) {
    return makeJString(
            env,
            gEngine.stats()
    );
}
