package com.pedro.tone3000m1

/** JNI boundary for the native audio engine. Keep blocking work off the audio callback. */
internal class NativeAudioEngine {
    external fun nativeLoadModel(path: String): String
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

    private companion object {
        init {
            System.loadLibrary("tone3000m1")
        }
    }
}
