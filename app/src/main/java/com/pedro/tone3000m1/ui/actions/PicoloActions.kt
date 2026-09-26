package com.pedro.tone3000m1.ui.actions

import com.pedro.tone3000m1.ui.model.UiLocalNamCapture

/** Commands the Compose UI can send to the audio application. */
internal interface PicoloActions {
    fun addFxNative(effect: Int)
    suspend fun cycleOutput(): Int
    fun loadPreset(slot: Int)
    fun moveModule(blockId: String, direction: Int)
    fun removeCabinetIr()
    fun removeFx(fxIndex: Int)
    fun removeFxNative(nativeIndex: Int)
    fun removeNam(chainIndex: Int)
    fun savePreset(slot: Int)
    fun scanUsbAudio(): String
    fun selectPackageCaptures(blockId: String): Boolean
    fun selectLocalNam(capture: UiLocalNamCapture, importMode: String): Boolean
    fun setCabinetBypass(bypassed: Boolean)
    fun setCabinetEq(band: Int, db: Double)
    fun setCabinetEqEnabled(enabled: Boolean)
    fun setCabinetInGain(db: Double)
    fun setCabinetMix(mix: Double)
    fun setCabinetOutGain(db: Double)
    fun setEqEnabled(enabled: Boolean)
    fun setEqHigh(db: Double)
    fun setEqLow(db: Double)
    fun setEqMid(db: Double)
    fun setFxBypass(fxIndex: Int, bypassed: Boolean)
    fun setFxMix(fxIndex: Int, mix: Double)
    fun setFxNativeBypass(nativeIndex: Int, bypassed: Boolean)
    fun setFxNativeMix(nativeIndex: Int, mix: Double)
    fun setFxNativeOutputGainDb(nativeIndex: Int, gainDb: Double)
    fun setFxNativeParameter(nativeIndex: Int, parameter: Int, value: Double)
    fun setFxNativeType(nativeIndex: Int, effect: Int)
    fun setFxNativeTiming(nativeIndex: Int, tempoSync: Boolean, leftNote: String, rightNote: String)
    fun setGlobalTapTempoBpm(bpm: Float)
    fun setGateEnabled(enabled: Boolean)
    fun setGateThreshold(db: Double)
    fun setInputGain(db: Double)
    fun setNamBypass(chainIndex: Int, enabled: Boolean)
    fun setNamEq(chainIndex: Int, band: Int, db: Double)
    fun setNamEqEnabled(chainIndex: Int, enabled: Boolean)
    fun setNamEqPosition(chainIndex: Int, pre: Boolean)
    fun setNamGain(chainIndex: Int, db: Double)
    fun setNamInGain(chainIndex: Int, db: Double)
    fun setNamMix(chainIndex: Int, mix: Double)
    fun setNamNormalize(chainIndex: Int, enabled: Boolean)
    fun setNamQuality(chainIndex: Int, full: Boolean)
    fun setOutputGain(db: Double)
    fun startAudio(): String
    fun stopAudio(): String
}
