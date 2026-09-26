cmake_minimum_required(VERSION 3.22)
project(tone3000_audio_qa LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(CPP_DIR "${PROJECT_ROOT}/app/src/main/cpp")
set(CHOWDSP_MODULES_DIR "${CPP_DIR}/third_party/chowdsp_utils/modules")

file(GLOB_RECURSE CHOWDSP_PATHS LIST_DIRECTORIES true
        "${CHOWDSP_MODULES_DIR}/common/*"
        "${CHOWDSP_MODULES_DIR}/dsp/*"
        "${CHOWDSP_MODULES_DIR}/music/*")
set(CHOWDSP_INCLUDE_DIRS)
foreach(CHOWDSP_PATH IN LISTS CHOWDSP_PATHS)
    if(IS_DIRECTORY "${CHOWDSP_PATH}" AND CHOWDSP_PATH MATCHES "/(chowdsp_[^/]+|third_party)$")
        list(APPEND CHOWDSP_INCLUDE_DIRS "${CHOWDSP_PATH}")
    endif()
endforeach()

add_executable(tone3000_audio_qa
        render_fx.cpp
        "${CPP_DIR}/third_party/airwindows/AirwindowsKPlate140.cpp"
        "${CPP_DIR}/third_party/airwindows/AirwindowsTapeDelay2.cpp"
        "${CHOWDSP_MODULES_DIR}/common/chowdsp_core/chowdsp_core.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_dsp_utils/chowdsp_dsp_utils.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_buffers/chowdsp_buffers.cpp")

file(GLOB NAM_SOURCES_TOP "${CPP_DIR}/NeuralAmpModelerCore/NAM/*.cpp" "${CPP_DIR}/NeuralAmpModelerCore/NAM/*.c")
file(GLOB NAM_SOURCES_SUB "${CPP_DIR}/NeuralAmpModelerCore/NAM/*/*.cpp")
set(NAM_SOURCES ${NAM_SOURCES_TOP} ${NAM_SOURCES_SUB})
set(AUDIO_DSP_TOOLS_DIR "${CPP_DIR}/NeuralAmpModelerCore/Dependencies/AudioDSPTools")

add_executable(tone3000_chain_qa
        render_current_chain.cpp
        ${NAM_SOURCES}
        "${AUDIO_DSP_TOOLS_DIR}/dsp/wav.cpp"
        "${AUDIO_DSP_TOOLS_DIR}/dsp/ImpulseResponse.cpp"
        "${AUDIO_DSP_TOOLS_DIR}/dsp/dsp.cpp"
        "${CPP_DIR}/third_party/airwindows/AirwindowsKPlate140.cpp"
        "${CPP_DIR}/third_party/airwindows/AirwindowsTapeDelay2.cpp"
        "${CHOWDSP_MODULES_DIR}/common/chowdsp_core/chowdsp_core.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_dsp_utils/chowdsp_dsp_utils.cpp"
        "${CHOWDSP_MODULES_DIR}/dsp/chowdsp_buffers/chowdsp_buffers.cpp")

target_include_directories(tone3000_audio_qa PRIVATE
        "${CPP_DIR}"
        "${CPP_DIR}/third_party/airwindows"
        "${CHOWDSP_MODULES_DIR}/common"
        "${CHOWDSP_MODULES_DIR}/dsp"
        "${CHOWDSP_MODULES_DIR}/music"
        ${CHOWDSP_INCLUDE_DIRS})

target_include_directories(tone3000_chain_qa PRIVATE
        "${CPP_DIR}"
        "${CPP_DIR}/NeuralAmpModelerCore"
        "${CPP_DIR}/NeuralAmpModelerCore/Dependencies/eigen"
        "${CPP_DIR}/NeuralAmpModelerCore/Dependencies/nlohmann"
        "${AUDIO_DSP_TOOLS_DIR}/dsp"
        "${CPP_DIR}/third_party/airwindows"
        "${CHOWDSP_MODULES_DIR}/common"
        "${CHOWDSP_MODULES_DIR}/dsp"
        "${CHOWDSP_MODULES_DIR}/music"
        ${CHOWDSP_INCLUDE_DIRS})

target_compile_definitions(tone3000_audio_qa PRIVATE
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

target_compile_definitions(tone3000_chain_qa PRIVATE
        NAM_ENABLE_A2_FAST
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

if(MSVC)
    target_compile_options(tone3000_audio_qa PRIVATE /O2)
else()
    target_compile_options(tone3000_audio_qa PRIVATE -O2 -Wno-multichar)
    target_compile_options(tone3000_chain_qa PRIVATE -O2 -Wno-multichar -Wno-unused-parameter)
endif()
