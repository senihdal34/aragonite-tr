#!/usr/bin/env bash
# End-to-end test: the app opens on the weekly planner, Home is reachable and HAFTA returns to it,
# and the planner notebook holds a week page and a to-do page for this week and the next 8 weeks.
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
pages=$(sqlite3 "$D/app_database" "select count(*) from Page where background like 'planner-week:%'")
todos=$(sqlite3 "$D/app_database" "select count(*) from Page where background like 'planner-todo:%'")
books=$(sqlite3 "$D/app_database" "select count(*) from Notebook where title='Planlayıcı'")
monday=$(python3 -c "import datetime as d; t=d.date.today(); print(t-d.timedelta(days=t.weekday()))")
cur=$(sqlite3 "$D/app_database" "select count(*) from Page where background='planner-week:$monday'")
rm -rf "$D"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1 ($2)"; else echo "FAIL  $1 (got $2, expected $3)"; fail=1; fi; }
check "one Planlayıcı notebook" "$books" 1
check "week pages (this week + 8 ahead)" "$pages" 9
check "to-do pages" "$todos" 9
check "page for the current week exists" "$cur" 1
exit $fail
