# Bakery Labels — Android app

Expiry-date management and label printing for bakeries. Kotlin + Jetpack Compose,
offline database (Room), optional cloud sync (Firebase Firestore).

## What it does

- **Print screen**: pick a product → the label preview updates live → set the
  manufacturing date → expiry is calculated from the product's shelf life (or set
  it manually) → choose label size and number of prints → Print or PDF.
- **Label** (exactly this, nothing else): bakery name, subtitle (optional),
  divider, product name, `MFG Date:` and `EXP Date:` (expiry in red).
  Black background, thin rounded border, rounded corners.
- **Default label size: 50 × 30 mm** with a 2 mm gap (most common thermal roll).
  Presets 40×25, 60×40, 75×50 and any custom size (Admin → Label design).
- **Number of prints**: − / + stepper, type any number 1–999, quick 1/5/10/20.
  The default count is set in Admin → Label design.
- **Batches**: every print creates a batch whose dates are fixed. Changing the day
  or a product's shelf life never moves an existing batch's expiry. Reprint any batch.
- **Admin** (PIN, default `1234`): products & shelf life, label design (name,
  subtitle, size, font size, border, corners, colours, date format, gap, default
  copies), printer, cloud sync, PIN change.
- **Printers**: Bluetooth (classic SPP), Wi-Fi (raw port 9100), USB OTG,
  Android print service, PDF export/share. Language TSPL (label printers such
  as Xprinter, TSC, Gprinter) or ESC/POS. 203 or 300 dpi. Test connection and
  test print buttons.

One renderer (`label/LabelRenderer.kt`) draws the preview, the PDF and the printer
image, so what you see is what prints.

## Build

1. Install **Android Studio** (Ladybug 2024.2 or newer).
2. File → Open → this `BakeryLabel` folder. Let Gradle sync (needs internet once).
3. Connect a phone (USB debugging on) and press **Run**, or
   Build → Build App Bundle(s)/APK(s) → Build APK(s).

Requires Android 8.0 (API 26) or newer.

## Cloud sync (optional)

The app works fully offline without this.

1. Create a project at console.firebase.google.com.
2. Add an Android app with package name `com.bakery.labelapp`.
3. Download `google-services.json` and put it in the `app/` folder. Rebuild.
4. In Firebase: Build → Authentication → Sign-in method → enable **Anonymous**.
5. Build → Firestore Database → Create database, then set these rules:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /bakeries/{code}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

6. In the app: Admin → Cloud sync → enter a **bakery code** (for example
   `ahmed-bakery-lahore`) and Connect. Use the same code on every phone of the
   same bakery. Choose a code that is hard to guess, because anyone with the app
   and the code can read that bakery's data.

Changes are saved on the phone first and uploaded when internet is available.

## Printer tips

- Bluetooth: pair the printer in phone Settings → Bluetooth first (PIN usually
  `0000` or `1234`), then pick it in the app's Printer screen.
- Most label printers (Xprinter XP-365B, XP-420B, TSC, Gprinter) use **TSPL**.
- Thermal printers print one colour. The label prints as a black block with white
  text; the red expiry date prints white too. Use the "thermal print" switch on
  the print screen to see exactly how it will look. Full colours appear in PDF
  and colour printers.
- A full black background uses more print-head heat. If text looks faint or
  smudged, lower the printer's darkness/speed in its own settings.
- If a label comes out as a negative, turn on **Invert image** in Printer setup.

## Project layout

```
app/src/main/java/com/bakery/labelapp/
  data/     Room entities, DAOs, repository, Firestore sync
  label/    LabelRenderer (the label drawing), date helpers
  print/    TSPL / ESC/POS commands, Bluetooth / Wi-Fi / USB, PDF & Android print
  ui/       ViewModel, theme, shared components, screens
```
