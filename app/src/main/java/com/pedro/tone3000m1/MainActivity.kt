package com.pedro.tone3000m1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.data.repository.NamChainRepository
import com.pedro.tone3000m1.data.repository.FxChainRepository
import com.pedro.tone3000m1.data.repository.PresetRepositoryImpl
import com.pedro.tone3000m1.data.repository.AudioRoutingRepositoryImpl
import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import com.pedro.tone3000m1.controller.AudioParameterController
import com.pedro.tone3000m1.ui.state.PicoloStateRepository
import com.pedro.tone3000m1.ui.model.readPicoloState
import com.pedro.tone3000m1.data.repository.Tone3000ApiRepository
import com.pedro.tone3000m1.data.repository.ToneImportRepositoryImpl
import com.pedro.tone3000m1.data.repository.ToneSessionRepositoryImpl
import com.pedro.tone3000m1.data.repository.TonePackageCaptureRepositoryImpl
import com.pedro.tone3000m1.data.repository.AppPreferencesDataStore
import com.pedro.tone3000m1.data.repository.CurrentToneRepositoryImpl
import com.pedro.tone3000m1.data.repository.CabinetImpulseRepositoryImpl
import com.pedro.tone3000m1.domain.usecase.AudioRoutingUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPresetUseCase
import com.pedro.tone3000m1.domain.usecase.SavePresetUseCase
import com.pedro.tone3000m1.domain.model.PresetData
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.PresetRepository
import com.pedro.tone3000m1.domain.usecase.CompleteToneSelectionUseCase
import com.pedro.tone3000m1.domain.usecase.CommitCurrentToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.CommitExtraToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.DownloadToneModelUseCase
import com.pedro.tone3000m1.domain.usecase.ImportLocalNamFileUseCase
import com.pedro.tone3000m1.domain.usecase.ListToneModelsUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPackageCapturesUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPrimaryToneCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.ImportFxCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.ImportCabinetImpulseUseCase
import com.pedro.tone3000m1.domain.usecase.AddExtraNamCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.RebuildNamChainUseCase
import com.pedro.tone3000m1.domain.usecase.ReplaceNamCaptureUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareNamBlockReplacementUseCase
import com.pedro.tone3000m1.domain.usecase.MergePackageCapturesUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareImpulseResponseUseCase
import com.pedro.tone3000m1.domain.usecase.PrepareToneAuthorizationUseCase
import com.pedro.tone3000m1.domain.usecase.RestorePreviousToneModelUseCase
import com.pedro.tone3000m1.ui.actions.PicoloActions
import com.pedro.tone3000m1.ui.actions.StatePublishingPicoloActions
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {

        private const val API_TAG =
            "Tone3000Api"

        /*
         * MANTENHA SUA PUBLISHABLE KEY ATUAL AQUI.
         */
        private const val PUBLISHABLE_KEY =
            "t3k_pub_DsUzerYA0GTgcxCtNMeP434SrTxhO_Q0"

        private const val REDIRECT_URI =
            "tone3000m1://callback"

        private const val API_BASE =
            "https://www.tone3000.com"

        private const val AUTHORIZE_URL =
            "$API_BASE/api/v1/oauth/authorize"

        private const val PREFS =
            "tone3000"

        private const val PREF_LAST_MODEL_PATH =
            PresetPreferenceKeys.LAST_MODEL_PATH

        private const val PREF_LAST_MODEL_NAME =
            PresetPreferenceKeys.LAST_MODEL_NAME

        private const val PREF_LAST_MODEL_SIZE =
            PresetPreferenceKeys.LAST_MODEL_SIZE

        private const val PREF_LAST_TONE_ID =
            PresetPreferenceKeys.LAST_TONE_ID

        private const val PREF_LAST_TONE_TITLE =
            PresetPreferenceKeys.LAST_TONE_TITLE

        private const val PREF_LAST_TONE_IMAGE =
            "last_tone_image"

        private const val PREF_LAST_MODEL_TYPE = PresetPreferenceKeys.LAST_MODEL_TYPE

        private const val PREF_CABINET_IR_PATH =
            PresetPreferenceKeys.CABINET_IR_PATH
        private const val PREF_FX_CHAIN = "fx_ir_chain_json"
        private const val PREF_FX_NATIVE_CHAIN = "fx_native_chain_json"

        private const val PREF_CABINET_IR_IMAGE =
            "cabinet_ir_image"

        private const val PREF_CABINET_IR_TITLE = PresetPreferenceKeys.CABINET_IR_TITLE
        private const val PREF_CABINET_IR_TYPE = PresetPreferenceKeys.CABINET_IR_TYPE
        private const val PREF_CABINET_IR_TONE_ID = PresetPreferenceKeys.CABINET_IR_TONE_ID

        private const val PREF_CABINET_IR_BYPASS =
            PresetPreferenceKeys.CABINET_IR_BYPASS

        private const val PREF_CABINET_IR_POSITION =
            PresetPreferenceKeys.CABINET_IR_POSITION

        private const val PREF_CABINET_IR_IN_GAIN = PresetPreferenceKeys.CABINET_IR_IN_GAIN
        private const val PREF_CABINET_IR_OUT_GAIN = PresetPreferenceKeys.CABINET_IR_OUT_GAIN
        private const val PREF_CABINET_IR_MIX = PresetPreferenceKeys.CABINET_IR_MIX
        private const val PREF_CABINET_IR_EQ_PRE = PresetPreferenceKeys.CABINET_IR_EQ_PRE
        private const val PREF_CABINET_IR_EQ_PREFIX = PresetPreferenceKeys.CABINET_IR_EQ_PREFIX

        private const val PREF_INPUT_GAIN =
            PresetPreferenceKeys.INPUT_GAIN

        private const val PREF_OUTPUT_GAIN =
            PresetPreferenceKeys.OUTPUT_GAIN

        private const val PREF_INPUT_CHANNEL =
            PresetPreferenceKeys.INPUT_CHANNEL

        private const val PREF_OUTPUT_PAIR =
            PresetPreferenceKeys.OUTPUT_PAIR

        private const val PREF_ACTIVE_PRESET_SLOT = PresetPreferenceKeys.ACTIVE_PRESET_SLOT

        private const val PREF_GATE_ENABLED =
            PresetPreferenceKeys.GATE_ENABLED

        private const val PREF_GATE_THRESHOLD =
            PresetPreferenceKeys.GATE_THRESHOLD

        private const val PREF_EQ_LOW =
            PresetPreferenceKeys.EQ_LOW

        private const val PREF_EQ_MID =
            PresetPreferenceKeys.EQ_MID

        private const val PREF_EQ_HIGH =
            PresetPreferenceKeys.EQ_HIGH

        private const val PREF_EQ_ENABLED = PresetPreferenceKeys.EQ_ENABLED

        private const val PREF_EXTRA_NAM_CHAIN =
            PresetPreferenceKeys.EXTRA_NAM_CHAIN

        private const val PREF_NAM_GAIN_DB = PresetPreferenceKeys.NAM_GAIN_DB
        private const val PREF_NAM_IN_GAIN_DB = PresetPreferenceKeys.NAM_IN_GAIN_DB
        private const val PREF_NAM_MIX = PresetPreferenceKeys.NAM_MIX
        private const val PREF_NAM_EQ_LOW_DB = PresetPreferenceKeys.NAM_EQ_LOW_DB
        private const val PREF_NAM_EQ_MID_DB = PresetPreferenceKeys.NAM_EQ_MID_DB
        private const val PREF_NAM_EQ_HIGH_DB = PresetPreferenceKeys.NAM_EQ_HIGH_DB
        private const val PREF_NAM_EQ_BAND3_DB = PresetPreferenceKeys.NAM_EQ_BAND3_DB
        private const val PREF_NAM_EQ_BAND4_DB = PresetPreferenceKeys.NAM_EQ_BAND4_DB
        private const val PREF_NAM_EQ_BAND5_DB = PresetPreferenceKeys.NAM_EQ_BAND5_DB
        private const val PREF_NAM_BYPASS = PresetPreferenceKeys.NAM_BYPASS
        private const val PREF_NAM_EQ_PRE = PresetPreferenceKeys.NAM_EQ_PRE
        private const val PREF_NAM_EQ_ENABLED = PresetPreferenceKeys.NAM_EQ_ENABLED
        private const val PREF_CABINET_IR_EQ_ENABLED = "cabinet_ir_eq_enabled"
        private const val PREF_NAM_NORMALIZE = PresetPreferenceKeys.NAM_NORMALIZE
        private const val PREF_NAM_A2_FULL = PresetPreferenceKeys.NAM_A2_FULL
        private const val PREF_EXPERIMENT_DEFAULTS_APPLIED = "experiment_defaults_applied_v1"

        private const val PREF_PENDING_IMPORT_MODE =
            "pending_import_mode"

        private const val PREF_PENDING_TONE_IMAGE =
            "pending_tone_image"

        private const val PREF_PENDING_TONE_TYPE =
            "pending_tone_type"

        private const val PREF_SELECTED_ADD_TYPE =
            "selected_add_type"

        private const val MAX_NAM_BLOCKS =
            4

        private const val PRESET_COUNT =
            4

    }


    // ========================================================
    // UI
    // ========================================================

    private lateinit var composeView: ComposeView

    private val status = MutableStateFlow("")
    private var picoloStateRepository: PicoloStateRepository? = null
    private val stateSnapshotVersion = AtomicLong(0L)
    private var bypass =
        false

    // ========================================================
    // OAUTH / LIFECYCLE
    // ========================================================

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var pendingOAuthIntent: Intent? =
        null

    @Volatile
    private var oauthCallbackInProgress =
        false


    // ========================================================
    // PREFS
    // ========================================================

    private val appContainer by lazy {
        PicoloAppContainer(
            context = applicationContext,
            filesDirectory = filesDir,
            config = PicoloAppContainer.Config(
                preferencesName = PREFS,
                apiBase = API_BASE,
                publishableKey = PUBLISHABLE_KEY,
                redirectUri = REDIRECT_URI,
                lastModelPathKey = PREF_LAST_MODEL_PATH,
                extraNamChainKey = PREF_EXTRA_NAM_CHAIN,
                fxChainKey = PREF_FX_CHAIN,
                fxNativeChainKey = PREF_FX_NATIVE_CHAIN,
                inputChannelKey = PREF_INPUT_CHANNEL,
                outputPairKey = PREF_OUTPUT_PAIR,
                presetCount = PRESET_COUNT,
                maxNamBlocks = MAX_NAM_BLOCKS,
            ),
        )
    }

    private val prefs get() = appContainer.preferences
    private val audioEngine get() = appContainer.audioEngine
    private val audioParameterController by lazy {
        AudioParameterController(
            saveFloatPreference = { key, value -> prefs.edit().putFloat(key, value).apply() },
            saveBooleanPreference = { key, value -> prefs.edit().putBoolean(key, value).apply() },
            setInputGainNative = audioEngine::nativeSetInputGainDb,
            setOutputGainNative = audioEngine::nativeSetOutputGainDb,
            setGateEnabledNative = audioEngine::nativeSetGateEnabled,
            setGateThresholdNative = audioEngine::nativeSetGateThresholdDb,
            setEqLowNative = audioEngine::nativeSetEqLowDb,
            setEqMidNative = audioEngine::nativeSetEqMidDb,
            setEqHighNative = audioEngine::nativeSetEqHighDb,
            setEqEnabledNative = audioEngine::nativeSetEqEnabled,
        )
    }
    private val namChainRepository get() = appContainer.namChainRepository
    private val fxChainRepository get() = appContainer.fxChainRepository
    private val importFxCaptureUseCase get() = appContainer.importFxCaptureUseCase
    private val importCabinetImpulseUseCase get() = appContainer.importCabinetImpulseUseCase
    private val presetRepository get() = appContainer.presetRepository
    private val audioRoutingUseCase get() = appContainer.audioRoutingUseCase
    private val savePresetUseCase get() = appContainer.savePresetUseCase
    private val loadPresetUseCase get() = appContainer.loadPresetUseCase
    private val tone3000ApiRepository get() = appContainer.tone3000ApiRepository
    private val toneSessionRepository get() = appContainer.toneSessionRepository
    private val currentToneRepository get() = appContainer.currentToneRepository
    private val activeToneRepository get() = appContainer.activeToneRepository
    private val restorePreviousToneModelUseCase get() = appContainer.restorePreviousToneModelUseCase
    private val tonePackageCaptureRepository get() = appContainer.tonePackageCaptureRepository
    private val mergePackageCapturesUseCase get() = appContainer.mergePackageCapturesUseCase
    private val loadPackageCapturesUseCase get() = appContainer.loadPackageCapturesUseCase
    private val toneImportRepository get() = appContainer.toneImportRepository
    private val prepareToneAuthorizationUseCase get() = appContainer.prepareToneAuthorizationUseCase
    private val completeToneSelectionUseCase get() = appContainer.completeToneSelectionUseCase
    private val listToneModelsUseCase get() = appContainer.listToneModelsUseCase
    private val downloadToneModelUseCase get() = appContainer.downloadToneModelUseCase
    private val importLocalNamFileUseCase get() = appContainer.importLocalNamFileUseCase
    private val prepareImpulseResponseUseCase get() = appContainer.prepareImpulseResponseUseCase
    private val commitCurrentToneModelUseCase get() = appContainer.commitCurrentToneModelUseCase
    private val loadPrimaryToneCaptureUseCase get() = appContainer.loadPrimaryToneCaptureUseCase
    private val prepareNamBlockReplacementUseCase get() = appContainer.prepareNamBlockReplacementUseCase
    private val commitExtraToneModelUseCase get() = appContainer.commitExtraToneModelUseCase
    private val addExtraNamCaptureUseCase get() = appContainer.addExtraNamCaptureUseCase
    private val rebuildNamChainUseCase get() = appContainer.rebuildNamChainUseCase
    private val replaceNamCaptureUseCase get() = appContainer.replaceNamCaptureUseCase


    // ========================================================
    // PERMISSION
    // ========================================================

    private val requestMicPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                if (
                    pendingOAuthIntent ==
                    null
                ) {
                    restoreLastModel()
                }

            } else {

                status.value =
                    "RECORD_AUDIO permission denied"
            }
        }

    private val openIrFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            Thread {
                try {
                    val destination = File(filesDir, "cabinet-${System.currentTimeMillis()}.wav")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Unable to open selected IR" }
                        FileOutputStream(destination).use { output -> input.copyTo(output) }
                    }
                    val result = audioEngine.nativeLoadImpulseResponse(destination.absolutePath)
                    if (result.startsWith("IR LOADED")) {
                        val position = if (prefs.contains(PREF_CABINET_IR_POSITION)) {
                            prefs.getInt(PREF_CABINET_IR_POSITION, audioEngine.nativeGetNamBlockCount())
                        } else {
                            audioEngine.nativeGetNamBlockCount()
                        }.coerceIn(0, audioEngine.nativeGetNamBlockCount())
                        audioEngine.nativeSetImpulseResponsePosition(position)
                        prefs.edit()
                            .putString(PREF_CABINET_IR_PATH, destination.absolutePath)
                            .putString(PREF_CABINET_IR_TITLE, destination.nameWithoutExtension)
                            .remove(PREF_CABINET_IR_TONE_ID)
                            .putString(PREF_CABINET_IR_TYPE, "IR")
                            .putInt(PREF_CABINET_IR_POSITION, position)
                            .apply()
                    }
                    runOnUiThread { status.value = result }
                } catch (e: Exception) {
                    runOnUiThread { status.value = "IR LOAD FAILED\n${e.message}" }
                }
            }.start()
        }


    // ========================================================
    // ACTIVITY
    // ========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        supportActionBar?.hide()

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        createUi()

        applyRequestedAudioDefaultsOnce()
        restoreGainSettings()
        restoreRoutingSettings()
        restoreDspSettings()
        syncFxNativeChain(readFxNativeChain(), reset = true)

        val launchedFromOAuth =
            isOAuthCallback(
                intent?.data
            )

        if (launchedFromOAuth) {
            pendingOAuthIntent =
                intent
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            if (!launchedFromOAuth) {
                restoreLastModel()
            }

        } else {

            requestMicPermission.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }


    override fun onNewIntent(
        intent: Intent
    ) {

        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        if (
            isOAuthCallback(
                intent.data
            )
        ) {

            pendingOAuthIntent =
                intent

            Log.i(
                API_TAG,
                "OAuth callback received"
            )

            status.value = "TONE3000 callback received...\nPreparing selection..."

            // singleTask activities can receive onNewIntent while already
            // resumed; in that case Android does not call onResume again.
            processPendingOAuthIntent()
        }
    }


    override fun onResume() {

        super.onResume()

        processPendingOAuthIntent()
    }

    private fun processPendingOAuthIntent() {

        val pending =
            pendingOAuthIntent
                ?: return

        pendingOAuthIntent =
            null

        mainHandler.postDelayed(
            {
                status.value = "TONE3000 selection received...\nConnecting to catalog..."
                handleOAuthIntent(pending)
            },
            500
        )
    }


    override fun onStop() {

        audioEngine.nativeStop()

        super.onStop()
    }


    private fun isOAuthCallback(
        uri: Uri?
    ): Boolean {

        return uri?.scheme == "tone3000m1" &&
                uri.host == "callback"
    }


    // ========================================================
    // UI
    // ========================================================

    private fun createUi() {
        // Compose is the only attached UI. Collect status changes to publish snapshots.
        lifecycleScope.launch {
            status.collect { publishComposeState() }
        }

        val composeViewModel = ViewModelProvider(this)[PicoloComposeViewModel::class.java]
        val composeActions = AudioAppController()
        val stateRepository = PicoloStateRepository(readStats = {
            withContext(Dispatchers.IO) { composeActions.getStats() }
        })
        picoloStateRepository = stateRepository
        composeViewModel.observe(stateRepository)
        val publishingActions = StatePublishingPicoloActions(composeActions, ::publishComposeState)
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                PicoloComposeApp(
                    actions = publishingActions,
                    viewModel = composeViewModel,
                    onBrowse = { mode -> startTone3000SelectFlow(mode) },
                )
            }
        }
        setContentView(composeView)
        publishComposeState()
    }

    private fun publishComposeState() {
        val repository = picoloStateRepository ?: return
        val requestVersion = stateSnapshotVersion.incrementAndGet()
        lifecycleScope.launch(Dispatchers.IO) {
            val serializedState = pluginStateJson()
            val statusText = withContext(Dispatchers.Main.immediate) { status.value }
            if (requestVersion == stateSnapshotVersion.get()) {
                repository.publish(readPicoloState(serializedState, statusText))
            }
        }
    }


    // ========================================================
    // MULTI-NAM CHAIN
    // ========================================================

    private fun readExtraNamChain(): MutableList<ExtraNamEntry> =
        namChainRepository.readExtraNamChain()

    private fun persistExtraNamChain(entries: List<ExtraNamEntry>) =
        namChainRepository.persistExtraNamChain(entries)
    private fun readNamChainEntries(): MutableList<ExtraNamEntry> {
        val result = mutableListOf<ExtraNamEntry>()
        val firstPath = prefs.getString(PREF_LAST_MODEL_PATH, null)

        if (firstPath != null && File(firstPath).exists()) {
            result.add(
                ExtraNamEntry(
                    toneId = prefs.getString(PREF_LAST_TONE_ID, "") ?: "",
                    toneTitle = prefs.getString(PREF_LAST_TONE_TITLE, "") ?: "",
                    modelId = 0L,
                    modelName = prefs.getString(PREF_LAST_MODEL_NAME, File(firstPath).name)
                        ?: File(firstPath).name,
                    size = prefs.getString(PREF_LAST_MODEL_SIZE, "unknown") ?: "unknown",
                    path = firstPath,
                    // A missing preference means a fresh active block, never
                    // the stale value left in the Activity from a previous chain.
                    bypass = prefs.getBoolean(PREF_NAM_BYPASS, false),
                    gainDb = prefs.getFloat(PREF_NAM_GAIN_DB, -15.0f),
                    inGainDb = prefs.getFloat(PREF_NAM_IN_GAIN_DB, 0.0f),
                    mix = prefs.getFloat(PREF_NAM_MIX, 1.0f),
                    eqLowDb = prefs.getFloat(PREF_NAM_EQ_LOW_DB, 0.0f),
                    eqMidDb = prefs.getFloat(PREF_NAM_EQ_MID_DB, 0.0f),
                    eqHighDb = prefs.getFloat(PREF_NAM_EQ_HIGH_DB, 0.0f)
                    ,eqBand3Db = prefs.getFloat(PREF_NAM_EQ_BAND3_DB, 0.0f)
                    ,eqBand4Db = prefs.getFloat(PREF_NAM_EQ_BAND4_DB, 0.0f)
                    ,eqBand5Db = prefs.getFloat(PREF_NAM_EQ_BAND5_DB, 0.0f)
                    ,eqPre = prefs.getBoolean(PREF_NAM_EQ_PRE, false)
                    ,eqEnabled = prefs.getBoolean(PREF_NAM_EQ_ENABLED, true)
                    ,normalize = prefs.getBoolean(PREF_NAM_NORMALIZE, true)
                    ,a2Full = prefs.getBoolean(PREF_NAM_A2_FULL, false)
                    ,imageUrl = prefs.getString(PREF_LAST_TONE_IMAGE, "") ?: ""
                    ,moduleType = prefs.getString(PREF_LAST_MODEL_TYPE, "AMP") ?: "AMP"
                )
            )
        }

        result.addAll(readExtraNamChain())
        return result
    }

    private fun readFxChain(): MutableList<FxImpulseEntry> = fxChainRepository.readImpulseChain()

    private fun persistFxChain(entries: List<FxImpulseEntry>) = fxChainRepository.persistImpulseChain(entries)

    private fun readFxNativeChain(): MutableList<FxNativeEntry> = fxChainRepository.readNativeChain()

    private fun persistFxNativeChain(entries: List<FxNativeEntry>) = fxChainRepository.persistNativeChain(entries)

    private fun FxImpulseEntry.toUiJson() = JSONObject()
        .put("toneId", toneId).put("title", title).put("image", image)
        .put("modelId", modelId).put("modelName", modelName).put("path", path)
        .put("bypass", bypass).put("mix", mix).put("position", position)

    private fun FxNativeEntry.toUiJson() = JSONObject()
        .put("effect", effect).put("bypass", bypass).put("mix", mix)
        .put("param1", param1).put("param2", param2).put("param3", param3)

    private fun syncFxNativeChain(entries: List<FxNativeEntry>, reset: Boolean = false) {
        if (reset) for (slot in 0 until 8) audioEngine.nativeClearFxNative(slot)
        entries.forEachIndexed { slot, item ->
            val type = item.effect.coerceIn(0, 3)
            if (reset) audioEngine.nativeConfigureFxNative(slot, type, MAX_NAM_BLOCKS)
            audioEngine.nativeSetFxNativeBypass(slot, item.bypass)
            audioEngine.nativeSetFxNativeMix(slot, item.mix)
            audioEngine.nativeSetFxNativeParameter(slot, 0, item.param1)
            audioEngine.nativeSetFxNativeParameter(slot, 1, item.param2)
            audioEngine.nativeSetFxNativeParameter(slot, 2, item.param3)
            audioEngine.nativeSetFxNativePosition(slot, MAX_NAM_BLOCKS)
        }
    }


    private fun persistNamChainEntries(entries: List<ExtraNamEntry>) {
        if (entries.isEmpty()) {
            activeToneRepository.clear()
            persistExtraNamChain(emptyList())
            bypass = false
            prefs.edit()
                .remove(PREF_NAM_BYPASS)
                .remove(PREF_NAM_GAIN_DB)
                .remove(PREF_NAM_IN_GAIN_DB)
                .remove(PREF_NAM_MIX)
                .remove(PREF_NAM_EQ_LOW_DB)
                .remove(PREF_NAM_EQ_MID_DB)
                .remove(PREF_NAM_EQ_HIGH_DB)
                .remove(PREF_NAM_EQ_BAND3_DB)
                .remove(PREF_NAM_EQ_BAND4_DB)
                .remove(PREF_NAM_EQ_BAND5_DB)
                .remove(PREF_NAM_EQ_PRE)
                .remove(PREF_NAM_NORMALIZE)
            .remove(PREF_NAM_A2_FULL)
            .remove(PREF_LAST_TONE_IMAGE)
                .apply()
            audioEngine.nativeSetImpulseResponsePosition(0)
            return
        }

        val first = entries.first()

        prefs.edit()
            .putString(PREF_LAST_MODEL_PATH, first.path)
            .putString(PREF_LAST_MODEL_NAME, first.modelName)
            .putString(PREF_LAST_MODEL_SIZE, first.size)
            .putString(PREF_LAST_TONE_ID, first.toneId)
            .putString(PREF_LAST_TONE_TITLE, first.toneTitle)
            .putString(PREF_LAST_TONE_IMAGE, first.imageUrl)
            .putString(PREF_LAST_MODEL_TYPE, first.moduleType)
            .putFloat(PREF_NAM_GAIN_DB, first.gainDb)
            .putFloat(PREF_NAM_IN_GAIN_DB, first.inGainDb)
            .putFloat(PREF_NAM_MIX, first.mix)
            .putFloat(PREF_NAM_EQ_LOW_DB, first.eqLowDb)
            .putFloat(PREF_NAM_EQ_MID_DB, first.eqMidDb)
            .putFloat(PREF_NAM_EQ_HIGH_DB, first.eqHighDb)
            .putFloat(PREF_NAM_EQ_BAND3_DB, first.eqBand3Db)
            .putFloat(PREF_NAM_EQ_BAND4_DB, first.eqBand4Db)
            .putFloat(PREF_NAM_EQ_BAND5_DB, first.eqBand5Db)
            .putBoolean(PREF_NAM_BYPASS, first.bypass)
            .putBoolean(PREF_NAM_EQ_PRE, first.eqPre)
            .putBoolean(PREF_NAM_EQ_ENABLED, first.eqEnabled)
            .putBoolean(PREF_NAM_NORMALIZE, first.normalize)
            .putBoolean(PREF_NAM_A2_FULL, first.a2Full)
            .apply()

        persistExtraNamChain(entries.drop(1))
        bypass = first.bypass
        val cabinetPosition = prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS)
            .coerceIn(0, entries.size)
        prefs.edit().putInt(PREF_CABINET_IR_POSITION, cabinetPosition).apply()
        audioEngine.nativeSetImpulseResponsePosition(cabinetPosition)
        val fxEntries = readFxChain()
        fxEntries.forEachIndexed { index, fx ->
            val position = fx.position.coerceIn(0, entries.size)
            fxEntries[index] = fx.copy(position = position)
            audioEngine.nativeSetFxImpulseResponsePosition(index, position)
        }
        persistFxChain(fxEntries)
    }


    private fun rebuildNativeNamChain(entries: List<ExtraNamEntry>): String {
        return rebuildNamChainUseCase.execute(entries)
    }


    private fun applyPersistedNamControls() {
        readNamChainEntries().forEachIndexed { index, entry ->
            audioEngine.nativeSetChainNamGainDb(index, entry.gainDb)
            audioEngine.nativeSetChainNamInGainDb(index, entry.inGainDb)
            audioEngine.nativeSetChainNamMix(index, entry.mix)
            audioEngine.nativeSetChainNamEqDb(index, 0, entry.eqLowDb)
            audioEngine.nativeSetChainNamEqDb(index, 1, entry.eqMidDb)
            audioEngine.nativeSetChainNamEqDb(index, 2, entry.eqHighDb)
            audioEngine.nativeSetChainNamEqDb(index, 3, entry.eqBand3Db)
            audioEngine.nativeSetChainNamEqDb(index, 4, entry.eqBand4Db)
            audioEngine.nativeSetChainNamEqDb(index, 5, entry.eqBand5Db)
            audioEngine.nativeSetChainNamEqPre(index, entry.eqPre)
            audioEngine.nativeSetChainNamEqEnabled(index, entry.eqEnabled)
            audioEngine.nativeSetChainNamNormalize(index, entry.normalize && entry.moduleType != "PEDAL")
            audioEngine.nativeSetChainNamQuality(index, entry.a2Full && entry.moduleType == "AMP")
        }
    }


    private fun removeNamBlock(chainIndex: Int) {
        Thread {
            val entries = readNamChainEntries()

            if (chainIndex !in entries.indices) {
                return@Thread
            }

            val removed = entries.removeAt(chainIndex)
            val result = rebuildNativeNamChain(entries)

            if (
                entries.isEmpty() ||
                result.startsWith("NAM CHAIN READY")
            ) {
                persistNamChainEntries(entries)

                if (entries.none { it.path == removed.path }) {
                    try {
                        File(removed.path).delete()
                    } catch (_: Exception) {
                    }
                }
            }

            runOnUiThread {
                val otherModulesRemain = readFxChain().isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null)?.let { File(it).exists() } == true
                status.value = if (entries.isEmpty() && !otherModulesRemain) {
                    "NAM CHAIN EMPTY\n\nUse ADD NAM to insert a block."
                } else {
                    result
                }
            }
        }.start()
    }


    private fun restoreExtraNamChainNative(): String {

        audioEngine.nativeClearExtraNamBlocks()


        val entries =
            readExtraNamChain()


        if (entries.isEmpty()) {

            return "No extra NAM blocks."
        }


        val restored =
            mutableListOf<ExtraNamEntry>()


        entries
            .take(
                MAX_NAM_BLOCKS -
                        1
            )
            .forEach { entry ->

                val result =
                    audioEngine.nativeAddChainModel(
                        entry.path
                    )


                if (
                    result.startsWith(
                        "CHAIN NAM ADDED"
                    )
                ) {

                    val chainIndex =
                        restored.size +
                                1


                    audioEngine.nativeSetChainNamBypass(
                        chainIndex,
                        entry.bypass
                    )


                    restored.add(
                        entry
                    )
                }
            }


        persistExtraNamChain(
            restored
        )


        return "Restored ${restored.size} extra NAM block(s)."
    }


    private fun clearExtraNamChain() {

        Thread {

            audioEngine.nativeClearExtraNamBlocks()


            readExtraNamChain()
                .forEach {

                    try {

                        File(
                            it.path
                        ).delete()

                    } catch (
                        _: Exception
                    ) {
                    }
                }


            persistExtraNamChain(
                emptyList()
            )


            runOnUiThread {

                status.value =
                    "EXTRA NAM BLOCKS CLEARED"
            }

        }.start()
    }


    // ========================================================
    // ANDROID APP UI
    // ========================================================

    private fun pluginStateJson(): String {

        val result =
            JSONObject()


        result.put(
            "running",
            audioEngine.nativeIsRunning()
        )

        result.put(
            "bypass",
            bypass
        )

        result.put(
            "modelName",
            prefs.getString(
                PREF_LAST_MODEL_NAME,
                "No capture loaded"
            )
        )
        result.put("activePresetSlot", prefs.getInt(PREF_ACTIVE_PRESET_SLOT, 0))

        result.put(
            "modelSize",
            prefs.getString(
                PREF_LAST_MODEL_SIZE,
                ""
            )
        )

        result.put(
            "toneTitle",
            prefs.getString(
                PREF_LAST_TONE_TITLE,
                ""
            )
        )

        val cabinetPath = prefs.getString(PREF_CABINET_IR_PATH, null)
        result.put("cabinetIrLoaded", cabinetPath != null && File(cabinetPath).exists())
        result.put("cabinetIrName", prefs.getString(PREF_CABINET_IR_TITLE, cabinetPath?.let { File(it).nameWithoutExtension } ?: "") ?: "")
        result.put("cabinetIrModuleType", prefs.getString(PREF_CABINET_IR_TYPE, "IR") ?: "IR")
        result.put("cabinetIrLong", prefs.getString(PREF_CABINET_IR_TYPE, "IR") == "FX")
        result.put("cabinetIrImage", prefs.getString(PREF_CABINET_IR_IMAGE, ""))
        result.put("cabinetIrBypass", prefs.getBoolean(PREF_CABINET_IR_BYPASS, false))
        result.put("cabinetIrPosition", prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS))
        result.put("cabinetIrInGain", prefs.getFloat(PREF_CABINET_IR_IN_GAIN, 0.0f).toDouble())
        result.put("cabinetIrOutGain", prefs.getFloat(PREF_CABINET_IR_OUT_GAIN, 0.0f).toDouble())
        result.put("cabinetIrMix", prefs.getFloat(PREF_CABINET_IR_MIX, 1.0f).toDouble())
        result.put("cabinetIrEqPre", prefs.getBoolean(PREF_CABINET_IR_EQ_PRE, false))
        result.put("cabinetIrEqEnabled", prefs.getBoolean(PREF_CABINET_IR_EQ_ENABLED, true))
        val cabinetEq = JSONArray()
        for (band in 0 until 6) cabinetEq.put(prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + band, 0.0f).toDouble())
        result.put("cabinetIrEq", cabinetEq)

        result.put(
            "inputGain",
            prefs.getFloat(
                PREF_INPUT_GAIN,
                0.0f
            ).toDouble()
        )

        result.put(
            "outputGain",
            prefs.getFloat(
                PREF_OUTPUT_GAIN,
                -10.0f
            ).toDouble()
        )

        result.put(
            "inputChannel",
            prefs.getInt(
                PREF_INPUT_CHANNEL,
                0
            )
        )

        result.put(
            "outputPair",
            prefs.getInt(
                PREF_OUTPUT_PAIR,
                0
            )
        )

        result.put(
            "gateEnabled",
            prefs.getBoolean(
                PREF_GATE_ENABLED,
                false
            )
        )

        result.put(
            "gateThreshold",
            prefs.getFloat(
                PREF_GATE_THRESHOLD,
                -65.0f
            ).toDouble()
        )

        result.put(
            "eqLow",
            prefs.getFloat(
                PREF_EQ_LOW,
                0.0f
            ).toDouble()
        )

        result.put(
            "eqMid",
            prefs.getFloat(
                PREF_EQ_MID,
                0.0f
            ).toDouble()
        )

        result.put(
            "eqHigh",
            prefs.getFloat(
                PREF_EQ_HIGH,
                0.0f
            ).toDouble()
        )

        result.put("eqEnabled", prefs.getBoolean(PREF_EQ_ENABLED, true))

        result.put(
            "routing",
            audioEngine.nativeGetRoutingInfo()
        )

        result.put(
            "audioDevice",
            audioEngine.nativeGetAudioDeviceInfo()
        )

        result.put(
            "namBlockCount",
            audioEngine.nativeGetNamBlockCount()
        )


        val namChain =
            JSONArray()


        val firstModelPath = prefs.getString(PREF_LAST_MODEL_PATH, null)
        val hasFirstModel = firstModelPath != null && File(firstModelPath).exists()
        if (hasFirstModel) {
            namChain.put(
                JSONObject()
                .put(
                    "chainIndex",
                    0
                )
                .put(
                    "modelName",
                    prefs.getString(
                        PREF_LAST_MODEL_NAME,
                        "No capture loaded"
                    )
                )
                .put(
                    "toneTitle",
                    prefs.getString(
                        PREF_LAST_TONE_TITLE,
                        ""
                    )
                )
                .put(
                    "size",
                    prefs.getString(
                        PREF_LAST_MODEL_SIZE,
                        ""
                    )
                )
                .put(
                    "bypass",
                    bypass
                )
                .put("gainDb", prefs.getFloat(PREF_NAM_GAIN_DB, -15.0f))
                .put("inGainDb", prefs.getFloat(PREF_NAM_IN_GAIN_DB, 0.0f))
                .put("mix", prefs.getFloat(PREF_NAM_MIX, 1.0f))
                .put("eqLowDb", prefs.getFloat(PREF_NAM_EQ_LOW_DB, 0.0f))
                .put("eqMidDb", prefs.getFloat(PREF_NAM_EQ_MID_DB, 0.0f))
                .put("eqHighDb", prefs.getFloat(PREF_NAM_EQ_HIGH_DB, 0.0f))
                .put("eqBand3Db", prefs.getFloat(PREF_NAM_EQ_BAND3_DB, 0.0f))
                .put("eqBand4Db", prefs.getFloat(PREF_NAM_EQ_BAND4_DB, 0.0f))
                .put("eqBand5Db", prefs.getFloat(PREF_NAM_EQ_BAND5_DB, 0.0f))
                .put("eqPre", prefs.getBoolean(PREF_NAM_EQ_PRE, false))
                .put("eqEnabled", prefs.getBoolean(PREF_NAM_EQ_ENABLED, true))
                .put("normalize", prefs.getBoolean(PREF_NAM_NORMALIZE, true))
                .put("a2Full", prefs.getBoolean(PREF_NAM_A2_FULL, false))
                .put("moduleType", prefs.getString(PREF_LAST_MODEL_TYPE, "AMP"))
                .put("images", JSONArray().put(prefs.getString(PREF_LAST_TONE_IMAGE, "")))
            )
        }


        readExtraNamChain()
            .forEachIndexed { index, entry ->

                namChain.put(
                    JSONObject()
                        .put(
                            "chainIndex",
                            index + if (hasFirstModel) 1 else 0
                        )
                        .put(
                            "modelName",
                            entry.modelName
                        )
                        .put(
                            "toneTitle",
                            entry.toneTitle
                        )
                        .put(
                            "size",
                            entry.size
                        )
                        .put(
                            "bypass",
                            entry.bypass
                        )
                        .put("gainDb", entry.gainDb)
                        .put("inGainDb", entry.inGainDb)
                        .put("mix", entry.mix)
                        .put("eqLowDb", entry.eqLowDb)
                        .put("eqMidDb", entry.eqMidDb)
                        .put("eqHighDb", entry.eqHighDb)
                        .put("eqBand3Db", entry.eqBand3Db)
                        .put("eqBand4Db", entry.eqBand4Db)
                        .put("eqBand5Db", entry.eqBand5Db)
                        .put("eqPre", entry.eqPre)
                        .put("eqEnabled", entry.eqEnabled)
                        .put("normalize", entry.normalize)
                        .put("a2Full", entry.a2Full)
                        .put("moduleType", entry.moduleType)
                        .put("images", JSONArray().put(entry.imageUrl))
                )
            }


        result.put(
            "namChain",
            namChain
        )

        val signalChain = JSONArray()
        val fxChain = readFxChain()
        val fxNativeChain = readFxNativeChain()
        result.put("fxChain", JSONArray().also { array -> fxChain.forEach { array.put(it.toUiJson()) } })
        result.put("fxNativeChain", JSONArray().also { array -> fxNativeChain.forEach { array.put(it.toUiJson()) } })
        val irLoaded = cabinetPath != null && File(cabinetPath).exists()
        val irPosition = prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS)
            .coerceIn(0, namChain.length())
        for (position in 0..namChain.length()) {
            if (irLoaded && irPosition == position) {
                signalChain.put(JSONObject()
                    .put("type", "CABINET_IR")
                    .put("position", position)
                    .put("name", File(cabinetPath).name))
            }
            fxChain.forEachIndexed { index, fx ->
                if (fx.position == position) {
                    signalChain.put(JSONObject()
                        .put("type", "FX")
                        .put("fxIndex", index)
                        .put("position", position)
                        .put("name", fx.title)
                        .put("bypass", fx.bypass)
                        .put("mix", fx.mix.toDouble()))
                }
            }
            if (position < namChain.length()) signalChain.put(namChain.getJSONObject(position).put("type", "NAM"))
        }
        // FXNative is intentionally a stereo post section: it cannot be
        // inserted before a NAM or either cabinet/space convolution stage.
        fxNativeChain.forEachIndexed { index, item ->
            val effect = item.effect.coerceIn(0, 3)
            val names = arrayOf("ChowMatrix Delay", "BYOD BBD Delay", "BYOD Smooth Reverb", "BYOD Shimmer Reverb")
            signalChain.put(JSONObject()
                .put("type", "FX_NATIVE")
                .put("nativeIndex", index)
                .put("effect", effect)
                .put("position", MAX_NAM_BLOCKS)
                .put("name", names[effect])
                .put("bypass", item.bypass)
                .put("mix", item.mix.toDouble())
                .put("param1", item.param1.toDouble())
                .put("param2", item.param2.toDouble())
                .put("param3", item.param3.toDouble()))
        }
        result.put("signalChain", signalChain)


        val presets =
            JSONArray()


        for (
        slot in
        1..PRESET_COUNT
        ) {

            val preset =
                readPreset(
                    slot
                )


            val item =
                JSONObject()


            item.put(
                "slot",
                slot
            )

            item.put(
                "saved",
                preset != null
            )

            item.put(
                "label",
                presetLabel(
                    slot
                )
            )


            if (preset != null) {

                item.put(
                    "modelName",
                    preset.modelName
                )

                item.put(
                    "toneTitle",
                    preset.toneTitle
                        ?: ""
                )
            }


            presets.put(
                item
            )
        }


        result.put(
            "presets",
            presets
        )


        return result.toString()
    }


    internal inner class AudioAppController : PicoloActions {

        fun getState(): String {

            return pluginStateJson()
        }


        override fun setInputGain(db: Double) = audioParameterController.setInputGain(db)

        override fun setOutputGain(db: Double) = audioParameterController.setOutputGain(db)

        override fun setGateEnabled(enabled: Boolean) = audioParameterController.setGateEnabled(enabled)

        override fun setGateThreshold(db: Double) = audioParameterController.setGateThreshold(db)

        override fun setEqLow(db: Double) = audioParameterController.setEqLow(db)

        override fun setEqMid(db: Double) = audioParameterController.setEqMid(db)

        override fun setEqHigh(db: Double) = audioParameterController.setEqHigh(db)

        override fun setEqEnabled(enabled: Boolean) = audioParameterController.setEqEnabled(enabled)


        fun cycleInput(): Int {
            val selected = audioRoutingUseCase.cycleInput()
            return selected
        }

        override fun cycleOutput(): Int {
            val selected = audioRoutingUseCase.cycleOutput()
            return selected
        }


        override fun startAudio(): String {

            syncFxNativeChain(readFxNativeChain(), reset = true)

            val result =
                audioEngine.nativeStart()


            runOnUiThread {

                status.value =
                    result


            }


            return result
        }


        override fun stopAudio(): String {

            audioEngine.nativeStop()


            runOnUiThread {

                status.value =
                    "STOPPED"
            }


            return "STOPPED"
        }

        fun toggleBypass(): Boolean {

            bypass =
                !bypass


            audioEngine.nativeSetBypass(
                bypass
            )

            prefs.edit().putBoolean(PREF_NAM_BYPASS, bypass).apply()


return bypass
        }


        fun browseTone3000() {

            runOnUiThread {

                startTone3000SelectFlow(
                    "replace"
                )
            }
        }

        fun setSelectedAddType(type: String) {
            val normalized = type.uppercase(Locale.US)
            if (normalized == "AMP" || normalized == "PEDAL" || normalized == "FX" || normalized == "IR") {
                prefs.edit().putString(PREF_SELECTED_ADD_TYPE, normalized).apply()
            }
        }


        fun addNam() {

            if (
                audioEngine.nativeGetNamBlockCount() >=
                MAX_NAM_BLOCKS
            ) {

                runOnUiThread {

                    status.value =
                        "NAM CHAIN FULL\n\nMaximum: $MAX_NAM_BLOCKS blocks."
                }

                return
            }


            runOnUiThread {

                startTone3000SelectFlow(
                    if (audioEngine.nativeGetNamBlockCount() == 0) {
                        "replace"
                    } else {
                        "add"
                    }
                )
            }
        }

        /** Receives a local .nam payload from the native import flow. */
        fun loadLocalTone(title: String, filesJson: String, targetBlockId: String): String {
            return try {
                val files = JSONArray(filesJson)
                if (files.length() == 0) return JSONObject().put("error", "No local file").toString()
                val source = files.getJSONObject(0)
                val name = source.optString("name", "$title.nam")
                val encoded = source.optString("data")
                val importedFile = importLocalNamFileUseCase.execute(name, encoded)
                val destination = importedFile.file

                val entries = readNamChainEntries().toMutableList()
                val requested = targetBlockId.removePrefix("nam-").toIntOrNull()
                val target = requested?.takeIf { it in entries.indices } ?: entries.size
                if (target >= MAX_NAM_BLOCKS) {
                    destination.delete()
                    return JSONObject().put("error", "NAM chain full").toString()
                }
                val entry = ExtraNamEntry(
                    toneId = "local-${destination.name}",
                    toneTitle = title,
                    modelId = 0L,
                            modelName = importedFile.originalName.removeSuffix(".nam"),
                    size = "unknown",
                    path = destination.absolutePath,
                    bypass = false,
                    eqLowDb = 0.0f,
                    eqMidDb = 0.0f,
                    eqHighDb = 0.0f,
                    eqBand3Db = 0.0f,
                    eqBand4Db = 0.0f,
                    eqBand5Db = 0.0f
                )
                if (target < entries.size) entries[target] = entry else entries.add(entry)
                persistNamChainEntries(entries)
                val result = rebuildNativeNamChain(entries)
                if (!result.contains("failed", ignoreCase = true) && !result.contains("error", ignoreCase = true)) {
                    JSONObject().put("blockId", "nam-$target").toString()
                } else {
                    JSONObject().put("error", result).toString()
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Local tone import failed", error)
                JSONObject().put("error", error.message ?: "Local import failed").toString()
            }
        }

        /** Starts the authenticated downloader for package captures selected in Compose. */
        override fun selectPackageCaptures(blockId: String): Boolean {
            val source = when {
                blockId.startsWith("nam-") -> {
                    val index = blockId.removePrefix("nam-").toIntOrNull() ?: return false
                    readNamChainEntries().getOrNull(index)?.let { entry ->
                        PackageCaptureSource(
                            blockId = blockId,
                            toneId = entry.toneId,
                            toneTitle = entry.toneTitle,
                            moduleType = entry.moduleType,
                            imageUrl = entry.imageUrl,
                            importMode = "replace:$index",
                        )
                    }
                }
                blockId.startsWith("fx-") -> {
                    val index = blockId.removePrefix("fx-").toIntOrNull() ?: return false
                    readFxChain().getOrNull(index)?.let { entry ->
                        PackageCaptureSource(
                            blockId = blockId,
                            toneId = entry.toneId,
                            toneTitle = entry.title,
                            moduleType = "FX",
                            imageUrl = entry.image,
                            importMode = "replace-fx:$index",
                        )
                    }
                }
                blockId == "cabinet-ir" -> {
                    val path = prefs.getString(PREF_CABINET_IR_PATH, null)
                    if (path.isNullOrBlank() || !File(path).exists()) null else PackageCaptureSource(
                        blockId = blockId,
                        toneId = prefs.getString(PREF_CABINET_IR_TONE_ID, "") ?: "",
                        toneTitle = prefs.getString(PREF_CABINET_IR_TITLE, "Cabinet IR") ?: "Cabinet IR",
                        moduleType = "IR",
                        imageUrl = prefs.getString(PREF_CABINET_IR_IMAGE, "") ?: "",
                        importMode = "",
                    )
                }
                else -> null
            }

            if (source == null || source.toneId.isBlank() || source.toneId.startsWith("local-")) {
                runOnUiThread {
                    showPackageCaptureUnavailable()
                }
                return true
            }

            runOnUiThread {
                status.value = "Loading captures from:\n${source.toneTitle}"
            }
            Thread {
                try {
                    val token = runBlocking { toneSessionRepository.accessToken() }
                    if (token.isNullOrBlank()) {
                        runOnUiThread {
                            showPackageCaptureUnavailable("Sign in to TONE3000 to view this package's captures.")
                        }
                        return@Thread
                    }
                    val models = runBlocking {
                        loadPackageCapturesUseCase.execute(source.toneId, source.moduleType, token)
                    }
                    runOnUiThread {
                        if (models.isEmpty()) {
                            showPackageCaptureUnavailable("No captures found in ${source.toneTitle}.")
                            return@runOnUiThread
                        }

                        prefs.edit()
                            .putString(PREF_PENDING_IMPORT_MODE, source.importMode)
                            .putString(PREF_PENDING_TONE_IMAGE, source.imageUrl)
                            .putString(PREF_PENDING_TONE_TYPE, source.moduleType)
                            .putString(PREF_SELECTED_ADD_TYPE, source.moduleType)
                            .apply()

                        when (source.moduleType) {
                            "FX" -> showFxModelSelectionDialog(
                                source.toneId,
                                source.toneTitle,
                                source.imageUrl,
                                models,
                                token,
                            )
                            "IR" -> showCabinetModelSelectionDialog(
                                source.toneId,
                                source.toneTitle,
                                source.imageUrl,
                                models,
                                token,
                            )
                            else -> showModelSelectionDialog(
                                source.toneId,
                                source.toneTitle,
                                models,
                                token,
                            )
                        }
                    }
                } catch (error: Exception) {
                    Log.e(API_TAG, "Could not list package captures for ${source.blockId}", error)
                    runOnUiThread {
                        showPackageCaptureUnavailable("Couldn't load this package's captures. Check your connection and sign-in.")
                    }
                }
            }.start()
            return true
        }

       private fun downloadAndLoadCabinet(
            toneId: String,
            toneTitle: String,
            imageUrl: String,
            model: OnlineModel,
            token: String,
            moduleType: String
        ) {
            try {
                val audio = importCabinetImpulseUseCase.execute(toneId, toneTitle, imageUrl, model, token, moduleType)
                prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
                runOnUiThread {
                    status.value = "CABINET IR READY\n\n$toneTitle\n${model.name}\n$audio"
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Cabinet IR download/load failed", error)
                runOnUiThread { status.value = "CABINET IR LOAD FAILED\n\n${error.message}" }
            }
        }

        private fun downloadAndLoadFx(
            toneId: String, toneTitle: String, imageUrl: String, model: OnlineModel, token: String
        ) {
            val requestedReplacementIndex = prefs.getString(PREF_PENDING_IMPORT_MODE, "add")
                ?.removePrefix("replace-fx:")?.toIntOrNull()
            Thread {
                try {
                    val imported = importFxCaptureUseCase.execute(
                        toneId = toneId,
                        toneTitle = toneTitle,
                        imageUrl = imageUrl,
                        model = model,
                        token = token,
                        requestedReplacementIndex = requestedReplacementIndex,
                    )
                    prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
                    runOnUiThread {
                        status.value = "FX READY\n\n$toneTitle\n${model.name}\n${imported.audioResult}"
                    }
                } catch (error: Exception) {
                    Log.e(API_TAG, "FX import failed", error)
                    runOnUiThread { status.value = "FX LOAD FAILED\n${error.message}" }
                }
            }.start()
        }

        fun loadSelectedCabinet(
            toneId: String,
            toneTitle: String,
            modelId: Long,
            modelName: String,
            modelSize: String,
            modelUrl: String,
            token: String,
            moduleType: String
        ) {
            val model = OnlineModel(modelId, modelName, modelSize, modelUrl)
            if (moduleType == "FX") {
                val importMode = prefs.getString(PREF_PENDING_IMPORT_MODE, "add") ?: "add"
                val replaceIndex = importMode.removePrefix("replace-fx:").toIntOrNull()
                if (readFxChain().size >= 8 && replaceIndex == null) {
                    runOnUiThread { status.value = "FX CHAIN FULL\nMaximum 8 space effects." }
                } else {
                    downloadAndLoadFx(toneId, toneTitle, "", model, token)
                }
            } else {
                downloadAndLoadCabinet(
                    toneId = toneId,
                    toneTitle = toneTitle,
                    imageUrl = "",
                    model = model,
                    token = token,
                    moduleType = moduleType
                )
            }
        }

        fun addCabinetIr() {
            runOnUiThread {
                openIrFile.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
            }
        }

        override fun removeCabinetIr() {
            val resumeAudio = audioEngine.nativeIsRunning()
            audioEngine.nativeClearImpulseResponse()
            audioEngine.nativeSetImpulseResponseBypass(false)
            audioEngine.nativeSetImpulseResponseInGainDb(0.0f)
            audioEngine.nativeSetImpulseResponseOutGainDb(0.0f)
            audioEngine.nativeSetImpulseResponseMix(1.0f)
            audioEngine.nativeSetImpulseResponseEqPre(false)
            audioEngine.nativeSetImpulseResponseEqEnabled(true)
            for (band in 0 until 6) audioEngine.nativeSetImpulseResponseEqDb(band, 0.0f)
            prefs.getString(PREF_CABINET_IR_PATH, null)?.let { path ->
                try { File(path).delete() } catch (_: Exception) { }
            }
            prefs.edit()
                .remove(PREF_CABINET_IR_PATH)
                .remove(PREF_CABINET_IR_IMAGE)
                .remove(PREF_CABINET_IR_TITLE)
                .remove(PREF_CABINET_IR_TONE_ID)
                .remove(PREF_CABINET_IR_TYPE)
                .remove(PREF_CABINET_IR_BYPASS)
                .remove(PREF_CABINET_IR_POSITION)
                .remove(PREF_CABINET_IR_IN_GAIN)
                .remove(PREF_CABINET_IR_OUT_GAIN)
                .remove(PREF_CABINET_IR_MIX)
                .remove(PREF_CABINET_IR_EQ_PRE)
                .apply {
                    for (band in 0 until 6) remove(PREF_CABINET_IR_EQ_PREFIX + band)
                }
                .apply()
            val hasOtherModules = audioEngine.nativeGetNamBlockCount() > 0 || readFxChain().isNotEmpty()
            val audioResult = if (resumeAudio && hasOtherModules) audioEngine.nativeStart() else ""
            runOnUiThread {
                status.value = if (audioResult.startsWith("AUDIO ACTIVE")) {
                    "CABINET IR REMOVED\n$audioResult"
                } else if (resumeAudio && hasOtherModules) {
                    "CABINET IR REMOVED\n$audioResult"
                } else {
                    "CABINET IR REMOVED"
                }
            }
        }

        override fun removeFx(fxIndex: Int) {
            Thread {
                val entries = readFxChain()
                if (fxIndex !in entries.indices) return@Thread
                val wasRunning = audioEngine.nativeIsRunning()
                val removed = entries.removeAt(fxIndex)
                for (slot in 0 until 8) audioEngine.nativeClearFxImpulseResponse(slot)
                entries.forEachIndexed { slot, item ->
                    val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, item.path)
                    if (!loaded.startsWith("FX LOADED")) {
                        runOnUiThread { status.value = "FX CHAIN RELOAD FAILED\n$loaded" }
                        return@Thread
                    }
                    audioEngine.nativeSetFxImpulseResponseBypass(slot, item.bypass)
                    audioEngine.nativeSetFxImpulseResponseMix(slot, item.mix)
                    val namCount = audioEngine.nativeGetNamBlockCount()
                    audioEngine.nativeSetFxImpulseResponsePosition(slot, item.position.coerceIn(0, namCount))
                }
                persistFxChain(entries)
                try { File(removed.path).delete() } catch (_: Exception) { }
                val hasModules = audioEngine.nativeGetNamBlockCount() > 0 || entries.isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null
                val audio = if (wasRunning && hasModules) audioEngine.nativeStart() else ""
                runOnUiThread { status.value = "FX REMOVED\n$audio" }
            }.start()
        }

        override fun setFxBypass(fxIndex: Int, bypassed: Boolean) {
            val entries = readFxChain()
            val item = entries.getOrNull(fxIndex) ?: return
            entries[fxIndex] = item.copy(bypass = bypassed)
            persistFxChain(entries)
            audioEngine.nativeSetFxImpulseResponseBypass(fxIndex, bypassed)
        }

        override fun setFxMix(fxIndex: Int, mix: Double) {
            val entries = readFxChain()
            val item = entries.getOrNull(fxIndex) ?: return
            val value = mix.toFloat().coerceIn(0f, 1f)
            entries[fxIndex] = item.copy(mix = value)
            persistFxChain(entries)
            audioEngine.nativeSetFxImpulseResponseMix(fxIndex, value)
        }

        override fun addFxNative(effect: Int) {
            val entries = readFxNativeChain()
            if (entries.size >= 8) {
                runOnUiThread { status.value = "FXNATIVE CHAIN FULL\nMaximum 8 native effects." }
                return
            }
            val wasRunning = audioEngine.nativeIsRunning()
            val selected = effect.coerceIn(0, 3)
            val entry = FxNativeEntry(
                effect = selected,
                param1 = when (selected) { 0, 1 -> 350f; 2 -> 1500f; else -> 150f },
                param2 = when (selected) { 0, 1 -> 0.35f; 2 -> 0.5f; else -> 5000f },
            )
            entries.add(entry)
            persistFxNativeChain(entries)
            syncFxNativeChain(entries, reset = true)
            val audio = if (wasRunning) audioEngine.nativeStart() else ""
            runOnUiThread { status.value = "FXNATIVE ADDED\nStereo post NAM/CAB\n$audio" }
        }

        override fun removeFxNative(nativeIndex: Int) {
            val entries = readFxNativeChain()
            if (nativeIndex !in entries.indices) return
            val wasRunning = audioEngine.nativeIsRunning()
            entries.removeAt(nativeIndex)
            persistFxNativeChain(entries)
            syncFxNativeChain(entries, reset = true)
            val audio = if (wasRunning && (audioEngine.nativeGetNamBlockCount() > 0 || readFxChain().isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null)) audioEngine.nativeStart() else ""
            runOnUiThread { status.value = "FXNATIVE REMOVED\n$audio" }
        }

        override fun setFxNativeBypass(nativeIndex: Int, bypassed: Boolean) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            entries[nativeIndex] = entry.copy(bypass = bypassed)
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeBypass(nativeIndex, bypassed)
        }

        override fun setFxNativeMix(nativeIndex: Int, mix: Double) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val value = mix.toFloat().coerceIn(0f, 1f)
            entries[nativeIndex] = entry.copy(mix = value)
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeMix(nativeIndex, value)
        }

        override fun setFxNativeParameter(nativeIndex: Int, parameter: Int, value: Double) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val effect = entry.effect
            val normalized = when (parameter) {
                0 -> value.toFloat().coerceIn(if (effect < 2) 20f else if (effect == 2) 500f else 50f,
                    if (effect < 2) 2000f else if (effect == 2) 5000f else 250f)
                1 -> value.toFloat().coerceIn(if (effect < 2) 0f else if (effect == 2) 0f else 1000f,
                    if (effect < 2) 0.94f else if (effect == 2) 1f else 10000f)
                else -> value.toFloat().coerceIn(-12f, 12f)
            }
            entries[nativeIndex] = when (parameter) {
                0 -> entry.copy(param1 = normalized)
                1 -> entry.copy(param2 = normalized)
                else -> entry.copy(param3 = normalized)
            }
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeParameter(nativeIndex, parameter, normalized)
        }

        override fun setFxNativeType(nativeIndex: Int, effect: Int) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val wasRunning = audioEngine.nativeIsRunning()
            val selected = effect.coerceIn(0, 3)
            entries[nativeIndex] = entry.copy(
                effect = selected,
                param1 = when (selected) { 0, 1 -> 350f; 2 -> 1500f; else -> 150f },
                param2 = when (selected) { 0, 1 -> 0.35f; 2 -> 0.5f; else -> 5000f },
                param3 = 12f,
            )
            persistFxNativeChain(entries)
            syncFxNativeChain(entries, reset = true)
            if (wasRunning) audioEngine.nativeStart()
        }

        override fun setCabinetBypass(bypassed: Boolean) {
            audioEngine.nativeSetImpulseResponseBypass(bypassed)
            prefs.edit().putBoolean(PREF_CABINET_IR_BYPASS, bypassed).apply()
        }

        fun moveCabinet(direction: Int) {
            val maxPosition = audioEngine.nativeGetNamBlockCount().coerceIn(0, MAX_NAM_BLOCKS)
            val current = prefs.getInt(PREF_CABINET_IR_POSITION, maxPosition).coerceIn(0, maxPosition)
            val next = (current + direction.coerceIn(-1, 1)).coerceIn(0, maxPosition)
            audioEngine.nativeSetImpulseResponsePosition(next)
            prefs.edit().putInt(PREF_CABINET_IR_POSITION, next).apply()
        }

        override fun setCabinetInGain(db: Double) {
            val value = db.toFloat().coerceIn(-24.0f, 24.0f)
            audioEngine.nativeSetImpulseResponseInGainDb(value)
            prefs.edit().putFloat(PREF_CABINET_IR_IN_GAIN, value).apply()
        }

        override fun setCabinetOutGain(db: Double) {
            val value = db.toFloat().coerceIn(-24.0f, 12.0f)
            audioEngine.nativeSetImpulseResponseOutGainDb(value)
            prefs.edit().putFloat(PREF_CABINET_IR_OUT_GAIN, value).apply()
        }

        override fun setCabinetMix(mix: Double) {
            val value = mix.toFloat().coerceIn(0.0f, 1.0f)
            audioEngine.nativeSetImpulseResponseMix(value)
            prefs.edit().putFloat(PREF_CABINET_IR_MIX, value).apply()
        }

        override fun setCabinetEq(band: Int, db: Double) {
            if (band !in 0 until 6) return
            val value = db.toFloat().coerceIn(-12.0f, 12.0f)
            audioEngine.nativeSetImpulseResponseEqDb(band, value)
            prefs.edit().putFloat(PREF_CABINET_IR_EQ_PREFIX + band, value).apply()
        }

        fun setCabinetEqPosition(pre: Boolean) {
            audioEngine.nativeSetImpulseResponseEqPre(pre)
            prefs.edit().putBoolean(PREF_CABINET_IR_EQ_PRE, pre).apply()
        }

        override fun setCabinetEqEnabled(enabled: Boolean) {
            audioEngine.nativeSetImpulseResponseEqEnabled(enabled)
            prefs.edit().putBoolean(PREF_CABINET_IR_EQ_ENABLED, enabled).apply()
        }


        fun changeNam(chainIndex: Int) {
            if (chainIndex !in 0 until audioEngine.nativeGetNamBlockCount()) {
                return
            }

            runOnUiThread {
                startTone3000SelectFlow("replace:$chainIndex")
            }
        }


        override fun removeNam(chainIndex: Int) {
            removeNamBlock(chainIndex)
        }


        fun moveNam(chainIndex: Int, direction: Int) {
            Thread {
                val entries = readNamChainEntries()
                val target = chainIndex + direction

                if (
                    chainIndex !in entries.indices ||
                    target !in entries.indices
                ) {
                    return@Thread
                }

                val moved = entries.removeAt(chainIndex)
                entries.add(target, moved)

                val result = rebuildNativeNamChain(entries)

                if (result.startsWith("NAM CHAIN READY")) {
                    persistNamChainEntries(entries)
                }

                runOnUiThread {
                    status.value = result
                }
            }.start()
        }

        /** Reorders the mixed NAM + cabinet chain, matching the Compose tile
         * order. The native graph and persisted IR position are changed by
         * the same rollback-safe reorder routine used by the Web UI. */
        override fun moveModule(blockId: String, direction: Int) {
            Thread {
                try {
                    val chain = JSONObject(pluginStateJson()).optJSONArray("signalChain") ?: return@Thread
                    val ids = buildList {
                        for (index in 0 until chain.length()) {
                            val item = chain.optJSONObject(index) ?: continue
                            add(when (item.optString("type")) {
                                "CABINET_IR" -> "cabinet-ir"
                                "FX" -> "fx-${item.optInt("fxIndex", index)}"
                                else -> "nam-${item.optInt("chainIndex", index)}"
                            })
                        }
                    }.toMutableList()
                    val current = ids.indexOf(blockId)
                    if (current < 0 || ids.isEmpty()) return@Thread
                    val target = (current + direction).coerceIn(0, ids.lastIndex)
                    if (target == current) return@Thread
                    val moved = ids.removeAt(current)
                    ids.add(target, moved)
                    val requested = JSONArray()
                    ids.forEach { requested.put(it) }
                    reorderChain(requested.toString())
                } catch (error: Exception) {
                    Log.e(API_TAG, "Compose module reorder failed", error)
                }
            }.start()
        }

        fun reorderChain(blockIdsJson: String): Boolean {
            return try {
                val requested = JSONArray(blockIdsJson)
                    .let { array -> (0 until array.length()).map { array.optString(it) } }
                val entries = readNamChainEntries()
                val wasRunning = audioEngine.nativeIsRunning()
                val fxEntries = readFxChain()
                val orderedIndices = requested
                    .filter { it.startsWith("nam-") }
                    .mapNotNull { it.removePrefix("nam-").toIntOrNull() }
                    .filter { it in entries.indices }
                    .distinct()
                    .toMutableList()
                entries.indices.forEach { if (it !in orderedIndices) orderedIndices.add(it) }
                val reordered = orderedIndices.map { entries[it] }
                val requestedFxIndices = requested.filter { it.startsWith("fx-") }
                    .mapNotNull { it.removePrefix("fx-").toIntOrNull() }
                    .filter { it in fxEntries.indices }.distinct().toMutableList()
                fxEntries.indices.forEach { if (it !in requestedFxIndices) requestedFxIndices.add(it) }
                val reorderedFx = requestedFxIndices.map { fxEntries[it] }.toMutableList()
                val fxPositions = mutableMapOf<Int, Int>()
                var namBefore = 0
                requested.forEach { id ->
                    when {
                        id.startsWith("nam-") -> namBefore++
                        id.startsWith("fx-") -> id.removePrefix("fx-").toIntOrNull()?.let { fxPositions[it] = namBefore }
                    }
                }
                val cabinetPosition = requested.indexOf("cabinet-ir")
                    .takeIf { it >= 0 }
                    ?.coerceIn(0, reordered.size)
                    ?: prefs.getInt(PREF_CABINET_IR_POSITION, reordered.size).coerceIn(0, reordered.size)
                // Do not report success or settle the UI until the native chain has actually been
                // rebuilt in that order. If loading any model fails, restore
                // the previous DSP chain and keep its persisted ordering.
                val result = rebuildNativeNamChain(reordered)
                if (result.startsWith("NAM CHAIN READY")) {
                    persistNamChainEntries(reordered)
                    prefs.edit().putInt(PREF_CABINET_IR_POSITION, cabinetPosition).apply()
                    audioEngine.nativeSetImpulseResponsePosition(cabinetPosition)
                    for (slot in 0 until 8) audioEngine.nativeClearFxImpulseResponse(slot)
                    reorderedFx.forEachIndexed { slot, item ->
                        val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, item.path)
                        if (!loaded.startsWith("FX LOADED")) return false
                        reorderedFx[slot] = item.copy(position = fxPositions[requestedFxIndices.getOrNull(slot)] ?: reordered.size)
                        audioEngine.nativeSetFxImpulseResponseBypass(slot, item.bypass)
                        audioEngine.nativeSetFxImpulseResponseMix(slot, item.mix)
                        audioEngine.nativeSetFxImpulseResponsePosition(slot, item.position.coerceIn(0, reordered.size))
                    }
                    persistFxChain(reorderedFx)
                    val audio = if (wasRunning && (reordered.isNotEmpty() || reorderedFx.isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null)) audioEngine.nativeStart() else ""
                    runOnUiThread { status.value = if (audio.isBlank()) result else "$result\n$audio" }
                    true
                } else {
                    val rollback = rebuildNativeNamChain(entries)
                    runOnUiThread {
                        status.value = if (rollback.startsWith("NAM CHAIN READY")) {
                            "Reordenação cancelada: $result"
                        } else {
                            "Falha ao reordenar e restaurar cadeia: $rollback"
                        }
                    }
                    false
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Chain reorder failed", error)
                false
            }
        }

        fun resetToDefault(): Boolean {
            return try {
                audioEngine.nativeClearNamChain()
                audioEngine.nativeClearImpulseResponse()
                for (slot in 0 until 8) audioEngine.nativeClearFxImpulseResponse(slot)
                activeToneRepository.clear()
                persistExtraNamChain(emptyList())
                audioEngine.nativeSetEqLowDb(0.0f)
                audioEngine.nativeSetEqMidDb(0.0f)
                audioEngine.nativeSetEqHighDb(0.0f)
                audioEngine.nativeSetEqEnabled(true)
                prefs.edit()
                    .putFloat(PREF_EQ_LOW, 0.0f)
                    .putFloat(PREF_EQ_MID, 0.0f)
                    .putFloat(PREF_EQ_HIGH, 0.0f)
                    .putBoolean(PREF_EQ_ENABLED, true)
                    .remove(PREF_CABINET_IR_PATH)
                    .remove(PREF_CABINET_IR_IMAGE)
                    .remove(PREF_CABINET_IR_TITLE)
                    .remove(PREF_CABINET_IR_TYPE)
                    .remove(PREF_CABINET_IR_POSITION)
                    .remove(PREF_CABINET_IR_BYPASS)
                    .remove(PREF_CABINET_IR_IN_GAIN)
                    .remove(PREF_CABINET_IR_OUT_GAIN)
                    .remove(PREF_CABINET_IR_MIX)
                    .remove(PREF_CABINET_IR_EQ_PRE)
                    .remove(PREF_CABINET_IR_EQ_ENABLED)
                    .remove(PREF_FX_CHAIN)
                    .apply {
                        for (band in 0 until 6) remove(PREF_CABINET_IR_EQ_PREFIX + band)
                    }
                    .apply()
                bypass = false
                audioEngine.nativeSetBypass(false)
                true
            } catch (error: Exception) {
                Log.e(API_TAG, "Reset to default failed", error)
                false
            }
        }


        override fun setNamBypass(
            chainIndex: Int,
            enabled: Boolean
        ) {

            audioEngine.nativeSetChainNamBypass(
                chainIndex,
                enabled
            )


            if (chainIndex == 0) {

                bypass =
                    enabled

                prefs.edit().putBoolean(PREF_NAM_BYPASS, enabled).apply()


return
            }


            val entries =
                readExtraNamChain()


            val metadataIndex =
                chainIndex -
                        1


            if (
                metadataIndex in
                entries.indices
            ) {

                val current =
                    entries[
                        metadataIndex
                    ]


                entries[
                    metadataIndex
                ] =
                    current.copy(
                        bypass =
                            enabled
                    )


                persistExtraNamChain(
                    entries
                )
            }
        }


        override fun setNamGain(chainIndex: Int, db: Double) {
            setNamControl(chainIndex, db, 0)
        }

        override fun setNamInGain(chainIndex: Int, db: Double) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val value = db.toFloat().coerceIn(-24.0f, 24.0f)
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(inGainDb = value)
            persistNamChainEntries(all)
            audioEngine.nativeSetChainNamInGainDb(chainIndex, value)
        }

        override fun setNamMix(chainIndex: Int, mix: Double) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val value = mix.toFloat().coerceIn(0.0f, 1.0f)
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(mix = value)
            persistNamChainEntries(all)
            audioEngine.nativeSetChainNamMix(chainIndex, value)
        }

        override fun setNamEq(chainIndex: Int, band: Int, db: Double) {
            setNamControl(chainIndex, db, band + 1)
        }

        override fun setNamEqPosition(chainIndex: Int, pre: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(eqPre = pre)
            persistNamChainEntries(all)
            audioEngine.nativeSetChainNamEqPre(chainIndex, pre)
        }

        override fun setNamEqEnabled(chainIndex: Int, enabled: Boolean) {
            if (chainIndex == 0) {
                prefs.edit().putBoolean(PREF_NAM_EQ_ENABLED, enabled).apply()
            } else {
                val entries = readNamChainEntries()
                val extraIndex = chainIndex - 1
                if (extraIndex !in entries.indices) return
                val all = entries.toMutableList()
                all[extraIndex] = all[extraIndex].copy(eqEnabled = enabled)
                persistNamChainEntries(all)
            }
            audioEngine.nativeSetChainNamEqEnabled(chainIndex, enabled)
        }

        override fun setNamNormalize(chainIndex: Int, enabled: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(normalize = enabled)
            persistNamChainEntries(all)
            audioEngine.nativeSetChainNamNormalize(chainIndex, enabled)
        }

        override fun setNamQuality(chainIndex: Int, full: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            if (full && entries[chainIndex].moduleType != "AMP") {
                runOnUiThread { status.value = "A2 Full is available for AMP blocks only." }
                return
            }
            val result = audioEngine.nativeSetChainNamQuality(chainIndex, full)
            if (!result.startsWith("A2 ")) {
                runOnUiThread { status.value = result }
                return
            }
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(a2Full = full)
            persistNamChainEntries(all)
            runOnUiThread { status.value = result }
        }

        private fun setNamControl(chainIndex: Int, rawDb: Double, control: Int) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val current = entries[chainIndex]
            val value = rawDb.toFloat().coerceIn(if (control == 0) -24.0f else -12.0f,
                if (control == 0) 12.0f else 12.0f)
            val updated = when (control) {
                0 -> current.copy(gainDb = value)
                1 -> current.copy(eqLowDb = value)
                2 -> current.copy(eqMidDb = value)
                3 -> current.copy(eqHighDb = value)
                4 -> current.copy(eqBand3Db = value)
                5 -> current.copy(eqBand4Db = value)
                else -> current.copy(eqBand5Db = value)
            }
            val all = entries.toMutableList()
            all[chainIndex] = updated
            persistNamChainEntries(all)
            if (control == 0) audioEngine.nativeSetChainNamGainDb(chainIndex, value)
            else audioEngine.nativeSetChainNamEqDb(chainIndex, control - 1, value)
        }


        fun clearExtraNams() {

            clearExtraNamChain()
        }


        override fun loadPreset(
            slot: Int
        ) {

            if (
                slot !in
                1..PRESET_COUNT
            ) {
                return
            }


            runOnUiThread {

                loadPreset(
                    slot
                )
            }
        }


        override fun savePreset(
            slot: Int
        ) {

            if (
                slot !in
                1..PRESET_COUNT
            ) {
                return
            }


            runOnUiThread {

                savePreset(
                    slot
                )
            }
        }

        fun renamePreset(slot: Int, name: String): Boolean {
            return presetRepository.rename(slot, name)
        }

        fun deletePreset(slot: Int): Boolean {
            return presetRepository.delete(slot)
        }

        fun movePreset(slot: Int, delta: Int): Boolean {
            return presetRepository.move(slot, delta)
        }


        override fun scanUsbAudio(): String {

            return audioEngine.nativeScanUsbAudio()
        }


        fun getStats(): String {

            return audioEngine.nativeGetStats()
        }

    }


    // ========================================================
    // ROUTING
    // ========================================================

    private fun restoreRoutingSettings() {
        audioRoutingUseCase.restoreSavedRoutes()
    }


    // ========================================================
    // DSP CHAIN
    // ========================================================

    private fun restoreDspSettings() {

        val gateEnabled =
            prefs.getBoolean(
                PREF_GATE_ENABLED,
                false
            )


        val gateThreshold =
            prefs.getFloat(
                PREF_GATE_THRESHOLD,
                -65.0f
            )


        val lowDb =
            prefs.getFloat(
                PREF_EQ_LOW,
                0.0f
            )


        val midDb =
            prefs.getFloat(
                PREF_EQ_MID,
                0.0f
            )


        val highDb =
            prefs.getFloat(
                PREF_EQ_HIGH,
                0.0f
            )


        audioEngine.nativeSetGateEnabled(
            gateEnabled
        )

        audioEngine.nativeSetGateThresholdDb(
            gateThreshold
        )

        audioEngine.nativeSetEqLowDb(
            lowDb
        )

        audioEngine.nativeSetEqMidDb(
            midDb
        )

        audioEngine.nativeSetEqHighDb(
            highDb
        )

        audioEngine.nativeSetEqEnabled(
            prefs.getBoolean(PREF_EQ_ENABLED, true)
        )


    }


    // ========================================================
    // PRESETS
    // ========================================================

    private fun readPreset(slot: Int): PresetData? = presetRepository.read(slot)

    private fun presetLabel(slot: Int): String = presetRepository.label(slot)


    private fun showSavePresetDialog() {

        val currentPath =
            prefs.getString(
                PREF_LAST_MODEL_PATH,
                null
            )


        if (
            currentPath == null ||
            !File(currentPath).exists()
        ) {

            status.value =
                "PRESET SAVE FAILED\n\nNo NAM model is currently loaded."

            return
        }


        val labels =
            Array(
                PRESET_COUNT
            ) { index ->

                presetLabel(
                    index +
                            1
                )
            }


        AlertDialog
            .Builder(this)

            .setTitle(
                "SAVE PRESET"
            )

            .setItems(
                labels
            ) { _, index ->

                savePreset(
                    index +
                            1
                )
            }

            .setNegativeButton(
                "Cancel",
                null
            )

            .show()
    }


    private fun savePreset(slot: Int) {
        status.value = "Saving preset $slot..."

        Thread {
            try {
                val savedLabel = savePresetUseCase.execute(slot, bypass)
                runOnUiThread {
                    status.value = "PRESET $slot SAVED\n\n$savedLabel"
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Preset save failed", error)
                runOnUiThread {
                    status.value = "PRESET SAVE FAILED\n\n${error.message ?: error}"
                }
            }
        }.start()
    }


    private fun showLoadPresetDialog() {

        val availableSlots =
            (
                    1..PRESET_COUNT
                    )
                .filter {

                    readPreset(
                        it
                    ) != null
                }


        if (availableSlots.isEmpty()) {

            status.value =
                "No saved presets."

            return
        }


        val labels =
            availableSlots
                .map {

                    presetLabel(
                        it
                    )
                }
                .toTypedArray()


        AlertDialog
            .Builder(this)

            .setTitle(
                "LOAD PRESET"
            )

            .setItems(
                labels
            ) { _, index ->

                loadPreset(
                    availableSlots[index]
                )
            }

            .setNegativeButton(
                "Cancel",
                null
            )

            .show()
    }


    private fun loadPreset(
        slot: Int
    ) {

        val preset =
            readPreset(
                slot
            )
                ?: run {

                    status.value =
                        "PRESET LOAD FAILED\n\nPreset $slot is empty."

                    return
                }




        status.value =
            "LOADING PRESET $slot...\n\n" +
                    presetLabel(
                        slot
                    )


        Thread {

            try {

                val result = loadPresetUseCase.execute(preset).engineResult

                val restoredChain = rebuildNativeNamChain(readNamChainEntries())
                val irPath = preset.cabinetIrPath
                if (irPath != null && File(irPath).exists()) {
                    audioEngine.nativeLoadImpulseResponse(irPath)
                    audioEngine.nativeSetImpulseResponseBypass(preset.cabinetIrBypass)
                    audioEngine.nativeSetImpulseResponsePosition(preset.cabinetIrPosition)
                    audioEngine.nativeSetImpulseResponseInGainDb(preset.cabinetIrInGain)
                    audioEngine.nativeSetImpulseResponseOutGainDb(preset.cabinetIrOutGain)
                    audioEngine.nativeSetImpulseResponseMix(preset.cabinetIrMix)
                    audioEngine.nativeSetImpulseResponseEqPre(preset.cabinetIrEqPre)
                    preset.cabinetIrEqDb.forEachIndexed(audioEngine::nativeSetImpulseResponseEqDb)
                } else {
                    audioEngine.nativeClearImpulseResponse()
                }


                runOnUiThread {
                    bypass =
                        false

                    audioEngine.nativeSetBypass(
                        false
                    )

                    val audioResult = audioEngine.nativeStart()
                    status.value += "\n\n$audioResult"





                    status.value =
                        "PRESET $slot READY\n\n" +
                                result + "\n" + restoredChain
                }


            } catch (
                e: Exception
            ) {

                Log.e(
                    API_TAG,
                    "Preset load failed",
                    e
                )


                runOnUiThread {


                    status.value =
                        "PRESET LOAD FAILED\n\n" +
                                (
                                        e.message
                                            ?: e.toString()
                                        )
                }
            }

        }.start()
    }

    // ========================================================
    // GAIN
    // ========================================================

    private fun applyRequestedAudioDefaultsOnce() {
        if (prefs.getBoolean(PREF_EXPERIMENT_DEFAULTS_APPLIED, false)) return

        // This explicit preference change establishes the test condition on
        // existing installs once, while future installs use the same default.
        prefs.edit().putFloat(PREF_OUTPUT_GAIN, -10.0f).apply()

        val entries = readNamChainEntries()
        if (entries.isNotEmpty()) {
            persistNamChainEntries(entries.map { entry ->
                entry.copy(a2Full = entry.moduleType == "AMP")
            })
        }

        prefs.edit().putBoolean(PREF_EXPERIMENT_DEFAULTS_APPLIED, true).apply()
    }

    private fun restoreGainSettings() {

        val inputGain =
            prefs.getFloat(
                PREF_INPUT_GAIN,
                0.0f
            )

        val outputGain =
            prefs.getFloat(
                PREF_OUTPUT_GAIN,
                -10.0f
            )


        audioEngine.nativeSetInputGainDb(
            inputGain
        )

        audioEngine.nativeSetOutputGainDb(
            outputGain
        )




    }


    // ========================================================
    // SAVED MODEL
    // ========================================================

    private fun restoreLastModel() {

        val path =
            prefs.getString(
                PREF_LAST_MODEL_PATH,
                null
            )

        if (path == null) {

            val extraEntries = readExtraNamChain()
            if (extraEntries.isNotEmpty()) {
                Thread {
                    val restored = rebuildNativeNamChain(extraEntries)
                    val audio = if (restored.startsWith("NAM CHAIN READY")) audioEngine.nativeStart() else restored
                    runOnUiThread {
                        status.value = restored + "\n" + audio
                    }
                }.start()
                return
            }

            status.value =
                "Ready.\n\nNo saved model."


            return
        }


        val file =
            File(
                path
            )


        if (!file.exists()) {

            activeToneRepository.clear()

            status.value =
                "Saved model file no longer exists."


            return
        }


        val name =
            prefs.getString(
                PREF_LAST_MODEL_NAME,
                null
            ) ?: file.name


        val size =
            prefs.getString(
                PREF_LAST_MODEL_SIZE,
                null
            ) ?: "unknown"


        val toneTitle =
            prefs.getString(
                PREF_LAST_TONE_TITLE,
                null
            )


        status.value =
            "Loading saved model..."


        Thread {

            val result =
                audioEngine.nativeLoadModel(
                    file.absolutePath
                )


            val extraRestore =
                if (
                    result.startsWith(
                        "MODEL LOADED"
                    )
                ) {

                    val restored = restoreExtraNamChainNative()
                    applyPersistedNamControls()
                    val irPath = prefs.getString(PREF_CABINET_IR_PATH, null)
                    if (irPath != null && File(irPath).exists()) {
                        audioEngine.nativeLoadImpulseResponse(irPath)
                        audioEngine.nativeSetImpulseResponseBypass(prefs.getBoolean(PREF_CABINET_IR_BYPASS, false))
                        audioEngine.nativeSetImpulseResponsePosition(prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS))
                        audioEngine.nativeSetImpulseResponseInGainDb(prefs.getFloat(PREF_CABINET_IR_IN_GAIN, 0.0f))
                        audioEngine.nativeSetImpulseResponseOutGainDb(prefs.getFloat(PREF_CABINET_IR_OUT_GAIN, 0.0f))
                        audioEngine.nativeSetImpulseResponseMix(prefs.getFloat(PREF_CABINET_IR_MIX, 1.0f))
                        audioEngine.nativeSetImpulseResponseEqPre(prefs.getBoolean(PREF_CABINET_IR_EQ_PRE, false))
                        audioEngine.nativeSetImpulseResponseEqEnabled(prefs.getBoolean(PREF_CABINET_IR_EQ_ENABLED, true))
                        for (band in 0 until 6) audioEngine.nativeSetImpulseResponseEqDb(band, prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + band, 0.0f))
                    }
                    readFxChain().forEachIndexed { slot, fx ->
                        val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, fx.path)
                        if (loaded.startsWith("FX LOADED")) {
                            audioEngine.nativeSetFxImpulseResponseBypass(slot, fx.bypass)
                            audioEngine.nativeSetFxImpulseResponseMix(slot, fx.mix)
                            val namCount = audioEngine.nativeGetNamBlockCount()
                            audioEngine.nativeSetFxImpulseResponsePosition(slot, fx.position.coerceIn(0, namCount))
                        }
                    }
                    restored

                } else {

                    ""
                }


            runOnUiThread {

                if (
                    result.startsWith(
                        "MODEL LOADED"
                    )
                ) {





                    status.value =
                        "SAVED MODEL LOADED\n\n" +
                                result +
                                "\n\n" +
                                extraRestore


                    bypass =
                        false

                    audioEngine.nativeSetBypass(
                        false
                    )


                } else {


                    status.value =
                        result
                }
            }

        }.start()
    }


    // ========================================================
    // TONE3000 BROWSE
    // ========================================================

    private fun startTone3000SelectFlow(
        importMode: String = "replace"
    ) {
        val labels = arrayOf("PEDAL", "AMP (NAM A2-Lite)", "FX (reverb / IR)", "IR (cabinet)")
        AlertDialog.Builder(this)
            .setTitle("Adicionar módulo")
            .setItems(labels) { _, which ->
                val type = when (which) { 0 -> "PEDAL"; 1 -> "AMP"; 2 -> "FX"; else -> "IR" }
                prefs.edit()
                    .putString(PREF_SELECTED_ADD_TYPE, type)
                    .putString(PREF_PENDING_TONE_TYPE, type)
                    .apply()
                openTone3000SelectFlow(importMode)
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun openTone3000SelectFlow(
        importMode: String =
            "replace"
    ) {

        if (
            PUBLISHABLE_KEY.contains(
                "COLOQUE_SUA_CHAVE"
            )
        ) {

            status.value =
                "Configure PUBLISHABLE_KEY primeiro."

            return
        }


        /*
         * Going to the browser must never destroy the current model.
         * We stop audio and disable START while a new selection is pending,
         * but the currently persisted/current DSP remains available until
         * a new capture has been downloaded and loaded successfully.
         */
        audioEngine.nativeStop()



        prefs
            .edit()
            .putString(
                PREF_PENDING_IMPORT_MODE,
                importMode
            )
            .apply()


        val selectedModuleType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
        prefs.edit().putString(PREF_PENDING_TONE_TYPE, selectedModuleType).apply()
        lifecycleScope.launch {
            try {
                val authorization = withContext(Dispatchers.IO) {
                    prepareToneAuthorizationUseCase.execute()
                }
                val gears = when (selectedModuleType) {
                    "PEDAL" -> "pedal"
                    "FX" -> "space"
                    "IR" -> "cab"
                    else -> "amp-cab_amp"
                }
                val uri = Uri.parse(AUTHORIZE_URL).buildUpon()
                    .appendQueryParameter("client_id", PUBLISHABLE_KEY)
                    .appendQueryParameter("redirect_uri", REDIRECT_URI)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("code_challenge", authorization.codeChallenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("state", authorization.state)
                    .appendQueryParameter("prompt", "select_tone")
                    .appendQueryParameter("format", if (selectedModuleType == "IR" || selectedModuleType == "FX") "ir" else "nam")
                    .appendQueryParameter("gears", gears)
                    .apply {
                        if (selectedModuleType != "IR" && selectedModuleType != "FX") {
                            appendQueryParameter("architecture", "2")
                        }
                    }
                    .appendQueryParameter("menubar", "true")
                    .appendQueryParameter("preview", "true")
                    .build()

                status.value = "Opening TONE3000...\n\nCurrent model preserved until a new capture is loaded."
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this@MainActivity, uri)
            } catch (error: Exception) {
                Log.e(API_TAG, "Could not prepare TONE3000 authorization", error)
                status.value = "Could not open TONE3000.\n${error.message}"
                restoreCurrentModelAvailability("TONE3000 authorization setup failed.")
            }
        }
    }

    // ========================================================
    // OAUTH CALLBACK
    // ========================================================

    private fun handleOAuthIntent(
        callbackIntent: Intent
    ) {

        val uri =
            callbackIntent.data
                ?: return


        if (!isOAuthCallback(uri)) {
            return
        }


        if (oauthCallbackInProgress) {

            Log.w(
                API_TAG,
                "OAuth callback already processing"
            )

            return
        }


        val error =
            uri.getQueryParameter(
                "error"
            )


        if (error != null) {

            restoreCurrentModelAvailability(
                "TONE3000 OAuth error:\n$error"
            )

            return
        }


        val canceled =
            uri.getQueryParameter(
                "canceled"
            ) == "true"


        val code =
            uri.getQueryParameter(
                "code"
            )


        val returnedState =
            uri.getQueryParameter(
                "state"
            )


        val toneId =
            uri.getQueryParameter(
                "tone_id"
            )


        if (
            canceled &&
            toneId == null
        ) {

            lifecycleScope.launch(Dispatchers.IO) { toneSessionRepository.clearPendingAuthorization() }

            restoreCurrentModelAvailability(
                "TONE3000 selection canceled."
            )

            return
        }


        if (code == null) {

            restoreCurrentModelAvailability(
                "OAuth callback sem code."
            )

            return
        }


        if (toneId == null) {

            restoreCurrentModelAvailability(
                "OAuth OK, mas nenhum tone foi selecionado."
            )

            return
        }


        oauthCallbackInProgress =
            true




        setIntent(
            Intent(
                this,
                MainActivity::class.java
            )
        )


        Thread {

            try {

                stage(
                    "1/3 - EXCHANGING TOKEN..."
                )


                val selectedType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
                prefs.edit()
                    .putString(PREF_SELECTED_ADD_TYPE, selectedType)
                    .putString(PREF_PENDING_TONE_TYPE, selectedType)
                    .apply()
                val selection = runBlocking {
                    completeToneSelectionUseCase.execute(
                        code = code,
                        returnedState = returnedState,
                        toneId = toneId,
                        moduleType = selectedType,
                        onProgress = ::stage,
                    )
                }
                val token = selection.token.accessToken
                val tone = selection.tone
                val models = selection.models
                runBlocking { tonePackageCaptureRepository.save(toneId, selectedType, models) }
                val architecture = if (selection.moduleType == "IR" || selection.moduleType == "FX") null else 2


                Log.i(
                    API_TAG,
                    "OAuth flow complete: ${models.size} A2 captures"
                )


                runOnUiThread {
                    if (architecture == null) {
                        if (models.isEmpty()) {
                            restoreCurrentModelAvailability("No impulse response found for:\n${tone.title}")
                        } else if (selectedType == "FX") {
                            showFxModelSelectionDialog(
                                toneId = toneId,
                                toneTitle = tone.title,
                                imageUrl = "",
                                models = models,
                                token = token
                            )
                        } else {
                            stage("Loading impulse response:\n${models.first().name}")
                            val selectedModel = models.first()
                            Thread {
                                AudioAppController().loadSelectedCabinet(
                                    toneId = toneId,
                                    toneTitle = tone.title,
                                    modelId = selectedModel.id,
                                    modelName = selectedModel.name,
                                    modelSize = selectedModel.size,
                                    modelUrl = selectedModel.modelUrl,
                                    token = token,
                                    moduleType = selectedType
                                )
                            }.start()
                        }
                    } else {
                        showModelSelectionDialog(
                            toneId = toneId,
                            toneTitle = tone.title,
                            models = models,
                            token = token
                        )
                    }
                }


            } catch (
                e: Exception
            ) {

                Log.e(
                    API_TAG,
                    "TONE3000 flow failed",
                    e
                )


                runOnUiThread {

                    restoreCurrentModelAvailability(
                        "TONE3000 ERROR\n\n" +
                                e.javaClass.simpleName +
                                "\n" +
                                (
                                        e.message
                                            ?: e.toString()
                                        )
                    )
                }

            } finally {

                oauthCallbackInProgress =
                    false
            }

        }.start()
    }

    private fun stage(
        value: String
    ) {

        Log.i(
            API_TAG,
            value
        )


        runOnUiThread {

            status.value =
                value
        }
    }


    private data class PackageCaptureSource(
        val blockId: String,
        val toneId: String,
        val toneTitle: String,
        val moduleType: String,
        val imageUrl: String,
        val importMode: String,
    )


    // ========================================================
    // MODEL SELECTION
    // ========================================================

    private fun showModelSelectionDialog(
        toneId: String,
        toneTitle: String,
        models: List<OnlineModel>,
        token: String
    ) {

        // Keep the full compatible package list with the block. The small
        // in-editor selector must offer the same captures as the browser that
        // originally loaded this package, even if a later API query is partial.
        val selectedModuleType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
        if (models.isEmpty()) {

            restoreCurrentModelAvailability(
                "No compatible A2 captures found for:\n$toneTitle"
            )

            return
        }


        if (models.size == 1) {

            val model =
                models[0]


            Log.i(
                API_TAG,
                "Only one A2 capture available; auto-selecting " +
                        "${model.name} (#${model.id})"
            )


            status.value =
                "1 compatible A2 capture found.\n\n" +
                        "Loading:\n${model.name}"




            downloadAndLoadModel(
                toneId =
                    toneId,

                toneTitle =
                    toneTitle,

                model =
                    model,

                token =
                    token
            )

            return
        }


        val order =
            mapOf(
                "standard" to 0,
                "lite" to 1,
                "feather" to 2,
                "nano" to 3,
                "custom" to 4
            )


        val sorted =
            models.sortedWith(

                compareBy<OnlineModel> {

                    order[
                        it.size.lowercase()
                    ] ?: 99

                }.thenBy {

                    it.name.lowercase()
                }
            )


        val hidePedalTier = selectedModuleType == "PEDAL"
        val labels =
            sorted
                .map {
                    if (hidePedalTier) "${it.name} • #${it.id}"
                    else "${it.size.uppercase()} • ${it.name} • #${it.id}"
                }
                .toTypedArray()




        status.value =
            "$toneTitle\n\n" +
                    "${sorted.size} compatible captures found.\n" +
                    "Select a capture to continue."


        /*
         * Android AlertDialog uses the same content panel for a message and
         * a ListView. Combining setMessage() + setItems() can result in the
         * list not being rendered by some platform/theme combinations.
         *
         * For a TONE3000 package with multiple captures the list is the
         * important content, so keep the dialog list-only and put the context
         * in the title/status instead.
         */
        val importMode =
            prefs.getString(
                PREF_PENDING_IMPORT_MODE,
                "replace"
            ) ?: "replace"


        val actionVerb = if (importMode == "add") "ADD" else "REPLACE"
        val actionTarget = if (hidePedalTier) "PEDAL" else "NAM"
        val actionTitle =
            if (importMode == "add") {

                "$actionVerb $actionTarget — SELECT CAPTURE"

            } else {

                val replacementIndex = importMode
                    .removePrefix("replace:")
                    .toIntOrNull()

                if (replacementIndex != null) {
                    "$actionVerb $actionTarget ${replacementIndex + 1} — SELECT CAPTURE"
                } else {
                    "$actionVerb $actionTarget 1 — SELECT CAPTURE"
                }
            }


        val dialog =
            AlertDialog
                .Builder(this)

                .setTitle(
                    actionTitle
                )

                .setItems(
                    labels
                ) { _, index ->

                    val selected =
                        sorted[index]


                    Log.i(
                        API_TAG,
                        "Capture selected: " +
                                "${selected.name} " +
                                "(id=${selected.id}, size=${selected.size}) " +
                                "importMode=$importMode"
                    )


                    status.value =
                        "$toneTitle\n\n" +
                                "Selected capture:\n" +
                                if (hidePedalTier) "${selected.name} • #${selected.id}"
                                else "${selected.name}\n${selected.size.uppercase()} • #${selected.id}"


                    downloadAndLoadModel(
                        toneId =
                            toneId,

                        toneTitle =
                            toneTitle,

                        model =
                            selected,

                        token =
                            token
                    )
                }

                .setNegativeButton(
                    "CANCEL"
                ) { _, _ ->

                    prefs
                        .edit()
                        .remove(
                            PREF_PENDING_IMPORT_MODE
                        )
                        .apply()


                    restoreCurrentModelAvailability(
                        "Capture selection canceled.\n" +
                                "Current chain kept."
                    )
                }

                .setCancelable(
                    false
                )

                .create()


        dialog.setCanceledOnTouchOutside(
            false
        )


        dialog.show()
    }

    private fun showFxModelSelectionDialog(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        models: List<OnlineModel>,
        token: String
    ) {
        if (models.isEmpty()) {
            prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
            restoreCurrentModelAvailability("No space captures found for:\n$toneTitle")
            return
        }

        fun loadSelected(model: OnlineModel) {
            status.value = "$toneTitle\n\nSelected space capture:\n${model.name}\nLoading..."
            Thread {
                AudioAppController().loadSelectedCabinet(
                    toneId = toneId,
                    toneTitle = toneTitle,
                    modelId = model.id,
                    modelName = model.name,
                    modelSize = model.size,
                    modelUrl = model.modelUrl,
                    token = token,
                    moduleType = "FX",
                )
            }.start()
        }

        if (models.size == 1) {
            loadSelected(models.single())
            return
        }

        val labels = models.map { "${it.name} • #${it.id}" }.toTypedArray()
        val importMode = prefs.getString(PREF_PENDING_IMPORT_MODE, "add") ?: "add"
        val targetIndex = importMode.removePrefix("replace-fx:").toIntOrNull()
        val title = if (targetIndex != null) {
            "REPLACE FX ${targetIndex + 1} — SELECT SPACE CAPTURE"
        } else {
            "ADD FX — SELECT SPACE CAPTURE"
        }
        status.value = "$toneTitle\n\n${models.size} space captures found. Select one to continue."
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, index -> loadSelected(models[index]) }
            .setNegativeButton("CANCEL") { _, _ ->
                prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
                restoreCurrentModelAvailability("Space selection canceled.\nCurrent chain kept.")
            }
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun showCabinetModelSelectionDialog(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        models: List<OnlineModel>,
        token: String,
    ) {
        if (models.isEmpty()) {
            restoreCurrentModelAvailability("No cabinet captures found for:\n$toneTitle")
            return
        }

        fun loadSelected(model: OnlineModel) {
            status.value = "$toneTitle\n\nSelected cabinet capture:\n${model.name}\nLoading..."
            Thread {
                AudioAppController().loadSelectedCabinet(
                    toneId = toneId,
                    toneTitle = toneTitle,
                    modelId = model.id,
                    modelName = model.name,
                    modelSize = model.size,
                    modelUrl = model.modelUrl,
                    token = token,
                    moduleType = "IR",
                )
            }.start()
        }

        if (models.size == 1) {
            loadSelected(models.single())
            return
        }

        val labels = models.map { "${it.name} • #${it.id}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("CABINET IR — SELECT CAPTURE")
            .setItems(labels) { _, index -> loadSelected(models[index]) }
            .setNegativeButton("CANCEL") { _, _ ->
                restoreCurrentModelAvailability("Cabinet selection canceled.\nCurrent chain kept.")
            }
            .setCancelable(false)
            .create()
            .also { it.setCanceledOnTouchOutside(false) }
            .show()
    }

    private fun showPackageCaptureUnavailable(message: String = "No package captures are associated with this block.") {
        AlertDialog.Builder(this)
            .setTitle("PACKAGE CAPTURES")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========================================================
    // MODEL DOWNLOAD
    // ========================================================

    private fun downloadAndLoadModel(
        toneId: String,
        toneTitle: String,
        model: OnlineModel,
        token: String
    ) {

        val imageUrl = prefs.getString(PREF_PENDING_TONE_IMAGE, "") ?: ""
        val moduleType = prefs.getString(PREF_PENDING_TONE_TYPE, "AMP") ?: "AMP"



        status.value =
            "DOWNLOADING CAPTURE...\n\n" +
                    "Tone: $toneTitle\n" +
                    "Capture: ${model.name}\n" +
                    "Size: ${model.size.uppercase()}\n" +
                    "Model ID: ${model.id}"


        Thread {

            var pendingFile: File? =
                null


            try {

                val importMode =
                    prefs.getString(
                        PREF_PENDING_IMPORT_MODE,
                        "replace"
                    ) ?: "replace"

                val replacementIndex = importMode
                    .removePrefix("replace:")
                    .toIntOrNull()


                audioEngine.nativeStop()


                val downloaded =
                    downloadToneModelUseCase.execute(
                        model =
                            model,

                        token =
                            token,

                        fileName =
                            "pending-tone3000-model-${model.id}.nam"
                    )
                pendingFile = downloaded


                if (
                    importMode ==
                    "add"
                ) {

                    stage(
                        "ADDING NAM BLOCK...\n${model.name}"
                    )


                    val added = addExtraNamCaptureUseCase.execute(
                        pendingFile = downloaded,
                        toneId = toneId,
                        toneTitle = toneTitle,
                        imageUrl = imageUrl,
                        model = model,
                        moduleType = moduleType,
                    )
                    pendingFile = null


                    prefs
                        .edit()
                        .remove(
                            PREF_PENDING_IMPORT_MODE
                        )
                        .apply()


                    runOnUiThread {



                        status.value =
                            "NAM BLOCK ADDED\n\n" +
                                    "Tone: $toneTitle\n" +
                                    "Capture: ${model.name}\n" +
                                    "Size: ${model.size.uppercase()}\n\n" +
                                    added.chainResult + "\n" + added.audioResult
                    }


                    return@Thread
                }


                if (replacementIndex != null && replacementIndex >= 0) {
                    stage("REPLACING NAM BLOCK ${replacementIndex + 1}...\n${model.name}")

                    val replaced = replaceNamCaptureUseCase.execute(
                        currentEntries = readNamChainEntries(),
                        replacementIndex = replacementIndex,
                        pendingFile = downloaded,
                        toneId = toneId,
                        toneTitle = toneTitle,
                        imageUrl = imageUrl,
                        model = model,
                        moduleType = moduleType,
                    )
                    pendingFile = null

                    persistNamChainEntries(replaced.entries)
                    if (replaced.previousPath != replaced.replacement.path) {
                        File(replaced.previousPath).delete()
                    }

                    prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()

                    runOnUiThread {
                        status.value = "NAM BLOCK ${replacementIndex + 1} REPLACED\n\n" +
                                "Tone: $toneTitle\n" +
                                "Capture: ${model.name}\n" +
                                "Size: ${model.size.uppercase()}\n\n" +
                                replaced.rebuildResult + "\n" + replaced.audioResult
                    }

                    return@Thread
                }


                stage("LOADING CAPTURE...\n${model.name}")
                val loadedCapture = loadPrimaryToneCaptureUseCase.execute(
                    toneId = toneId,
                    toneTitle = toneTitle,
                    model = model,
                    moduleType = moduleType,
                    downloadedFile = downloaded,
                )
                pendingFile = null
                val loadResult = loadedCapture.loadResult
                val audioResult = loadedCapture.audioResult

                prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()

                runOnUiThread {
                    bypass = false
                    audioEngine.nativeSetBypass(false)
                    status.value = "TONE3000 CAPTURE READY\n\n$loadResult\n$audioResult"
                }

            } catch (
                e: Exception
            ) {

                pendingFile?.delete()


                Log.e(
                    API_TAG,
                    "Capture download/load failed",
                    e
                )


                /*
                 * A failed switch must not leave the app in an ambiguous state.
                 * Reload the previously persisted model explicitly.
                 */
                val previous =
                    reloadPersistedModelAfterFailedSwitch()


                runOnUiThread {



                    if (previous.first) {

                        bypass =
                            false

                        audioEngine.nativeSetBypass(
                            false
                        )

                    }


                    status.value =
                        "CAPTURE ERROR\n\n" +
                                e.javaClass.simpleName +
                                "\n" +
                                (
                                        e.message
                                            ?: e.toString()
                                        ) +
                                "\n\n" +
                                if (previous.first) {
                                    "Previous model restored."
                                } else {
                                    "No previous model could be restored.\n" +
                                            previous.second
                                }
                }
            }

        }.start()
    }

    private fun restoreCurrentModelAvailability(
        message: String
    ) {

        val path =
            prefs.getString(
                PREF_LAST_MODEL_PATH,
                null
            )


        val available =
            path != null &&
                    File(path).exists()




        status.value =
            buildString {

                append(
                    message
                )


                if (available) {

                    append(
                        "\n\nCurrent model kept and ready."
                    )

                } else {

                    append(
                        "\n\nNo current model is available."
                    )
                }
            }
    }


    private fun reloadPersistedModelAfterFailedSwitch(): Pair<Boolean, String> {
        val result = restorePreviousToneModelUseCase.execute()
        return result.restored to result.details
    }

    // ========================================================
    // PERSIST MODEL
    // ========================================================

    // ========================================================
    // UI STATE
    // ========================================================

}
