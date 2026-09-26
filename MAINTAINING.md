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
pushes to main. Lint runs as an advisory check and its report is retained with
the test reports; inspect findings before a release. The workflow does not
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
