# Property Ledger (دفتر العقارات)

Private, 100% offline Android app for managing buildings, rented units, tenants, rent, debts,
expenses, maintenance and rent collected for other family members.

* Kotlin, Jetpack Compose (Material 3), Room/SQLite. No backend, no cloud, no analytics.
* The manifest declares **no INTERNET permission**.
* Arabic (right-to-left) is the main language; English can be chosen in Settings. Night mode in Settings.
* Data lives only on the phone. Backups are ZIP files shared through the normal Android share sheet.

## Install on the phone

Copy `app/build/outputs/apk/release/app-release.apk` to the phone (USB, Bluetooth, WhatsApp to yourself...),
open it and allow "install from this source" when Android asks.

## Build

```
set JAVA_HOME=C:\Users\yakza\android-tools\jdk
gradlew.bat :app:assembleRelease
```

Release signing uses `keystore/release.jks` + `keystore.properties` (both git-ignored).
**Keep a copy of both safely.** A future update only installs over this version if it is signed with the same key.

## Backup files

* `property_data_backup_<date>.zip` - `backup/database.json`, no photos.
* `property_full_backup_<date>.zip` - `backup/database.json` + `backup/photos/{properties,units,tenants,maintenance,expenses}/`.
* `property_csv_<date>.zip` - one CSV per table (opens in Excel).

Restore replaces the local data. A safety copy of the previous data is written to the app's private
`safety/` folder first.

## Data model notes

* Every record has a UUID id. Visible numbers and names are ordinary editable fields.
* Money is stored as individual transactions: rent obligations, payments, payment allocations, debts,
  handovers, expenses. A payment stores its own "collected for" person, so changing a unit's default
  never rewrites history.
* Buildings, floors, units, people and tenants are archived, not deleted. Payments are voided, not deleted.
* Units already carry `planX/planY/planW/planH/planRotation/planShape/planLabel` and buildings
  `posX/posY/posZ`, reserved for a future floor-plan / 3D view.
* Schema changes must add a Room `Migration` (see `data/Database.kt`). The database is never recreated.

## Tests

`gradlew.bat :app:testDebugUnitTest` runs the money-logic tests (partial payments, old debts,
allocation order, collected-for history, handovers, net income).
