package com.example.wajuscanner.ui.idphoto

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wajuscanner.pdf.PdfGenerator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Negeri skrin Foto Pasport: kamera → memproses → hasil 35:50.
 */
sealed interface IdPhotoUiState {
    data object Camera : IdPhotoUiState
    data object Processing : IdPhotoUiState
    data class Result(val photo: Bitmap) : IdPhotoUiState
}

/**
 * ViewModel untuk [PassportPhotoScreen]. Mengurus pipeline pemprosesan foto
 * pasport (Decode JPEG → mask selfie segmentation → komposit atas latar putih
 * → potong tengah 35:50), penyimpanan PNG melalui MediaStore dan penjanaan
 * helaian cetak A4 300dpi.
 *
 * Tiada RenderScript (dihapuskan): komposit latar putih dilakukan dengan
 * kanvas + PorterDuff SRC_IN pada salinan mask-alpha.
 */
@HiltViewModel
class PassportPhotoViewModel @Inject constructor(
    private val pdfGenerator: PdfGenerator,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IdPhotoUiState>(IdPhotoUiState.Camera)
    val uiState: StateFlow<IdPhotoUiState> = _uiState

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** False = kamera belakang (default), true = kamera depan (mod self).*/
    private val _selfMode = MutableStateFlow(false)
    val selfMode: StateFlow<Boolean> = _selfMode

    fun toggleSelfMode() {
        _selfMode.value = !_selfMode.value
    }

    /**
     * True = ganti latar dengan putih (selfie segmentation). False = kekalkan
     * latar asal foto. Penggantian latar melembutkan sedikit tepi siluet
     * (had topeng model), jadi pengguna boleh matikannya untuk ketajaman tepi
     * maksimum.
     */
    private val _whiteBackground = MutableStateFlow(true)
    val whiteBackground: StateFlow<Boolean> = _whiteBackground

    fun setWhiteBackground(enabled: Boolean) {
        _whiteBackground.value = enabled
    }

    /**
     * Lazy supaya model segmentasi hanya dimuat pada foto pertama diproses.
     * SINGLE_IMAGE_MODE: setiap foto diproses berasingan (tiada smoothing
     * antara frame). Tanpa enableRawSizeMask(), mask di-skala automatik ke
     * saiz imej input — sesuai untuk komposit piksel-demi-piksel langsung.
     */
    private val segmenterLazy = lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }
    private val segmenter get() = segmenterLazy.value

    /**
     * Pengesan muka (bundled ML Kit, offline) — mengukur kotak muka supaya
     * pangkas ikut nisbah rasmi: muka = 50–60% tinggi foto (JIM/ICAO).
     */
    private val faceDetectorLazy = lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.25f)
            .build()
        FaceDetection.getClient(options)
    }
    private val faceDetector get() = faceDetectorLazy.value

    /** Tangkapan kamera diterima; close ImageProxy selepas byte dibaca. */
    fun onCaptured(imageProxy: ImageProxy) {
        if (_uiState.value is IdPhotoUiState.Processing) {
            imageProxy.close()
            return
        }
        val bytes = runCatching {
            val buffer = imageProxy.planes.first().buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        }.getOrElse {
            imageProxy.close()
            _message.value = "Gagal membaca data foto."
            return
        }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        _message.value = null
        _uiState.value = IdPhotoUiState.Processing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = IdPhotoUiState.Result(
                    processPhoto(bytes, rotationDegrees, _whiteBackground.value)
                )
            } catch (e: Exception) {
                _message.value = e.localizedMessage ?: "Gagal memproses foto."
                _uiState.value = IdPhotoUiState.Camera
            }
        }
    }

    /** Balik ke kamera untuk tangkap semula. */
    fun retake() {
        _message.value = null
        _uiState.value = IdPhotoUiState.Camera
    }

    /** Lapor ralat dari layer UI (kegagalan tangkapan kamera). */
    fun reportError(text: String) {
        _message.value = text
    }

    // ---- Pipeline pemprosesan (dispatch: Dispatchers.IO) ----

    /**
     * a. Decode JPEG (cap sisi panjang 2048) → b. komposit latar putih →
     * c. potong tengah ke nisbah 35:50 (sisi panjang dikekalkan).
     */
    private suspend fun processPhoto(bytes: ByteArray, rotationDegrees: Int, replaceBackground: Boolean): Bitmap {
        val source = decodeCapped(bytes, rotationDegrees)
        val person = if (replaceBackground) {
            try {
                compositeOverWhite(source)
            } catch (e: Exception) {
                // Segmenter tidak sedia/gagal: kekalkan foto asal (tiada ganti latar putih).
                source
            }
        } else {
            // Pengguna pilih kekalkan latar asal — tiada segmentation, tepi lebih tajam.
            source
        }
        return try {
            autoCropStandard(person)
        } catch (e: Exception) {
            // Pengesan muka gagal: pangkas tengah seperti sebelum ini.
            centerCrop35x50(person)
        }
    }

    /**
     * Pangkas ikut standard foto pasport Malaysia (JIM/ICAO):
     * tinggi muka (dagu→mahkota) = 50–60% tinggi crop (sasaran 55%),
     * margin atas ~10 mm (20%), nisbah 35:50. Kotak muka ML Kit menutupi
     * kawasan dagu→dahi sahaja; jadi tinggi kepala penuh dianggarkan
     * kotak × 1.28 (rambut tampak di atas kotak).
     */
    private suspend fun autoCropStandard(source: Bitmap): Bitmap {
        var bitmap = source
        var face = detectFace(bitmap)
            ?: throw IllegalStateException("Tiada muka dijumpai untuk auto-crop.")

        // Auto-level: betulkan guling kepala guna sudut rasmi ML Kit
        // (headEulerAngleZ). Pengesahan kendiri: hanya guna hasil putaran jika
        // sudut baki benar-benar mengecil (elak arah putaran tersilap).
        if (abs(face.roll) >= DESKEW_MIN_DEGREES) {
            val deskewed = rotateBy(bitmap, face.roll)
            val residual = detectFace(deskewed)
            if (residual != null && abs(residual.roll) < abs(face.roll)) {
                bitmap = deskewed
                face = residual
            } else {
                deskewed.recycle()
            }
        }

        val box = face.box
        val faceH = box.height().coerceAtLeast(1)
        val headH = faceH * 1.28f
        val targetHeadFraction = 0.55f
        val cropH = (headH / targetHeadFraction).roundToInt()
        val cropW = (cropH * (35f / 50f)).roundToInt()

        // Mahkota dianggarkan 20% tinggi kotak di atas kotak muka (dahi).
        val crownY = box.top - (faceH * 0.20f).roundToInt()
        val marginTop = (cropH * 0.20f).roundToInt()

        var left = box.centerX() - cropW / 2
        var top = crownY - marginTop

        // Tolak sempad bitmap (jangka kepala terlalu rapat pinggir bingkai).
        left = left.coerceIn(0, (bitmap.width - cropW).coerceAtLeast(0))
        top = top.coerceIn(0, (bitmap.height - cropH).coerceAtLeast(0))
        val w = cropW.coerceAtMost(bitmap.width)
        val h = cropH.coerceAtMost(bitmap.height)
        if (w < cropW * 0.7f || h < cropH * 0.7f) {
            // Bingkai terlalu sempit untuk standard — biar fallback lakukan.
            throw IllegalStateException("Bingkai terlalu sempit untuk nisbah pasport.")
        }

        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
        setOf(bitmap, source).forEach { if (it !== cropped) it.recycle() }
        return cropped
    }

    /** Muka terbesar dalam imej: kotak + sudut guling (headEulerAngleZ). */
    private data class FaceInfo(val box: Rect, val roll: Float)

    private suspend fun detectFace(bitmap: Bitmap): FaceInfo? =
        Tasks.await(
            faceDetector.process(InputImage.fromBitmap(bitmap, 0)),
            FACE_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        ).maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?.let { FaceInfo(it.boundingBox, it.headEulerAngleZ) }

    /**
     * Putar [src] mengikut darjah (positif = mengikut arah jam), sudut lutsinar
     * dipenuhkan putih. [src] tidak dikitar semula — pemanggil yang uruskan.
     */
    private fun rotateBy(src: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(rotated, 0f, 0f, bitmapPaint())
        if (rotated !== src) rotated.recycle()
        return out
    }

    /** Decode JPEG + putar ikut rotasi sensor + cap sisi panjang kepada 2048 px. */
    private fun decodeCapped(bytes: ByteArray, rotationDegrees: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_EDGE) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IllegalStateException("Gagal decode JPEG.")

        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
        if (rotated !== decoded) decoded.recycle()

        val longEdge = maxOf(rotated.width, rotated.height)
        if (longEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longEdge
            val scaled = Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).roundToInt().coerceAtLeast(1),
                (rotated.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== rotated) rotated.recycle()
            return scaled
        }
        return rotated
    }

    /**
     * Ganti latar dengan putih melalui mask selfie segmentation:
     * 1. mask (keyakinan float setiap piksel) → Alpha-bitmap putih,
     * 2. salinan sumber diserapkan mask melalui PorterDuff SRC_IN,
     * 3. lukis salinan itu di atas kanvas putih.
     * Bila cubaan mana-mana langkah gagal, [source] TIDAK dikitar semula dan
     * exception disampai — pemanggil mengekalkan foto asal.
     */
    private fun compositeOverWhite(source: Bitmap): Bitmap {
        val mask = Tasks.await(
            segmenter.process(InputImage.fromBitmap(source, 0)),
            SEGMENT_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
        val maskW = mask.width
        val maskH = mask.height

        // 1. mask → alpha bitmap (putih, alpha = keyakinan latar depan).
        val alphaMask = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
        try {
            val buffer = mask.buffer
            buffer.rewind()
            val pixels = IntArray(maskW * maskH)
            val count = minOf(buffer.remaining() / 4, pixels.size)
            for (i in 0 until count) {
                val confidence = buffer.getFloat()
                val alpha = (confidence.coerceIn(0f, 1f) * 255f).roundToInt()
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            alphaMask.setPixels(pixels, 0, maskW, 0, 0, maskW, maskH)
        } catch (e: Exception) {
            alphaMask.recycle()
            throw e
        }

        // 2. banding salinan sumber dengan mask: DST_IN mengekalkan IMEJ
        //    sumber di mana alpha mask tinggi (SRC_IN tersilap — ia mengekalkan
        //    mask putih, lalu hasil jadi putih kosong).
        val masked = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val maskedCanvas = Canvas(masked)
        maskedCanvas.drawBitmap(source, 0f, 0f, bitmapPaint())
        maskedCanvas.drawBitmap(
            alphaMask,
            null,
            RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
            bitmapPaint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) },
        )
        alphaMask.recycle()

        // 3. lukis atas kanvas putih.
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(masked, 0f, 0f, bitmapPaint())
        masked.recycle()
        source.recycle()
        return result
    }

    /** Potong tengah ke nisbah 35:50; sisi panjang imej sumber dikekalkan. */
    private fun centerCrop35x50(source: Bitmap): Bitmap {
        val targetRatio = 35f / 50f
        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropW = (source.height * targetRatio).roundToInt().coerceAtLeast(1)
            val left = (source.width - cropW) / 2
            Bitmap.createBitmap(source, left, 0, cropW, source.height).also { source.recycle() }
        } else {
            val cropH = (source.width / targetRatio).roundToInt().coerceAtLeast(1)
            val top = (source.height - cropH) / 2
            Bitmap.createBitmap(source, 0, top, source.width, cropH).also { source.recycle() }
        }
    }

    private fun bitmapPaint() = Paint(Paint.FILTER_BITMAP_FLAG)

    // ---- Simpan PNG (MediaStore) ----

    /** Simpan potret akhir 35:50 sebagai PNG ke Pictures/WajuScanner. */
    fun savePng(context: Context) {
        val current = _uiState.value as? IdPhotoUiState.Result ?: return
        viewModelScope.launch {
            try {
                _message.value = null
                val bytes = withContext(Dispatchers.IO) { encodePng(current.photo) }
                withContext(Dispatchers.IO) {
                    savePngToGallery(context, bytes, "pasport_${System.currentTimeMillis()}.png")
                }
                _message.value = "PNG disimpan ke Pictures/WajuScanner."
            } catch (e: Exception) {
                _message.value = e.localizedMessage ?: "Gagal menyimpan PNG."
            }
        }
    }

    /**
     * Simpan PNG melalui MediaStore. minSdk 26: RELATIVE_PATH/IS_PENDING hanya
     * digunakan API 29+ (posisi pisah ikut polisi projek).
     */
    private fun savePngToGallery(context: Context, bytes: ByteArray, fileName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gagal mendaftar fail dalam MediaStore.")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("Gagal membuka output stream MediaStore.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: IOException) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    // ---- Helaian cetak A4 300dpi ----

    /**
     * Compose helaian A4 300dpi (2480x3508 ARGB): latar putih, 2 lajur x 4
     * baris portret 413x591 px (35x50 mm pada 300dpi), gutter 12 px + garis
     * panduan potong kelabu kelabu nipis (1 px solid). Disimpan ke
     * Pictures/WajuScanner DAN salinan cache/exports untuk dikongsi melalui
     * FileProvider (authority ${applicationId}.fileprovider, fail cache-path
     * "exports/" sudah didaftar dalam file_paths.xml).
     */
    fun createPrintSheet(context: Context, onReady: (Uri) -> Unit) {
        val current = _uiState.value as? IdPhotoUiState.Result ?: return
        viewModelScope.launch {
            try {
                _message.value = null
                val bytes = withContext(Dispatchers.IO) {
                    val sheet = buildPrintSheet(current.photo)
                    val encoded = encodePng(sheet)
                    sheet.recycle()
                    encoded
                }
                val fileName = "helaian_cetak_${System.currentTimeMillis()}.png"
                val file = withContext(Dispatchers.IO) {
                    savePngToGallery(context, bytes, fileName)
                    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(exportsDir, fileName).apply { writeBytes(bytes) }
                }
                val uri = pdfGenerator.getUriForFile(file)
                withContext(Dispatchers.Main) { onReady(uri) }
            } catch (e: Exception) {
                _message.value = e.localizedMessage ?: "Gagal menjana helaian cetak."
            }
        }
    }

    private fun buildPrintSheet(photo: Bitmap): Bitmap {
        val sheet = Bitmap.createBitmap(SHEET_WIDTH, SHEET_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = CUT_GUIDE_COLOR
        }
        val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Pra-skala foto ke saiz sel SEKALI (kaedah bertahap) supaya lukisan
        // ke dalam sel menjadi 1:1 — elak aliasing dan cetakan lebih tajam
        // berbanding mengecilkan ~3.5x sekali gus semasa drawBitmap.
        val cellPhoto = downscaleHighQuality(photo, PHOTO_W.roundToInt(), PHOTO_H.roundToInt())

        val cell = RectF()
        val startX = (SHEET_WIDTH - (SHEET_COLS * PHOTO_W + (SHEET_COLS + 1) * GAP)) / 2f
        val startY = (SHEET_HEIGHT - (SHEET_ROWS * PHOTO_H + (SHEET_ROWS + 1) * GAP)) / 2f
        for (row in 0 until SHEET_ROWS) {
            for (col in 0 until SHEET_COLS) {
                val left = startX + GAP + col * (PHOTO_W + GAP)
                val top = startY + GAP + row * (PHOTO_H + GAP)
                cell.set(left, top, left + PHOTO_W, top + PHOTO_H)
                canvas.drawBitmap(cellPhoto, null, cell, drawPaint)
                canvas.drawRect(cell, guide)
            }
        }
        if (cellPhoto !== photo) cellPhoto.recycle()
        return sheet
    }

    /**
     * Skala turun berkualiti tinggi: halving bertahap (setiap langkah 2x guna
     * penapisan bilinear, anggaran penapis box) sebelum langkah akhir tepat.
     * Mengelak aliasing yang berlaku bila sumber jauh lebih besar daripada sel.
     * Mengembalikan [src] sendiri jika sudah tepat saiz sasaran.
     */
    private fun downscaleHighQuality(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        var current = src
        var w = src.width
        var h = src.height
        while (w / 2 >= targetW && h / 2 >= targetH) {
            val next = Bitmap.createScaledBitmap(current, (w / 2).coerceAtLeast(1), (h / 2).coerceAtLeast(1), true)
            if (current !== src) current.recycle()
            current = next
            w = current.width
            h = current.height
        }
        if (w != targetW || h != targetH) {
            val next = Bitmap.createScaledBitmap(current, targetW, targetH, true)
            if (current !== src) current.recycle()
            current = next
        }
        return current
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    override fun onCleared() {
        if (segmenterLazy.isInitialized()) {
            runCatching { segmenterLazy.value.close() }
        }
        if (faceDetectorLazy.isInitialized()) {
            runCatching { faceDetectorLazy.value.close() }
        }
        super.onCleared()
    }

    private companion object {
        /** Cap sisi panjang untuk decode tangkapan. */
        const val MAX_EDGE = 2048

        /** Had masa pengesan muka (elak hang pada peranti bermasalah). */
        const val FACE_TIMEOUT_SECONDS = 10L

        /** Kecondongan minimum (darjah) sebelum auto-level dijalankan. */
        const val DESKEW_MIN_DEGREES = 1.0f

        /** Had masa segmentasi selfie (elak hang pada peranti bermasalah). */
        const val SEGMENT_TIMEOUT_SECONDS = 15L

        /** Subfolder galeri aplikasi. */
        const val GALLERY_FOLDER = "Pictures/WajuScanner"

        /** A4 pada 300dpi. */
        const val SHEET_WIDTH = 2480
        const val SHEET_HEIGHT = 3508
        const val SHEET_COLS = 2
        const val SHEET_ROWS = 4

        /** 35 mm x 50 mm pada 300dpi. */
        const val PHOTO_W = 413f
        const val PHOTO_H = 591f

        /** Gutter antara foto. */
        const val GAP = 12f

        /** Garis panduan potong: kelabu nipis. */
        val CUT_GUIDE_COLOR = Color.argb(60, 0, 0, 0)
    }
}
