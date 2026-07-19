cmake_minimum_required(VERSION 3.16)
if(APPLE)
    project(gamepadjni LANGUAGES C OBJC)
else()
    project(gamepadjni LANGUAGES C)
endif()

# ─────────────────────────────────────────────
# Find JNI (Java Native Interface)
# Derive JNI include paths directly from JAVA_HOME.
# This avoids find_package(JNI) which fails in MSYS2/MinGW
# when JAVA_HOME contains spaces (e.g., "C:/Program Files/...").
# ─────────────────────────────────────────────
if(NOT DEFINED JAVA_HOME AND DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME "$ENV{JAVA_HOME}")
endif()
if(NOT JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME is not set. Please set JAVA_HOME to your JDK installation path.")
endif()
message(STATUS "JAVA_HOME: ${JAVA_HOME}")

set(JAVA_INCLUDE_PATH "${JAVA_HOME}/include")
if(APPLE)
    set(JAVA_INCLUDE_PATH2 "${JAVA_HOME}/include/darwin")
elseif(WIN32)
    set(JAVA_INCLUDE_PATH2 "${JAVA_HOME}/include/win32")
else()
    set(JAVA_INCLUDE_PATH2 "${JAVA_HOME}/include/linux")
endif()

message(STATUS "JNI include: ${JAVA_INCLUDE_PATH}")
message(STATUS "JNI include2: ${JAVA_INCLUDE_PATH2}")

# ─────────────────────────────────────────────
# SDL3: use prebuilt from prebuilt/sdl
# ─────────────────────────────────────────────
set(SDL3_DIR "${CMAKE_SOURCE_DIR}/prebuilt/sdl")

# SDL3 include directory
set(SDL3_INCLUDE_DIR "${SDL3_DIR}/include")

# Platform-specific SDL3 library
if(APPLE)
    # Use CMAKE_OSX_ARCHITECTURES if set, otherwise fall back to system processor
    if(DEFINED CMAKE_OSX_ARCHITECTURES)
        if("${CMAKE_OSX_ARCHITECTURES}" MATCHES "arm64|aarch64")
            set(_SDL3_ARCH_DIR "darwin-aarch64")
        else()
            set(_SDL3_ARCH_DIR "darwin-x86_64")
        endif()
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(_SDL3_ARCH_DIR "darwin-aarch64")
    else()
        set(_SDL3_ARCH_DIR "darwin-x86_64")
    endif()
    set(SDL3_LIB_DIR "${SDL3_DIR}/${_SDL3_ARCH_DIR}")
elseif(WIN32)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(SDL3_LIB_DIR "${SDL3_DIR}/windows-aarch64")
    else()
        set(SDL3_LIB_DIR "${SDL3_DIR}/windows-x86_64")
    endif()
else()
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(SDL3_LIB_DIR "${SDL3_DIR}/linux-aarch64")
    else()
        set(SDL3_LIB_DIR "${SDL3_DIR}/linux-x86_64")
    endif()
endif()

# Find SDL3 library file
# On Windows/MinGW, the import library (.dll.a) is in lib/ subdirectory
if(WIN32)
    find_library(SDL3_LIBRARY
        NAMES SDL3 SDL3.0
        PATHS "${SDL3_LIB_DIR}/lib" "${SDL3_LIB_DIR}"
        NO_DEFAULT_PATH
    )
else()
    find_library(SDL3_LIBRARY
        NAMES SDL3 SDL3.0
        PATHS "${SDL3_LIB_DIR}"
        NO_DEFAULT_PATH
    )
endif()

if(NOT SDL3_LIBRARY)
    message(FATAL_ERROR "SDL3 library not found in ${SDL3_LIB_DIR}")
endif()

message(STATUS "SDL3 include: ${SDL3_INCLUDE_DIR}")
message(STATUS "SDL3 library: ${SDL3_LIBRARY}")
message(STATUS "JNI include: ${JAVA_INCLUDE_PATH}")
message(STATUS "JNI include2: ${JAVA_INCLUDE_PATH2}")

# ─────────────────────────────────────────────
# Build the JNI shared library
# ─────────────────────────────────────────────
set(GAMEPAD_JNI_SOURCES src/main/c/gamepad_jni.c)
if(APPLE)
    list(APPEND GAMEPAD_JNI_SOURCES src/main/c/gamepad_jni_macos.m)
endif()

add_library(gamepadjni SHARED
    ${GAMEPAD_JNI_SOURCES}
)

target_include_directories(gamepadjni PRIVATE
    ${SDL3_INCLUDE_DIR}
    ${JAVA_INCLUDE_PATH}
    ${JAVA_INCLUDE_PATH2}
)

# Link against SDL3
target_link_libraries(gamepadjni PRIVATE
    ${SDL3_LIBRARY}
)

# macOS: link frameworks for GCController queries
if(APPLE)
    target_link_libraries(gamepadjni PRIVATE
        "-framework Foundation"
        "-framework GameController"
    )
endif()

# macOS: set install name and deployment target
if(APPLE)
    set_target_properties(gamepadjni PROPERTIES
        MACOSX_RPATH TRUE
        INSTALL_RPATH "@loader_path"
        CMAKE_OSX_DEPLOYMENT_TARGET "10.13"
    )
endif()

# Strip symbols for release
set_target_properties(gamepadjni PROPERTIES
    C_STANDARD 11
    POSITION_INDEPENDENT_CODE ON
)

# Windows: remove 'lib' prefix from DLL (Windows convention: SDL3.dll, not libSDL3.dll)
if(WIN32)
    set_target_properties(gamepadjni PROPERTIES
        PREFIX ""
    )
endif()

# ─────────────────────────────────────────────
# Install / copy to build output
# ─────────────────────────────────────────────
# Copy the built library to prebuilt/jni/<platform>/
if(APPLE)
    if(DEFINED CMAKE_OSX_ARCHITECTURES)
        if("${CMAKE_OSX_ARCHITECTURES}" MATCHES "arm64|aarch64")
            set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/darwin-aarch64")
        else()
            set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/darwin-x86_64")
        endif()
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/darwin-aarch64")
    else()
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/darwin-x86_64")
    endif()
elseif(WIN32)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/windows-aarch64")
    else()
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/windows-x86_64")
    endif()
else()
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/linux-aarch64")
    else()
        set(NATIVE_OUTPUT_DIR "${CMAKE_SOURCE_DIR}/prebuilt/jni/linux-x86_64")
    endif()
endif()

add_custom_command(TARGET gamepadjni POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${NATIVE_OUTPUT_DIR}"
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:gamepadjni> "${NATIVE_OUTPUT_DIR}/"
    COMMENT "Copying gamepadjni to ${NATIVE_OUTPUT_DIR}"
)
