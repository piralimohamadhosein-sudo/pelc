cmake_minimum_required(VERSION 3.22)
project(pelc_shaping CXX)

# ---------------------------------------------------------------------------
# HarfBuzz. Sources are vendored into third_party/ (git-ignored) and are
# downloaded automatically on the first build. Pre-download them with
# scripts/fetch-native-deps.sh to build fully offline.
# ---------------------------------------------------------------------------
set(HB_VERSION "8.5.0")
set(HB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/harfbuzz-${HB_VERSION}")
set(HB_TARBALL "${CMAKE_CURRENT_SOURCE_DIR}/third_party/harfbuzz-${HB_VERSION}.tar.xz")

if(NOT EXISTS "${HB_DIR}/src/harfbuzz.cc")
    message(STATUS "HarfBuzz ${HB_VERSION} not found; downloading ...")
    file(MAKE_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/third_party")
    file(DOWNLOAD
        "https://github.com/harfbuzz/harfbuzz/releases/download/${HB_VERSION}/harfbuzz-${HB_VERSION}.tar.xz"
        "${HB_TARBALL}"
        STATUS HB_STATUS)
    list(GET HB_STATUS 0 HB_CODE)
    if(NOT HB_CODE EQUAL 0)
        message(FATAL_ERROR "Failed to download HarfBuzz ${HB_VERSION}: ${HB_STATUS}")
    endif()
    file(ARCHIVE_EXTRACT INPUT "${HB_TARBALL}" DESTINATION "${CMAKE_CURRENT_SOURCE_DIR}/third_party")
endif()

if(NOT EXISTS "${HB_DIR}/src/harfbuzz.cc")
    message(FATAL_ERROR "HarfBuzz amalgamated source missing at ${HB_DIR}/src/harfbuzz.cc")
endif()

# Single shared library: JNI bridge + the HarfBuzz amalgamation.
add_library(pelc_shaping SHARED
    harfbuzz_jni.cpp
    "${HB_DIR}/src/harfbuzz.cc"
)

target_include_directories(pelc_shaping PRIVATE "${HB_DIR}/src")
target_compile_features(pelc_shaping PRIVATE cxx_std_17)
target_compile_options(pelc_shaping PRIVATE
    -fexceptions
    -Wno-unused-parameter
    -Wno-unused-variable
)
