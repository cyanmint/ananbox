include(ExternalProject)
include(Talloc)
include(Unwind)
set(PROOT_VENDOR_DIR ${CMAKE_CURRENT_SOURCE_DIR}/proot)
set(PROOT_SRC ${PROOT_VENDOR_DIR}/src)
set(PROOT_BIN ${CMAKE_CURRENT_BINARY_DIR}/proot-build)
set(PROOT_C_FLAGS "-I${TALLOC_INCLUDE_DIRS} -I${PROOT_SRC} -D_GNU_SOURCE -I${UNWIND_INCLUDE_DIRS} -w")
set(PROOT_LINKER_FLAGS "-L${TALLOC_BIN}/lib -L${CMAKE_BINARY_DIR} -ltalloc -lunwind_ptrace")
# Get API level from ANDROID_PLATFORM (format: android-XX) or fall back to 24
if(ANDROID_PLATFORM)
    string(REGEX REPLACE "android-" "" ANDROID_API_LEVEL "${ANDROID_PLATFORM}")
else()
    set(ANDROID_API_LEVEL 24)
endif()
# specific correct clang target to use
if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(PROOT_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${ANDROID_API_LEVEL}-clang)
else()
    set(PROOT_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/${CMAKE_ANDROID_ARCH_ABI}-linux-android${ANDROID_API_LEVEL}-clang)
endif()
ExternalProject_Add(
        proot
        SOURCE_DIR ${PROOT_VENDOR_DIR}
        CONFIGURE_COMMAND cd ${PROOT_SRC} && make clean
        BUILD_COMMAND cd ${PROOT_SRC} && make CC=${PROOT_C_COMPILER} LD=${PROOT_C_COMPILER} STRIP=${CMAKE_STRIP} OBJCOPY=${CMAKE_OBJCOPY} OBJDUMP=${CMAKE_OBJDUMP} CFLAGS=${PROOT_C_FLAGS} LDFLAGS=${PROOT_LINKER_FLAGS} HAS_SWIG= HAS_PYTHON_CONFIG=
        BUILD_IN_SOURCE 1
        # hacked: only lib*.so can be packed into apk
        # Copy proot binary and loader binary (needed for noexec filesystem workaround)
        # The loader is a small static binary that proot needs to execute in PROOT_TMP_DIR
        # On Android, filesDir has noexec flag, so we bundle the loader in the APK's lib dir
        # and use PROOT_LOADER env variable to point to it (lib dir has exec permission)
        INSTALL_COMMAND cd ${PROOT_SRC} && cp -f ./proot ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot.so && cp -f ./loader/loader ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot-loader.so
        DEPENDS talloc unwind_ptrace
)
