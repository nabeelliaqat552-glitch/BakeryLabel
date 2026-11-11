# Testing report

Date: 11 November 2026. Build environment had no Android SDK, emulator, phone or printer.

## Summary
- **Run and passed (22 automated tests):** date engine, barcode rules, unit conversion, printer command generation, template library, tokens, CSV.
- **Type-checked:** database entities, DAOs, repository, seed data, label model (against stand-in library stubs).
- **Not compiled:** the Android-dependent code (Compose screens, renderer, connections, Android Print). No syntax errors were found in a parser pass, but type errors are possible on first build.
- **Not run:** anything that needs a device or printer.

Run the automated tests with `./gradlew testDebugUnitTest` (also part of the GitHub workflow).

## The 20 required tests

| # | Test | Status | Evidence / how to do it |
|---|---|---|---|
| 1 | Create a business and add products | Not run on device | Onboarding › Create business; Products › Add product |
| 2 | Multiple businesses, data separation | Not run on device | Every query filters by `business_id`; create two businesses and compare lists |
| 3 | English label | Not run on device | Quick Print, language English |
| 4 | Urdu label | Not run on device | Quick Print, language اردو |
| 5 | Bilingual label | Partly: token and language logic tested | `tokensResolveAndHide` |
| 6 | Automatic expiry | **Passed** | 10-11-2026 + 5 days = 15-11-2026; month, year and leap-year cases |
| 7 | Manual expiry override | **Passed** (logic) | `manualExpiryIsNeverRecalculated` |
| 8 | Generate and scan a barcode | Partly: check digits and validation passed | Scan needs a printed label |
| 9 | Custom label size | Partly: ready sizes tested | Label Sizes › Add custom size |
| 10 | Edit a ready-made template | Partly: templates verified as editable element layouts, none overlapping or outside the label | Open a template in the designer |
| 11 | Custom design | Not run on device | Label Designer |
| 12 | Print by each connection | Not run | Needs printers |
| 13 | Physical label dimensions | Partly: 50 mm = 400 dots at 203 DPI, 591 at 300 | Measure the test label with a ruler |
| 14 | Batch printing | Not run on device | Batch Printing |
| 15 | Offline operation | Not run on device | Airplane mode; no feature needs internet |
| 16 | Backup and restore | Partly: CSV round trip with Urdu passed | Backup, then restore as new business |
| 17 | After device restart | Not run on device | Interrupted jobs are marked Failed on next start |
| 18 | Database migration | Not applicable yet (schema version 1) | Schema export is enabled; destructive migration is never used |
| 19 | Unsupported printer handling | Not run on device | Wrong language or a label wider than the head gives a plain message |
| 20 | Printed Urdu and barcode readability | Not run | Needs a printer; use the test label |

Honest reading: items 6 and 7 are verified; 5, 8, 9, 10, 13 and 16 are verified in part; the rest must be done on a phone with a printer before the app is called finished.
