package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.PresetData

internal interface PresetRepository {
    fun read(slot: Int): PresetData?
    fun saveCurrent(slot: Int, namBypassFallback: Boolean): String
    fun activate(preset: PresetData)
    fun label(slot: Int): String
    fun rename(slot: Int, name: String): Boolean
    fun delete(slot: Int): Boolean
    fun move(slot: Int, delta: Int): Boolean
}
