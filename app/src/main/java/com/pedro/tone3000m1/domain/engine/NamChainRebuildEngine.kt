package com.pedro.tone3000m1.domain.engine

internal interface NamChainRebuildEngine {
    fun isRunning(): Boolean
    fun clearChain()
    fun loadPrimary(path: String): String
    fun addBlock(path: String): String
    fun setBlockBypass(index: Int, bypass: Boolean)
    fun setBlockGain(index: Int, db: Float)
    fun setBlockInGain(index: Int, db: Float)
    fun setBlockMix(index: Int, mix: Float)
    fun setBlockEqDb(index: Int, band: Int, db: Float)
    fun setBlockEqPre(index: Int, pre: Boolean)
    fun setBlockEqEnabled(index: Int, enabled: Boolean)
    fun setBlockNormalize(index: Int, enabled: Boolean)
    fun setBlockQuality(index: Int, full: Boolean): String
    fun startChain(): String
}
