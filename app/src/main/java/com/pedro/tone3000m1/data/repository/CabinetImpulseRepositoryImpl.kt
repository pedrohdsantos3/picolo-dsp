package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.domain.repository.CabinetImpulseRepository
import java.io.File

/** Writes the existing cabinet IR preference keys and defaults. */
internal class CabinetImpulseRepositoryImpl(
    private val preferences: SharedPreferences,
) : CabinetImpulseRepository {
    override fun save(path: File, imageUrl: String, title: String, toneId: String, moduleType: String, position: Int, mix: Float) {
        preferences.edit()
            .putString("cabinet_ir_path", path.absolutePath)
            .putString("cabinet_ir_image", imageUrl)
            .putString("cabinet_ir_title", title)
            .putString("cabinet_ir_tone_id", toneId)
            .putString("cabinet_ir_module_type", moduleType)
            .putInt("cabinet_ir_position", position)
            .putBoolean("cabinet_ir_bypass", false)
            .putFloat("cabinet_ir_in_gain_db", 0.0f)
            .putFloat("cabinet_ir_out_gain_db", 0.0f)
            .putFloat("cabinet_ir_mix", mix)
            .putBoolean("cabinet_ir_eq_pre", false)
            .putBoolean("cabinet_ir_eq_enabled", true)
            .apply { for (band in 0 until 6) putFloat("cabinet_ir_eq_$band", 0.0f) }
            .apply()
    }
}
