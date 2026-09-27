package com.pedro.tone3000m1.ui.actions

import com.pedro.tone3000m1.controller.AudioParameterController
import com.pedro.tone3000m1.controller.AudioSessionController
import com.pedro.tone3000m1.controller.CabinetParameterController
import com.pedro.tone3000m1.controller.CabinetRemovalController
import com.pedro.tone3000m1.controller.FxChainRemovalController
import com.pedro.tone3000m1.controller.FxParameterController
import com.pedro.tone3000m1.controller.NamParameterController
import com.pedro.tone3000m1.controller.PackageCaptureController
import com.pedro.tone3000m1.domain.engine.TunerEngine
import com.pedro.tone3000m1.ui.model.UiLocalNamCapture

/** Adapts tested domain controllers to the command contract consumed by Compose. */
internal class PicoloActionsCoordinator(
    private val audioParameters: AudioParameterController,
    private val audioSession: AudioSessionController,
    private val namParameters: NamParameterController,
    private val cabinetParameters: CabinetParameterController,
    private val fxParameters: FxParameterController,
    private val removeCabinet: CabinetRemovalController,
    private val removeFxChain: FxChainRemovalController,
    private val removeNamAction: (Int) -> Unit,
    private val packageCaptureAction: PackageCaptureController,
    private val localNamAction: (UiLocalNamCapture, String) -> Boolean,
    private val moveModuleAction: (String, Int) -> Unit,
    private val addFxNativeAction: (Int) -> Unit,
    private val removeFxNativeAction: (Int) -> Unit,
    private val setFxNativeTypeAction: (Int, Int) -> Unit,
    private val persistGlobalTapTempo: (Float) -> Unit,
    private val loadPresetAction: (Int) -> Unit,
    private val savePresetAction: (Int) -> Unit,
    private val scanUsbAudioAction: suspend () -> String,
    private val startAudioAction: () -> String,
    private val stopAudioAction: () -> String,
    private val tunerEngine: TunerEngine,
) : PicoloActions {
    override fun setInputGain(db: Double) = audioParameters.setInputGain(db)
    override fun setOutputGain(db: Double) = audioParameters.setOutputGain(db)
    override fun setGateEnabled(enabled: Boolean) = audioParameters.setGateEnabled(enabled)
    override fun setGateThreshold(db: Double) = audioParameters.setGateThreshold(db)
    override fun setEqLow(db: Double) = audioParameters.setEqLow(db)
    override fun setEqMid(db: Double) = audioParameters.setEqMid(db)
    override fun setEqHigh(db: Double) = audioParameters.setEqHigh(db)
    override fun setEqEnabled(enabled: Boolean) = audioParameters.setEqEnabled(enabled)

    override suspend fun cycleOutput(): Int = audioSession.cycleOutput()
    override fun startAudio(): String = startAudioAction.invoke()
    override fun stopAudio(): String = stopAudioAction.invoke()
    override fun setTunerEnabled(enabled: Boolean) = tunerEngine.setTunerEnabled(enabled)
    override fun setTunerMuted(muted: Boolean) = tunerEngine.setOutputMuted(muted)
    override fun tunerFrequencyHz(): Float = tunerEngine.tunerFrequencyHz()
    override fun tunerInputLevel(): Float = tunerEngine.tunerInputLevel()

    override fun addFxNative(effect: Int) = addFxNativeAction.invoke(effect)
    override fun removeFxNative(nativeIndex: Int) = removeFxNativeAction.invoke(nativeIndex)
    override fun setFxNativeType(nativeIndex: Int, effect: Int) = setFxNativeTypeAction.invoke(nativeIndex, effect)
    override fun setFxNativeTiming(nativeIndex: Int, tempoSync: Boolean, leftNote: String, rightNote: String) =
        fxParameters.setNativeTiming(nativeIndex, tempoSync, leftNote, rightNote)
    override fun setGlobalTapTempoBpm(bpm: Float) {
        val normalized = bpm.coerceIn(40f, 240f)
        persistGlobalTapTempo(normalized)
        fxParameters.setGlobalTapTempoBpm(normalized)
    }
    override fun setFxNativeBypass(nativeIndex: Int, bypassed: Boolean) = fxParameters.setNativeBypass(nativeIndex, bypassed)
    override fun setFxNativeMix(nativeIndex: Int, mix: Double) = fxParameters.setNativeMix(nativeIndex, mix)
    override fun setFxNativeOutputGainDb(nativeIndex: Int, gainDb: Double) = fxParameters.setNativeOutputGainDb(nativeIndex, gainDb)
    override fun setFxNativeParameter(nativeIndex: Int, parameter: Int, value: Double) =
        fxParameters.setNativeParameter(nativeIndex, parameter, value)

    override fun setFxBypass(fxIndex: Int, bypassed: Boolean) = fxParameters.setFxBypass(fxIndex, bypassed)
    override fun setFxMix(fxIndex: Int, mix: Double) = fxParameters.setFxMix(fxIndex, mix)
    override fun removeFx(fxIndex: Int) {
        removeFxChain.remove(fxIndex)
    }

    override fun setCabinetBypass(bypassed: Boolean) = cabinetParameters.setBypass(bypassed)
    override fun setCabinetInGain(db: Double) = cabinetParameters.setInGain(db)
    override fun setCabinetOutGain(db: Double) = cabinetParameters.setOutGain(db)
    override fun setCabinetMix(mix: Double) = cabinetParameters.setMix(mix)
    override fun setCabinetEq(band: Int, db: Double) = cabinetParameters.setEq(band, db)
    override fun setCabinetEqEnabled(enabled: Boolean) = cabinetParameters.setEqEnabled(enabled)
    override fun removeCabinetIr() = removeCabinet.remove()

    override fun removeNam(chainIndex: Int) = removeNamAction.invoke(chainIndex)
    override fun setNamBypass(chainIndex: Int, enabled: Boolean) = namParameters.setBypass(chainIndex, enabled)
    override fun setNamGain(chainIndex: Int, db: Double) = namParameters.setGain(chainIndex, db)
    override fun setNamInGain(chainIndex: Int, db: Double) = namParameters.setInGain(chainIndex, db)
    override fun setNamMix(chainIndex: Int, mix: Double) = namParameters.setMix(chainIndex, mix)
    override fun setNamEq(chainIndex: Int, band: Int, db: Double) = namParameters.setEq(chainIndex, band, db)
    override fun setNamEqFrequency(chainIndex: Int, band: Int, frequencyHz: Float) = namParameters.setEqFrequency(chainIndex, band, frequencyHz)
    override fun setNamEqQ(chainIndex: Int, band: Int, q: Float) = namParameters.setEqQ(chainIndex, band, q)
    override fun setNamEqType(chainIndex: Int, band: Int, type: String) = namParameters.setEqType(chainIndex, band, type)
    override fun setNamEqPosition(chainIndex: Int, pre: Boolean) = namParameters.setEqPosition(chainIndex, pre)
    override fun setNamEqEnabled(chainIndex: Int, enabled: Boolean) = namParameters.setEqEnabled(chainIndex, enabled)
    override fun setNamNormalize(chainIndex: Int, enabled: Boolean) = namParameters.setNormalize(chainIndex, enabled)
    override fun setNamQuality(chainIndex: Int, full: Boolean) = namParameters.setQuality(chainIndex, full)
    override fun resetNamParameters(chainIndex: Int) = namParameters.resetParameters(chainIndex)

    override fun moveModule(blockId: String, direction: Int) = moveModuleAction.invoke(blockId, direction)
    override fun selectPackageCaptures(blockId: String): Boolean = packageCaptureAction.selectPackageCaptures(blockId)
    override fun selectLocalNam(capture: UiLocalNamCapture, importMode: String): Boolean =
        localNamAction(capture, importMode)
    override fun loadPreset(slot: Int) = loadPresetAction.invoke(slot)
    override fun savePreset(slot: Int) = savePresetAction.invoke(slot)
    override suspend fun scanUsbAudio(): String = scanUsbAudioAction.invoke()
}
