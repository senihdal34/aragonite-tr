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

# Open the book of the hero box: NeoReader must come to the front AT THE SAVED POSITION (page "(N/M)" of its own list).
expected=$(adb shell "content query --uri content://com.onyx.content.database.ContentProvider/Metadata --projection lastAccess:progress" \
  | python3 -c "
import sys,re
rows=[(int(m.group(1)),m.group(2)) for l in sys.stdin for m in [re.search(r'lastAccess=(\d+), progress=(\d+/\d+)',l)] if m]
print(max(rows)[1] if rows else '')")
cat > /tmp/open-book.yaml <<'YAML'
appId: io.aragonite.notes
---
- launchApp
- tapOn: "Reading"
- extendedWaitUntil:
    visible: "Streak"
    timeout: 20000
- tapOn: "Resume|Resume reading|Shuffle"
YAML
maestro test /tmp/open-book.yaml
sleep 8
top=$(adb shell dumpsys activity activities | grep -m1 topResumedActivity || true)
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
shown=$(adb shell "cat /sdcard/ui.xml" | grep -o '([0-9]*/[0-9]*)' | head -1 | tr -d '()')
adb shell input keyevent KEYCODE_BACK; sleep 1
echo "$top" | grep -q "com.onyx.kreader" && echo "PASS  tapping a book opens NeoReader" || { echo "FAIL  NeoReader not on top: $top"; exit 1; }
if [ -n "$expected" ] && [ "$shown" = "$expected" ]; then echo "PASS  opens where you stopped ($shown)"; else echo "FAIL  expected page $expected, reader shows '$shown'"; exit 1; fi
