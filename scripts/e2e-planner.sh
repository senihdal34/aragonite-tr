#!/usr/bin/env bash
# End-to-end test: the app opens on the weekly planner page, the week tool moves between weeks, a tag is added and the
# topic deleted, a note is deleted.
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
weeks=$(sqlite3 "$D/app_database" "select count(*) from Page where background like 'planner-week:%'")
books=$(sqlite3 "$D/app_database" "select count(*) from Notebook where title='Planlayıcı'")
monday=$(python3 -c "import datetime as d; t=d.date.today(); print(t-d.timedelta(days=t.weekday()))")
prev=$(python3 -c "import datetime as d; t=d.date.today(); print(t-d.timedelta(days=t.weekday()+7))")
cur=$(sqlite3 "$D/app_database" "select count(*) from Page where background='planner-week:$monday'")
before=$(sqlite3 "$D/app_database" "select count(*) from Page where background='planner-week:$prev'")
other=$(sqlite3 "$D/app_database" "select count(*) from Page where background like 'planner-%' and background not like 'planner-week:%'")
notes=$(sqlite3 "$D/app_database" "select count(*) from Page where background='inbox'")
tags=$(sqlite3 "$D/app_database" "select count(*) from Kv where key like 'pagetags:%' and value like '%e2etest%'")
hidden=$(sqlite3 "$D/app_database" "select count(*) from Kv where key='hiddentopics' and value like '%e2etest%'")
rm -rf "$D"
adb uninstall $PKG >/dev/null 2>&1 || true   # the test copy is temporary: DeusNote stays the only app
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1 ($2)"; else echo "FAIL  $1 (got $2, expected $3)"; fail=1; fi; }
has() { if echo "$2" | grep -q "$3"; then echo "PASS  $1"; else echo "FAIL  $1 (got: $2)"; fail=1; fi; }
check "one Planlayıcı notebook" "$books" 1
check "week pages (this week, 8 ahead, last week made by the tool)" "$weeks" 10
check "page for this week exists" "$cur" 1
check "previous week was created by the tool" "$before" 1
check "no other planner pages" "$other" 0
check "one note deleted (seed has 5)" "$notes" 4
check "deleted topic is off every note" "$tags" 0
check "deleted topic is hidden" "$hidden" 1
exit $fail
