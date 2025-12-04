# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22)

# Suppress CMake developer warnings
if(POLICY CMP0115)
    cmake_policy(SET CMP0115 NEW)
endif()
set(CMAKE_SUPPRESS_DEVELOPER_WARNINGS ON CACHE BOOL "" FORCE)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
enable_language( C CXX ASM )

# Suppress all warnings in third-party code (including -Wunused-variable)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -w -Wno-unused-variable")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -w -Wno-unused-variable")

# Declares and names the project.

set(HOST_CMAKE_C_COMPILER /usr/bin/clang)
set(HOST_CMAKE_CXX_COMPILER /usr/bin/clang++)

# emugl: abandon library loading
SET(USE_EGL_GETPROCADDRESS 1)

project("ananbox")

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})
include(Boost)
option(BUILD_ANANBOX_DEMO "" off)
option(BUILD_ANANBOX_SERVER "Build the standalone Ananbox server for Termux" OFF)
find_package(Boost COMPONENTS filesystem log_setup log serialization system thread program_options)

add_subdirectory(anbox)

include(Proot)

include_directories(
        ${CMAKE_SOURCE_DIR}/anbox
        ${CMAKE_SOURCE_DIR}/anbox/src
        ${CMAKE_SOURCE_DIR}/anbox/external
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/shared
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/include
        ${CMAKE_BINARY_DIR}/anbox/external/android-emugl/host/include
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/shared/OpenglCodecCommon
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/libs
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/include/libOpenglRender
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/libs/GLESv1_dec
        ${CMAKE_BINARY_DIR}/anbox/external/android-emugl/host/libs/GLESv1_dec
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/libs/GLESv2_dec
        ${CMAKE_BINARY_DIR}/anbox/external/android-emugl/host/libs/GLESv2_dec
        ${CMAKE_SOURCE_DIR}/anbox/external/android-emugl/host/libs/renderControl_dec
        ${CMAKE_BINARY_DIR}/anbox/external/android-emugl/host/libs/renderControl_dec
        ${Boost_INCLUDE_DIRS}
)

# Server sources for streaming functionality
set(SERVER_SOURCES
    ${CMAKE_SOURCE_DIR}/anbox/src/server/streaming_server.cpp
    ${CMAKE_SOURCE_DIR}/anbox/src/server/streaming_layer_composer.cpp
    ${CMAKE_SOURCE_DIR}/anbox/src/server/software_color_buffer.cpp
    ${CMAKE_SOURCE_DIR}/anbox/src/server/adb_forwarder.cpp
)

# Build libanbox.so as a Position Independent Executable (PIE) that can be:
# 1. Loaded as a shared library by the Android app (JNI)
# 2. Executed directly as a standalone server (Termux/local server mode)
# 
# This is achieved by building as executable with -pie and exporting dynamic symbols
if(ANDROID)
    find_library(log-lib log)
    
    # Build as an executable that looks like a shared library
    add_executable(anbox
        libanbox.cpp
        ${SERVER_SOURCES}
    )
    
    # Make it look like a shared library so it gets packaged in APK
    set_target_properties(anbox PROPERTIES
        OUTPUT_NAME "anbox"
        PREFIX "lib"
        SUFFIX ".so"
        # Enable PIE for security and to allow execution
        POSITION_INDEPENDENT_CODE ON
    )
    
    # Export all symbols so it can be loaded as a shared library for JNI
    # -rdynamic exports all symbols to the dynamic symbol table
    target_link_options(anbox PRIVATE
        -pie
        -rdynamic
        -Wl,--export-dynamic
    )
    
    target_include_directories(anbox PRIVATE
        ${CMAKE_SOURCE_DIR}/anbox/src/server
        ${CMAKE_SOURCE_DIR}/anbox/src
        ${CMAKE_SOURCE_DIR}/anbox/src/anbox
    )
    
    target_link_libraries(anbox
        anbox-core
        ${Boost_LIBRARIES}
        android
        ${log-lib}
        EGL
        GLESv2
    )
endif()