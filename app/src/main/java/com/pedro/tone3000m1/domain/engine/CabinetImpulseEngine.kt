package com.pedro.tone3000m1.domain.engine

internal interface CabinetImpulseEngine {
    fun loadImpulseResponse(path: String): String
    fun namBlockCount(): Int
    fun setPosition(namBlocksBefore: Int)
    fun setBypass(bypass: Boolean)
    fun setInGain(db: Float)
    fun setOutGain(db: Float)
    fun setMix(mix: Float)
    fun setEqPre(pre: Boolean)
    fun setEqEnabled(enabled: Boolean)
    fun setEqDb(band: Int, db: Float)
    fun start(): String
}
