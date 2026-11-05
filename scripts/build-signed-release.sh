#!/usr/bin/env bash
# Builds and verifies a Pocket release signed with its permanent key on Ubuntu.
#
#   scripts/build-signed-release.sh --setup <path-to-keystore>   Store the key on this machine, once.
#   scripts/build-signed-release.sh                              Build and verify a signed release.
#
# The password stays in the login keyring and reaches only keytool and Gradle, through their environment.
set -euo pipefail
export LC_ALL=C

# The published release certificate. Any other key would force people to uninstall and lose their data.
readonly EXPECTED_CERT="d9d3555d1c0173e45141ff7d63c01a377c2cc7a68d0193a0aad24751eaef5fd4"
readonly KEY_ALIAS="pocket"
readonly KEYRING_ATTRIBUTES=(service pocket-release account signing)
readonly KEYSTORE="${POCKET_KEYSTORE:-$HOME/PocketKeys/pocket-release.jks}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly repo_root
readonly GRADLE_RUN="$repo_root/.agents/skills/gradle-run/scripts/gradle_run.py"
readonly RELEASE_APK="$repo_root/app/build/outputs/apk/release/app-release.apk"

fail() {
    echo "error: $*" >&2
    exit 1
}

resolve_java() {
    if [[ -z "${JAVA_HOME:-}" ]]; then
        local candidate
        for candidate in "$HOME"/.gradle/jdks/*17*/ /usr/lib/jvm/java-17-openjdk-amd64/; do
            if [[ -x "${candidate}bin/keytool" ]]; then
                export JAVA_HOME="${candidate%/}"
                break
            fi
        done
    fi
    [[ -x "${JAVA_HOME:-}/bin/keytool" ]] || fail "JDK 17 not found. Set JAVA_HOME."
}

# Prints the SHA-256 of the signing certificate, reading the store password from POCKET_RELEASE_STORE_PASSWORD.
keystore_certificate() {
    "$JAVA_HOME/bin/keytool" -list -v -keystore "$1" -alias "$KEY_ALIAS" -storepass:env POCKET_RELEASE_STORE_PASSWORD 2>/dev/null |
        sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]'
}

setup() {
    local source_keystore="$1"
    [[ -f "$source_keystore" ]] || fail "Keystore not found: $source_keystore"
    [[ -t 0 ]] || fail "--setup asks for the password, so run it in your own terminal."
    command -v secret-tool >/dev/null || fail "secret-tool not found. Run: sudo apt-get install libsecret-tools"
    if [[ -e "$KEYSTORE" && ! "$source_keystore" -ef "$KEYSTORE" ]]; then
        fail "$KEYSTORE already exists. Compare the two files and move one away before running setup."
    fi

    local password actual
    read -rsp "Keystore password: " password
    echo
    actual="$(POCKET_RELEASE_STORE_PASSWORD="$password" keystore_certificate "$source_keystore")" || true
    [[ -n "$actual" ]] || fail "Could not read the '$KEY_ALIAS' key. Check the keystore and password."
    [[ "$actual" == "$EXPECTED_CERT" ]] || fail "This keystore holds certificate $actual, not Pocket's $EXPECTED_CERT."

    if [[ ! "$source_keystore" -ef "$KEYSTORE" ]]; then
        mkdir -p "$(dirname "$KEYSTORE")"
        chmod 700 "$(dirname "$KEYSTORE")"
        install -m 600 "$source_keystore" "$KEYSTORE"
    fi
    chmod 600 "$KEYSTORE"
    printf '%s' "$password" | secret-tool store --label="Pocket release signing" "${KEYRING_ATTRIBUTES[@]}"
    echo "Stored: keystore at $KEYSTORE, password in the login keyring."
    echo "Delete any other copy of the keystore you no longer need, and keep your password manager backup."
}

# Confirms the real token is compiled in, without ever printing it.
verify_token_in_apk() {
    python3 -I - "$RELEASE_APK" <<'PYTHON'
import os, pathlib, sys, zipfile

home = pathlib.Path(os.environ.get("GRADLE_USER_HOME") or pathlib.Path.home() / ".gradle")
properties = home / "gradle.properties"
token = ""
if properties.exists():
    for line in properties.read_text().splitlines():
        if line.startswith("POCKET_BANXICO_TOKEN="):
            token = line.split("=", 1)[1].strip()
token = token or os.environ.get("POCKET_BANXICO_TOKEN", "")
if not token:
    sys.exit("error: POCKET_BANXICO_TOKEN is not set.")
apk = zipfile.ZipFile(sys.argv[1])
dexes = [name for name in apk.namelist() if name.endswith(".dex")]
if not any(token.encode() in apk.read(name) for name in dexes):
    sys.exit("error: the built APK does not contain the Banxico token.")
PYTHON
}

build() {
    command -v secret-tool >/dev/null || fail "secret-tool not found. Run: sudo apt-get install libsecret-tools"
    [[ -f "$KEYSTORE" ]] || fail "Keystore not found: $KEYSTORE. Run: $0 --setup <path-to-keystore>"
    [[ "$(stat -c %a "$KEYSTORE")" == "600" ]] || fail "$KEYSTORE must have mode 600."

    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    local build_tools
    build_tools="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
    [[ -n "$build_tools" ]] || fail "No Android build tools under $ANDROID_HOME."

    local password actual
    password="$(secret-tool lookup "${KEYRING_ATTRIBUTES[@]}")" ||
        fail "No password in the keyring, or it is locked. Run: $0 --setup <path-to-keystore>"
    actual="$(POCKET_RELEASE_STORE_PASSWORD="$password" keystore_certificate "$KEYSTORE")" || true
    [[ "$actual" == "$EXPECTED_CERT" ]] ||
        fail "$KEYSTORE holds certificate '${actual:-unreadable}', not Pocket's $EXPECTED_CERT."

    cd "$repo_root"
    if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
        echo "warning: tracked files have uncommitted changes. Publish only a build of a merged commit." >&2
    fi

    # A stale APK must never pass the checks below.
    rm -f "$RELEASE_APK"

    local workflow status=0
    workflow="$(python3 "$GRADLE_RUN" create | python3 -I -c 'import json, sys; print(json.load(sys.stdin)["workflow"])')"
    # No daemon or configuration cache: neither may keep the signing values after this build.
    POCKET_RELEASE_STORE_FILE="$KEYSTORE" \
        POCKET_RELEASE_STORE_PASSWORD="$password" \
        POCKET_RELEASE_KEY_ALIAS="$KEY_ALIAS" \
        POCKET_RELEASE_KEY_PASSWORD="$password" \
        python3 "$GRADLE_RUN" run --workflow "$workflow" --scope targeted \
        --question "Does the signed release assemble?" -- \
        ./gradlew :app:assembleRelease --no-daemon --no-configuration-cache || status=$?
    python3 "$GRADLE_RUN" finish --workflow "$workflow" >/dev/null
    password=""
    [[ "$status" == 0 ]] || fail "The release build failed with exit status $status."
    [[ -f "$RELEASE_APK" ]] || fail "The build produced no signed APK."

    local signer
    signer="$("$build_tools/apksigner" verify --print-certs "$RELEASE_APK" 2>/dev/null |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p')" || fail "APK signature verification failed."
    [[ "$signer" == "$EXPECTED_CERT" ]] || fail "The APK is signed by '$signer', not Pocket's $EXPECTED_CERT."
    verify_token_in_apk

    "$build_tools/aapt2" dump badging "$RELEASE_APK" | sed -n 1p
    echo "Commit: $(git rev-parse HEAD)"
    echo "Certificate: $signer"
    sha256sum "$RELEASE_APK"
    echo "Signature, certificate and Banxico token verified."
}

resolve_java
case "${1:-}" in
    --setup)
        [[ $# == 2 ]] || fail "Usage: $0 --setup <path-to-keystore>"
        setup "$2"
        ;;
    "")
        build
        ;;
    *)
        fail "Usage: $0 [--setup <path-to-keystore>]"
        ;;
esac
