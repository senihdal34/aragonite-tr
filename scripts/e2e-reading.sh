#!/usr/bin/env bash
# End-to-end test of the Reading tab on the tablet (the single DeusNote app): backs up the notes, installs this
# build in place, runs the Maestro flow, then taps a book and checks that NeoReader comes to the front.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes
B=~/Workspace/Kai-Sync/projects/aragonite-tr-backups/$(date +%Y-%m-%d-%H%M)-reading-e2e-oncesi
mkdir -p "$B"
adb shell am force-stop $PKG; sleep 2
adb pull /sdcard/Documents/notabledb "$B/notabledb" >/dev/null
echo "backup: $B"

./gradlew assembleDebug --console=plain -q
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell input keyevent KEYCODE_WAKEUP

maestro test .maestro/50-reading.yaml

# Open a book from the board: the hero "Continue" / "Open" pill, then NeoReader must be on top.
cat > /tmp/open-book.yaml <<'YAML'
appId: io.aragonite.notes
---
- launchApp
- tapOn: "READING"
- extendedWaitUntil:
    visible: "STREAK"
    timeout: 20000
- tapOn: "CONTINUE|OPEN"
YAML
maestro test /tmp/open-book.yaml
sleep 4
top=$(adb shell dumpsys activity activities | grep -m1 topResumedActivity || true)
adb shell input keyevent KEYCODE_BACK; sleep 1
if echo "$top" | grep -q "com.onyx.kreader"; then echo "PASS  tapping a book opens NeoReader"; else echo "FAIL  NeoReader not on top: $top"; exit 1; fi
