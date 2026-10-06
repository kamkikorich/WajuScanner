# WajuScanner — Panduan untuk AI Agent

Berkas ini adalah fakta TERVERIFIKASI untuk projek ini. Patuhi ia sebelum
menulis atau membetulkan kod. JANGAN trial-and-error — semak fakta di bawah
dahulu, kemudian buat SATU build verification sahaja.

## Verified facts (disahkan 5 Okt 2026 dari JAR + dokumen rasmi)

### ML Kit Document Scanner — NAMA PAKEJ
Artifact: `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0`
Kelas sebenar berada dalam pakej `com.google.mlkit.vision.documentscanner`
(BUKAN `com.google.android.gms.mlkit.vision.documentscanner`, walaupun
artifact group ialah com.google.android.gms). Import yang BETUL:

```kotlin
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
```

Sumber: `unzip -l` classes.jar dalam AAR gradle cache — kelas berada di
`com/google/mlkit/vision/documentscanner/`. Contoh kod rasmi
https://developers.google.com/ml-kit/vision/doc-scanner/android juga
menunjukkan pakej ini.

### GmsDocumentScannerOptions.Builder — HAD NILAI
- `setPageLimit(n)`: n MESTI >= 1. Bytecode builder (`javap -c`) menunjukkan
  `checkArgument(pageLimit > 0)` — nilai 0 membuang `IllegalArgumentException`
  MASA RUNTIME walaupun kod compile lulus.
- Unlimited = JANGAN panggil setPageLimit langsung (default builder = -1).
- Dokumentasi rasmi TIDAK menyebut had ini — satu-satunya rujukan ialah
  bytecode. Kod projek dah dibetulkan (lihat ScannerLauncher.kt).

### GmsDocumentScanningResult API
- `GmsDocumentScanningResult.fromActivityResultIntent(intent)` — statik.
- `result.getPages()` → List `GmsDocumentScanningResult.Page`, `page.getImageUri()`.
- `result.getPdf()` → `GmsDocumentScanningResult.Pdf`, `.getUri()`, `.getPageCount()`.
- Semua getter Java-style (getPages(), bukan property `pages`).

### Build dari terminal (CLI)
```
./gradlew :app:assembleDebug
```
- Java: JDK 21 sistem (`jdk21-openjdk` via pacman) — update automatik ikut `pacman -Syu`.
- Android Studio TELAH DIUNINSTALL (5 Okt 2026). JANGAN guna JAVA_HOME=/opt/android-studio/jbr lagi.
- Android SDK di ~/Android/Sdk (platform android-37, build-tools 36.0.0); update via
  `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager` (headless).

## Workflow wajib untuk isu API/library

1. Rujuk dokumen rasmi TERKINI dahulu (ML Kit: developers.google.com/ml-kit).
2. Jika docs tidak jelas: semak JAR/AAR sebenar dalam ~/.gradle/caches
   (`unzip -l classes.jar`) — fakta pakej dari JAR mengatasi carian web.
3. Jika ada constraint nilai builder yang tidak berdokumen: `javap -c`
   bytecode builder dari JBR (/opt/android-studio/jbr/bin/javap).
4. Selepas fix: SATU build verification (`:app:compileDebugKotlin`),
   JANGAN edit-build-ulang berulang kali.

## Konteks projek

- Tujuan: "Smart Scanner 2026" — document scanner offline-first (kamera →
  crop → multi-page → PDF + OCR). Pelan berfasa (Phase 0-12) ada dalam
  task/implementation plan Android Studio agent.
- Status: Phase 0-2 selesai (setup, Home+Navigation, Room+Repository).
  Phase 3 (Camera) — ScannerLauncher kini betul dan compile lulus.
- Stack: Jetpack Compose + Material3, Hilt, Room, WorkManager, CameraX,
  ML Kit (document scanner 16.0.0 + text recognition), Navigation Compose.
- minSdk 26, targetSdk 37, Kotlin + KSP.