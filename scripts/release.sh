#!/usr/bin/env bash
# Builds the APK of a Kiri release, the same way on a computer and in the release workflow:
#
#   KIRI_RELEASE_STORE=/path/kiri-release.jks KIRI_RELEASE_PASSWORD=... scripts/release.sh
#
# (or with keystore.properties at the root of the repository, which git ignores).
#
# It builds the public app (without the folder `personal`), runs the tests, makes sure nothing of
# `personal` went in, checks that the APK is signed with Kiri's release key, and leaves in
# build/release/ the APK, its SHA-256 and the release notes taken from CHANGELOG.md.
# Extra arguments go to Gradle.
set -euo pipefail

cd "$(dirname "$0")/.."

# The SHA-256 of the certificate of Kiri's release key. A release signed with any other key
# would not install over the ones before it, so the script stops.
RELEASE_CERT_SHA256="6c9e39655b51f18c3dc0c6e7007d83d12b8830301a3a67ee033da46edb0a00c3"

version="$(sed -n 's/^kiri\.version=//p' gradle.properties | tr -d '[:space:]')"
[ -n "$version" ] || { echo "kiri.version is missing from gradle.properties" >&2; exit 1; }
echo "Kiri $version"

# The notes of this version in the changelog: everything under "## [<version>]" up to the next
# version.
out="build/release"
rm -rf "$out"
mkdir -p "$out"
notes="$out/notas-$version.md"
awk -v v="$version" '
    /^## \[/ { inside = (index($0, "## [" v "]") == 1); next }
    inside { print }
' CHANGELOG.md | sed -e '/./,$!d' > "$notes"
[ -s "$notes" ] || { echo "CHANGELOG.md has no section \"## [$version]\"" >&2; exit 1; }

./gradlew -Pkiri.public=true :core:test :app:assembleRelease "$@"

apk="app/build/outputs/apk/release/app-release.apk"
[ -f "$apk" ] || {
    echo "No signed APK: set KIRI_RELEASE_STORE and KIRI_RELEASE_PASSWORD, or keystore.properties" >&2
    exit 1
}

# Nothing of the owner's own folder may be in a release. Besides its package, the folder can list
# in personal/release-markers.txt (one extended regex per line) strings that only its code holds,
# which survive the renaming R8 does.
markers="kiri/personal"
if [ -f personal/release-markers.txt ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && markers="$markers|$line"
    done < personal/release-markers.txt
fi
if unzip -p "$apk" 'classes*.dex' | grep -a -q -E "$markers"; then
    echo "The release holds code from the personal folder." >&2
    exit 1
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
apksigner="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
[ -x "$apksigner" ] || { echo "apksigner not found in the Android SDK ($sdk)" >&2; exit 1; }
cert="$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [ "$cert" != "$RELEASE_CERT_SHA256" ]; then
    echo "The APK is signed with another key ($cert), not Kiri's release key." >&2
    exit 1
fi

name="kiri-launcher-$version.apk"
cp "$apk" "$out/$name"
(cd "$out" && sha256sum "$name" > "$name.sha256")
echo "Ready in $out/:"
ls -l "$out"
