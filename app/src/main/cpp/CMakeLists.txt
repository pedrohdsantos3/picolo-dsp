cmake_minimum_required(VERSION 3.22.1)

project("tone3000m1")


# ============================================================
# TinyALSA
# ============================================================

set(
        BUILD_SHARED_LIBS
        OFF
        CACHE BOOL ""
        FORCE
)

set(
        TINYALSA_USES_PLUGINS
        OFF
        CACHE BOOL ""
        FORCE
)

set(
        TINYALSA_BUILD_EXAMPLES
        OFF
        CACHE BOOL ""
        FORCE
)

set(
        TINYALSA_BUILD_UTILS
        OFF
        CACHE BOOL ""
        FORCE
)

add_subdirectory(
        third_party/tinyalsa
        tinyalsa-build
)

# ChowDSP's official DSP modules, compiled as a standalone CMake library (no
# JUCE plugin/UI target). BYOD processor adapters are implemented in the app's
# realtime callback, while delay, pitch-shift, filter and FDN primitives are
# taken directly from these GPLv3 modules.
set(CHOWDSP_MODULES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/chowdsp_utils/modules")
file(GLOB_RECURSE CHOWDSP_INCLUDE_DIRS LIST_DIRECTORIES true
        "${CHOWDSP_MODULES_DIR}/common/*"
        "${CHOWDSP_MODULES_DIR}/dsp/*"
        "${CHOWDSP_MODULES_DIR}/music/*")
list(FILTER CHOWDSP_INCLUDE_DIRS INCLUDE REGEX "/(chowdsp_[^/]+|third_party)/?$")
add_library(chowdsp_dsp_modules STATIC
        "${CHOWDSP_MODULES_DIR}/common/chowdsp_core/chowdsp_core.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_dsp_utils/chowdsp_dsp_utils.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_buffers/chowdsp_buffers.cpp")
target_include_directories(chowdsp_dsp_modules PUBLIC
        "${CHOWDSP_MODULES_DIR}/common"
        "${CHOWDSP_MODULES_DIR}/dsp"
        "${CHOWDSP_MODULES_DIR}/music"
        ${CHOWDSP_INCLUDE_DIRS})
target_compile_definitions(chowdsp_dsp_modules PUBLIC
        CHOWDSP_NO_XSIMD=0
        JUCE_MODULE_AVAILABLE_juce_core=0
        JUCE_MODULE_AVAILABLE_juce_audio_basics=0
        JUCE_MODULE_AVAILABLE_juce_dsp=0
        JUCE_MODULE_AVAILABLE_chowdsp_core=1
        JUCE_MODULE_AVAILABLE_chowdsp_simd=1
        JUCE_MODULE_AVAILABLE_chowdsp_buffers=1
        JUCE_MODULE_AVAILABLE_chowdsp_math=1
        JUCE_MODULE_AVAILABLE_chowdsp_data_structures=1
        JUCE_MODULE_AVAILABLE_chowdsp_dsp_data_structures=1
        JUCE_MODULE_AVAILABLE_chowdsp_filters=1
        JUCE_MODULE_AVAILABLE_chowdsp_dsp_utils=1
        JUCE_MODULE_AVAILABLE_chowdsp_reverb=1)
target_compile_features(chowdsp_dsp_modules PUBLIC cxx_std_20)


# ============================================================
# NeuralAmpModelerCore
# ============================================================

set(
        NAM_CORE_DIR
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/NeuralAmpModelerCore"
)


#
# Pegamos TODOS os sources dentro de NAM/,
# inclusive subdiretórios mais profundos.
#
file(
        GLOB_RECURSE
        NAM_SOURCES

        CONFIGURE_DEPENDS

        "${NAM_CORE_DIR}/NAM/*.cpp"
        "${NAM_CORE_DIR}/NAM/*.c"
)

file(
        GLOB_RECURSE
        AUDIO_DSP_SOURCES
        CONFIGURE_DEPENDS
        "${NAM_CORE_DIR}/Dependencies/AudioDSPTools/dsp/*.cpp"
)


#
# OBJECT em vez de STATIC.
#
# O NAM registra parsers através de static initializers.
#
# Se usarmos uma .a normal, o linker pode descartar arquivos
# como container.cpp por não existir uma referência explícita
# a seus símbolos.
#
# Como OBJECT library, todos os translation units entram
# no nosso .so final.
#
add_library(
        namcore_objects
        OBJECT

        ${NAM_SOURCES}
        ${AUDIO_DSP_SOURCES}
        "${NAM_CORE_DIR}/NAM/linear.cpp"
)


target_include_directories(
        namcore_objects

        PUBLIC

        "${NAM_CORE_DIR}"
        "${NAM_CORE_DIR}/Dependencies/eigen"
        "${NAM_CORE_DIR}/Dependencies/nlohmann"
        "${NAM_CORE_DIR}/Dependencies/AudioDSPTools"
        "${NAM_CORE_DIR}/Dependencies/AudioDSPTools/dsp"
)


target_compile_features(
        namcore_objects

        PUBLIC

        cxx_std_20
)


# Enable the specialized A2 WaveNet implementation. The factory only selects
# it for models matching the A2 shape; other NAM architectures stay generic.
target_compile_definitions(
        namcore_objects

        PUBLIC

        NAM_SAMPLE_FLOAT
        NAM_ENABLE_A2_FAST
)


target_compile_options(
        namcore_objects

        PRIVATE

        -O3
        -ffast-math
)


# ============================================================
# Android / JNI engine
# ============================================================

add_library(
        tone3000m1
        SHARED

        native-lib.cpp
        third_party/airwindows/AirwindowsKPlate140.cpp
        third_party/airwindows/AirwindowsTapeDelay2.cpp
)

target_include_directories(tone3000m1 PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/third_party/airwindows")


target_compile_features(
        tone3000m1

        PRIVATE

        cxx_std_20
)


target_compile_options(
        tone3000m1

        PRIVATE

        -O3
)


target_link_libraries(
        tone3000m1

        PRIVATE

        tinyalsa
        namcore_objects
        chowdsp_dsp_modules

        android
        log
)
