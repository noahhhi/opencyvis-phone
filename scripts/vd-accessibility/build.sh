#!/bin/sh
set -eu
src=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
out=${1:?Usage: build.sh output.apk}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk" ]; then
    sdk="$HOME/Library/Android/sdk"
    if [ ! -f "$sdk/platforms/android-36/android.jar" ] || [ ! -x "$sdk/build-tools/36.0.0/aapt2" ]; then
        sdk=/opt/homebrew/share/android-commandlinetools
    fi
fi
bt="$sdk/build-tools/36.0.0"
jar="$sdk/platforms/android-36/android.jar"
if [ ! -f "$jar" ] || [ ! -x "$bt/aapt2" ]; then
    printf '%s\n' 'Set ANDROID_HOME to an SDK with Platform 36 and Build Tools 36.0.0.' >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/classes"
javac --release 8 -cp "$jar" -d "$tmp/classes" "$src/Probe.java"
"$bt/d8" --min-api 33 --lib "$jar" --output "$tmp" "$tmp/classes/local/codex/vdaccess/Probe.class"
"$bt/aapt2" link -I "$jar" --manifest "$src/AndroidManifest.xml" -o "$tmp/base.apk"
(cd "$tmp" && zip -q base.apk classes.dex)
"$bt/zipalign" -f 4 "$tmp/base.apk" "$tmp/aligned.apk"
keytool -genkeypair -keystore "$tmp/test.p12" -storepass android -alias test -keyalg RSA -keysize 2048 -validity 2 -dname CN=VDProbe >/dev/null 2>&1
"$bt/apksigner" sign --ks "$tmp/test.p12" --ks-pass pass:android --out "$out" "$tmp/aligned.apk"
