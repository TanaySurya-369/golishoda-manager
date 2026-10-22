# Build and Release

## Local prerequisites

- JDK 17, Android SDK platform 35, build tools 35.0.0.
- Gradle Wrapper 8.9 is committed and downloads Gradle on its first invocation.
- Configure Android Studio SDK settings or `ANDROID_HOME`/`ANDROID_SDK_ROOT`.

## Build and test

Windows: `gradlew.bat --no-daemon testDebugUnitTest assembleDebug`

macOS/Linux: `./gradlew --no-daemon testDebugUnitTest assembleDebug`

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub workflow

`.github/workflows/build-apk.yml` runs on pushes to `main`, pull requests targeting `main`, and manual dispatch. It uses Ubuntu 24.04, Temurin JDK 17, Android SDK 35, runs unit tests, builds the debug APK, then uploads the actual artifact as `Golishoda-Manager-Debug-APK` for 14 days. The workflow needs no signing secrets. A local run is not a GitHub Actions run; verify the Actions run status on GitHub before claiming remote success.

## Release signing

The debug APK is suitable for development/personal testing. For distribution, create a release build variant and sign it with an owner-controlled release key kept out of the repository. Store signing credentials only in a secure keystore/secrets manager, rotate and back up keys securely, and never commit keystore files, passwords, or signing properties.
