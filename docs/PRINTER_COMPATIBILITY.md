# Printer compatibility matrix

**No physical printer has been tested with this build.** The table says what is implemented and how it was checked. A printer counts as working only after a test label is confirmed on it; please fill in the last table as you test.

## What is implemented

| Area | Item | Implemented | Checked so far |
|---|---|---|---|
| Language | TSPL (SIZE, GAP, DENSITY, SPEED, BITMAP, PRINT) | Yes | Command bytes unit-tested |
| Language | ESC/POS (GS v 0 raster, banded, ESC J feed) | Yes | Command bytes unit-tested |
| Language | ZPL II (^GFA, ^PW, ^LL, ^PQ, ~SD) | Yes | Command bytes unit-tested |
| Language | CPCL (EG, FORM, PRINT) | Yes | Command bytes unit-tested |
| Connection | Bluetooth Classic (SPP) | Yes | Not run on a device |
| Connection | Bluetooth LE (first writable characteristic) | Yes, experimental | Not run on a device |
| Connection | USB OTG (printer class / bulk OUT) | Yes | Not run on a device |
| Connection | Wi-Fi / LAN raw TCP (9100) | Yes | Not run on a device |
| Output | Android Print Framework, exact-size pages | Yes | Not run on a device |
| Output | PDF and PNG export (300 DPI) | Yes | Not run on a device |

All languages send the label as an image, so Urdu shaping and fonts do not depend on the printer.

## Not supported
- Printers that only work through a maker's own app protocol (many "phone-only" mini label printers).
- EPL, DPL, SBPL, Star line mode, Brother raster / P-touch. Use Android Print with the maker's plugin if one exists.
- Reading status back from the printer (paper out, cover open). The app therefore asks you to confirm each job.
- 600 DPI is selectable but only useful on printers that have it.

## Your test results

| Printer model | Connection | Language | DPI | Label size | Size correct | Urdu correct | Barcode scans | Notes |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |
