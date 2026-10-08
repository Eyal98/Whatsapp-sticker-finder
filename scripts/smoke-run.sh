#!/usr/bin/env bash
# Runs the on-device smoke test on a connected emulator (see .github/workflows/smoke.yml).
# SMOKE_TEST_CLASS limits the run to one test class; SMOKE_RAM_BAND asserts the emulator's RAM band.
# A hang (the instrumentation crashing on start can leave Gradle waiting) fails after 40 minutes,
# and the emulator's log is saved either way, with the app's and test runner's lines printed.
# Lines starting with PERF are the first-run measurements, saved to perf.txt.
set -u
out="${RUNNER_TEMP:-/tmp}"
pkg=com.eyal98.stickerfinder
args=()
[ -n "${SMOKE_TEST_CLASS:-}" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.class=$SMOKE_TEST_CLASS")
[ -n "${SMOKE_RAM_BAND:-}" ] && args+=("-Pandroid.testInstrumentationRunnerArguments.expectRamBand=$SMOKE_RAM_BAND")
adb logcat -c || true
timeout 40m ./gradlew --no-daemon connectedMinifiedAndroidTest "${args[@]}"
status=$?
[ "$status" -eq 124 ] && echo "::error::The smoke test timed out after 40 minutes"
# Cold start as a user's first launch: the process is stopped, then the activity is started and timed until drawn.
adb shell am force-stop "$pkg" || true
adb shell am start -W -n "$pkg/.MainActivity" 2>&1 | tr -d '\r' | tee "$out/am-start.txt" > /dev/null
cold=$(sed -n 's/^TotalTime: //p' "$out/am-start.txt")
adb shell log -t PeelItPerf "PERF cold_start_total_ms=${cold:-unknown}" || true
adb logcat -d -v time > "$out/logcat.txt" 2>&1 || true
grep -E "PERF " "$out/logcat.txt" > "$out/perf.txt" || true
echo "---- first-run measurements (PERF) ----"
cat "$out/perf.txt" || true
echo "---- emulator log (app, test runner, crashes) ----"
grep -E "AndroidRuntime|FATAL|DEBUG|ModelSmokeTest|FirstRunTest|TestRunner|stickerfinder|UnsatisfiedLink|NoSuchMethod|ClassNotFound" "$out/logcat.txt" | tail -n 200 || true
exit "$status"
