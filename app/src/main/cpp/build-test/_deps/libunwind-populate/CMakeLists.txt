cmake_minimum_required(VERSION 3.31.6)
project(libunwind-populate NONE)
include(ExternalProject)
ExternalProject_Add(libunwind
  URL               "https://github.com/libunwind/libunwind/releases/download/v1.3.1/libunwind-1.3.1.tar.gz"
  URL_HASH          "SHA256=43997a3939b6ccdf2f669b50fdb8a4d3205374728c2923ddc2354c65260214f8"
  SOURCE_DIR        "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/_deps/libunwind-src"
  CONFIGURE_COMMAND ./configure
                    --disable-dependency-tracking
                    --disable-documentation
                    --disable-minidebuginfo
                    --disable-shared
                    --disable-coredump
                    --enable-ptrace
  BUILD_COMMAND     ""
  INSTALL_COMMAND   ""
  TEST_COMMAND      ""
  BUILD_IN_SOURCE TRUE
)
