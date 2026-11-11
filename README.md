# FoodLabel Studio

Professional food label designer and printing app for Android phones (Kotlin, Jetpack Compose, Room). Works offline, no account.

## Status of this delivery (read first)

- Full Android Studio project and Kotlin source: **included**.
- APK file: **not included yet.** The environment this was written in had no access to the Android SDK or Gradle downloads, so the app could not be compiled there. The project builds the APK through the included GitHub Actions workflow or in Android Studio (steps below).
- Because the app has not been compiled yet, the first build may report small errors that need fixing. The pure logic (dates, barcode check digits, printer command generation, templates, CSV) was compiled and tested: 22 tests pass. See `docs/TESTING_REPORT.md`.
- Nothing has been printed on a real printer yet. See `docs/PRINTER_COMPATIBILITY.md`.

## Get the APK without a computer (GitHub)

1. Create a free GitHub account and a new repository.
2. Upload the contents of this folder (use "Add file › Upload files", or the GitHub mobile site in desktop mode).
3. Open the **Actions** tab. The workflow "Build APK" starts on its own (or tap "Run workflow").
4. When it finishes (about 5–8 minutes), open the run and download **FoodLabelStudio-APKs**. It holds the debug APK and the release APK.
5. On the phone, open the APK and allow "Install unknown apps" when asked.

## Build in Android Studio

Open the folder in Android Studio (Ladybug or newer), let it sync, then **Build › Build APK(s)**.
Command line: `./gradlew assembleDebug assembleRelease` (JDK 17, Android SDK 35).

Release signing: set `FLS_KEYSTORE_FILE`, `FLS_KEYSTORE_PASSWORD`, `FLS_KEY_ALIAS`, `FLS_KEY_PASSWORD` (or GitHub secrets, with the keystore as `FLS_KEYSTORE_BASE64`). Without them the release APK is signed with the debug key so it still installs, but it is not suitable for the Play Store.

## Code map

All code is in one Gradle module (`app`) with one package per area. This replaces the multi-module layout in the brief so the first build is simpler; the packages map one-to-one and can be split into modules later.

| Package | What it holds |
|---|---|
| `core` | mm / inch / dot / point conversion |
| `data.db` | Room entities, DAOs, database (every business table carries `business_id`) |
| `data.repo` | Repository, business bundles for backup and duplicate |
| `data.seed` | Starter categories and products, label sizes, printer profiles, 48 templates |
| `dates` | Expiry calculation and date formats |
| `barcode` | Validation, check digits, internal codes, ZXing encoding |
| `label` | Label model, tokens such as `{name_ur}`, DPI-accurate renderer |
| `printing.driver` | TSPL, ESC/POS, ZPL, CPCL |
| `printing.connection` | Bluetooth Classic, BLE, USB OTG, TCP, discovery |
| `printing` | Print queue, PDF / PNG / Android Print, test label |
| `backup` | ZIP / JSON backup, restore, CSV import and export |
| `ui` | Compose screens, navigation, theme |

Dependency injection is a small hand-written container (`AppContainer`) instead of Hilt.

## Known gaps against the brief

- App menus are in English only. Labels, product and category names fully support Urdu, English or both. An Urdu translation of the menus is the next step.
- Display type uses the phone's serif font instead of a bundled premium font (no font files could be downloaded).
- Urdu on labels uses the phone's own Urdu font (Nastaliq on phones that have Noto Nastaliq Urdu, Naskh otherwise). Letters join correctly on both.
- Printing runs in the app process, not a WorkManager job. Keep the app open until a job finishes.
- "Automatic protocol detection" is not offered: thermal printers do not reliably report their language. The user picks it and confirms with a test label.
- Product photos are stored but not placed on labels (the logo is).
