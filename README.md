# WajuScanner

**Smart Scanner 2026 — private, offline-first Android scanner.** Camera → multi-page
document → watermarked PDF + on-device OCR + QR/barcode scanning with real-time
bounding boxes. No account, no API keys, no data leaves the device.

[![CI status](https://github.com/kamkikorich/WajuScanner/actions/workflows/build.yml/badge.svg)](https://github.com/kamkikorich/WajuScanner/actions/workflows/build.yml)

## Highlights

- **Document scanner** — ML Kit Document Scanner handles edge detection, perspective
  warp, multi-page capture, and gallery import.
- **Multi-page PDF export** with three compression presets tuned for what most web
  upload forms actually accept:
  - **Penuh** — print quality (2048 px, JPEG q85)
  - **Mampat** — ~5× smaller, suited to web upload limits (1500 px, JPEG q60)
  - **Mampat (Kelabu)** — additional grayscale conversion, smallest payload, best for
    text-only documents
- **On-device OCR** — ML Kit Text Recognition runs entirely on the device. Every page
  is searched by both name and OCR text in the library.
- **QR / barcode scanner** with 13 supported formats (QR, Aztec, Data Matrix, PDF417,
  Code 128 / 39 / 93, EAN-13 / 8, ITF, UPC-A / E, Codabar). ML Kit's auto-zoom and
  `enableAllPotentialBarcodes()` keep detection reliable at distance, and the
  overlay draws a green bounding box around every code in frame.
- **ID-card mode** — scan front + back of a Malaysian IC, driver's licence, or
  passport; the app draws a `FRONT` / `BACK` watermark on each side and combines
  them into a single page. Best-effort OCR on the front side becomes the document
  filename.
- **Offline-first** — Room database, private files directory. OCR, PDF export, and
  barcode detection never leave the device. The only network calls are the optional
  ML Kit model download on first use.

## Tech stack

Jetpack Compose · Material 3 · Hilt · Room · WorkManager · CameraX · ML Kit
(Bundled Document Scanner 16.0.0, Text Recognition 16.0.1, Barcode Scanning 17.3.0)

- **minSdk** 26 (Android 8.0) — `targetSdk` 37
- **Kotlin** 2.2.10, **JDK** 21
- **Architecture**: clean (domain / data / ui), single-Activity Navigation Compose,
  sealed UiState per screen, WorkManager-backed OCR pipeline

## Build from source

Requires Android SDK 37, JDK 21. The project uses the Gradle Version Catalog
(`gradle/libs.versions.toml`) so dependencies are pulled automatically.

```bash
# Run the unit test suite
./gradlew :app:testDebugUnitTest

# Build the debug APK
./gradlew :app:assembleDebug

# Build a signed release APK (uses the keystore configured in app/build.gradle.kts)
./gradlew :app:assembleRelease
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`. CI runs
`testDebugUnitTest` and `assembleDebug` on every push to `main` and every pull
request — see [`.github/workflows/build.yml`](.github/workflows/build.yml).

## Install on a phone

1. Download the debug APK from the most recent green run on the
   [Actions tab](https://github.com/kamkikorich/WajuScanner/actions) — under
   "Artifacts", grab `app-debug.zip` and extract the APK.
2. On the phone, enable **Settings → Apps → Special access → Install unknown
   apps** for whichever browser or file manager you used to download.
3. Open the APK file and accept the install prompt.

## Permissions

| Permission | Why |
|------------|-----|
| `CAMERA`   | Required for document scan, ID-card scan, and QR/barcode scan |

WajuScanner does not request `INTERNET`, `READ_MEDIA_IMAGES`, storage, or location.
The only data leaving the device is whatever you explicitly export or share.

## Project layout

```
app/src/main/java/com/example/wajuscanner/
├── MainActivity.kt              single activity, edge-to-edge
├── ui/
│   ├── navigation/              type-safe routes + animated NavHost
│   ├── home/                    document library + search
│   ├── scanner/                 ML Kit Document Scanner + ID-card mode
│   ├── editor/                  rotate + grayscale filters + per-page save
│   ├── preview/                 page list, reorder, delete, duplicate
│   ├── detail/                  document detail + PDF export (3 compression modes)
│   ├── ocr/                     OCR result screen
│   ├── qr/                      QR/barcode scanner (13 formats + auto-zoom)
│   ├── settings/                storage stats + privacy
├── data/
│   ├── local/db/                Room (Document, Page, OcrResult entities)
│   ├── local/storage/           private files + thumbnails
│   └── repository/              DocumentRepositoryImpl
├── domain/
│   ├── model/                   Document, Page, PdfOptions, PdfCompressionMode
│   ├── repository/              DocumentRepository
│   └── usecase/                 ScanDocument, ManagePages, RunOcr, Export, OcrWorker
├── pdf/                         PdfGenerator + filters
├── image/                       image utilities (combine, watermark, scale)
└── core/
    ├── common/                  Constants
    ├── util/                    ImageUtils, FileUtils, BitmapUtils, IdCardNameExtractor
    └── di/                      AppModule
```

## License

Released under the **MIT License**. See [`LICENSE`](LICENSE) for the full text.
You can use, modify, and redistribute this code, including for commercial
purposes, provided the copyright and license notice are preserved.

## Acknowledgements

Built on top of open-source and Google libraries. No commercial SDKs, no
analytics, no tracking.