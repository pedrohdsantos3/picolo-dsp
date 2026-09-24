package com.pedro.tone3000m1.domain.engine

internal interface ExtraNamChainEngine {
    fun addChainModel(path: String): String
    fun namBlockCount(): Int
    fun setBypass(index: Int, bypass: Boolean)
    fun setInGain(index: Int, db: Float)
    fun setMix(index: Int, mix: Float)
    fun setGain(index: Int, db: Float)
    fun setEqDb(index: Int, band: Int, db: Float)
    fun setEqPre(index: Int, pre: Boolean)
    fun setNormalize(index: Int, normalize: Boolean)
    fun setEqEnabled(index: Int, enabled: Boolean)
    fun setQuality(index: Int, full: Boolean): String
    fun start(): String
}
