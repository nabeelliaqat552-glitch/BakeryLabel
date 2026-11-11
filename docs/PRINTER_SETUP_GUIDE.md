# Printer setup guide

Open **Settings & Tools › Printer Manager**.

## Step 1: how is it connected?
- **Bluetooth:** switch the printer on. Tap **Search** and allow the permission. Tap **Pair** on your printer (PIN is often 0000 or 1234), tap Search again, then **Add**.
- **USB cable:** connect with an OTG adapter. The device appears in the list. Tap **Allow**, then **Add**. Not every phone powers USB devices.
- **Wi-Fi / LAN:** phone and printer on the same network. Type the printer's IP address (print the printer's self-test page to see it) and port (usually 9100), or tap **Find on Wi-Fi**.
- **Android Print:** for printers with their own Android print plugin. Labels go as a PDF with pages exactly the label size.

## Step 2: printer language
The printer's manual says which it uses.

| Language | Typical printers |
|---|---|
| TSPL | Label printers (TSC and compatibles; many Xprinter, Gprinter, iDPRT label models) |
| ESC/POS | Receipt-style 58 mm / 80 mm printers |
| ZPL | Zebra and Zebra-compatible |
| CPCL | Some mobile printers |

Also set **Resolution** (203 DPI on most; 300 on some), **Print width** (48 mm for 58 mm receipt printers, 72 mm for 80 mm, about 104 mm for 4-inch label printers), **Darkness** and **Speed**.

## Step 3: test and confirm
Tap **Print test label**. It prints a border, marks every 5 mm, Urdu text and a barcode.
- Measure the border with a ruler: it must match the label size.
- Check the Urdu letters are joined.
- Scan the barcode.

Answer **Yes** only if all three are right. The printer is then marked "test confirmed".

## If something is wrong
| What you see | Try |
|---|---|
| Nothing prints | Paper loaded the right way, cover closed, battery; then another language |
| Random letters print | Wrong language: try TSPL, then ESC/POS |
| Label too big or too small | Wrong resolution: switch 203 / 300 DPI |
| Print shifted sideways | Change "Shift right (mm)" or "Print width" |
| Each label drifts down the roll | Set the correct gap in Label Sizes; on ESC/POS printers adjust the gap until it lines up |
| Too light / too dark | Change Darkness |
| "Label is wider than the printer" | Choose a narrower label size; the app will not shrink a label |
| Bluetooth will not connect | Make sure no other phone is connected to the printer; switch it off and on |
