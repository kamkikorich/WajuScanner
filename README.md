# WajuScanner

Smart Scanner 2026 — document scanner offline-first (kamera → multi-page → PDF + OCR)
untuk Android.

## Ciri

- 📷 Scan dokumen dengan **ML Kit Document Scanner** + **CameraX**
- 📄 Multi-page PDF export + OCR (ML Kit Text Recognition)
- 🔍 **QR / barcode scanner** (ML Kit Barcode Scanning — QR, Aztec, Data Matrix, PDF417, Code 128)
- 🔗 QR URL dipaparkan sebagai hyperlink boleh klik
- 💾 **Offline-first** — Room, tiada data keluar

## Stack: Jetpack Compose · Material 3 · Hilt · Room · WorkManager · CameraX · ML Kit
- minSdk 26, targetSdk 37, Kotlin 2.2.10, JDK 21

## Build dari source

```bash
./gradlew :app:assembleDebug          # APK debug (~86 MB)
./gradlew :app:assembleRelease        # APK release (perlu signing config)
```

## Pasang APK siap (debug build)

Lihat [Releases](https://github.com/kamkikorich/WajuScanner/releases) untuk APK
debug yang telah disusun. Untuk pasang dari release tag, muat turun
`app-debug.apk` dari halaman release terkini, benarkan **Install from unknown
sources** dalam Settings, kemudian buka fail.

## Kebenaran

- `CAMERA` — untuk document scan + QR scanner

## Lesen

MIT