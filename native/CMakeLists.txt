cmake_minimum_required(VERSION 3.22)
project(dictado_native LANGUAGES C CXX OBJC)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# whisper.cpp se enlaza de forma estática dentro de libdictado_whisper.dylib
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_METAL ON CACHE BOOL "" FORCE)
set(GGML_METAL_EMBED_LIBRARY ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

if(NOT WHISPER_SRC)
  message(FATAL_ERROR "Define -DWHISPER_SRC=<ruta a whisper.cpp>")
endif()
add_subdirectory(${WHISPER_SRC} whisper EXCLUDE_FROM_ALL)

add_library(dictado_whisper SHARED whisper_bridge.c)
target_link_libraries(dictado_whisper PRIVATE whisper)

add_library(dictado_macos SHARED macos_bridge.m)
target_link_libraries(dictado_macos PRIVATE "-framework AppKit" "-framework AVFoundation" "-framework Foundation")

foreach(t dictado_whisper dictado_macos)
  set_target_properties(${t} PROPERTIES
    INSTALL_NAME_DIR "@loader_path"
    BUILD_WITH_INSTALL_RPATH ON
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
endforeach()

# Objective-C con conteo automático de referencias (ARC): sin esto, los objetos guardados en variables
# estáticas (p. ej. la última app activa) se liberan y provocan un cierre inesperado.
set_source_files_properties(macos_bridge.m PROPERTIES COMPILE_FLAGS "-fobjc-arc")
