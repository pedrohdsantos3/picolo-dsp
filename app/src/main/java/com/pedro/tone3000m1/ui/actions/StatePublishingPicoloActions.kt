package com.pedro.tone3000m1.ui.actions

/** Runs the state publisher after every UI command, including commands that return a result. */
internal class StatePublishingPicoloActions(
    private val delegate: PicoloActions,
    private val publish: () -> Unit,
) : PicoloActions {
    private inline fun <T> afterAction(action: () -> T): T = try {
        action()
    } finally {
        publish()
    }
    override fun addFxNative(effect: Int) = afterAction { delegate.addFxNative(effect) }
    override suspend fun cycleOutput(): Int = try {
        delegate.cycleOutput()
    } finally {
        publish()
    }
    override fun loadPreset(slot: Int) = afterAction { delegate.loadPreset(slot) }
    override fun moveModule(blockId: String, direction: Int) = afterAction { delegate.moveModule(blockId, direction) }
    override fun removeCabinetIr() = afterAction { delegate.removeCabinetIr() }
    override fun removeFx(fxIndex: Int) = afterAction { delegate.removeFx(fxIndex) }
    override fun removeFxNative(nativeIndex: Int) = afterAction { delegate.removeFxNative(nativeIndex) }
    override fun removeNam(chainIndex: Int) = afterAction { delegate.removeNam(chainIndex) }
    override fun savePreset(slot: Int) = afterAction { delegate.savePreset(slot) }
    override fun scanUsbAudio(): String = afterAction { delegate.scanUsbAudio() }
    override fun selectPackageCaptures(blockId: String): Boolean = afterAction { delegate.selectPackageCaptures(blockId) }
    override fun selectLocalNam(capture: com.pedro.tone3000m1.ui.model.UiLocalNamCapture, importMode: String): Boolean =
        afterAction { delegate.selectLocalNam(capture, importMode) }
    override fun setCabinetBypass(bypassed: Boolean) = afterAction { delegate.setCabinetBypass(bypassed) }
    override fun setCabinetEq(band: Int, db: Double) = afterAction { delegate.setCabinetEq(band, db) }
    override fun setCabinetEqEnabled(enabled: Boolean) = afterAction { delegate.setCabinetEqEnabled(enabled) }
    override fun setCabinetInGain(db: Double) = afterAction { delegate.setCabinetInGain(db) }
    override fun setCabinetMix(mix: Double) = afterAction { delegate.setCabinetMix(mix) }
    override fun setCabinetOutGain(db: Double) = afterAction { delegate.setCabinetOutGain(db) }
    override fun setEqEnabled(enabled: Boolean) = afterAction { delegate.setEqEnabled(enabled) }
    override fun setEqHigh(db: Double) = afterAction { delegate.setEqHigh(db) }
    override fun setEqLow(db: Double) = afterAction { delegate.setEqLow(db) }
    override fun setEqMid(db: Double) = afterAction { delegate.setEqMid(db) }
    override fun setFxBypass(fxIndex: Int, bypassed: Boolean) = afterAction { delegate.setFxBypass(fxIndex, bypassed) }
    override fun setFxMix(fxIndex: Int, mix: Double) = afterAction { delegate.setFxMix(fxIndex, mix) }
    override fun setFxNativeBypass(nativeIndex: Int, bypassed: Boolean) = afterAction { delegate.setFxNativeBypass(nativeIndex, bypassed) }
    override fun setFxNativeMix(nativeIndex: Int, mix: Double) = afterAction { delegate.setFxNativeMix(nativeIndex, mix) }
    override fun setFxNativeOutputGainDb(nativeIndex: Int, gainDb: Double) = afterAction { delegate.setFxNativeOutputGainDb(nativeIndex, gainDb) }
    override fun setFxNativeParameter(nativeIndex: Int, parameter: Int, value: Double) = afterAction { delegate.setFxNativeParameter(nativeIndex, parameter, value) }
    override fun setFxNativeType(nativeIndex: Int, effect: Int) = afterAction { delegate.setFxNativeType(nativeIndex, effect) }
    override fun setFxNativeTiming(nativeIndex: Int, tempoSync: Boolean, leftNote: String, rightNote: String) =
        afterAction { delegate.setFxNativeTiming(nativeIndex, tempoSync, leftNote, rightNote) }
    override fun setGlobalTapTempoBpm(bpm: Float) = afterAction { delegate.setGlobalTapTempoBpm(bpm) }
    override fun setGateEnabled(enabled: Boolean) = afterAction { delegate.setGateEnabled(enabled) }
    override fun setGateThreshold(db: Double) = afterAction { delegate.setGateThreshold(db) }
    override fun setInputGain(db: Double) = afterAction { delegate.setInputGain(db) }
    override fun setNamBypass(chainIndex: Int, enabled: Boolean) = afterAction { delegate.setNamBypass(chainIndex, enabled) }
    override fun setNamEq(chainIndex: Int, band: Int, db: Double) = afterAction { delegate.setNamEq(chainIndex, band, db) }
    override fun setNamEqEnabled(chainIndex: Int, enabled: Boolean) = afterAction { delegate.setNamEqEnabled(chainIndex, enabled) }
    override fun setNamEqPosition(chainIndex: Int, pre: Boolean) = afterAction { delegate.setNamEqPosition(chainIndex, pre) }
    override fun setNamGain(chainIndex: Int, db: Double) = afterAction { delegate.setNamGain(chainIndex, db) }
    override fun setNamInGain(chainIndex: Int, db: Double) = afterAction { delegate.setNamInGain(chainIndex, db) }
    override fun setNamMix(chainIndex: Int, mix: Double) = afterAction { delegate.setNamMix(chainIndex, mix) }
    override fun setNamNormalize(chainIndex: Int, enabled: Boolean) = afterAction { delegate.setNamNormalize(chainIndex, enabled) }
    override fun setNamQuality(chainIndex: Int, full: Boolean) = afterAction { delegate.setNamQuality(chainIndex, full) }
    override fun setOutputGain(db: Double) = afterAction { delegate.setOutputGain(db) }
    override fun startAudio(): String = afterAction { delegate.startAudio() }
    override fun stopAudio(): String = afterAction { delegate.stopAudio() }
}
