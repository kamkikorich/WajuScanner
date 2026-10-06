# QR Scanner — Plan

## Goal
Add a live QR/2D barcode scanner screen to WajuScanner using CameraX + ML Kit
Barcode Scanning (bundled). When a code is found, the raw value is shown in a
dialog with Copy and Share actions. Backed by Hilt ViewModel and Compose.

## Verified facts (sumber: webfetch 6 Okt 2026)
- Artifact: `com.google.mlkit:barcode-scanning:17.3.0` (bundled, +2.4 MB).
- minSdk 23 — kita 26 ✅.
- ImageAnalysis.Analyzer pattern from `developer.android.com/ml-kit/vision/barcode-scanning/android`.
- `camera-mlkit-vision` ialah wrapper yang **bukan** diperlukan untuk analisis manual
  (kita panggil `BarcodeScanning.getClient().process(inputImage)` sendiri). Skip wrapper
  untuk kurangkan dependency surface (patuh Simplicity First).
- Auto-zoom available dari 17.2.0+ — but auto-zoom CameraControl integration **memerlukan**
  CameraX camera-info wiring. Untuk MVP kita skip auto-zoom (boleh tambah kemudian).
- 13 format disokong; kita akan subset: QR, Aztec, Data Matrix, PDF417, Code 128.

## Skop perubahan (6 fail)

1. `gradle/libs.versions.toml`
   - Tambah `mlkitBarcodeScanning = "17.3.0"`
   - Tambah alias `mlkit-barcode-scanning`
2. `app/build.gradle.kts`
   - Tambah `implementation(libs.mlkit.barcode.scanning)`
3. `app/src/main/java/com/example/wajuscanner/ui/navigation/SmartScannerDestinations.kt`
   - Tambah `data object QrScanner`
4. `app/src/main/java/com/example/wajuscanner/ui/qr/QrViewModel.kt` (baru)
   - State: Idle, Scanning, Found(rawValue), PermissionMissing, Error(msg)
   - Logic: start camera, process frames, return first stable result
5. `app/src/main/java/com/example/wajuscanner/ui/qr/QrScannerScreen.kt` (baru)
   - Compose: top app bar (back), PreviewView viewfinder, overlay rectangle,
     permission gate, "Found" dialog dengan Copy + Share
6. `app/src/main/java/com/example/wajuscanner/MainActivity.kt`
   - Tambah `composable<SmartScannerDestinations.QrScanner>` route

## Reuse (tiada perubahan)
- Hilt graph sedia ada (no new @Inject yang kompleks)
- Permission API dari `androidx.activity.compose.rememberLauncherForActivityResult`
- Theme: `MaterialTheme` sedia ada — guna `surface`, `onSurface`
- Manifest: `CAMERA` permission sudah ada (`<uses-permission ... CAMERA />`)

## TIDAK dibuat (Simplicity First)
- ❌ `camera-mlkit-vision` wrapper — overhead, kita guna `BarcodeScanning.getClient` sendiri
- ❌ Auto-zoom — boleh ditambah kemudian, keluar skop MVP
- ❌ History/simpan QR ke DB — out of scope
- ❌ Generate QR — fungsi lain
- ❌ Unit tests — boleh ditambah selepas MVP (low-risk, tiada side effects)

## Verification gates
1. Selepas dependency ditambah: `./gradlew :app:dependencies | grep barcode` → ada
2. Selepas Kotlin compile: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
3. Selepas APK built: `./gradlew :app:assembleDebug` → APK di `app/build/outputs/apk/debug/`

## Cara uji di device (manual)
1. Pasang APK: `./gradlew :app:installDebug` (atau gunakan emulator/device sendiri)
2. Buka app → Home → tap butang QR (akan ditambah kemudian, atau navigate manual dari
   sebarang tempat guna SmartScannerDestinations.QrScanner)
3. Bagi kebenaran kamera
4. Halakan kamera pada QR — nilai muncul dalam dialog
5. Tap "Salin" — nilai masuk clipboard
6. Tap "Kongsi" — share intent bawa nilai
7. Tap "Imbas Lagi" atau back — kembali ke viewfinder