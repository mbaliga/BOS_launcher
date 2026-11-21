#!/usr/bin/env bash
# NFR-06: memory at rest on home, budget 180 MB PSS with 10 widgets. Add the widgets by hand first, then run:
# Uses adb (platform-tools) to talk to the attached device.
#   tools/measure_pss.sh [samples=5] [interval_s=20]
# Prints each sample and exits 1 when the median is over the budget. Run on the mid-range reference device.
set -u
. "$(dirname "$0")/_common.sh"; need_device
SAMPLES="${1:-5}"; GAP="${2:-20}"; BUDGET_MB=180
go_home; sleep 5
vals=()
for i in $(seq 1 "$SAMPLES"); do
  kb=$($ADB shell dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL +[0-9]/ {print $2; exit}')
  [ -n "$kb" ] && vals+=("$kb") && echo "sample $i: $((kb/1024)) MB PSS"
  sleep "$GAP"
done
[ ${#vals[@]} -gt 0 ] || { echo "no PSS reading (is $PKG running?)" >&2; exit 2; }
med=$(printf '%s\n' "${vals[@]}" | sort -n | awk '{a[NR]=$1} END {print a[int((NR+1)/2)]}')
echo "median: $((med/1024)) MB (budget $BUDGET_MB MB)"
[ $((med/1024)) -le $BUDGET_MB ] || { echo "NFR-06 FAIL"; exit 1; }
echo "NFR-06 PASS (device measurement; not run in CI)"
