include(ExternalProject)
set(TALLOC_SRC ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc)
set(TALLOC_BIN ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc-build)
set(TALLOC_STATIC_LIB ${TALLOC_BIN}/lib/libtalloc.a)
set(TALLOC_INCLUDE_DIRS ${TALLOC_BIN}/include)
# Get API level from ANDROID_PLATFORM (format: android-XX) or fall back to 24
if(ANDROID_PLATFORM)
    string(REGEX REPLACE "android-" "" ANDROID_API_LEVEL "${ANDROID_PLATFORM}")
else()
    set(ANDROID_API_LEVEL 24)
endif()
# specific correct clang target to use
if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(TALLOC_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_API_LEVEL}-clang)
else()
    set(TALLOC_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/${CMAKE_ANDROID_ARCH_ABI}-linux-android${ANDROID_API_LEVEL}-clang)
endif()
ExternalProject_Add(
        talloc
        URL https://download.samba.org/pub/talloc/talloc-2.4.1.tar.gz
        BUILD_IN_SOURCE 1
        CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env "CC=${TALLOC_C_COMPILER}" "LD=${TALLOC_C_COMPILER}" "HOSTCC=gcc" ./configure --prefix=${TALLOC_BIN} --disable-rpath --disable-python --without-gettext --cross-compile --cross-answers=${CMAKE_CURRENT_SOURCE_DIR}/talloc/cross-answers.txt
        BUILD_COMMAND ${CMAKE_COMMAND} -E env "CC=${TALLOC_C_COMPILER}" "LD=${TALLOC_C_COMPILER}" make
        INSTALL_COMMAND mkdir -p ${TALLOC_INCLUDE_DIRS} ${TALLOC_BIN}/lib && sh -c "${CMAKE_AR} rcs ${TALLOC_STATIC_LIB} bin/default/talloc.c*.o" && cp -f talloc.h ${TALLOC_INCLUDE_DIRS}
)