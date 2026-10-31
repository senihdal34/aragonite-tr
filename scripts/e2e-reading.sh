#!/usr/bin/env bash
# End-to-end test of the Reading tab on the tablet (the single DeusNote app).
#
#   ./scripts/e2e-reading.sh            full run: backup, build, install, portrait, landscape
#   ./scripts/e2e-reading.sh --no-build skip the backup/build/install (test the app that is already on the tablet)
#   E2E_SKIP_MAESTRO=1 ./scripts/e2e-reading.sh --no-build   only the adb checks (panels, Sync, hero, goal), much faster
#   E2E_OPEN_DUST=1 ...                 also open the Gathering-dust book (this marks a never-opened book as opened)
#   ./scripts/e2e-reading.sh --restore-rotation   only puts the rotation back (after a run that was killed hard)
#
# What it does:
#   1. backs up the notes (Documents/notabledb) and installs this build in place (never uninstalls);
#   2. PORTRAIT: the Maestro flows 50 (every page and its boxes, chevrons, dots, swipe), 52 (Sync, shuffle, lists, dust,
#      search, book detail), 53 (goal change and
#      restore to 15), then the checks that leave the app: Wi-Fi / Light / Night open the Android panels and Back returns,
#      Sync reloads the data, the hero opens NeoReader at the saved page, a dust-box Open opens NeoReader;
#   3. LANDSCAPE: rotates the tablet (always put back by a trap, even when a step fails), runs flow 51 (the wide
#      6-column layout, every page, Planner / Notes / Favorites), 55 (controls, lists, search, goal screen) and the
#      same checks again.
# Nothing in /sdcard/Documents/notabledb is ever deleted. Another session or the user may use the tablet: before every
# step the script waits (up to 3 minutes) until the foreground app is this app, KOReader, NeoReader or the launcher.
set -uo pipefail
cd "$(dirname "$0")/.."
# ./scripts/e2e-reading.sh --restore-rotation   only puts the rotation back (after a run that was killed hard)
if [ "${1:-}" = "--restore-rotation" ]; then
  adb shell settings put system user_rotation 0; adb shell settings put system accelerometer_rotation 1; exit 0
fi
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH=/opt/homebrew/bin:$JAVA_HOME/bin:$PATH
PKG=io.aragonite.notes
ACT=$PKG/com.ethran.notable.MainActivity
OUT=.e2e-shots/reading
mkdir -p "$OUT"
FAILS=0
UI=$(mktemp -t e2e-ui.XXXXXX)

# --- rotation is ALWAYS restored: portrait, auto-rotate on (the tablet's normal state)
restore_rotation() {
  adb shell settings put system user_rotation 0 >/dev/null 2>&1
  adb shell settings put system accelerometer_rotation 1 >/dev/null 2>&1
  rm -f "$UI"
}
trap restore_rotation EXIT
trap 'exit 130' INT TERM

pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; FAILS=$((FAILS + 1)); }
focus() { adb shell dumpsys window | grep -m1 mCurrentFocus || true; }

# Wait until the foreground belongs to a window we may drive; wake the screen first.
wait_foreground() {
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  local i f
  for i in $(seq 1 90); do
    f=$(focus)
    if echo "$f" | grep -qE "io\.aragonite\.notes|org\.koreader|com\.onyx|com\.android\.settings"; then
      # the screensaver is a com.onyx window too: wake it
      echo "$f" | grep -q "DreamActivity" && adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || return 0
    fi
    sleep 2
  done
  fail "foreground never became ours: $(focus)"
  return 1
}

back_to_app() { adb shell am start -n "$ACT" >/dev/null 2>&1; sleep 2; }

# tap the first node whose text or content-desc equals $1 (regex, full match)
tap_label() {
  adb shell uiautomator dump /sdcard/e2e-ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/e2e-ui.xml > "$UI"
  python3 - "$1" "$UI" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
pat = re.compile(sys.argv[1])
for e in ET.parse(sys.argv[2]).iter("node"):
    for v in (e.get("text") or "", e.get("content-desc") or ""):
        if v and pat.fullmatch(v):
            m = [int(x) for x in re.findall(r"\d+", e.get("bounds"))]
            subprocess.run(["adb", "shell", "input", "tap", str((m[0] + m[2]) // 2), str((m[1] + m[3]) // 2)])
            sys.exit(0)
sys.exit(1)
PY
}

screen_has() { adb shell uiautomator dump /sdcard/e2e-ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/e2e-ui.xml | grep -qE "$1"; }

# Maestro on this tablet sometimes hangs when its driver app is left over from the last run, so: reset the driver before
# every run, kill a run that takes longer than $FLOW_TIMEOUT seconds, and try each flow twice.
FLOW_TIMEOUT=${FLOW_TIMEOUT:-600}
run_flow_once() { # name file
  pkill -f "maestro.cli" >/dev/null 2>&1   # a Maestro left over from an earlier run makes the next one hang at its first step
  adb shell am force-stop dev.mobile.maestro >/dev/null 2>&1; adb shell am force-stop dev.mobile.maestro.test >/dev/null 2>&1
  sleep 2
  maestro test "$2" --test-output-dir "$OUT/maestro-$1" >"$OUT/$1.log" 2>&1 &
  local pid=$! waited=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 3; waited=$((waited + 3))
    if [ "$waited" -ge "$FLOW_TIMEOUT" ]; then pkill -P "$pid" >/dev/null 2>&1; kill "$pid" >/dev/null 2>&1; pkill -f "maestro.cli" >/dev/null 2>&1; echo "(timeout after ${FLOW_TIMEOUT}s)" >>"$OUT/$1.log"; wait "$pid" 2>/dev/null; return 1; fi
  done
  wait "$pid"
}
run_flow() { # name file
  [ -n "${E2E_SKIP_MAESTRO:-}" ] && { echo "SKIP  $1 (E2E_SKIP_MAESTRO)"; return 0; }
  wait_foreground || return 1
  back_to_app
  if run_flow_once "$1" "$2"; then pass "$1 ($2)"; return 0; fi
  echo "retry $1 (first run failed, see $OUT/$1.log)"; cp "$OUT/$1.log" "$OUT/$1.first.log"
  wait_foreground || return 1
  back_to_app
  if run_flow_once "$1" "$2"; then pass "$1 ($2) on the second run"; else fail "$1 ($2) - see $OUT/$1.log"; fi
}

# Wi-Fi / Light / Night open an Android settings panel, Back returns to the app; Sync reloads the data.
check_toggles() { # label
  wait_foreground || return 1
  back_to_app
  tap_label "Reading" >/dev/null; sleep 2
  tap_label "Page Home" >/dev/null; sleep 1
  local what
  for what in "Wi-Fi" "Light" "Night"; do
    tap_label "Toggle $what" || { fail "$1: no 'Toggle $what' button"; continue; }
    sleep 3
    if focus | grep -q "com.android.settings"; then pass "$1: $what opens the Android settings panel"; else fail "$1: $what did not open settings: $(focus)"; fi
    adb shell input keyevent KEYCODE_BACK; sleep 2
    if focus | grep -q "$PKG"; then pass "$1: Back from the $what panel returns to the app"; else fail "$1: not back in the app after $what: $(focus)"; back_to_app; fi
  done
  adb logcat -c
  tap_label "Toggle Sync" || fail "$1: no Sync button"
  sleep 3
  if adb logcat -d -s ReadingViewModel:I | grep -q "refresh: books="; then pass "$1: Sync reloads the data"; else fail "$1: Sync did not reload the data"; fi
}

# The newest lastAccess row of NeoReader's own library holds the page the hero must open at, as "page/total".
expected_page() {
  adb shell "content query --uri content://com.onyx.content.database.ContentProvider/Metadata --projection lastAccess:progress" \
    | python3 -c "
import sys, re
rows = [(int(m.group(1)), m.group(2)) for l in sys.stdin for m in [re.search(r'lastAccess=(\d+), progress=(\d+/\d+)', l)] if m]
print(max(rows)[1] if rows else '')"
}

check_open_book() { # label
  wait_foreground || return 1
  local expected shown top
  expected=$(expected_page)
  back_to_app
  tap_label "Reading" >/dev/null; sleep 2
  tap_label "Page Home" >/dev/null; sleep 1
  tap_label "Resume|Resume reading" || fail "$1: no Resume button on the hero box"
  sleep 8
  top=$(adb shell dumpsys activity activities | grep -m1 topResumedActivity || true)
  adb shell uiautomator dump /sdcard/e2e-ui.xml >/dev/null 2>&1
  # NeoReader shows the page as "N/M" in its page_name field (an EPUB's chapter title may also carry "(1/1)"); PDFs: "(N/M)"
  shown=$(adb exec-out cat /sdcard/e2e-ui.xml | python3 -c "
import re, sys
x = sys.stdin.read()
m = re.search(r'text=\"(\d+/\d+)\"[^>]*resource-id=\"com.onyx.kreader:id/page_name\"', x) or re.search(r'resource-id=\"com.onyx.kreader:id/page_name\"[^>]*text=\"(\d+/\d+)\"', x) or re.search(r'\((\d+/\d+)\)', x)
print(m.group(1) if m else '')")
  adb exec-out screencap -p > "$OUT/$1-reader.png" 2>/dev/null
  if echo "$top" | grep -q "com.onyx.kreader"; then pass "$1: the hero opens NeoReader"; else fail "$1: NeoReader not on top: $top"; fi
  if [ -n "$expected" ] && [ "$shown" = "$expected" ]; then pass "$1: opens where you stopped ($shown)"; else fail "$1: expected page $expected, reader shows '$shown'"; fi
  back_to_app
  adb shell am force-stop com.onyx.kreader >/dev/null 2>&1   # every opened book leaves a 120 MB reader process; they starve the tablet and Maestro stalls
  # a dust-box Open (Decor page) opens NeoReader too, when the library has a never-opened book
  tap_label "Reading" >/dev/null; sleep 2
  tap_label "Page Decor" >/dev/null; sleep 2
  if [ -z "${E2E_OPEN_DUST:-}" ]; then
    echo "SKIP  $1: dust Open (opening it would mark a never-opened book as opened; set E2E_OPEN_DUST=1)"
  elif screen_has 'content-desc="Open book"'; then
    tap_label "Open book"; sleep 6
    if focus | grep -q "com.onyx.kreader"; then pass "$1: the dust box Open opens NeoReader"; else fail "$1: dust Open did not open NeoReader: $(focus)"; fi
    back_to_app
    adb shell am force-stop com.onyx.kreader >/dev/null 2>&1
  else
    echo "SKIP  $1: no never-opened book, so no dust Open button"
  fi
}

# The Planner tab opens the handwritten week page (an editor on the pen surface: Maestro stalls there, adb does not).
check_planner_tab() { # label
  wait_foreground || return 1
  back_to_app
  tap_label "Reading" >/dev/null; sleep 2
  tap_label "Planner" || { fail "$1: no Planner tab"; return; }
  sleep 10
  if screen_has 'BU HAFTA'; then pass "$1: the Planner tab opens the weekly page"; else fail "$1: the weekly planner page did not open"; fi
  tap_label "Reading" || { fail "$1: no Reading tab on the planner page"; back_to_app; return; }
  sleep 4
  if screen_has 'content-desc="Page Home"'; then pass "$1: Reading tab reachable again from the planner page"; else fail "$1: board did not come back from the planner"; fi
}

check_goal_is_15() {
  wait_foreground || return 1
  back_to_app
  tap_label "Reading" >/dev/null; sleep 2
  tap_label "Page Home" >/dev/null; sleep 1
  if screen_has '0 of 15|[0-9]+ of 15'; then pass "yearly goal is back at 15"; else fail "yearly goal is not 15 - set it with the menu in the Reading tab"; fi
}

if [ "${1:-}" != "--no-build" ]; then
  B=~/Workspace/Kai-Sync/projects/aragonite-tr-backups/$(date +%Y-%m-%d-%H%M)-reading-e2e-oncesi
  mkdir -p "$B"
  adb shell am force-stop $PKG; sleep 2
  adb pull /sdcard/Documents/notabledb "$B/notabledb" >/dev/null
  echo "backup: $B"
  ./gradlew assembleDebug --console=plain -q || { echo "build failed"; exit 1; }
  adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null || { echo "install failed"; exit 1; }
fi
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
sleep 3

PHASES=${E2E_PHASES:-"portrait landscape"}   # e.g. E2E_PHASES=landscape
if echo "$PHASES" | grep -qw portrait; then
echo "=== PORTRAIT ==="
run_flow portrait .maestro/50-reading.yaml
run_flow controls-portrait .maestro/52-reading-controls.yaml
run_flow goal-portrait .maestro/53-reading-goal.yaml
check_goal_is_15
check_toggles portrait
check_planner_tab portrait
check_open_book portrait
adb exec-out screencap -p > "$OUT/portrait-end.png" 2>/dev/null
fi

if echo "$PHASES" | grep -qw landscape; then
echo "=== LANDSCAPE ==="
adb shell settings put system accelerometer_rotation 0   # auto-rotate would ignore user_rotation
adb shell settings put system user_rotation 1
sleep 5
adb shell wm size | head -1 >/dev/null
# a wrong orientation would test the wrong layout: the screenshot must be wider than tall
adb exec-out screencap -p > "$OUT/landscape-start.png" 2>/dev/null
python3 - "$OUT/landscape-start.png" <<'PY' || { fail "the tablet did not rotate to landscape"; }
import struct, sys
d = open(sys.argv[1], "rb").read(32)
w, h = struct.unpack(">II", d[16:24])
sys.exit(0 if w > h else 1)
PY
run_flow landscape .maestro/51-reading-landscape.yaml
run_flow controls-landscape .maestro/55-reading-landscape-controls.yaml
run_flow goal-landscape .maestro/53-reading-goal.yaml
check_goal_is_15
check_toggles landscape
check_planner_tab landscape
check_open_book landscape
fi

restore_rotation
sleep 3
echo
if [ "$FAILS" -eq 0 ]; then echo "ALL PASSED"; else echo "$FAILS CHECK(S) FAILED"; exit 1; fi
