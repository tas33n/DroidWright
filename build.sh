#!/bin/bash

# UI Automator App - Build Script
# This script automates the build process for the Android app

set -e

PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
BUILD_TYPE="${1:-debug}"
OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/apk"

echo "=========================================="
echo "UI Automator App - Build Script"
echo "=========================================="
echo ""

# Check Java
echo "[1/5] Checking Java installation..."
if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Please install JDK 11+"
    echo "Download: https://adoptopenjdk.net"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep "version" | head -1)
echo "✓ Found: $JAVA_VERSION"
echo ""

# Check Android SDK (optional warning)
echo "[2/5] Checking Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠ ANDROID_HOME not set. Gradle may download required components."
else
    echo "✓ ANDROID_HOME: $ANDROID_HOME"
fi
echo ""

# Make gradlew executable
echo "[3/5] Preparing build tools..."
chmod +x "$PROJECT_DIR/gradlew"
echo "✓ Gradle wrapper ready"
echo ""

# Clean build
echo "[4/5] Cleaning previous builds..."
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" clean --no-daemon --quiet
echo "✓ Clean complete"
echo ""

# Build APK
echo "[5/5] Building $BUILD_TYPE APK..."
case "$BUILD_TYPE" in
    debug)
        "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" assembleDebug --no-daemon
        APK_FILE="$OUTPUT_DIR/debug/app-debug.apk"
        ;;
    release)
        if [ ! -f "$PROJECT_DIR/release.keystore" ]; then
            echo "ERROR: release.keystore not found"
            echo "Generate keystore with: keytool -genkey -v -keystore release.keystore ..."
            exit 1
        fi
        "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" assembleRelease --no-daemon
        APK_FILE="$OUTPUT_DIR/release/app-release.apk"
        ;;
    *)
        echo "ERROR: Invalid build type. Use 'debug' or 'release'"
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "Build Complete!"
echo "=========================================="
echo ""

if [ -f "$APK_FILE" ]; then
    APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
    echo "✓ APK generated successfully"
    echo "  Location: $APK_FILE"
    echo "  Size: $APK_SIZE"
    echo ""
    echo "Installation:"
    echo "  adb install -r $APK_FILE"
else
    echo "ERROR: APK file not found"
    exit 1
fi
