cmake_minimum_required(VERSION 3.4.1)

project(thornotes_paddle_ocr)

set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../paddle/PaddleLite/cxx")
include_directories(${PaddleLite_DIR}/include)

set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../paddle/OpenCV/sdk/native/jni")
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS
    "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os -DNDEBUG -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc")

add_library(
    thornotes_paddle_ocr
    SHARED
    paddle_ocr_jni.cc
    pipeline.cc
    utils.cc
    cls_process.cc
    rec_process.cc
    det_process.cc
    db_post_process.cc
    clipper.cpp)

add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(
    paddle_light_api_shared
    PROPERTIES
    IMPORTED_LOCATION
    ${PaddleLite_DIR}/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

target_link_libraries(
    thornotes_paddle_ocr
    paddle_light_api_shared
    ${OpenCV_LIBS}
    ${jnigraphics-lib}
    ${android-lib}
    ${log-lib})
