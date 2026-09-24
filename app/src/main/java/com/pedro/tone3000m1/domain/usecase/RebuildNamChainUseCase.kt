package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.NamChainRebuildEngine
import com.pedro.tone3000m1.domain.model.ExtraNamEntry

internal class RebuildNamChainUseCase(private val engine: NamChainRebuildEngine) {
    fun execute(entries: List<ExtraNamEntry>): String {
        val wasRunning = engine.isRunning()
        engine.clearChain()

        entries.forEachIndexed { index, entry ->
            val result = if (index == 0) engine.loadPrimary(entry.path) else engine.addBlock(entry.path)
            val loaded = if (index == 0) result.startsWith("MODEL LOADED") else result.startsWith("CHAIN NAM ADDED")
            if (!loaded) return result

            engine.setBlockBypass(index, entry.bypass)
            engine.setBlockGain(index, entry.gainDb)
            engine.setBlockInGain(index, entry.inGainDb)
            engine.setBlockMix(index, entry.mix)
            engine.setBlockEqDb(index, 0, entry.eqLowDb)
            engine.setBlockEqDb(index, 1, entry.eqMidDb)
            engine.setBlockEqDb(index, 2, entry.eqHighDb)
            engine.setBlockEqDb(index, 3, entry.eqBand3Db)
            engine.setBlockEqDb(index, 4, entry.eqBand4Db)
            engine.setBlockEqDb(index, 5, entry.eqBand5Db)
            engine.setBlockEqPre(index, entry.eqPre)
            engine.setBlockEqEnabled(index, entry.eqEnabled)
            engine.setBlockNormalize(index, entry.normalize && entry.moduleType != "PEDAL")
            engine.setBlockQuality(index, entry.a2Full && entry.moduleType == "AMP")
        }

        if (wasRunning) {
            val audioResult = engine.startChain()
            if (!audioResult.startsWith("AUDIO ACTIVE")) return audioResult
        }

        return "NAM CHAIN READY\nblocks=${entries.size}" + if (wasRunning) "\nAUDIO ACTIVE" else ""
    }
}
