#!/usr/bin/env bash
# End-to-end test: Home screen lists + thumbnails (debug build only, seeded DB, no real notes touched).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes.debug
[ -f .e2e-seed/app_database ] || { echo "missing .e2e-seed/app_database"; exit 2; }
PAGES=$(sqlite3 .e2e-seed/app_database "select count(*) from Page where pinned=0 or pinned=1")

./gradlew -PSIDE_BY_SIDE=true assembleDebug --console=plain -q
# The side-by-side TEST app (io.aragonite.notes.debug) may carry an older signing key: reinstall it.
# The real app (io.aragonite.notes) is never touched by this script.
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1 || {
  adb uninstall $PKG >/dev/null; adb install app/build/outputs/apk/debug/app-debug.apk >/dev/null; }
adb shell input keyevent KEYCODE_WAKEUP
adb shell am force-stop $PKG
adb shell pm clear $PKG >/dev/null           # no thumbnails yet
adb shell rm -rf /sdcard/Documents/notabledb-debug
adb shell mkdir -p /sdcard/Documents/notabledb-debug
adb push .e2e-seed/app_database /sdcard/Documents/notabledb-debug/app_database >/dev/null
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow

maestro test .maestro/20-home.yaml

echo "waiting for thumbnails (up to 3 min)..."
for _ in $(seq 1 45); do
  n=$(adb shell "run-as $PKG sh -c 'ls files/pages/previews/thumbs 2>/dev/null | wc -l'" | tr -d '\r ')
  [ "$n" -ge "$PAGES" ] && break; sleep 4
done
fail=0
small=$(adb shell "run-as $PKG sh -c 'find files/pages/previews/thumbs -type f -size -2k | wc -l'" | tr -d '\r ')
[ "$n" -ge "$PAGES" ] && echo "PASS  thumbnails created for all $n pages" || { echo "FAIL  only $n thumbnails"; fail=1; }
[ "$small" -eq 0 ] && echo "PASS  no empty thumbnail files" || { echo "FAIL  $small tiny thumbnail files"; fail=1; }
adb shell am force-stop $PKG; maestro test .maestro/20-home.yaml >/dev/null
exit $fail
