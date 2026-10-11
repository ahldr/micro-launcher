+++
  weight = 50
+++


# Building from Source

## Using the command line

Install JDK 17 and the Android SDK.
Make sure that `JAVA_HOME` and `ANDROID_HOME` are set correctly.

```bash
git clone https://github.com/jrpie/Launcher
cd Launcher

./gradlew assembleDefaultRelease
```

This will create an APK file at `app/build/outputs/apk/default/release/app-default-release-unsigned.apk`.

Note that you need to sign it:

```bash
apksigner sign --ks "$YOUR_KEYSTORE" \
    --ks-key-alias "$YOUR_ALIAS" \
    --ks-pass="pass:$YOUR_PASSWORD" \
    --key-pass="pass:$YOUR_PASSWORD" \
    --alignment-preserved \
    --v1-signing-enabled=true \
    --v2-signing-enabled=true \
    --v3-signing-enabled=true \
    --v4-signing-enabled=true \
    app-default-release-unsigned.apk
```

See [this guide](https://developer.android.com/build/building-cmdline)
for further instructions.

## Using Android Studio

Install [Android Studio](https://developer.android.com/studio), import this project and build it.

See [this guide](https://developer.android.com/studio/run)
for further instructions.

## CI Pipeline

The [CI pipeline](https://github.com/jrpie/Launcher/actions) automatically creates debug builds.

{{% hint warning %}}
Note: These builds are not signed with a release key.
They are built in debug mode and are only suitable for testing.
{{% /hint %}}

### Release builds

The `Release` workflow (`.github/workflows/release.yml`) builds signed release APKs for the `default` and
`accrescent` flavors. Pushing a tag `vX.Y.Z` (it has to match `versionName` in `app/build.gradle.kts`) creates a
draft GitHub release containing the APKs and their SHA-256 checksums. The workflow can also be started manually
to test the release build; the APKs are then only attached to the workflow run.

Signing needs these repository secrets:

| Secret | Content |
|--------|---------|
| `RELEASE_KEYSTORE_BASE64` | the release keystore, encoded with `base64 -w0 release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

Tag builds fail if the secrets are missing. Manual runs without them produce APKs signed with the debug key.
Locally, the same signing configuration can be used by setting the environment variables
`RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.
