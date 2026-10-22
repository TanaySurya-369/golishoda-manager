# Golishoda Manager

Golishoda Manager is an **offline-first native Kotlin Android app** for recording soda-bottle shop deliveries, empty-bottle returns, payments, balances, stock, and daily reports. App ID: `com.golishoda.manager`.

## Features

- Five bottom tabs: Home, Shops, Add Entry, Daily Supply, and Reports; Settings is available as an additional destination.
- Search shops; add shops and archive/restore records without deleting history.
- Record deliveries, returns, and payments against dated, price-snapshotted ledger events.
- Show current bottle stock, outstanding due, and advance credit separately.
- Explore daily activity and date-range collection/value summaries.
- Export the shop list, daily activity, ledger, outstanding, and collection reports as properly escaped CSV via Android's save-file picker.
- Create versioned JSON backups and restore after a replacement confirmation. Photos are not included.
- Work offline; no login, advertising, analytics, Internet permission, or server synchronization.

## Technology and structure

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room/SQLite, Coroutines/Flow.
- MVVM-friendly screen state and repository-backed persistence; business arithmetic lives in a separate testable domain rules object.
- `app/src/main/java/com/golishoda/manager/data/`: Room entities, DAO, database, repository.
- `app/src/main/java/com/golishoda/manager/domain/`: stock, ledger, money, and report rules.
- `app/src/main/java/com/golishoda/manager/ui/`: Compose destinations and theme.
- `app/src/main/java/com/golishoda/manager/backup/` and `export/`: JSON backup and CSV escaping.
- `app/src/test/`: business-rule and CSV tests.

## Requirements and local development

- Android Studio or Android SDK command-line tools with Android platform 35 and build tools 35.0.0.
- JDK 17. Gradle 8.9 is downloaded by the committed wrapper.
- Open the repository root in Android Studio or VS Code with Android/Kotlin support. Gradle sync downloads dependencies.
- Set `ANDROID_HOME` or configure the SDK path in Android Studio; a local `local.properties` is ignored by Git.

Build a debug APK on Windows:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest assembleDebug
```

On macOS/Linux:

```sh
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Run JVM unit tests with `./gradlew --no-daemon testDebugUnitTest`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Business rules

- Sodas In means filled bottles delivered to a shop; Sodas Out means empty bottles returned.
- Closing stock is opening stock + deliveries − returns. Returns are rejected if the chronological stock balance would fall below zero; a later delivery does not conceal an earlier stock discrepancy.
- All money is integer paise. A transaction saves its unit price so editing the shop's future default cannot change history.
- Return credit uses that transaction's recorded unit price. Signed balance = deliveries × price − returns × price − payments; positive means due, negative means advance credit.
- Payments represent money received, not delivery value or bottle returns.
- Only a dated transaction with a positive delivery counts toward shops supplied.
- Dates are stored as ISO `yyyy-MM-dd`; creation timestamps use epoch milliseconds and the device local date is used by default.

## Push to GitHub and obtain the APK

Create a new empty repository on GitHub, then from this project folder add its URL as the `origin` remote and push the `main` branch. For example:

```sh
git init
git add .
git commit -m "Build Golishoda Manager Android app"
git branch -M main
git remote add origin https://github.com/YOUR-ACCOUNT/YOUR-REPOSITORY.git
git push -u origin main
```

The GitHub Actions workflow runs on pushes and pull requests targeting `main`, and can also be started from the **Actions** tab using **Run workflow**. It runs unit tests and builds a debug APK.

To download it:

1. Open the GitHub repository.
2. Select the **Actions** tab.
3. Open the latest successful **Build Android APK** workflow run.
4. Find the Artifacts section.
5. Download `Golishoda-Manager-Debug-APK`.
6. Extract the downloaded artifact ZIP.
7. Locate `app-debug.apk`.
8. Transfer the APK to the Android phone if necessary.
9. Open the APK and follow Android's installation prompts. If Android blocks installation, enable the appropriate setting to allow installation from the app being used, then disable that permission again when it is no longer needed.

A debug APK is for development and personal testing. A production release should use a properly managed release-signing key stored outside source control (for example, protected CI secrets or a secure signing system); never commit keystores or passwords.

## Back up and restore

Open Settings → **Create data backup** and choose a file location. To restore, select **Restore from backup**, choose the versioned JSON file, then explicitly confirm replacement. A malformed or unsupported backup is rejected before the database is changed. Photos are not embedded, and document/image permissions or moved files can make photo references unavailable. Uninstalling the app or clearing app data may erase local records; keep backups elsewhere.

## Limitations

The initial version does not implement photo capture/picking, a business-name/default-price settings editor, PDF export, date picker dialogs, transaction edits/reversal UI, archived account balance filters, or shop-ledger-specific date filters. Transaction history is retained, so ordinary UI does not delete its shop records. Corrections currently require a documented manual ledger correction policy; do not edit the underlying database directly. GitHub Actions uploads a debug artifact only after a successful remote workflow run; no remote run is claimed by this project setup.
