cmake_minimum_required(VERSION 3.22.1)
project(closecode_pty C)

add_library(closecode_pty SHARED pty_bridge.c)
target_compile_features(closecode_pty PRIVATE c_std_11)
target_compile_options(closecode_pty PRIVATE -Wall -Wextra -Werror)
