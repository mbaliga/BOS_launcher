#!/usr/bin/env bash
# NFR-09 / NFR-10: 2-hour scripted soak of the launcher on an attached device. No ANR in the launcher process, no crash.
# Uses adb (platform-tools) to talk to the attached device.
# Usage: tools/soak.sh [seconds=7200]
# Loop: HOME, open the app drawer and search by tapping and typing, rotate, open and close a few apps, press HOME,
# and every minute check logcat for ANR or fatal exceptions in $PKG and that the process is alive.
# Exits 1 on the first ANR or crash and leaves tools/soak-out/ with the evidence. Device-only; never run in CI.
set -u
. "$(dirname "$0")/_common.sh"; need_device
DURATION="${1:-7200}"
OUT="$(dirname "$0")/soak-out"; mkdir -p "$OUT"
$ADB logcat -b all -c
start=$(date +%s); iter=0
fail() { echo "SOAK FAIL after $(( $(date +%s) - start )) s, iteration $iter: $1"; $ADB logcat -b all -d > "$OUT/logcat.txt"; $ADB shell dumpsys activity processes > "$OUT/processes.txt"; $ADB pull /data/anr "$OUT/anr" >/dev/null 2>&1; exit 1; }
check() {
  $ADB shell pidof "$PKG" >/dev/null || fail "process $PKG is not running"
  $ADB logcat -b all -d -v brief | grep -E "ANR in $PKG|FATAL EXCEPTION|Process: $PKG" | grep -v "^---" | head -1 | grep -q . && fail "ANR or crash in log"
  $ADB shell dumpsys activity processes | grep -A3 "$PKG" | grep -q "notResponding=true" && fail "ANR flag set"
}
size=$($ADB shell wm size | awk -F'[ x]' '/Physical size/ {print $3" "$4}'); W=${size% *}; H=${size#* }
while [ $(( $(date +%s) - start )) -lt "$DURATION" ]; do
  iter=$((iter+1))
  go_home; sleep 1
  $ADB shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 200; sleep 1        # drawer or palette gesture
  $ADB shell input text "cal"; sleep 1; $ADB shell input keyevent KEYCODE_BACK; sleep 1
  $ADB shell settings put system accelerometer_rotation 0
  $ADB shell settings put system user_rotation $((iter % 4)); sleep 1               # rotation
  $ADB shell am start -a android.intent.action.MAIN -c android.intent.category.APP_CALCULATOR >/dev/null 2>&1; sleep 1
  go_home; sleep 1
  $ADB shell input keyevent KEYCODE_APP_SWITCH; sleep 1; go_home                   # recents round trip
  [ $((iter % 10)) -eq 0 ] && check
  [ $((iter % 50)) -eq 0 ] && $ADB shell am kill "$PKG" && sleep 2 && go_home      # process death restore
done
$ADB shell settings put system user_rotation 0
check
echo "SOAK PASS: $iter iterations in $(( $(date +%s) - start )) s with no ANR or crash (device run; record the date in docs/TEST_COVERAGE.md notes)."
