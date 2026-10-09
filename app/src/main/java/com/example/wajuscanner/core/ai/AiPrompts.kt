package com.example.wajuscanner.core.ai

/** Tindakan AI yang disokong (fungsi paling lazim untuk app pengimbas dokumen). */
enum class AiAction { SUMMARIZE, EXTRACT_FIELDS, ASK }

object AiPrompts {

    const val SYSTEM_INSTRUCTION =
        "Anda pembantu dokumen yang tepat. Jawab berdasarkan kandungan dokumen sahaja, " +
            "ringkas dan dalam Bahasa Melayu."

    fun build(action: AiAction, documentText: String, question: String?): String = when (action) {
        AiAction.SUMMARIZE ->
            "Ringkaskan dokumen berikut dalam 2-3 ayat, kemudian senaraikan 3-6 poin penting.\n\n" +
                "Dokumen:\n---\n$documentText"

        AiAction.EXTRACT_FIELDS ->
            "Ekstrak medan penting daripada dokumen ini (tarikh, jumlah wang, nombor telefon, " +
                "nama individu/syarikat, nombor rujukan). Bentuk senarai 'Medan: Nilai'. " +
                "Jika tiada, tulis 'Tiada'.\n\nDokumen:\n---\n$documentText"

        AiAction.ASK ->
            "Jawab soalan berikut berdasarkan dokumen sahaja. Jika jawapan tiada dalam dokumen, " +
                "katakan 'Tiada dalam dokumen'.\n\n" +
                "Soalan: ${question.orEmpty()}\n\nDokumen:\n---\n$documentText"
    }
}
