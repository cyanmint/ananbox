# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.22)
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
enable_language( C CXX ASM )

# Suppress excessive warnings in third-party code
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -w")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -w")

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

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        anbox

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        libanbox.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        anbox
        anbox-core
        android
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

# Build standalone server for Termux (must be after Proot is included)
if(BUILD_ANANBOX_SERVER AND ANDROID)
    set(SERVER_SOURCES
        ${CMAKE_SOURCE_DIR}/anbox/src/server/streaming_server.cpp
        ${CMAKE_SOURCE_DIR}/anbox/src/server/main.cpp
    )

    add_executable(ananbox-server ${SERVER_SOURCES})
    
    target_include_directories(ananbox-server PRIVATE
        ${CMAKE_SOURCE_DIR}/anbox/src/server
        ${CMAKE_SOURCE_DIR}/anbox/src
        ${CMAKE_SOURCE_DIR}/anbox/src/anbox
        ${Boost_INCLUDE_DIRS}
    )
    
    target_link_libraries(ananbox-server
        anbox-core
        ${Boost_LIBRARIES}
        log
        android
        EGL
        GLESv2
    )
    
    # Note: proot is built separately by the main Android app build via Gradle
    # The server binary can be used with the libproot.so from the APK

    # Install the server binary
    install(TARGETS ananbox-server DESTINATION bin)
endif()