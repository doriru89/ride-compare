#!/usr/bin/env bash
# Dependency-light Android build. Requires SDK Platform 35, Build Tools 35.0.0 and JDK 17.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your Android SDK directory}"
build_tools="$ANDROID_SDK_ROOT/build-tools/35.0.0"
platform_jar="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
for executable in aapt2 d8 zipalign apksigner; do
  test -x "$build_tools/$executable" || { echo "Missing Android Build Tools 35.0.0: $executable" >&2; exit 1; }
done
test -f "$platform_jar" || { echo "Install SDK Platform android-35" >&2; exit 1; }
rm -rf build
mkdir -p build/compiled build/classes build/dex
"$build_tools/aapt2" compile --dir res -o build/compiled
"$build_tools/aapt2" link -o build/base.apk -I "$platform_jar" --manifest AndroidManifest.xml build/compiled/*.flat
if command -v javac >/dev/null; then
  javac -encoding UTF-8 -source 8 -target 8 -classpath "$platform_jar" -d build/classes src/com/orz/ridecompare/*.java
else
  : "${ECJ_JAR:?Install JDK 17 or set ECJ_JAR to Eclipse ECJ 3.38.0}"
  java -jar "$ECJ_JAR" -encoding UTF-8 -source 8 -target 8 -classpath "$platform_jar" -d build/classes src/com/orz/ridecompare/*.java
fi
"$build_tools/d8" --lib "$platform_jar" --min-api 32 --output build/dex build/classes/com/orz/ridecompare/*.class
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$build_tools/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Public source never contains a signing key. A local build gets its own development key.
# To update an existing installation, supply the original key via an absolute RIDE_KEYSTORE path.
keystore="${RIDE_KEYSTORE:-$PWD/.private/debug-signing.p12}"
export RIDE_STORE_PASSWORD="${RIDE_STORE_PASSWORD:-android}"
export RIDE_KEY_PASSWORD="${RIDE_KEY_PASSWORD:-$RIDE_STORE_PASSWORD}"
key_alias="${RIDE_KEY_ALIAS:-ride-test}"
if [ ! -f "$keystore" ]; then
  if [ -n "${RIDE_KEYSTORE:-}" ]; then
    echo "RIDE_KEYSTORE does not exist" >&2
    exit 1
  fi
  mkdir -p .private
  chmod 700 .private
  keytool -genkeypair -keystore "$keystore" -storepass:env RIDE_STORE_PASSWORD \
    -keypass:env RIDE_KEY_PASSWORD -alias "$key_alias" -dname 'CN=Ride Compare Local Development' \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
  chmod 600 "$keystore"
fi
"$build_tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" \
  --ks-pass env:RIDE_STORE_PASSWORD --key-pass env:RIDE_KEY_PASSWORD \
  --out build/ride-compare.apk build/aligned.apk
"$build_tools/apksigner" verify --verbose build/ride-compare.apk
