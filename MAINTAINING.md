# Maintaining Bifrost

## Source and provenance

The active repository is [KuriGohan-Kamehameha/Bifrost](https://github.com/KuriGohan-Kamehameha/Bifrost).
Pollux / MoonBench is the original creator. Keep existing authorship and the
unmodified GPLv3 `LICENSE` when distributing source or binaries.

The takeover baseline is Pollux's final published stable tag `1.3.1`, released
2026-08-21, at commit `1baddf1644ff0d7edd1bd0f4ba02f7eb6c8e3cfa`.
On 2026-09-26 upstream `main` pointed to that same commit. The previous fork
`main`, `308404f056e921d1400ad2e21fc22b179953c612`, was its ancestor: all 97
upstream commits were incorporated by fast-forward, retaining both histories.
Unreleased upstream feature/fix branches are not included in this baseline.

## Build and verify

Use JDK 17, the Android SDK (platform 36 and build-tools 36.0.0), and the checked-in
Gradle wrapper. Set `ANDROID_HOME` or an ignored `local.properties` with `sdk.dir`.

```sh
./gradlew --no-daemon --console=plain testDebugUnitTest assembleDebug assembleRelease
./gradlew --no-daemon --console=plain lintDebug
```

The Android checks workflow runs unit tests and both builds on pull requests and
pushes to main and maintenance branches. Lint errors fail the workflow; its
report is retained with the test reports. Review remaining warnings before a release. The workflow does not
publish APKs or use release signing credentials.
The debug package is `com.moonbench.bifrost.debug`; release remains
`com.moonbench.bifrost`. Keep the release application ID and external API intent
names stable so integrations continue to work.

## Signing and releases

Release signing is optional at build time. Without `keystore.properties`,
`assembleRelease` produces an **unsigned** APK. A successful build alone is not a
publishable release. Keep keystores and credentials outside Git; local signing
uses the ignored `keystore.properties` keys `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword`.

Before publishing:

1. Run checks and inspect lint findings. Test the release build on real hardware:
   first launch, notification permissions, background start/stop, sleep/wake,
   Ambient capture, Audio Reactive, old presets, battery alerts, and plugins.
2. Choose and protect the maintained distribution's signing key. Compare its
   certificate fingerprint with the previously distributed APK using
   `apksigner verify --print-certs`. Do not assume possession of Pollux's key or
   compatibility with older fork betas. Different certificates normally require
   export/uninstall/reinstall/import; uninstalling deletes app data.
3. Increase `versionCode` beyond 16 **and every previous APK in the maintained
   signing lineage**. Set a distinct `versionName`; the imported source retains
   upstream 1.3.1/16 until a new release is prepared.
4. Tag the exact tested source, publish the signed APK and SHA-256 checksum, and
   state signing compatibility, migration steps, changes, and known limitations.
   Supply corresponding source as required by GPLv3. Never relabel or overwrite
   Pollux's release tags.
5. Link users and Obtainium to this fork's releases. Source integration by itself
   does not create a release or make an older beta the new recommended download.

### Original 1.3.1 reference APK

The upstream asset `bifrost-1.3.1.apk` was downloaded and its APK signature
verified during the handoff. These identify the original binary, not a new fork
build or proof that this fork possesses its private signing key:

- APK SHA-256: `01734989eec369a0b7a1c8885f88308c96f1242fd7bd50c8f025f5dba7d906cd`
- Signer certificate SHA-256: `d15fe2c3a3980f08c3aa5badce6d9e36c662a9b9b7cd4e6e7b254c56aa3a4e5d`
- Signer subject: `O=MoonBench`

## Plugin catalogue

The maintained catalogue lives on this repository's **`plugin-catalog`** branch,
imported from upstream commit `5e73a1ae8953f70ec257367fcbbd7f55fbe01a5e`.
The existing bundles, versions, checksums, and author credits are preserved.
The default in new builds points there; the bundle URLs also use this fork.

Publish catalogue contents **before** an app that refers to them. Check every
bundle URL and SHA-256. New plugins/updates belong on `plugin-catalog`, not `main`.
Keep old versioned bundles available. Existing user-entered catalogue URLs remain
untouched; users explicitly pointing to Pollux's catalogue can change the source
in the Plugin Store to:

```
https://raw.githubusercontent.com/KuriGohan-Kamehameha/Bifrost/plugin-catalog/catalog.json
```

## Handoff validation (2026-09-26)

[Integration CI run](https://github.com/KuriGohan-Kamehameha/Bifrost/actions/runs/36278878810)
compiled debug and minified unsigned release builds and passed **60 tests in 12
suites**, with no failures, errors, or skipped tests. The live maintained plugin
catalogue and its bundle were fetched and their SHA-256 verified.

## Maintenance hardening (2026-09-26)

The capture service now owns the MediaProjection session and one reusable virtual
display. Consent is consumed once, parameter changes reconfigure the running
service, and revocation invalidates the session. Audio capture checks and requests
RECORD_AUDIO before startup; denied permission remains a recoverable user action.
These follow Android's [MediaProjection session rules](https://developer.android.com/media/grow/media-projection)
and [playback capture requirements](https://developer.android.com/media/platform/av-capture).
The Android 13 quick-settings compatibility call has a narrowly scoped lint
suppression; Android 14+ continues to use PendingIntent.

Plugin catalogue IDs and metadata are validated before use. Downloads require
HTTPS (including redirects), cap catalogue responses at 512 KiB and bundles at
8 MiB, and use random temporary filenames. ZIP imports cap each expanded entry
at 8 MiB. Preset/plugin bundles allow 32 MiB total and 256 entries; full backups
allow 64 MiB total and 512 entries. Unknown entries and directory payloads count
toward limits. Duplicate or unsafe paths fail before archive contents are applied.
Oversized legitimate backups must be split or their limits deliberately revisited.
Plugin updates reject duplicate names and conflicts with user/other-plugin presets,
and retain app mappings to presets that survive the update.

[Final hardening CI run](https://github.com/KuriGohan-Kamehameha/Bifrost/actions/runs/36280537500)
at source commit `3fcdfaa7c104366bbbffba57692d97e8c4a1671b` passed **88 tests
in 18 suites** with no failures, errors, or skipped tests, debug and minified
unsigned release builds, and the now-required lint check: **zero errors, 400
warnings**. Most warnings concern UI text, text sizes, Kotlin conveniences,
unused resources, and dependency versions. The subsequent validation-record
commit changes only this document.

Before a binary release, test permission denial/retry, consent across rotation,
repeated capture preset/settings changes, system capture revocation, stop/start,
and audio routing on actual Thor hardware. CI does not qualify hardware behavior.
No new APK has been installed, signed, or published by this source maintenance.
