package com.pedro.tone3000m1.domain.model

internal object NamEqDefaults {
    val frequenciesHz = listOf(100f, 250f, 650f, 1600f, 3500f, 8000f)
    val qValues = listOf(0.71f, 1f, 1f, 1f, 1.4f, 0.71f)
    val types = listOf("low_shelf", "bell", "bell", "bell", "bell", "high_shelf")
}

internal object NamEqBandType {
    const val LOW_CUT = "low_cut"
    const val LOW_SHELF = "low_shelf"
    const val BELL = "bell"
    const val HIGH_SHELF = "high_shelf"
    const val HIGH_CUT = "high_cut"

    fun coerce(index: Int, type: String): String = when (index) {
        0 -> if (type == LOW_CUT) LOW_CUT else LOW_SHELF
        5 -> if (type == HIGH_CUT) HIGH_CUT else HIGH_SHELF
        else -> BELL
    }
}
