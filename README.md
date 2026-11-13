# Bakery Label Studio — Android app

Bakery items, labels, barcodes, scanner and printing — Urdu + English.

## Download
Open **Releases** (right side of the repo page) → latest version → download
`BakeryLabelStudio.apk` on the phone → install (allow "Install unknown apps" once).

## Features
- Categories (Dairy, Biscuits, Cakes, Fast Food, Bread, Pastries + your own)
- Items with photo, English + Urdu name, price, weight, barcode, batch, shelf life, veg/non-veg
- Label designer: 4 templates, any size, colours, choose every field (Urdu name, MFG/EXP,
  phone, batch, Halal and Veg marks)
- Live camera barcode scanner + USB/Bluetooth scanner guns
- Printers: Bluetooth (classic SPP), WiFi (raw port 9100), USB OTG thermal printers in
  TSPL / ZPL / ESC-POS, image mode (exact label incl. Urdu) or text mode;
  A4 office printers through Android printing (WiFi/USB)
- PDF / PNG / printer-file export, backup & restore, CSV for Excel

## How it is built
`app/src/main/assets/index.html` is the whole app UI. `MainActivity.java` hosts it in a
WebView and adds native bridges (`Printing.java`) for Bluetooth, WiFi, USB, system print,
camera scanner and file saving. GitHub Actions (`.github/workflows/build-apk.yml`)
builds the APK on every push to this branch and publishes it as a Release.
