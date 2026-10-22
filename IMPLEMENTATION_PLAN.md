# Implementation Plan — Golishoda Manager

- [x] Inspect repository and local Android/Java/Gradle tooling.
- [ ] Scaffold a native Kotlin/Compose Android application, Gradle version catalog, manifest, and wrapper.
- [ ] Implement Room persistence and testable exact-paise ledger, stock, and reporting rules.
- [ ] Implement the five Compose destinations and database-backed shop/entry workflows.
- [ ] Add local JSON backup/restore, CSV export, validation, and automated tests.
- [ ] Configure GitHub Actions and document setup, business rules, usage, and APK downloads.
- [ ] Run every available build/test/static check and record actual outcomes and blockers.

## Inspection notes

- Workspace was empty.
- Java 21 is available; Java 17 was not detected as the active runtime.
- No Gradle executable, Android SDK environment variables, common Android SDK installation, or cached Gradle wrapper/build dependencies were found during inspection.
- Target: Android API 35, minimum API 26, Kotlin + Jetpack Compose Material 3, Room, offline-only.

## Implementation decisions

- Store transaction dates as ISO `yyyy-MM-dd` local dates; creation/update timestamps as epoch milliseconds.
- Store money as integer paise. Positive ledger balances mean due; negative balances are shown as nonnegative advance credit.
- Returned bottles are credited at the transaction's persisted unit price.
- Returns exceeding the recorded stock are rejected with a clear validation message; stock is never clamped.
- Keep transactions as auditable ledger events; corrections are represented as additional signed adjustment events rather than destructive historical edits.
- Backups are versioned JSON and require explicit replacement confirmation; shop photos are not embedded.
- CSV is the supported first-version export format; PDF export is not included.
