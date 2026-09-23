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


#
# IMPORTANTE PARA ESTE TESTE:
#
# NAM_ENABLE_A2_FAST foi removido intencionalmente.
#
# Assim o NeuralAmpModelerCore usa o caminho genérico
# do WaveNet em vez do fast-path específico do A2.
#
# Mantemos NAM_SAMPLE_FLOAT e todas as demais opções
# exatamente iguais para fazer um A/B válido.
#
target_compile_definitions(
        namcore_objects

        PUBLIC

        NAM_SAMPLE_FLOAT
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
)


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

        android
        log
)
