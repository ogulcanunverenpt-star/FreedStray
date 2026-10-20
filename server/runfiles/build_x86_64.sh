#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK_CLANG="/home/ogulcan/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android24-clang"

if [ ! -f "$NDK_CLANG" ]; then
    NDK_CLANG=$(which x86_64-linux-android24-clang 2>/dev/null || echo "")
fi

if [ -z "$NDK_CLANG" ]; then
    echo "ERROR: Android NDK x86_64 Clang cross-compiler not found!"
    exit 1
fi

echo "=================================================="
echo " Building Native x86_64 Android Emulator Binary..."
echo " Compiler: $NDK_CLANG"
echo " Input C File: $SCRIPT_DIR/sample_tool.c"
echo "=================================================="

"$NDK_CLANG" -O2 -fPIE -pie "$SCRIPT_DIR/sample_tool.c" -lz -o "$SCRIPT_DIR/sample_tool_x86_64"

echo "SUCCESS! Native x86_64 Binary created at:"
echo " -> $SCRIPT_DIR/sample_tool_x86_64"
file "$SCRIPT_DIR/sample_tool_x86_64"
