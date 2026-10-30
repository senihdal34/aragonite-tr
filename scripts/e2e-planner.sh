#!/usr/bin/env bash
# End-to-end test: Home opens on the week page, a typed to-do item is saved and ticked, a tag is added and the topic
# deleted, a note is deleted.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes.debug
[ -f .e2e-seed/app_database ] || { echo "missing .e2e-seed/app_database"; exit 2; }

./gradlew -PSIDE_BY_SIDE=true assembleDebug --console=plain -q
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null 2>&1 || {
  adb uninstall $PKG >/dev/null; adb install app/build/outputs/apk/debug/app-debug.apk >/dev/null; }
adb shell input keyevent KEYCODE_WAKEUP
adb shell am force-stop $PKG
adb shell pm clear $PKG >/dev/null
adb shell rm -rf /sdcard/Documents/notabledb-debug
adb shell mkdir -p /sdcard/Documents/notabledb-debug
adb push .e2e-seed/app_database /sdcard/Documents/notabledb-debug/app_database >/dev/null   # default settings: planner at start
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow

maestro test .maestro/40-planner.yaml
adb shell am force-stop $PKG; sleep 2

D=$(mktemp -d); adb pull /sdcard/Documents/notabledb-debug/. "$D" >/dev/null
sqlite3 "$D/app_database" 'PRAGMA wal_checkpoint(TRUNCATE);' >/dev/null
today=$(python3 -c "import datetime as d; print(d.date.today())")
todo=$(sqlite3 "$D/app_database" "select value from Kv where key='todo:$today'")
books=$(sqlite3 "$D/app_database" "select count(*) from Notebook where title='Planlayıcı'")
planner_pages=$(sqlite3 "$D/app_database" "select count(*) from Page where background like 'planner-%'")
notes=$(sqlite3 "$D/app_database" "select count(*) from Page where background='inbox'")
tags=$(sqlite3 "$D/app_database" "select count(*) from Kv where key like 'pagetags:%' and value like '%e2etest%'")
hidden=$(sqlite3 "$D/app_database" "select count(*) from Kv where key='hiddentopics' and value like '%e2etest%'")
rm -rf "$D"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1 ($2)"; else echo "FAIL  $1 (got $2, expected $3)"; fail=1; fi; }
has() { if echo "$2" | grep -q "$3"; then echo "PASS  $1"; else echo "FAIL  $1 (got: $2)"; fail=1; fi; }
has "to-do text saved for today" "$todo" "e2egorev"
has "to-do marked done" "$todo" '"done":true'
check "no planner notebook any more" "$books" 0
check "no planner pages" "$planner_pages" 0
check "one note deleted (seed has 5)" "$notes" 4
check "deleted topic is off every note" "$tags" 0
check "deleted topic is hidden" "$hidden" 1
exit $fail
