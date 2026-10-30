#!/usr/bin/env bash
# End-to-end test: long handwritten note -> Save & Exit -> exported markdown + image on the PC side.
# Runs against the debug build only (io.aragonite.notes.debug, DB in Documents/notabledb-debug).
# The real app and its notes are never touched. Needs a seed DB in .e2e-seed/app_database (gitignored).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes.debug
OUT=.e2e-shots/export-result
[ -f .e2e-seed/app_database ] || { echo "missing .e2e-seed/app_database"; exit 2; }

./gradlew -PSIDE_BY_SIDE=true assembleDebug --console=plain -q
# The side-by-side TEST app (io.aragonite.notes.debug) may carry an older signing key: reinstall it.
# The real app (io.aragonite.notes) is never touched by this script.
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1 || {
  adb uninstall $PKG >/dev/null; adb install app/build/outputs/apk/debug/app-debug.apk >/dev/null; }
adb shell input keyevent KEYCODE_WAKEUP
adb shell am force-stop $PKG
adb shell pm clear $PKG >/dev/null
adb shell rm -rf /sdcard/Documents/notabledb-debug /sdcard/Documents/e2e-inbox
adb shell mkdir -p /sdcard/Documents/notabledb-debug
# These flows start from Home: the seed copy opens Home instead of the weekly planner.
SEED=$(mktemp); cp .e2e-seed/app_database "$SEED"
sqlite3 "$SEED" "update Kv set value=replace(value,'\"showWelcome\":false','\"showWelcome\":false,\"startInPlanner\":false') where key='APP_SETTINGS'"
adb push "$SEED" /sdcard/Documents/notabledb-debug/app_database >/dev/null; rm -f "$SEED"
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow

maestro test .maestro/10-export-long-note.yaml

echo "waiting for the background export (up to 5 min)..."
for _ in $(seq 1 75); do
  n=$(adb shell 'ls /sdcard/Documents/e2e-inbox/*.md 2>/dev/null | wc -l' | tr -d '\r')
  [ "$n" -ge 1 ] && break; sleep 4
done
sleep 5   # let the jpg/sb1 writes finish
rm -rf "$OUT"; mkdir -p "$OUT"
adb pull /sdcard/Documents/e2e-inbox/. "$OUT" >/dev/null
python3 scripts/verify_export.py "$OUT"
