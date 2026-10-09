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
  (API ini wujud, tetapi PROJEK tidak menggunakannya — lihat bahagian RESULT FORMAT.)
- Semua getter Java-style (getPages(), bukan property `pages`).

### ML Kit Document Scanner — RESULT FORMAT (disahkan 9 Okt 2026)
- Projek minta `RESULT_FORMAT_JPEG` SAHAJA (bukan `RESULT_FORMAT_PDF`).
  PDF dari scanner tidak pernah digunakan — app jana PDF sendiri via `PdfGenerator`.
- Sebab: dokumen rasmi ("Tips to improve performance") menasihati minta hanya
  format output yang benar-benar digunakan; jana PDF tambahan membuang masa/memori.
- JANGAN tambah semula `RESULT_FORMAT_PDF` tanpa keperluan sebenar.
- Sumber: https://developers.google.com/ml-kit/vision/doc-scanner/android

### Room — SKEMA & MIGRASI (disahkan 9 Okt 2026)
- `app/build.gradle.kts` tetapkan `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
  dan `AppDatabase` guna `exportSchema = true`. Skema dijana ke
  `app/schemas/com.example.wajuscanner.data.local.db.AppDatabase/<version>.json`
  (kini `3.json`, identityHash dicatat) dan DIKOMIT — jangan gitignore folder `schemas`.
- Bila menaikkan `version` DB: tulis `Migration` baharu dalam `AppDatabase.kt`,
  daftar dalam `AppModule.provideAppDatabase` (`.addMigrations(...)`), dan commit
  JSON skema baharu. JANGAN tambah `fallbackToDestructiveMigration`.
- Belum ada migration test — perlukan Robolectric (max SDK 36, jadi `@Config(sdk=[36])`
  kerana projek `targetSdk` 37). Skema v1/v2 tiada kerana `exportSchema` dahulu `false`.

### Scan pipeline — resolusi & ML Kit (disahkan 9 Okt 2026)
- `ImageUtils.calculateInSampleSize` MESTI guna pola "separuh hanya jika hasil ≥ had"
  (`(w/sample)/2 >= maxDimension`). Pola lama (`w/sample > maxDimension`) memotong
  resolusi **separuh secara agresif** (2412px → 1206px; screenshot 1080x2412 jadi
  513x1123). `loadSampledBitmap` kini skala tepat ke had via `scaleDownIfNeeded`.
- Halaman disimpan pada `MAX_STORED_PAGE_DIMENSION` (2560) + `JPEG_QUALITY_PAGE` (92).
- **ML Kit Document Scanner memesongkan (skew) imej tanpa sempadan dokumen** (cth.
  screenshot) melalui auto-perspektifnya — sumber lurus boleh jadi miring. Laluan
  **"Import imej (apa adanya)"** (`ScannerViewModel.importImage`, butang di skrin
  Scanner) memintas ML Kit → kekal lurus + kualiti penuh.

### Kad ID — saiz cetakan 1:1 (disahkan 9 Okt 2026)
- Kad ID MESTI dicetak pada saiz fizikal sebenar **85.6 × 54 mm** (ISO/IEC 7810 ID-1),
  BUKAN diregangkan memenuhi halaman (membazir dakwat). Kelakuan "ID copy" fotokopi.
- Cara: `ImageUtils.composeIdCardSheet` meletak depan+belakang atas helaian A4
  (2480 × 3508 px = A4 @300 dpi, standard cetakan kad). Kad jadi 1011 × 638 px
  (85.6 × 54 mm @300 dpi). Eksport PDF (mod FULL) memuat pada
  `Constants.MAX_EXPORT_DIMENSION` (3508) → dicetak 1:1, kad tepat 85.6 mm.
- JANGAN turunkan `ID_SHEET_*` atau `MAX_EXPORT_DIMENSION` — kad akan jatuh bawah 300 dpi.
- Geometri tulen dalam `ImageUtils.idCardSheetLayout` (diuji JVM: `IdCardSheetLayoutTest`).
- JANGAN kembali ke `combineVertical/combineHorizontal`/`captionStrip` (sudah dibuang —
  ia menghasilkan imej tanpa nisbah A4, jadi PDF membesarkannya penuh halaman).
- Warna dikekalkan; pilihan kelabu kekal di eksport ("Mampat (Kelabu)") + editor.

### Anti-shake / penstabilan kamera (disahkan 9 Okt 2026)
- CameraX **tidak** mendedahkan penstabilan dalam `CameraControl` (isu terbuka Google:
  "Expose setVideoStabilizationEnabled in CameraX controller"). Laluan rasmi =
  **Camera2 Interop**: `Camera2Interop.Extender(imageCaptureBuilder)
  .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, ...)` dan
  `LENS_OPTICAL_STABILIZATION_MODE`. `applyCamera2Interop` hanya wujud 1.7.0-alpha03+
  (projek guna 1.6.2 — jangan guna).
- WAJIB semak sokongan dahulu sebelum set: `provider.getCameraInfo(selector)` →
  `Camera2CameraInfo.from(info).getCameraCharacteristic(...)` dan hanya set mod yang ada
  dalam `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES` / `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`.
  Set mod tak disokong (cth. kamera depan tanpa OIS) boleh menggagalkan tangkapan.
- Helper: `core/camera/CameraStabilization.kt`. Digunakan oleh skrin Foto Rasmi.
- Opt-in WAJIB guna anotasi **androidx**: `@OptIn(markerClass =
  [ExperimentalCamera2Interop::class])` (`androidx.annotation.OptIn`). **`kotlin.OptIn`
  TIDAK memuaskan lint** (`UnsafeOptInUsageError` kekal) walaupun kod compile lulus.
  Sama untuk `ExperimentalGetImage` di `QrViewModel.analyzer()`.
- Jalankan `./gradlew :app:lintDebug` sebelum commit — ia menangkap `NewApi`
  sebenar (cth. `Context#mainExecutor` API 28 pada minSdk 26 → guna `ContextCompat`).
- Aliran dokumen: butang **"Kamera (anti-shake)"** (`ui/scanner/DocumentCaptureScreen.kt`
  + `DocumentCaptureViewModel`) menangkap terus melalui CameraX (penstabilan, kualiti
  penuh, TIADA auto-perspektif ML Kit) → `ScanDocumentUseCase`. Entry dari skrin Scanner.

### AI (BYOK) — kunci, provider & simpanan (disahkan 9 Okt 2026)
- Ciri AI **opsyenal**: butang/dialog AI hanya muncul bila pengguna mengaktifkannya
  di Tetapan DAN kunci disimpan. JANGAN dedah AI di aliran biasa jika tidak diaktifkan;
  kegagalan AI (kunci luput/tak sah) hanya dipapar dalam helaian AI, tidak menyekat scan/simpan/eksport.
- Simpanan: **Preferences DataStore** (`files/datastore/ai_settings...`) + kunci API
  **disulitkan AES-256-GCM via Android Keystore** (alias `waju_ai_key`, lihat
  `core/security/SecretCipher.kt`). JANGAN guna `EncryptedSharedPreferences`
  (`androidx.security:security-crypto`) — ia **DEPRECATED April 2025**.
- **Gemini = Interactions API** (endpoint terkini): `POST
  https://generativelanguage.googleapis.com/v1beta/interactions`, header
  `x-goog-api-key: <KEY>`, body `{ "model": "gemini-3.8-flash", "input": "...",
  "system_instruction": "..." }`. BUKAN `models/<model>:generateContent` (lapuk).
- **OpenAI-compatible**: `POST {baseUrl}/chat/completions`, `Authorization: Bearer <KEY>`.
- `INTERNET` kini diminta — hanya untuk AI (opsyenal). Tanpa AI, app 100% offline.
- Fail: `core/ai/*`, `core/security/SecretCipher.kt`, `data/settings/AiSettingsStore.kt`,
  `ui/settings/AiSettingsSection.kt`, `ui/detail/AiSheet.kt`. Rangkaian guna
  `HttpURLConnection` + `kotlinx-serialization-json` (tiada OkHttp).
- **User-Agent WAJIB**: `HttpURLConnection` menghantar UA lalai `Dalvik/…` yang
  **DISEKAT Cloudflare** (HTTP 403 + badan HTML) di hadapan sesetengah API (cth.
  ollama.com). `postJson` menetapkan `User-Agent: WajuScanner/1.0 (Android)` —
  JANGAN buang header ini. (Disahkan 9 Okt 2026 dengan curl: UA Dalvik → 403,
  UA lain/tiada → 401 JSON.)
- Base URL jadi pilihan skema (`resolveChatCompletionsUrl` auto-tambah `https://`).
  Medan **Model** mesti nama model sah pembekal (cth ollama.com: `gemma4:31b`).
- Input **vision** (imej halaman): togol dalam helaian AI, HANYA provider
  OpenAI-compatible (bentuk `image_url` data URL, halaman pertama ≤1280px JPEG).
  Gemini = teks sahaja buat masa ini.
- Teks dokumen dihadkan `MAX_DOCUMENT_CHARS` (16k) via `boundDocumentText` sebelum dihantar.
- `RunOcrUseCase.recognizeDocument` memang digunakan (oleh AI) — jangan buang.

### Compose — edge-to-edge & papan kekunci (disahkan 9 Okt 2026)
- `targetSdk` 37 → **edge-to-edge wajib**; app melukis DI BELAKANG papan kekunci dan
  `windowSoftInputMode=adjustResize` **tidak lagi** menolak kandungan.
- Skrin dengan medan input MESTI guna **`Modifier.imePadding()`** pada bekas boleh
  skrol, jika tidak medan di bawah tersembunyi oleh papan kekunci.
  Contoh: `ui/settings/SettingsScreen.kt`, `ui/detail/AiSheet.kt`.

### Build & ujian dari terminal (CLI)
```
./gradlew :app:assembleDebug              # bina APK
./gradlew :app:testDebugUnitTest          # ujian unit (JVM) — parser AI, repository
./gradlew :app:connectedDebugAndroidTest  # ujian instrumented (perlu adb/peranti) — cipher, migration
```
- Java: JDK 21 sistem (`jdk21-openjdk` via pacman) — update automatik ikut `pacman -Syu`.
- Android Studio TELAH DIUNINSTALL (5 Okt 2026). JANGAN guna JAVA_HOME=/opt/android-studio/jbr lagi.
- Android SDK di ~/Android/Sdk (platform android-37, build-tools 36.0.0); update via
  `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager` (headless).
- Ujian instrumented dijalankan pada peranti sebenar melalui adb (bukan emulator); CI
  pula guna langkah emulator (`reactivecircus/android-emulator-runner`).

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
- Stack: Jetpack Compose + Material3, Hilt, Room, DataStore, CameraX,
  ML Kit (document scanner 16.0.0 + text recognition), Navigation Compose.
- minSdk 26, targetSdk 37, Kotlin + KSP.