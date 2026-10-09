#!/usr/bin/env bash
# Checks an Android App Bundle the way Google Play will, and fails the build if it wouldn't be
# accepted or if it lost the app's no-network guarantee.
#
# Play measures the *compressed download* a phone receives, not the bytes on disk, so sizes here all
# come from "bundletool get-size total". Play's limits (support.google.com/googleplay/android-developer/answer/9859372):
#
#   base module                                  500 MB
#   one asset pack                               1.5 GB
#   all modules + install-time asset packs       4 GB
#
# Checks, in order:
#   1. builds every APK Play would generate from the bundle;
#   2. no APK in that set asks for INTERNET or anything off the allowlist (the asset pack and the
#      config splits have manifests of their own, so checking only the base one isn't enough);
#   3. every native library loads on 16 KB-page phones;
#   4. the models are stored uncompressed, since the app memory-maps them with openFd();
#   5. the base module's download is inside both Play's limit and the budget below.
#
# Usage: BUNDLETOOL=path/to/bundletool.jar scripts/check-bundle.sh path/to/app.aab [out-dir]
set -euo pipefail

aab="${1:?usage: BUNDLETOOL=bundletool.jar $0 path/to/app.aab [out-dir]}"
outdir="${2:-$(dirname "$aab")/bundle-check}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The base module is about 30 MB once the models are in the asset pack. The budget is Play's limit
# for the gate, plus a much tighter one that trips the moment a model lands back in the base module
# (which is the mistake worth catching, and it is far cheaper to catch here than on upload).
play_base_limit_mb=500
base_budget_mb=150

: "${BUNDLETOOL:?set BUNDLETOOL to the bundletool jar}"
bt() { java -jar "$BUNDLETOOL" "$@"; }

rm -rf "$outdir"
mkdir -p "$outdir"
apks="$outdir/app.apks"

echo "::group::bundletool build-apks"
# Signed with bundletool's debug key unless a keystore is passed in: these APKs are only for
# measuring and for installing on a test device, never for upload. Play re-signs from the bundle.
ks_args=()
if [[ -n "${BUNDLE_KEYSTORE:-}" ]]; then
  ks_args=(
    "--ks=$BUNDLE_KEYSTORE"
    "--ks-pass=pass:${BUNDLE_KEYSTORE_PASSWORD:?BUNDLE_KEYSTORE needs BUNDLE_KEYSTORE_PASSWORD}"
    "--ks-key-alias=${BUNDLE_KEY_ALIAS:?BUNDLE_KEYSTORE needs BUNDLE_KEY_ALIAS}"
    "--key-pass=pass:${BUNDLE_KEYSTORE_PASSWORD}"
  )
fi
bt build-apks --bundle="$aab" --output="$apks" --overwrite "${ks_args[@]}"
echo "::endgroup::"

unzip -q "$apks" -d "$outdir/apks"
mapfile -t all_apks < <(find "$outdir/apks" -name '*.apk' | sort)
echo "APKs Play would generate from this bundle:"
for a in "${all_apks[@]}"; do
  printf '  %10d  %s\n' "$(stat -c %s "$a")" "${a#"$outdir/apks/"}"
done

echo "::group::No network access, in every APK of the set"
"$here/check-apk-permissions.sh" "${all_apks[@]}"
echo "::endgroup::"

echo "::group::16 KB memory pages"
for a in "${all_apks[@]}"; do
  # Only the splits that carry native code have anything to say; --strict fails the build.
  if unzip -l "$a" | grep -q 'lib/.*\.so'; then
    echo "--- ${a#"$outdir/apks/"}"
    python3 "$here/check-16kb-pages.py" "$a" --strict
  fi
done
echo "::endgroup::"

# openFd() can only memory-map an asset that is stored, not deflated. androidResources.noCompress in
# app/build.gradle.kts is meant to put that in the bundle's compression config and so into the asset
# pack's split too; this is the check that it actually did, because the failure mode on a phone is
# the model silently refusing to load.
echo "::group::Models stored uncompressed"
compressed=0
for a in "${all_apks[@]}"; do
  while read -r method name; do
    [[ "$method" == "Stored" ]] && continue
    echo "::error::$name is $method in ${a#"$outdir/apks/"}; openFd() can't memory-map it. Check androidResources.noCompress."
    compressed=1
  done < <(unzip -lv "$a" | awk '$0 ~ /\.(tflite|litertlm)$/ {print $2, $8}')
done
if [[ $compressed -ne 0 ]]; then exit 1; fi
echo "  all .tflite/.litertlm entries are Stored"
echo "::endgroup::"

# get-size total prints CSV with a MIN,MAX header; MAX is the worst-case download for any device the
# app supports. With no --modules it covers what Play downloads on install: the base module and every
# install-time asset pack.
size_of() {
  bt get-size total --apks="$apks" "$@" | tail -n 1 | cut -d, -f2
}
install_bytes="$(size_of)"
base_bytes="$(size_of --modules=base)"
pack_bytes=$((install_bytes - base_bytes))

mb() { echo "$(( $1 / 1000 / 1000 )) MB ($1 bytes)"; }
{
  echo "Compressed download size, as Play measures it:"
  echo "  base module          $(mb "$base_bytes")   limit ${play_base_limit_mb} MB, budget ${base_budget_mb} MB"
  echo "  install-time packs   $(mb "$pack_bytes")   limit 1.5 GB per pack"
  echo "  first download       $(mb "$install_bytes")   limit 4 GB for all modules and install-time packs"
} | tee "$outdir/sizes.txt"

status=0
if [[ $((base_bytes / 1000 / 1000)) -ge $play_base_limit_mb ]]; then
  echo "::error::The base module's download is over Play's ${play_base_limit_mb} MB limit; Play will reject this bundle." >&2
  status=1
elif [[ $((base_bytes / 1000 / 1000)) -ge $base_budget_mb ]]; then
  echo "::error::The base module's download is over the ${base_budget_mb} MB budget in $0. A model has probably fallen back into the base module instead of the asset pack (-PmodelsInAssetPack)." >&2
  status=1
fi
if [[ $((install_bytes / 1000 / 1000)) -ge 4000 ]]; then
  echo "::error::The first download is over Play's 4 GB limit for all modules and install-time asset packs." >&2
  status=1
fi
if [[ $status -eq 0 ]]; then
  echo "OK: inside Play's size limits, no network access, 16 KB-page safe, models uncompressed."
fi
exit $status
