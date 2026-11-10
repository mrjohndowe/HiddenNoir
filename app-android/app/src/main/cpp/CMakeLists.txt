cmake_minimum_required(VERSION 3.31.6)
project(hiddennoir LANGUAGES C CXX)

include(FetchContent)
# Keep CPU inference optimized in debug APKs as well.
string(APPEND CMAKE_C_FLAGS_DEBUG " -O2")
string(APPEND CMAKE_CXX_FLAGS_DEBUG " -O2")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
FetchContent_Declare(llama_cpp
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/1a03cf47f67be591699d1f0f7ca28e1ed6eb8c7e
    URL_HASH SHA256=910132208d4f206252da7c34b3d345c3877b7a02f392d6162a00a906518efc58
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(hiddennoir SHARED local_inference.cpp)
target_compile_features(hiddennoir PRIVATE cxx_std_17)
target_link_libraries(hiddennoir PRIVATE llama)
