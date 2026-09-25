package com.pedro.tone3000m1

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.pedro.tone3000m1.data.repository.AppPreferencesDataStore
import com.pedro.tone3000m1.data.repository.SelectedModuleTypeRepository
import com.pedro.tone3000m1.data.repository.AudioRoutingRepositoryImpl
import com.pedro.tone3000m1.data.repository.CabinetImpulseRepositoryImpl
import com.pedro.tone3000m1.data.repository.CurrentToneRepositoryImpl
import com.pedro.tone3000m1.data.repository.FxChainRepository
import com.pedro.tone3000m1.data.repository.NamChainRepository
import com.pedro.tone3000m1.data.repository.PresetRepositoryImpl
import com.pedro.tone3000m1.data.repository.Tone3000ApiRepository
import com.pedro.tone3000m1.data.repository.ToneImportRepositoryImpl
import com.pedro.tone3000m1.data.repository.TonePackageCaptureRepositoryImpl
import com.pedro.tone3000m1.data.repository.ToneSessionRepositoryImpl
import com.pedro.tone3000m1.domain.repository.PresetRepository
import com.pedro.tone3000m1.domain.usecase.AddExtraNamCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.AudioRoutingUseCase
import com.pedro.tone3000m1.domain.usecase.CommitCurrentToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.CommitExtraToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.CompleteToneSelectionUseCase
import com.pedro.tone3000m1.domain.usecase.DownloadToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.ImportCabinetImpulseUseCase
import com.pedro.tone3000m1.domain.usecase.ImportFxCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.ImportLocalNamFileUseCase
import com.pedro.tone3000m1.domain.usecase.ListToneModelsUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPackageCapturesUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPresetUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPrimaryToneCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.MergePackageCapturesUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareImpulseResponseUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareNamBlockReplacementUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareToneAuthorizationUseCase
import com.pedro.tone3000m1.domain.usecase.RebuildNamChainUseCase
import com.pedro.tone3000m1.domain.usecase.ReplaceNamCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.RestorePreviousToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.SavePresetUseCase
import java.io.File

/** Application dependency graph. Android UI and lifecycle behavior stay in MainActivity. */
internal class PicoloAppContainer(
    context: Context,
    filesDirectory: File,
    private val config: Config,
) {
    data class Config(
        val preferencesName: String,
        val apiBase: String,
        val publishableKey: String,
        val redirectUri: String,
        val lastModelPathKey: String,
        val extraNamChainKey: String,
        val fxChainKey: String,
        val fxNativeChainKey: String,
        val inputChannelKey: String,
        val outputPairKey: String,
        val presetCount: Int,
        val maxNamBlocks: Int,
    )

    private val appContext = context.applicationContext
    val preferences = appContext.getSharedPreferences(config.preferencesName, Context.MODE_PRIVATE)
    val audioEngine by lazy { NativeAudioEngine() }
    private val appPreferences: DataStore<Preferences> by lazy { AppPreferencesDataStore.get(appContext) }

    val namChainRepository by lazy {
        NamChainRepository(preferences, config.extraNamChainKey, config.lastModelPathKey)
    }
    val fxChainRepository by lazy {
        FxChainRepository(preferences, config.fxChainKey, config.fxNativeChainKey)
    }
    val presetRepository: PresetRepository by lazy {
        PresetRepositoryImpl(preferences, filesDirectory, config.presetCount, config.maxNamBlocks)
    }
    val audioRoutingUseCase by lazy {
        AudioRoutingUseCase(
            AudioRoutingRepositoryImpl(
                preferences = preferences,
                engine = audioEngine,
                inputChannelKey = config.inputChannelKey,
                outputPairKey = config.outputPairKey,
            ),
        )
    }
    val savePresetUseCase by lazy { SavePresetUseCase(presetRepository) }
    val loadPresetUseCase by lazy { LoadPresetUseCase(presetRepository, audioEngine) }
    val tone3000ApiRepository by lazy {
        Tone3000ApiRepository(config.apiBase, config.publishableKey, config.redirectUri)
    }
    val toneSessionRepository by lazy { ToneSessionRepositoryImpl(appPreferences) }
    val selectedModuleTypeRepository by lazy { SelectedModuleTypeRepository(appPreferences) }
    val currentToneRepository by lazy { CurrentToneRepositoryImpl(preferences) }
    val activeToneRepository by lazy { currentToneRepository }
    val restorePreviousToneModelUseCase by lazy {
        RestorePreviousToneModelUseCase(currentToneRepository, audioEngine)
    }
    val tonePackageCaptureRepository by lazy { TonePackageCaptureRepositoryImpl(appPreferences) }
    val mergePackageCapturesUseCase by lazy { MergePackageCapturesUseCase(tonePackageCaptureRepository) }
    val loadPackageCapturesUseCase by lazy {
        LoadPackageCapturesUseCase(listToneModelsUseCase, mergePackageCapturesUseCase, tonePackageCaptureRepository)
    }
    val toneImportRepository by lazy { ToneImportRepositoryImpl(filesDirectory) }
    val prepareToneAuthorizationUseCase by lazy { PrepareToneAuthorizationUseCase(toneSessionRepository) }
    val completeToneSelectionUseCase by lazy { CompleteToneSelectionUseCase(tone3000ApiRepository, toneSessionRepository) }
    val listToneModelsUseCase by lazy { ListToneModelsUseCase(tone3000ApiRepository) }
    val downloadToneModelUseCase by lazy { DownloadToneModelUseCase(tone3000ApiRepository, toneImportRepository) }
    val importLocalNamFileUseCase by lazy { ImportLocalNamFileUseCase(toneImportRepository) }
    val prepareImpulseResponseUseCase by lazy { PrepareImpulseResponseUseCase(toneImportRepository) }
    val importFxCaptureUseCase by lazy {
        ImportFxCaptureUseCase(downloadToneModelUseCase, prepareImpulseResponseUseCase, fxChainRepository, audioEngine)
    }
    val importCabinetImpulseUseCase by lazy {
        ImportCabinetImpulseUseCase(
            downloadToneModelUseCase,
            prepareImpulseResponseUseCase,
            CabinetImpulseRepositoryImpl(preferences),
            audioEngine,
        )
    }
    val commitCurrentToneModelUseCase by lazy { CommitCurrentToneModelUseCase(toneImportRepository) }
    val loadPrimaryToneCaptureUseCase by lazy {
        LoadPrimaryToneCaptureUseCase(commitCurrentToneModelUseCase, activeToneRepository, audioEngine)
    }
    val prepareNamBlockReplacementUseCase by lazy { PrepareNamBlockReplacementUseCase() }
    val commitExtraToneModelUseCase by lazy { CommitExtraToneModelUseCase(toneImportRepository) }
    val addExtraNamCaptureUseCase by lazy {
        AddExtraNamCaptureUseCase(commitExtraToneModelUseCase, namChainRepository, audioEngine)
    }
    val rebuildNamChainUseCase by lazy { RebuildNamChainUseCase(audioEngine) }
    val replaceNamCaptureUseCase by lazy {
        ReplaceNamCaptureUseCase(
            commitExtraToneModelUseCase,
            prepareNamBlockReplacementUseCase,
            rebuildNamChainUseCase,
            audioEngine,
        )
    }
}
