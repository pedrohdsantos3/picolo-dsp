package com.pedro.tone3000m1

import com.pedro.tone3000m1.domain.engine.AudioRoutingEngine
import com.pedro.tone3000m1.domain.engine.CabinetImpulseEngine
import com.pedro.tone3000m1.domain.engine.ExtraNamChainEngine
import com.pedro.tone3000m1.domain.engine.NamChainRebuildEngine
import com.pedro.tone3000m1.domain.engine.FxImpulseEngine
import com.pedro.tone3000m1.domain.engine.PresetAudioEngine
import com.pedro.tone3000m1.domain.engine.PrimaryToneCaptureEngine
import com.pedro.tone3000m1.domain.engine.ToneModelEngine

/** JNI boundary for the native audio engine. Keep blocking work off the audio callback. */
internal class NativeAudioEngine : AudioRoutingEngine, PresetAudioEngine, ToneModelEngine, PrimaryToneCaptureEngine, FxImpulseEngine, CabinetImpulseEngine, ExtraNamChainEngine, NamChainRebuildEngine {
    external fun nativeLoadModel(path: String): String

    override fun loadModel(path: String): String = nativeLoadModel(path)

    override fun applyModuleDefaults(moduleType: String) {
        if (moduleType == "PEDAL") {
            nativeSetChainNamGainDb(0, -10.0f)
            nativeSetChainNamInGainDb(0, 0.0f)
            nativeSetChainNamMix(0, 1.0f)
            for (band in 0 until 6) nativeSetChainNamEqDb(0, band, 0.0f)
            nativeSetChainNamEqPre(0, false)
            nativeSetChainNamEqEnabled(0, true)
            nativeSetChainNamNormalize(0, false)
        }
        nativeSetChainNamQuality(0, moduleType == "AMP")
    }

    override fun start(): String = nativeStart()

    override fun loadImpulseResponse(slot: Int, path: String): String = nativeLoadFxImpulseResponse(slot, path)
    override fun setImpulseResponseBypass(slot: Int, bypass: Boolean) = nativeSetFxImpulseResponseBypass(slot, bypass)
    override fun setImpulseResponseMix(slot: Int, mix: Float) = nativeSetFxImpulseResponseMix(slot, mix)
    override fun setImpulseResponsePosition(slot: Int, namBlocksBefore: Int) = nativeSetFxImpulseResponsePosition(slot, namBlocksBefore)
    override fun namBlockCount(): Int = nativeGetNamBlockCount()
    override fun loadImpulseResponse(path: String): String = nativeLoadImpulseResponse(path)
    override fun setPosition(namBlocksBefore: Int) = nativeSetImpulseResponsePosition(namBlocksBefore)
    override fun setBypass(bypass: Boolean) = nativeSetImpulseResponseBypass(bypass)
    override fun setInGain(db: Float) = nativeSetImpulseResponseInGainDb(db)
    override fun setOutGain(db: Float) = nativeSetImpulseResponseOutGainDb(db)
    override fun setMix(mix: Float) = nativeSetImpulseResponseMix(mix)
    override fun setEqPre(pre: Boolean) = nativeSetImpulseResponseEqPre(pre)
    override fun setEqEnabled(enabled: Boolean) = nativeSetImpulseResponseEqEnabled(enabled)
    override fun setEqDb(band: Int, db: Float) = nativeSetImpulseResponseEqDb(band, db)
    override fun addChainModel(path: String): String = nativeAddChainModel(path)
    override fun setBypass(index: Int, bypass: Boolean) = nativeSetChainNamBypass(index, bypass)
    override fun setInGain(index: Int, db: Float) = nativeSetChainNamInGainDb(index, db)
    override fun setMix(index: Int, mix: Float) = nativeSetChainNamMix(index, mix)
    override fun setGain(index: Int, db: Float) = nativeSetChainNamGainDb(index, db)
    override fun setEqDb(index: Int, band: Int, db: Float) = nativeSetChainNamEqDb(index, band, db)
    override fun setEqPre(index: Int, pre: Boolean) = nativeSetChainNamEqPre(index, pre)
    override fun setNormalize(index: Int, normalize: Boolean) = nativeSetChainNamNormalize(index, normalize)
    override fun setEqEnabled(index: Int, enabled: Boolean) = nativeSetChainNamEqEnabled(index, enabled)
    override fun setQuality(index: Int, full: Boolean): String = nativeSetChainNamQuality(index, full)
    override fun isRunning(): Boolean = nativeIsRunning()
    override fun clearChain() { nativeClearNamChain() }
    override fun loadPrimary(path: String): String = nativeLoadModel(path)
    override fun addBlock(path: String): String = nativeAddChainModel(path)
    override fun setBlockBypass(index: Int, bypass: Boolean) = nativeSetChainNamBypass(index, bypass)
    override fun setBlockGain(index: Int, db: Float) = nativeSetChainNamGainDb(index, db)
    override fun setBlockInGain(index: Int, db: Float) = nativeSetChainNamInGainDb(index, db)
    override fun setBlockMix(index: Int, mix: Float) = nativeSetChainNamMix(index, mix)
    override fun setBlockEqDb(index: Int, band: Int, db: Float) = nativeSetChainNamEqDb(index, band, db)
    override fun setBlockEqPre(index: Int, pre: Boolean) = nativeSetChainNamEqPre(index, pre)
    override fun setBlockEqEnabled(index: Int, enabled: Boolean) = nativeSetChainNamEqEnabled(index, enabled)
    override fun setBlockNormalize(index: Int, enabled: Boolean) = nativeSetChainNamNormalize(index, enabled)
    override fun setBlockQuality(index: Int, full: Boolean): String = nativeSetChainNamQuality(index, full)
    override fun startChain(): String = nativeStart()
    external fun nativeLoadImpulseResponse(path: String): String
    external fun nativeSetImpulseResponseBypass(bypass: Boolean)
    external fun nativeClearImpulseResponse()
    external fun nativeSetImpulseResponsePosition(namBlocksBefore: Int)
    external fun nativeSetImpulseResponseInGainDb(db: Float)
    external fun nativeSetImpulseResponseOutGainDb(db: Float)
    external fun nativeSetImpulseResponseMix(mix: Float)
    external fun nativeSetImpulseResponseEqDb(band: Int, db: Float)
    external fun nativeSetImpulseResponseEqPre(pre: Boolean)
    external fun nativeSetImpulseResponseEqEnabled(enabled: Boolean)
    external fun nativeLoadFxImpulseResponse(slot: Int, path: String): String
    external fun nativeClearFxImpulseResponse(slot: Int)
    external fun nativeSetFxImpulseResponseBypass(slot: Int, bypass: Boolean)
    external fun nativeSetFxImpulseResponseMix(slot: Int, mix: Float)
    external fun nativeSetFxImpulseResponsePosition(slot: Int, namBlocksBefore: Int)
    external fun nativeConfigureFxNative(slot: Int, type: Int, namBlocksBefore: Int)
    external fun nativeClearFxNative(slot: Int)
    external fun nativeSetFxNativeBypass(slot: Int, bypass: Boolean)
    external fun nativeSetFxNativeMix(slot: Int, mix: Float)
    external fun nativeSetFxNativeParameter(slot: Int, parameter: Int, value: Float)
    external fun nativeSetFxNativePosition(slot: Int, namBlocksBefore: Int)
    external fun nativeAddChainModel(path: String): String
    external fun nativeClearExtraNamBlocks(): String
    external fun nativeClearNamChain(): String
    external fun nativeSetChainNamBypass(chainIndex: Int, bypass: Boolean)
    external fun nativeSetChainNamQuality(chainIndex: Int, full: Boolean): String
    external fun nativeSetChainNamGainDb(chainIndex: Int, db: Float)
    external fun nativeSetChainNamInGainDb(chainIndex: Int, db: Float)
    external fun nativeSetChainNamMix(chainIndex: Int, mix: Float)
    external fun nativeSetChainNamNormalize(chainIndex: Int, enabled: Boolean)
    external fun nativeSetChainNamEqDb(chainIndex: Int, band: Int, db: Float)
    external fun nativeSetChainNamEqPre(chainIndex: Int, pre: Boolean)
    external fun nativeSetChainNamEqEnabled(chainIndex: Int, enabled: Boolean)
    external fun nativeGetNamBlockCount(): Int
    external fun nativeSwitchPresetGapless(
        path: String,
        inputGainDb: Float,
        outputGainDb: Float,
        inputChannel: Int,
        outputPair: Int,
        gateEnabled: Boolean,
        gateThresholdDb: Float,
        eqLowDb: Float,
        eqMidDb: Float,
        eqHighDb: Float,
    ): String
    external fun nativeStart(): String
    external fun nativeIsRunning(): Boolean
    external fun nativeStop()
    external fun nativeSetBypass(bypass: Boolean)
    external fun nativeSetInputGainDb(gainDb: Float)
    external fun nativeSetOutputGainDb(gainDb: Float)
    external fun nativeSetGateEnabled(enabled: Boolean)
    external fun nativeSetGateThresholdDb(db: Float)
    external fun nativeSetEqLowDb(db: Float)
    external fun nativeSetEqMidDb(db: Float)
    external fun nativeSetEqHighDb(db: Float)
    external fun nativeSetEqEnabled(enabled: Boolean)
    external fun nativeGetDspChainInfo(): String
    external fun nativeSetInputChannel(channel: Int)
    external fun nativeSetOutputPair(pairIndex: Int)
    external fun nativeCycleInputChannel(): Int
    external fun nativeCycleOutputPair(): Int
    external fun nativeGetRoutingInfo(): String
    external fun nativeScanUsbAudio(): String
    external fun nativeGetAudioDeviceInfo(): String
    external fun nativeGetStats(): String

    override fun cycleInputChannel(): Int = nativeCycleInputChannel()

    override fun cycleOutputPair(): Int = nativeCycleOutputPair()

    override fun setInputChannel(channel: Int) = nativeSetInputChannel(channel)

    override fun setOutputPair(pairIndex: Int) = nativeSetOutputPair(pairIndex)

    override fun routingInfo(): String = nativeGetRoutingInfo()

    override fun switchPresetGapless(
        path: String,
        inputGainDb: Float,
        outputGainDb: Float,
        inputChannel: Int,
        outputPair: Int,
        gateEnabled: Boolean,
        gateThresholdDb: Float,
        eqLowDb: Float,
        eqMidDb: Float,
        eqHighDb: Float,
    ): String = nativeSwitchPresetGapless(
        path,
        inputGainDb,
        outputGainDb,
        inputChannel,
        outputPair,
        gateEnabled,
        gateThresholdDb,
        eqLowDb,
        eqMidDb,
        eqHighDb,
    )

    private companion object {
        init {
            System.loadLibrary("tone3000m1")
        }
    }
}
