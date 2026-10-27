#!/usr/bin/env bash
# Usage: ./run-e2e.sh [flow.yaml ...]   (default: every flow in .maestro/)
# Resets ONLY the debug build (io.aragonite.notes.debug, DB in Documents/notabledb-debug).
# The real app (io.aragonite.notes) is never touched.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes.debug
./gradlew assembleDebug --console=plain -q
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell input keyevent KEYCODE_WAKEUP
adb shell am force-stop $PKG
adb shell pm clear $PKG >/dev/null
adb shell rm -rf /sdcard/Documents/notabledb-debug
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
mkdir -p .e2e-shots
maestro test "${@:-.maestro}" --test-output-dir .e2e-shots/maestro-out
