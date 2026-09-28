package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.NamChainRebuildEngine
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.NamEqDefaults

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
            val gains = listOf(entry.eqLowDb, entry.eqMidDb, entry.eqHighDb, entry.eqBand3Db, entry.eqBand4Db, entry.eqBand5Db)
            repeat(6) { band ->
                engine.setBlockEqBand(
                    index,
                    band,
                    entry.eqTypes.getOrElse(band) { NamEqDefaults.types[band] },
                    entry.eqFrequenciesHz.getOrElse(band) { NamEqDefaults.frequenciesHz[band] },
                    gains[band],
                    entry.eqQValues.getOrElse(band) { NamEqDefaults.qValues[band] },
                )
            }
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
