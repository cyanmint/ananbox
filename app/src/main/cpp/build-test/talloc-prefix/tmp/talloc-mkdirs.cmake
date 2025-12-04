# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION ${CMAKE_VERSION}) # this file comes with cmake

# If CMAKE_DISABLE_SOURCE_CHANGES is set to true and the source directory is an
# existing directory in our source tree, calling file(MAKE_DIRECTORY) on it
# would cause a fatal error, even though it would be a no-op.
if(NOT EXISTS "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc")
  file(MAKE_DIRECTORY "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc")
endif()
file(MAKE_DIRECTORY
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc-build"
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix"
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/tmp"
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc-stamp"
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src"
  "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/runner/work/ananbox/ananbox/app/src/main/cpp/build-test/talloc-prefix/src/talloc-stamp${cfgdir}") # cfgdir has leading slash
endif()
