# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
cmake_minimum_required(VERSION 3.6)

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        src/main/cpp/CLogReader.cpp)
