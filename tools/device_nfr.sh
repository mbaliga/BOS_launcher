#!/usr/bin/env bash
# NFR-01 to NFR-07, NFR-09, NFR-10: the device measurements as one command. Needs one device (the mid-range reference
# device, not an emulator) with a release or benchmark build installed and Liftoff as the home app, and network access
# for the macrobenchmark's first Gradle sync. It only runs the existing scripts and writes their output to one file;
# the numbers are for you to read and copy into docs/device-checklist.md (section 13).
# Usage: tools/device_nfr.sh [--quick] [--soak]   (--quick skips the Gradle macrobenchmark, --soak adds the 2 h soak)
set -u
here="$(cd "$(dirname "$0")" && pwd)"; root="$(dirname "$here")"
. "$here/_common.sh"; need_device
out="${LIFTOFF_NFR_OUT:-$root/build/device-nfr-$(date +%Y%m%d-%H%M%S).txt}"; mkdir -p "$(dirname "$out")"
quick=0; soak=0
for a in "$@"; do case "$a" in --quick) quick=1;; --soak) soak=1;; esac; done
rc=0
run() { echo "=== $1" | tee -a "$out"; shift; "$@" 2>&1 | tee -a "$out"; local s=${PIPESTATUS[0]}; echo "exit $s" | tee -a "$out"; [ "$s" -eq 0 ] || rc=1; }
echo "device: $($ADB shell getprop ro.product.model | tr -d '\r') Android $($ADB shell getprop ro.build.version.release | tr -d '\r')" | tee "$out"
run "NFR-01 NFR-02 NFR-05 (am start -W, gfxinfo)" "$here/perf.sh" 20
run "NFR-06 PSS with the widgets that are on home" "$here/measure_pss.sh"
if [ "$quick" -eq 0 ]; then
  run "NFR-01..05 macrobenchmark (HomeBenchmarks, PaletteBenchmarks)" "$root/gradlew" -p "$root" -Pliftoff.benchmark=true :benchmark:connectedBenchmarkAndroidTest
fi
[ "$soak" -eq 1 ] && run "NFR-10 soak 2 h" "$here/soak.sh"
echo "NFR-07 battery is an overnight run: tools/measure_battery.sh [hours] with the device idle, unplugged, screen off. NFR-09 needs the beta cohort." | tee -a "$out"
echo "written to $out; exit $rc (non-zero means a budget was missed or a step failed)"
exit $rc
