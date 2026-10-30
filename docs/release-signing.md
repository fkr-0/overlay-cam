# Release signing

Official release APKs are signed with a permanent key. Android refuses to upgrade an installed
app when the signing certificate changes, so **losing or replacing this key strands every
installed user**.

Overlay Cam uses the same release key as Android DJ Tools (alias `androidjtools`, certificate
subject `CN=Android DJ Tools`, SHA-256
`1cc66aec4e65bae015bec45a37dff163e2c5fad955abae23a4d9cbca88201ca6`). Sharing one key means a leak
or rotation affects both apps, and the certificate subject shows the other project's name. Rotating
it later requires APK Signature Scheme v3 key rotation for both apps.

## Build behavior

| Build | Signed with | Official |
| --- | --- | --- |
| `make release-apk` with the key configured | release key | only if the fingerprint matches the pin |
| `assembleRelease` without a key | Android debug key | no, never published |
| `assembleDebug` | Android debug key | no |

`app/build.gradle.kts` reads `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` from the environment, or `storeFile`,
`storePassword`, `keyAlias`, `keyPassword` from a git-ignored `keystore.properties`.
A partial configuration fails the build. `-PrequireReleaseSigning=true` (used by
`make release-apk`) fails the build when no key is configured.

## Key custody

The key lives in an encrypted backup, never in this repository (`*.p12`, `*.jks`, `*.keystore` and
`keystore.properties` are git-ignored). Keep at least two independent encrypted backups. Never paste
the key or its password into a chat, issue, PR or log. The SHA-256 fingerprint is public.

## Build and verify

```sh
export ANDROID_KEYSTORE_DIR=<directory containing the decrypted keystore>
export ANDROID_KEYSTORE_NAME=release.p12 ANDROID_KEY_ALIAS=androidjtools
read -rs ANDROID_KEYSTORE_PASSWORD && export ANDROID_KEYSTORE_PASSWORD
export ANDROID_KEY_PASSWORD=$ANDROID_KEYSTORE_PASSWORD
make release-apk
python3 scripts/verify_release_signature.py app/build/outputs/apk/release/app-release.apk \
  --expected-sha256 1cc66aec4e65bae015bec45a37dff163e2c5fad955abae23a4d9cbca88201ca6
```

`scripts/verify_release_signature.py` runs `apksigner verify`, requires exactly one signer and a
v2/v3 signature, rejects the Android debug certificate, and compares the certificate SHA-256 with
the pinned fingerprint. Test it with `cd scripts && python3 -m unittest test_verify_release_signature`.

## CI release

`.github/workflows/release.yml` runs on `v*` tag pushes only, so forks and pull requests never see
the secrets. It requires the tag to equal `v<versionName>` and a `## [<version>] -` CHANGELOG
heading, runs the unit tests, builds with `-PrequireReleaseSigning=true`, verifies the certificate
against the pin, and creates the GitHub Release (refusing to overwrite an existing one).

Repository secrets: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.p12`), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Repository variable: `ANDROID_RELEASE_CERT_SHA256`.
Set secrets with `gh secret set NAME` reading from stdin so values stay out of history.

## Upgrading from earlier builds

Earlier GitHub and sideloaded builds (0.1.1 through 0.4.0) are debug-signed. They cannot be
upgraded in place to a release-signed build, and the application ID changed from
`com.example.overlaycam` to `dev.fkr.overlaycam`: install the signed release fresh and uninstall the
old app when ready.
