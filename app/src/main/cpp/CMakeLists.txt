cmake_minimum_required(VERSION 3.22.1)
project(pymobile C)

# Must match tools/fetch-python.sh (the payload is downloaded before the build).
set(PY_MINOR "3.14" CACHE STRING "Python major.minor")

add_library(python SHARED IMPORTED)
set_target_properties(python PROPERTIES
    IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpython${PY_MINOR}.so")

add_library(pymobile SHARED pymobile.c)
target_include_directories(pymobile PRIVATE "${CMAKE_SOURCE_DIR}/python/include/python${PY_MINOR}")
target_link_libraries(pymobile python log)
