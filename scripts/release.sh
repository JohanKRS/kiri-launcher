#!/usr/bin/env bash
# Builds the APK and the app bundle (AAB, for Google Play) of a Kiri release, the same way on a
# computer and in the release workflow:
#
#   KIRI_RELEASE_STORE=/path/kiri-release.jks KIRI_RELEASE_PASSWORD=... scripts/release.sh
#
# (or with keystore.properties at the root of the repository, which git ignores).
#
# It builds the public app (without the folder `personal`), runs the tests, makes sure nothing of
# `personal` went in, checks that the APK and the bundle are signed with Kiri's release key, and
# leaves in build/release/ the APK, the bundle, their SHA-256 and the release notes taken from
# CHANGELOG.md. The bundle is what Google Play takes; the APK is for everyone else. The bundle is
# Google Play's build (the build type "play"): the same app less what Play asks to be declared
# before it publishes (app/src/play/AndroidManifest.xml), which the script checks is gone.
# Extra arguments go to Gradle.
set -euo pipefail

cd "$(dirname "$0")/.."

# The SHA-256 of the certificate of Kiri's release key. A release signed with any other key
# would not install over the ones before it, so the script stops.
RELEASE_CERT_SHA256="6c9e39655b51f18c3dc0c6e7007d83d12b8830301a3a67ee033da46edb0a00c3"

version="$(sed -n 's/^kiri\.version=//p' gradle.properties | tr -d '[:space:]')"
[ -n "$version" ] || { echo "kiri.version is missing from gradle.properties" >&2; exit 1; }
echo "Kiri $version"

# The key, from the environment or keystore.properties, as app/build.gradle.kts reads it. It is
# checked before the build, so a wrong key or password fails in seconds and says so.
store="${KIRI_RELEASE_STORE:-}"
password="${KIRI_RELEASE_PASSWORD:-}"
alias="${KIRI_RELEASE_ALIAS:-}"
if [ -f keystore.properties ]; then
    [ -n "$store" ] || store="$(sed -n 's/^storeFile=//p' keystore.properties)"
    [ -n "$password" ] || password="$(sed -n 's/^password=//p' keystore.properties)"
    [ -n "$alias" ] || alias="$(sed -n 's/^alias=//p' keystore.properties)"
fi
alias="${alias:-kiri}"
if [ -z "$store" ] || [ ! -f "$store" ] || [ -z "$password" ]; then
    echo "No release key: set KIRI_RELEASE_STORE and KIRI_RELEASE_PASSWORD, or keystore.properties" >&2
    exit 1
fi
if ! listing="$(keytool -list -v -keystore "$store" -storepass "$password" -alias "$alias" 2>&1)"; then
    echo "The release key could not be opened: $(printf '%s\n' "$listing" | grep -m 1 'keytool error')" >&2
    echo "The file has $(wc -c < "$store") bytes and SHA-256 $(sha256sum "$store" | cut -d' ' -f1)." >&2
    echo "Check that the keystore is whole (in base64, the whole line) and that the password is its own." >&2
    exit 1
fi
fingerprint="$(printf '%s\n' "$listing" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':' | tr 'A-F' 'a-f')"
if [ "$fingerprint" != "$RELEASE_CERT_SHA256" ]; then
    echo "The key in $store is not Kiri's release key (certificate $fingerprint)." >&2
    exit 1
fi

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

./gradlew -Pkiri.public=true :core:test :app:assembleRelease :app:bundlePlay "$@"

apk="app/build/outputs/apk/release/app-release.apk"
aab="app/build/outputs/bundle/play/app-play.aab"
[ -f "$apk" ] && [ -f "$aab" ] || {
    echo "No signed APK or bundle: set KIRI_RELEASE_STORE and KIRI_RELEASE_PASSWORD, or keystore.properties" >&2
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
if unzip -p "$apk" 'classes*.dex' | grep -a -q -E "$markers" \
    || unzip -p "$aab" 'base/dex/classes*.dex' | grep -a -q -E "$markers"; then
    echo "The release holds code from the personal folder." >&2
    exit 1
fi

# Google Play's build must leave out what Play asks to be declared. Its manifest is kept as a
# protocol buffer, in which every name is written out whole, so a search finds any that stayed.
manifest="$(unzip -p "$aab" base/manifest/AndroidManifest.xml | tr -d '\000')"
if [[ "$manifest" != *io.github.johankrs.kiri* ]]; then
    echo "The bundle has no manifest to check." >&2
    exit 1
fi
left=""
for name in MANAGE_EXTERNAL_STORAGE QUERY_ALL_PACKAGES READ_MEDIA_IMAGES BIND_ACCESSIBILITY_SERVICE KiriAccessibilityService; do
    if [[ "$manifest" == *"$name"* ]]; then
        left="$left $name"
    fi
done
if [ -n "$left" ]; then
    echo "The bundle for Google Play still asks for:$left" >&2
    exit 1
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
apksigner="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
[ -x "$apksigner" ] || { echo "apksigner not found in the Android SDK ($sdk)" >&2; exit 1; }
if ! signers="$("$apksigner" verify --print-certs "$apk" 2>&1)"; then
    echo "apksigner could not verify the APK:" >&2
    printf '%s\n' "$signers" >&2
    exit 1
fi
# Each version of apksigner words the line its own way ("Signer #1 certificate SHA-256 digest: ..."
# up to 36, "V2 Signer: certificate SHA-256 digest: ..." from 37), so any line with the digest
# counts, and every signer it lists must be Kiri's release key.
certs="$(printf '%s\n' "$signers" | sed -n 's/^.*certificate SHA-256 digest: //p' | sort -u)"
if [ "$certs" != "$RELEASE_CERT_SHA256" ]; then
    echo "The APK is not signed with Kiri's release key alone. apksigner says:" >&2
    printf '%s\n' "$signers" >&2
    exit 1
fi

# The bundle is signed the older way, like a jar: keytool reads its certificate.
if ! jarsigner -verify "$aab" 2>&1 | grep -q '^jar verified'; then
    echo "The bundle's signature does not verify." >&2
    exit 1
fi
bundle_cert="$(keytool -printcert -jarfile "$aab" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | sort -u | tr -d ':' | tr 'A-F' 'a-f')"
if [ "$bundle_cert" != "$RELEASE_CERT_SHA256" ]; then
    echo "The bundle is not signed with Kiri's release key alone (certificate $bundle_cert)." >&2
    exit 1
fi

name="kiri-launcher-$version.apk"
bundle="kiri-launcher-$version.aab"
cp "$apk" "$out/$name"
cp "$aab" "$out/$bundle"
(cd "$out" && sha256sum "$name" > "$name.sha256" && sha256sum "$bundle" > "$bundle.sha256")
echo "Ready in $out/:"
ls -l "$out"
