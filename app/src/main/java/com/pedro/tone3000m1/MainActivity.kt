package com.pedro.tone3000m1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import com.pedro.tone3000m1.data.model.ExtraNamEntry
import com.pedro.tone3000m1.data.model.OnlineModel
import com.pedro.tone3000m1.data.model.PicoloStateSnapshot
import com.pedro.tone3000m1.data.repository.NamChainRepository
import com.pedro.tone3000m1.data.repository.FxChainRepository
import com.pedro.tone3000m1.data.repository.PresetRepositoryImpl
import com.pedro.tone3000m1.data.repository.AudioRoutingRepositoryImpl
import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import com.pedro.tone3000m1.data.repository.PicoloStateRepository
import com.pedro.tone3000m1.data.repository.Tone3000ApiRepository
import com.pedro.tone3000m1.domain.usecase.AudioRoutingUseCase
import com.pedro.tone3000m1.domain.usecase.LoadPresetUseCase
import com.pedro.tone3000m1.domain.usecase.SavePresetUseCase
import com.pedro.tone3000m1.domain.model.PresetData
import com.pedro.tone3000m1.domain.repository.PresetRepository
import com.pedro.tone3000m1.ui.actions.PicoloActions
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
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

        private const val PREF_VERIFIER =
            "pkce_verifier"

        private const val PREF_STATE =
            "oauth_state"

        private const val PREF_ACCESS_TOKEN =
            "access_token"

        private const val PREF_REFRESH_TOKEN =
            "refresh_token"

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

        private const val PREF_LAST_MODEL_TYPE =
            "last_model_type"

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

        private const val PREF_EQ_ENABLED = "eq_enabled"

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
        private const val PREF_NAM_EQ_ENABLED = "nam_eq_enabled"
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

    private lateinit var legacyScrollView: ScrollView
    private lateinit var rootLayout: FrameLayout
    private lateinit var composeView: ComposeView

    private lateinit var status: TextView
    private lateinit var currentModelText: TextView
    private lateinit var audioDeviceText: TextView

    private lateinit var inputGainText: TextView
    private lateinit var outputGainText: TextView
    private lateinit var routingText: TextView

    private lateinit var inputRouteButton: Button
    private lateinit var outputRouteButton: Button

    private lateinit var presetText: TextView
    private lateinit var savePresetButton: Button
    private lateinit var loadPresetButton: Button

    private lateinit var inputGainSlider: SeekBar
    private lateinit var outputGainSlider: SeekBar

    private lateinit var dspChainText: TextView
    private lateinit var gateButton: Button
    private lateinit var gateThresholdText: TextView
    private lateinit var gateThresholdSlider: SeekBar

    private lateinit var eqLowText: TextView
    private lateinit var eqMidText: TextView
    private lateinit var eqHighText: TextView

    private lateinit var eqLowSlider: SeekBar
    private lateinit var eqMidSlider: SeekBar
    private lateinit var eqHighSlider: SeekBar

    private lateinit var startButton: Button
    private lateinit var bypassButton: Button

    private var bypass =
        false

    private var gateEnabled =
        false

    private var applyingPresetUi =
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

    private val prefs by lazy {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
    }

    private val audioEngine by lazy { NativeAudioEngine() }

    private val namChainRepository by lazy {
        NamChainRepository(prefs, PREF_EXTRA_NAM_CHAIN, PREF_LAST_MODEL_PATH)
    }

    private val fxChainRepository by lazy {
        FxChainRepository(prefs, PREF_FX_CHAIN, PREF_FX_NATIVE_CHAIN)
    }

    private val presetRepository: PresetRepository by lazy {
        PresetRepositoryImpl(prefs, filesDir, PRESET_COUNT, MAX_NAM_BLOCKS)
    }

    private val audioRoutingUseCase by lazy {
        AudioRoutingUseCase(
            AudioRoutingRepositoryImpl(
                preferences = prefs,
                engine = audioEngine,
                inputChannelKey = PREF_INPUT_CHANNEL,
                outputPairKey = PREF_OUTPUT_PAIR,
            ),
        )
    }

    private val savePresetUseCase by lazy {
        SavePresetUseCase(presetRepository)
    }

    private val loadPresetUseCase by lazy {
        LoadPresetUseCase(presetRepository, audioEngine)
    }

    private val tone3000ApiRepository by lazy {
        Tone3000ApiRepository(API_BASE, PUBLISHABLE_KEY, REDIRECT_URI)
    }


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

                status.text =
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
                    runOnUiThread { status.text = result }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "IR LOAD FAILED\n${e.message}" }
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
        refreshPresetUi()

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

            status.text = "TONE3000 callback received...\nPreparing selection..."

            // singleTask activities can receive onNewIntent while already
            // resumed; in that case Android does not call onResume again.
            processPendingOAuthIntent()
        }
    }


    override fun onResume() {

        super.onResume()

        processPendingOAuthIntent()
    }

    @Deprecated("Use back navigation in the active Compose destination")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun processPendingOAuthIntent() {

        val pending =
            pendingOAuthIntent
                ?: return

        pendingOAuthIntent =
            null

        mainHandler.postDelayed(
            {
                showComposeUi()
                status.text = "TONE3000 selection received...\nConnecting to catalog..."
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

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    32,
                    32,
                    32
                )
            }


        val returnToAppButton =
            Button(this).apply {

                text =
                    "RETURN TO APP UI"

                setOnClickListener {

                    showComposeUi()
                }
            }


        val title =
            TextView(this).apply {

                text =
                    "PicoloDSP"

                textSize =
                    26f
            }


        val subtitle =
            TextView(this).apply {

                text =
                    "\nTinyALSA + NAM A2\n"

                textSize =
                    17f
            }


        currentModelText =
            TextView(this).apply {

                text =
                    "Capture: none\n"

                textSize =
                    18f
            }


        audioDeviceText =
            TextView(this).apply {

                text =
                    "Audio interface: auto detect\n"

                textSize =
                    16f
            }


        val scanButton =
            Button(this).apply {

                text =
                    "SCAN USB AUDIO"

                setOnClickListener {

                    status.text =
                        audioEngine.nativeScanUsbAudio()
                }
            }


        val browseButton =
            Button(this).apply {

                text =
                    "BROWSE TONE3000"

                setOnClickListener {

                    startTone3000SelectFlow("replace")
                }
            }


        val reloadButton =
            Button(this).apply {

                text =
                    "RELOAD CURRENT NAM"

                setOnClickListener {

                    restoreLastModel()
                }
            }


        routingText =
            TextView(this).apply {

                text =
                    "\nROUTING\nInput: 1 (pending)\nOutput: 1-2 (pending)"

                textSize =
                    16f
            }


        inputRouteButton =
            Button(this).apply {

                text =
                    "NEXT INPUT"

                setOnClickListener {
                    audioRoutingUseCase.cycleInput()
                    refreshRoutingUi()
                }
            }


        outputRouteButton =
            Button(this).apply {

                text =
                    "NEXT OUTPUT PAIR"

                setOnClickListener {
                    audioRoutingUseCase.cycleOutput()
                    refreshRoutingUi()
                }
            }


        presetText =
            TextView(this).apply {

                text =
                    "\nPRESET\nActive: none"

                textSize =
                    16f
            }


        savePresetButton =
            Button(this).apply {

                text =
                    "SAVE PRESET"

                setOnClickListener {

                    showSavePresetDialog()
                }
            }


        loadPresetButton =
            Button(this).apply {

                text =
                    "LOAD PRESET"

                setOnClickListener {

                    showLoadPresetDialog()
                }
            }


        inputGainText =
            TextView(this).apply {

                text =
                    "\nInput Gain: 0.0 dB"
            }


        inputGainSlider =
            SeekBar(this).apply {

                max =
                    96

                progress =
                    48

                setOnSeekBarChangeListener(
                    object :
                        SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {

                            val gain =
                                -24.0f +
                                        progress *
                                        0.5f

                            inputGainText.text =
                                String.format(
                                    Locale.US,
                                    "\nInput Gain: %.1f dB",
                                    gain
                                )

                            if (!applyingPresetUi) {

                                audioEngine.nativeSetInputGainDb(
                                    gain
                                )
                            }

                            if (fromUser) {

                                prefs
                                    .edit()
                                    .putFloat(
                                        PREF_INPUT_GAIN,
                                        gain
                                    )
                                    .apply()
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }
                    }
                )
            }


        outputGainText =
            TextView(this).apply {

                text =
                    "\nOutput Gain: 0.0 dB"
            }


        outputGainSlider =
            SeekBar(this).apply {

                max =
                    72

                progress =
                    48

                setOnSeekBarChangeListener(
                    object :
                        SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {

                            val gain =
                                -24.0f +
                                        progress *
                                        0.5f

                            outputGainText.text =
                                String.format(
                                    Locale.US,
                                    "\nOutput Gain: %.1f dB",
                                    gain
                                )

                            if (!applyingPresetUi) {

                                audioEngine.nativeSetOutputGainDb(
                                    gain
                                )
                            }

                            if (fromUser) {

                                prefs
                                    .edit()
                                    .putFloat(
                                        PREF_OUTPUT_GAIN,
                                        gain
                                    )
                                    .apply()
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }
                    }
                )
            }


        dspChainText =
            TextView(this).apply {

                text =
                    "\nDSP CHAIN\nGate → NAM → EQ"

                textSize =
                    16f
            }


        gateButton =
            Button(this).apply {

                text =
                    "GATE: OFF"

                setOnClickListener {

                    gateEnabled =
                        !gateEnabled

                    audioEngine.nativeSetGateEnabled(
                        gateEnabled
                    )

                    prefs
                        .edit()
                        .putBoolean(
                            PREF_GATE_ENABLED,
                            gateEnabled
                        )
                        .apply()

                    refreshDspChainUi()
                }
            }


        gateThresholdText =
            TextView(this).apply {

                text =
                    "\nGate Threshold: -65.0 dB"
            }


        gateThresholdSlider =
            SeekBar(this).apply {

                max =
                    140

                progress =
                    50

                setOnSeekBarChangeListener(
                    object :
                        SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {

                            val db =
                                -90.0f +
                                        progress *
                                        0.5f


                            gateThresholdText.text =
                                String.format(
                                    Locale.US,
                                    "\nGate Threshold: %.1f dB",
                                    db
                                )


                            if (!applyingPresetUi) {

                                audioEngine.nativeSetGateThresholdDb(
                                    db
                                )
                            }


                            if (fromUser) {

                                prefs
                                    .edit()
                                    .putFloat(
                                        PREF_GATE_THRESHOLD,
                                        db
                                    )
                                    .apply()
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                        }
                    }
                )
            }


        eqLowText =
            TextView(this).apply {

                text =
                    "\nEQ Low 120 Hz: 0.0 dB"
            }


        eqLowSlider =
            SeekBar(this).apply {

                max =
                    48

                progress =
                    24

                setOnSeekBarChangeListener(
                    eqSeekListener(
                        label =
                            eqLowText,

                        prefix =
                            "EQ Low 120 Hz",

                        prefKey =
                            PREF_EQ_LOW,

                        nativeSetter =
                            audioEngine::nativeSetEqLowDb
                    )
                )
            }


        eqMidText =
            TextView(this).apply {

                text =
                    "\nEQ Mid 750 Hz: 0.0 dB"
            }


        eqMidSlider =
            SeekBar(this).apply {

                max =
                    48

                progress =
                    24

                setOnSeekBarChangeListener(
                    eqSeekListener(
                        label =
                            eqMidText,

                        prefix =
                            "EQ Mid 750 Hz",

                        prefKey =
                            PREF_EQ_MID,

                        nativeSetter =
                            audioEngine::nativeSetEqMidDb
                    )
                )
            }


        eqHighText =
            TextView(this).apply {

                text =
                    "\nEQ High 4 kHz: 0.0 dB"
            }


        eqHighSlider =
            SeekBar(this).apply {

                max =
                    48

                progress =
                    24

                setOnSeekBarChangeListener(
                    eqSeekListener(
                        label =
                            eqHighText,

                        prefix =
                            "EQ High 4 kHz",

                        prefKey =
                            PREF_EQ_HIGH,

                        nativeSetter =
                            audioEngine::nativeSetEqHighDb
                    )
                )
            }


        startButton =
            Button(this).apply {

                text =
                    "START NAM AUDIO"

                isEnabled =
                    false

                setOnClickListener {

                    syncFxNativeChain(readFxNativeChain(), reset = true)

                    val result =
                        audioEngine.nativeStart()

                    status.text =
                        result

                    audioDeviceText.text =
                        audioEngine.nativeGetAudioDeviceInfo()

                    refreshRoutingUi()
                }
            }


        bypassButton =
            Button(this).apply {

                text =
                    "MODE: NAM"

                setOnClickListener {

                    bypass =
                        !bypass

                    audioEngine.nativeSetBypass(
                        bypass
                    )

                    prefs.edit().putBoolean(PREF_NAM_BYPASS, bypass).apply()

                    updateBypassButton()
                }
            }


        val performanceButton =
            Button(this).apply {

                text =
                    "NAM PERFORMANCE"

                setOnClickListener {

                    status.text =
                        audioEngine.nativeGetStats()
                }
            }


        val stopButton =
            Button(this).apply {

                text =
                    "STOP AUDIO"

                setOnClickListener {

                    audioEngine.nativeStop()

                    status.text =
                        "STOPPED"

                    refreshRoutingUi()
                }
            }


        status =
            TextView(this).apply {

                text =
                    "Initializing..."

                textSize =
                    16f

                setTextIsSelectable(
                    true
                )
            }


        container.addView(returnToAppButton)
        container.addView(title)
        container.addView(subtitle)

        container.addView(currentModelText)
        container.addView(audioDeviceText)

        container.addView(scanButton)
        container.addView(browseButton)
        container.addView(reloadButton)

        container.addView(routingText)
        container.addView(inputRouteButton)
        container.addView(outputRouteButton)

        container.addView(presetText)
        container.addView(savePresetButton)
        container.addView(loadPresetButton)

        container.addView(inputGainText)
        container.addView(inputGainSlider)

        container.addView(outputGainText)
        container.addView(outputGainSlider)

        container.addView(dspChainText)
        container.addView(gateButton)
        container.addView(gateThresholdText)
        container.addView(gateThresholdSlider)

        container.addView(eqLowText)
        container.addView(eqLowSlider)
        container.addView(eqMidText)
        container.addView(eqMidSlider)
        container.addView(eqHighText)
        container.addView(eqHighSlider)

        container.addView(startButton)
        container.addView(bypassButton)
        container.addView(performanceButton)
        container.addView(stopButton)

        container.addView(status)


        legacyScrollView =
            ScrollView(this).apply {

                visibility =
                    View.GONE

                addView(
                    container
                )
            }


        rootLayout =
            FrameLayout(this)


        rootLayout.addView(
            legacyScrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val composeViewModel = ViewModelProvider(this)[PicoloComposeViewModel::class.java]
        val composeActions = AudioAppController()
        composeViewModel.observe(
            PicoloStateRepository(readSnapshot = {
                val (pluginState, stats) = withContext(Dispatchers.IO) {
                    pluginStateJson() to composeActions.getStats()
                }
                PicoloStateSnapshot(
                    pluginState = pluginState,
                    status = withContext(Dispatchers.Main.immediate) {
                        status.text?.toString().orEmpty()
                    },
                    stats = stats,
                )
            }),
        )
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                PicoloComposeApp(
                    actions = composeActions,
                    viewModel = composeViewModel,
                    onBrowse = { mode -> startTone3000SelectFlow(mode) },
                )
            }
        }
        rootLayout.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(
            rootLayout
        )
        showComposeUi()
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

    private fun readFxChain(): MutableList<JSONObject> = fxChainRepository.readImpulseChain()

    private fun persistFxChain(entries: List<JSONObject>) = fxChainRepository.persistImpulseChain(entries)

    private fun readFxNativeChain(): MutableList<JSONObject> = fxChainRepository.readNativeChain()

    private fun persistFxNativeChain(entries: List<JSONObject>) = fxChainRepository.persistNativeChain(entries)

    private fun syncFxNativeChain(entries: List<JSONObject>, reset: Boolean = false) {
        if (reset) for (slot in 0 until 8) audioEngine.nativeClearFxNative(slot)
        entries.forEachIndexed { slot, item ->
            val type = item.optInt("effect", 0).coerceIn(0, 3)
            if (reset) audioEngine.nativeConfigureFxNative(slot, type, MAX_NAM_BLOCKS)
            audioEngine.nativeSetFxNativeBypass(slot, item.optBoolean("bypass", false))
            audioEngine.nativeSetFxNativeMix(slot, item.optDouble("mix", 0.35).toFloat())
            audioEngine.nativeSetFxNativeParameter(slot, 0, item.optDouble("param1", when (type) { 0, 1 -> 350.0; 2 -> 1500.0; else -> 150.0 }).toFloat())
            audioEngine.nativeSetFxNativeParameter(slot, 1, item.optDouble("param2", when (type) { 0, 1 -> 0.35; 2 -> 0.5; else -> 5000.0 }).toFloat())
            audioEngine.nativeSetFxNativeParameter(slot, 2, item.optDouble("param3", 12.0).toFloat())
            audioEngine.nativeSetFxNativePosition(slot, MAX_NAM_BLOCKS)
        }
    }


    private fun persistNamChainEntries(entries: List<ExtraNamEntry>) {
        if (entries.isEmpty()) {
            clearPersistedModel()
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
            val position = fx.optInt("position", entries.size).coerceIn(0, entries.size)
            fx.put("position", position)
            audioEngine.nativeSetFxImpulseResponsePosition(index, position)
        }
        persistFxChain(fxEntries)
    }


    private fun rebuildNativeNamChain(entries: List<ExtraNamEntry>): String {
        val wasRunning = audioEngine.nativeIsRunning()
        audioEngine.nativeClearNamChain()

        entries.forEachIndexed { index, entry ->
            val result = if (index == 0) {
                audioEngine.nativeLoadModel(entry.path)
            } else {
                audioEngine.nativeAddChainModel(entry.path)
            }

            val loaded = if (index == 0) {
                result.startsWith("MODEL LOADED")
            } else {
                result.startsWith("CHAIN NAM ADDED")
            }

            if (!loaded) {
                return result
            }

            audioEngine.nativeSetChainNamBypass(index, entry.bypass)
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

        if (wasRunning) {
            val audioResult = audioEngine.nativeStart()
            if (!audioResult.startsWith("AUDIO ACTIVE")) {
                return audioResult
            }
        }

        return "NAM CHAIN READY\nblocks=${entries.size}" +
                if (wasRunning) "\nAUDIO ACTIVE" else ""
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
                startButton.isEnabled = entries.isNotEmpty() || otherModulesRemain
                updateBypassButton()
                status.text = if (entries.isEmpty() && !otherModulesRemain) {
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


    private fun commitExtraDownloadedModel(
        pendingFile: File,
        modelId: Long
    ): File {

        val destination =
            File(
                filesDir,
                "chain-nam-" +
                        System.currentTimeMillis() +
                        "-" +
                        modelId +
                        ".nam"
            )


        try {

            Files.move(
                pendingFile.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )

        } catch (
            _: AtomicMoveNotSupportedException
        ) {

            Files.move(
                pendingFile.toPath(),
                destination.toPath()
            )
        }


        if (
            !destination.exists() ||
            destination.length() ==
            0L
        ) {

            throw RuntimeException(
                "Failed to commit extra NAM block."
            )
        }


        return destination
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

                status.text =
                    "EXTRA NAM BLOCKS CLEARED"
            }

        }.start()
    }


    // ========================================================
    // ANDROID APP UI
    // ========================================================

    private fun showComposeUi() {
        legacyScrollView.visibility =
            View.GONE

        composeView.visibility =
            View.VISIBLE
    }


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
        result.put("fxChain", JSONArray().also { array -> fxChain.forEach { array.put(it) } })
        result.put("fxNativeChain", JSONArray().also { array -> fxNativeChain.forEach { array.put(it) } })
        val irLoaded = cabinetPath != null && File(cabinetPath).exists()
        val irPosition = prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS)
            .coerceIn(0, namChain.length())
        for (position in 0..namChain.length()) {
            if (irLoaded && irPosition == position) {
                signalChain.put(JSONObject()
                    .put("type", "CABINET_IR")
                    .put("position", position)
                    .put("name", cabinetPath?.let { File(it).name } ?: "Cabinet IR"))
            }
            fxChain.forEachIndexed { index, fx ->
                if (fx.optInt("position", namChain.length()) == position) {
                    signalChain.put(JSONObject()
                        .put("type", "FX")
                        .put("fxIndex", index)
                        .put("position", position)
                        .put("name", fx.optString("title", "Space FX"))
                        .put("bypass", fx.optBoolean("bypass", false))
                        .put("mix", fx.optDouble("mix", 0.5)))
                }
            }
            if (position < namChain.length()) signalChain.put(namChain.getJSONObject(position).put("type", "NAM"))
        }
        // FXNative is intentionally a stereo post section: it cannot be
        // inserted before a NAM or either cabinet/space convolution stage.
        fxNativeChain.forEachIndexed { index, item ->
            val effect = item.optInt("effect", 0).coerceIn(0, 3)
            val names = arrayOf("ChowMatrix Delay", "BYOD BBD Delay", "BYOD Smooth Reverb", "BYOD Shimmer Reverb")
            signalChain.put(JSONObject()
                .put("type", "FX_NATIVE")
                .put("nativeIndex", index)
                .put("effect", effect)
                .put("position", MAX_NAM_BLOCKS)
                .put("name", names[effect])
                .put("bypass", item.optBoolean("bypass", false))
                .put("mix", item.optDouble("mix", 0.35))
                .put("param1", item.optDouble("param1", when (effect) { 0, 1 -> 350.0; 2 -> 1500.0; else -> 150.0 }))
                .put("param2", item.optDouble("param2", when (effect) { 0, 1 -> 0.35; 2 -> 0.5; else -> 5000.0 }))
                .put("param3", item.optDouble("param3", 12.0)))
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


        override fun setInputGain(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -24.0f,
                        24.0f
                    )


            audioEngine.nativeSetInputGainDb(
                value
            )


            prefs
                .edit()
                .putFloat(
                    PREF_INPUT_GAIN,
                    value
                )
                .apply()


            runOnUiThread {

                applyingPresetUi =
                    true

                inputGainSlider.progress =
                    (
                            (
                                    value +
                                            24.0f
                                    ) *
                                    2.0f
                            ).roundToInt()

                applyingPresetUi =
                    false
            }
        }


        override fun setOutputGain(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -24.0f,
                        12.0f
                    )


            audioEngine.nativeSetOutputGainDb(
                value
            )


            prefs
                .edit()
                .putFloat(
                    PREF_OUTPUT_GAIN,
                    value
                )
                .apply()


            runOnUiThread {

                applyingPresetUi =
                    true

                outputGainSlider.progress =
                    (
                            (
                                    value +
                                            24.0f
                                    ) *
                                    2.0f
                            ).roundToInt()

                applyingPresetUi =
                    false
            }
        }


        override fun setGateEnabled(
            enabled: Boolean
        ) {

            gateEnabled =
                enabled


            audioEngine.nativeSetGateEnabled(
                enabled
            )


            prefs
                .edit()
                .putBoolean(
                    PREF_GATE_ENABLED,
                    enabled
                )
                .apply()


            runOnUiThread {

                refreshDspChainUi()
            }
        }


        override fun setGateThreshold(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -90.0f,
                        -20.0f
                    )


            audioEngine.nativeSetGateThresholdDb(
                value
            )


            prefs
                .edit()
                .putFloat(
                    PREF_GATE_THRESHOLD,
                    value
                )
                .apply()


            runOnUiThread {

                applyingPresetUi =
                    true

                gateThresholdSlider.progress =
                    (
                            (
                                    value +
                                            90.0f
                                    ) *
                                    2.0f
                            ).roundToInt()

                applyingPresetUi =
                    false
            }
        }


        override fun setEqLow(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_LOW,
                db,
                audioEngine::nativeSetEqLowDb,
                eqLowSlider
            )
        }


        override fun setEqMid(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_MID,
                db,
                audioEngine::nativeSetEqMidDb,
                eqMidSlider
            )
        }


        override fun setEqHigh(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_HIGH,
                db,
                audioEngine::nativeSetEqHighDb,
                eqHighSlider
            )
        }


        override fun setEqEnabled(enabled: Boolean) {
            audioEngine.nativeSetEqEnabled(enabled)
            prefs.edit().putBoolean(PREF_EQ_ENABLED, enabled).apply()
        }


        private fun setEqFromPlugin(
            prefKey: String,
            db: Double,
            setter: (Float) -> Unit,
            slider: SeekBar
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -12.0f,
                        12.0f
                    )


            setter(
                value
            )


            prefs
                .edit()
                .putFloat(
                    prefKey,
                    value
                )
                .apply()


            runOnUiThread {

                applyingPresetUi =
                    true

                slider.progress =
                    (
                            (
                                    value +
                                            12.0f
                                    ) *
                                    2.0f
                            ).roundToInt()

                applyingPresetUi =
                    false
            }
        }


        fun cycleInput(): Int {
            val selected = audioRoutingUseCase.cycleInput()
            runOnUiThread { refreshRoutingUi() }
            return selected
        }

        override fun cycleOutput(): Int {
            val selected = audioRoutingUseCase.cycleOutput()
            runOnUiThread { refreshRoutingUi() }
            return selected
        }


        override fun startAudio(): String {

            syncFxNativeChain(readFxNativeChain(), reset = true)

            val result =
                audioEngine.nativeStart()


            runOnUiThread {

                status.text =
                    result

                audioDeviceText.text =
                    audioEngine.nativeGetAudioDeviceInfo()

                refreshRoutingUi()
            }


            return result
        }


        override fun stopAudio(): String {

            audioEngine.nativeStop()


            runOnUiThread {

                status.text =
                    "STOPPED"
            }


            return "STOPPED"
        }

        fun setAccessToken(token: String): Boolean {
            val value = token.trim()
            if (value.isBlank()) {
                prefs.edit()
                    .remove(PREF_ACCESS_TOKEN)
                    .remove(PREF_REFRESH_TOKEN)
                    .apply()
                return false
            }
            prefs.edit().putString(PREF_ACCESS_TOKEN, value).apply()
            return true
        }


        fun toggleBypass(): Boolean {

            bypass =
                !bypass


            audioEngine.nativeSetBypass(
                bypass
            )

            prefs.edit().putBoolean(PREF_NAM_BYPASS, bypass).apply()


            runOnUiThread {

                updateBypassButton()
            }


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

                    status.text =
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
                if (!name.lowercase(Locale.US).endsWith(".nam")) {
                    return JSONObject().put("error", "Only .nam files are supported on Android").toString()
                }
                val encoded = source.optString("data")
                if (encoded.isBlank()) return JSONObject().put("error", "Empty local file").toString()
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val destination = File(filesDir, "local-${System.currentTimeMillis()}-$safeName")
                destination.writeBytes(Base64.decode(encoded, Base64.DEFAULT))

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
                    modelName = name.removeSuffix(".nam"),
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
                    runOnUiThread { showComposeUi() }
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
                            toneId = entry.optString("toneId"),
                            toneTitle = entry.optString("title", "Space FX"),
                            moduleType = "FX",
                            imageUrl = entry.optString("image"),
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

            val token = prefs.getString(PREF_ACCESS_TOKEN, null)
            if (token.isNullOrBlank()) {
                runOnUiThread {
                    showPackageCaptureUnavailable("Sign in to TONE3000 to view this package's captures.")
                }
                return true
            }

            runOnUiThread {
                status.text = "Loading captures from:\n${source.toneTitle}"
            }
            Thread {
                try {
                    val architecture = if (source.moduleType == "FX" || source.moduleType == "IR") null else 2
                    val freshModels = tone3000ApiRepository.listModels(source.toneId, token, architecture)
                    val models = mergePackageCaptures(source.toneId, source.moduleType, freshModels)
                    if (models.isNotEmpty()) cachePackageCaptures(source.toneId, source.moduleType, models)
                    runOnUiThread {
                        showComposeUi()
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
                        showComposeUi()
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
                val destination = File(filesDir, "cabinet-${model.id}.wav")
                if (destination.exists()) destination.delete()
                val downloaded = downloadModel(model, token, destination)
                val normalized = normalizeImpulseResponseWav(downloaded)
                val result = audioEngine.nativeLoadImpulseResponse(normalized.absolutePath)
                if (!result.startsWith("IR LOADED")) throw RuntimeException(result)
                val position = audioEngine.nativeGetNamBlockCount()
                audioEngine.nativeSetImpulseResponsePosition(position)
                audioEngine.nativeSetImpulseResponseBypass(false)
                audioEngine.nativeSetImpulseResponseInGainDb(0.0f)
                audioEngine.nativeSetImpulseResponseOutGainDb(0.0f)
                audioEngine.nativeSetImpulseResponseMix(if (moduleType == "FX") 0.5f else 1.0f)
                audioEngine.nativeSetImpulseResponseEqPre(false)
                audioEngine.nativeSetImpulseResponseEqEnabled(true)
                for (band in 0 until 6) audioEngine.nativeSetImpulseResponseEqDb(band, 0.0f)
                prefs.edit()
                    .putString(PREF_CABINET_IR_PATH, normalized.absolutePath)
                    .putString(PREF_CABINET_IR_IMAGE, imageUrl)
                    .putString(PREF_CABINET_IR_TITLE, toneTitle)
                    .putString(PREF_CABINET_IR_TONE_ID, toneId)
                    .putString(PREF_CABINET_IR_TYPE, moduleType)
                    .putInt(PREF_CABINET_IR_POSITION, position)
                    .putBoolean(PREF_CABINET_IR_BYPASS, false)
                    .putFloat(PREF_CABINET_IR_IN_GAIN, 0.0f)
                    .putFloat(PREF_CABINET_IR_OUT_GAIN, 0.0f)
                    .putFloat(PREF_CABINET_IR_MIX, if (moduleType == "FX") 0.5f else 1.0f)
                    .putBoolean(PREF_CABINET_IR_EQ_PRE, false)
                    .putBoolean(PREF_CABINET_IR_EQ_ENABLED, true)
                    .apply { for (band in 0 until 6) putFloat(PREF_CABINET_IR_EQ_PREFIX + band, 0.0f) }
                    .apply()
                val audio = audioEngine.nativeStart()
                prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
                runOnUiThread {
                    status.text = "CABINET IR READY\n\n$toneTitle\n${model.name}\n$audio"
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Cabinet IR download/load failed", error)
                runOnUiThread { status.text = "CABINET IR LOAD FAILED\n\n${error.message}" }
            }
        }

        private fun downloadAndLoadFx(
            toneId: String, toneTitle: String, imageUrl: String, model: OnlineModel, token: String
        ) {
            try {
                val entries = readFxChain()
                val targetIndex = prefs.getString(PREF_PENDING_IMPORT_MODE, "add")
                    ?.removePrefix("replace-fx:")?.toIntOrNull()
                    ?.takeIf { it in entries.indices }
                if (entries.size >= 8 && targetIndex == null) throw IllegalStateException("FX chain full (maximum 8)")
                val slot = targetIndex ?: entries.size
                val replacedPath = targetIndex?.let { entries[it].optString("path") }
                val destination = File(filesDir, "fx-space-${model.id}.wav")
                if (destination.exists()) destination.delete()
                val normalized = normalizeImpulseResponseWav(downloadModel(model, token, destination))
                val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, normalized.absolutePath)
                if (!loaded.startsWith("FX LOADED")) throw IllegalStateException(loaded)
                val entry = JSONObject()
                    .put("toneId", toneId).put("title", toneTitle).put("image", imageUrl)
                    .put("modelId", model.id).put("modelName", model.name)
                    .put("path", normalized.absolutePath).put("bypass", false).put("mix", 0.5)
                    .put("position", audioEngine.nativeGetNamBlockCount())
                if (targetIndex == null) entries.add(entry) else entries[targetIndex] = entry
                persistFxChain(entries)
                audioEngine.nativeSetFxImpulseResponseBypass(slot, false)
                audioEngine.nativeSetFxImpulseResponseMix(slot, 0.5f)
                audioEngine.nativeSetFxImpulseResponsePosition(slot, entry.optInt("position"))
                if (!replacedPath.isNullOrBlank() && replacedPath != normalized.absolutePath) {
                    try { File(replacedPath).delete() } catch (_: Exception) { }
                }
                val audio = audioEngine.nativeStart()
                prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()
                runOnUiThread {
                    status.text = "FX READY\n\n$toneTitle\n${model.name}\n$audio"
                    showComposeUi()
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "FX import failed", error)
                runOnUiThread { status.text = "FX LOAD FAILED\n${error.message}" }
            }
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
                    runOnUiThread { status.text = "FX CHAIN FULL\nMaximum 8 space effects." }
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

        /** Normalize downloaded cabinets to the mono PCM16 WAV accepted by the IR loader. */
        private fun normalizeImpulseResponseWav(source: File): File {
            val bytes = source.readBytes()
            require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid WAV header" }
            val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var format = 0
            var channels = 0
            var sampleRate = 0
            var bits = 0
            var dataOffset = -1
            var dataSize = 0
            var cursor = 12
            while (cursor + 8 <= bytes.size) {
                val chunk = String(bytes, cursor, 4, Charsets.US_ASCII)
                val size = view.getInt(cursor + 4)
                if (size < 0 || cursor + 8L + size > bytes.size) break
                when (chunk) {
                    "fmt " -> if (size >= 16) {
                        format = view.getShort(cursor + 8).toInt() and 0xffff
                        channels = view.getShort(cursor + 10).toInt() and 0xffff
                        sampleRate = view.getInt(cursor + 12)
                        bits = view.getShort(cursor + 22).toInt() and 0xffff
                    }
                    "data" -> {
                        dataOffset = cursor + 8
                        dataSize = size
                        break
                    }
                }
                cursor += 8 + size + (size and 1)
            }
            require(format == 1 || format == 3) { "Unsupported WAV format $format" }
            require(channels > 0 && sampleRate > 0 && dataOffset >= 0) { "Incomplete WAV" }
            require((format == 3 && bits == 32) || (format == 1 && bits in setOf(8, 16, 24, 32))) {
                "Unsupported WAV encoding $format/$bits"
            }
            val bytesPerSample = bits / 8
            val frameBytes = channels * bytesPerSample
            val frames = dataSize / frameBytes
            val output = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
            output.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + frames * 2)
                .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
                .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(frames * 2)

            fun sampleAsFloat(position: Int): Float = when {
                format == 3 -> Float.fromBits(view.getInt(position))
                bits == 8 -> ((bytes[position].toInt() and 0xff) - 128) / 128f
                bits == 16 -> view.getShort(position) / 32768f
                bits == 24 -> {
                    val raw = (bytes[position].toInt() and 0xff) or
                        ((bytes[position + 1].toInt() and 0xff) shl 8) or
                        (bytes[position + 2].toInt() shl 16)
                    (if (raw and 0x800000 != 0) raw or -0x1000000 else raw) / 8388608f
                }
                else -> view.getInt(position) / 2147483648f
            }
            for (frame in 0 until frames) {
                var mixed = 0f
                val base = dataOffset + frame * frameBytes
                for (channel in 0 until channels) mixed += sampleAsFloat(base + channel * bytesPerSample)
                output.putShort((mixed / channels).coerceIn(-1f, 1f).let { (it * 32767f).roundToInt().toShort() })
            }
            val normalized = File(filesDir, "${source.nameWithoutExtension}-pcm16.wav")
            normalized.outputStream().use { it.write(output.array()) }
            return normalized
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
                status.text = if (audioResult.startsWith("AUDIO ACTIVE")) {
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
                    val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, item.optString("path"))
                    if (!loaded.startsWith("FX LOADED")) {
                        runOnUiThread { status.text = "FX CHAIN RELOAD FAILED\n$loaded" }
                        return@Thread
                    }
                    audioEngine.nativeSetFxImpulseResponseBypass(slot, item.optBoolean("bypass", false))
                    audioEngine.nativeSetFxImpulseResponseMix(slot, item.optDouble("mix", 0.5).toFloat())
                    val namCount = audioEngine.nativeGetNamBlockCount()
                    audioEngine.nativeSetFxImpulseResponsePosition(slot, item.optInt("position", namCount).coerceIn(0, namCount))
                }
                persistFxChain(entries)
                try { File(removed.optString("path")).delete() } catch (_: Exception) { }
                val hasModules = audioEngine.nativeGetNamBlockCount() > 0 || entries.isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null
                val audio = if (wasRunning && hasModules) audioEngine.nativeStart() else ""
                runOnUiThread { status.text = "FX REMOVED\n$audio" }
            }.start()
        }

        override fun setFxBypass(fxIndex: Int, bypassed: Boolean) {
            val entries = readFxChain()
            val item = entries.getOrNull(fxIndex) ?: return
            item.put("bypass", bypassed)
            persistFxChain(entries)
            audioEngine.nativeSetFxImpulseResponseBypass(fxIndex, bypassed)
        }

        override fun setFxMix(fxIndex: Int, mix: Double) {
            val entries = readFxChain()
            val item = entries.getOrNull(fxIndex) ?: return
            val value = mix.toFloat().coerceIn(0f, 1f)
            item.put("mix", value.toDouble())
            persistFxChain(entries)
            audioEngine.nativeSetFxImpulseResponseMix(fxIndex, value)
        }

        override fun addFxNative(effect: Int) {
            val entries = readFxNativeChain()
            if (entries.size >= 8) {
                runOnUiThread { status.text = "FXNATIVE CHAIN FULL\nMaximum 8 native effects." }
                return
            }
            val wasRunning = audioEngine.nativeIsRunning()
            val selected = effect.coerceIn(0, 3)
            val entry = JSONObject().put("effect", selected).put("bypass", false).put("mix", 0.35)
                .put("param1", when (selected) { 0, 1 -> 350.0; 2 -> 1500.0; else -> 150.0 })
                .put("param2", when (selected) { 0, 1 -> 0.35; 2 -> 0.5; else -> 5000.0 })
                .put("param3", 12.0)
            entries.add(entry)
            persistFxNativeChain(entries)
            syncFxNativeChain(entries, reset = true)
            val audio = if (wasRunning) audioEngine.nativeStart() else ""
            runOnUiThread { status.text = "FXNATIVE ADDED\nStereo post NAM/CAB\n$audio" }
        }

        override fun removeFxNative(nativeIndex: Int) {
            val entries = readFxNativeChain()
            if (nativeIndex !in entries.indices) return
            val wasRunning = audioEngine.nativeIsRunning()
            entries.removeAt(nativeIndex)
            persistFxNativeChain(entries)
            syncFxNativeChain(entries, reset = true)
            val audio = if (wasRunning && (audioEngine.nativeGetNamBlockCount() > 0 || readFxChain().isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null)) audioEngine.nativeStart() else ""
            runOnUiThread { status.text = "FXNATIVE REMOVED\n$audio" }
        }

        override fun setFxNativeBypass(nativeIndex: Int, bypassed: Boolean) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            entry.put("bypass", bypassed)
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeBypass(nativeIndex, bypassed)
        }

        override fun setFxNativeMix(nativeIndex: Int, mix: Double) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val value = mix.toFloat().coerceIn(0f, 1f)
            entry.put("mix", value.toDouble())
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeMix(nativeIndex, value)
        }

        override fun setFxNativeParameter(nativeIndex: Int, parameter: Int, value: Double) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val effect = entry.optInt("effect", 0)
            val normalized = when (parameter) {
                0 -> value.toFloat().coerceIn(if (effect < 2) 20f else if (effect == 2) 500f else 50f,
                    if (effect < 2) 2000f else if (effect == 2) 5000f else 250f)
                1 -> value.toFloat().coerceIn(if (effect < 2) 0f else if (effect == 2) 0f else 1000f,
                    if (effect < 2) 0.94f else if (effect == 2) 1f else 10000f)
                else -> value.toFloat().coerceIn(-12f, 12f)
            }
            entry.put("param${parameter + 1}", normalized.toDouble())
            persistFxNativeChain(entries)
            audioEngine.nativeSetFxNativeParameter(nativeIndex, parameter, normalized)
        }

        override fun setFxNativeType(nativeIndex: Int, effect: Int) {
            val entries = readFxNativeChain()
            val entry = entries.getOrNull(nativeIndex) ?: return
            val wasRunning = audioEngine.nativeIsRunning()
            val selected = effect.coerceIn(0, 3)
            entry.put("effect", selected)
            entry.put("param1", when (selected) { 0, 1 -> 350.0; 2 -> 1500.0; else -> 150.0 })
            entry.put("param2", when (selected) { 0, 1 -> 0.35; 2 -> 0.5; else -> 5000.0 })
            entry.put("param3", 12.0)
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
                    updateBypassButton()
                    status.text = result
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
                        val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, item.optString("path"))
                        if (!loaded.startsWith("FX LOADED")) return false
                        item.put("position", fxPositions[requestedFxIndices.getOrNull(slot)] ?: reordered.size)
                        audioEngine.nativeSetFxImpulseResponseBypass(slot, item.optBoolean("bypass", false))
                        audioEngine.nativeSetFxImpulseResponseMix(slot, item.optDouble("mix", 0.5).toFloat())
                        audioEngine.nativeSetFxImpulseResponsePosition(slot, item.optInt("position").coerceIn(0, reordered.size))
                    }
                    persistFxChain(reorderedFx)
                    val audio = if (wasRunning && (reordered.isNotEmpty() || reorderedFx.isNotEmpty() || prefs.getString(PREF_CABINET_IR_PATH, null) != null)) audioEngine.nativeStart() else ""
                    runOnUiThread { status.text = if (audio.isBlank()) result else "$result\n$audio" }
                    true
                } else {
                    val rollback = rebuildNativeNamChain(entries)
                    runOnUiThread {
                        status.text = if (rollback.startsWith("NAM CHAIN READY")) {
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
                clearPersistedModel()
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


                runOnUiThread {

                    updateBypassButton()
                }

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
                runOnUiThread { status.text = "A2 Full is available for AMP blocks only." }
                return
            }
            val result = audioEngine.nativeSetChainNamQuality(chainIndex, full)
            if (!result.startsWith("A2 ")) {
                runOnUiThread { status.text = result }
                return
            }
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(a2Full = full)
            persistNamChainEntries(all)
            runOnUiThread { status.text = result }
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

        fun getAudioDeviceState(): String {
            return JSONObject()
                .put("running", audioEngine.nativeIsRunning())
                .put("standalone", true)
                .put("deviceType", "TinyALSA")
                .put("deviceName", audioEngine.nativeGetAudioDeviceInfo())
                .put("sampleRate", 48000)
                .put("bufferSize", 128)
                .put("inputChannels", 2)
                .put("outputChannels", 2)
                .put("supportsInput", true)
                .put("supportsOutput", true)
                .toString()
        }

        fun restartAudioDevice(): String {
            audioEngine.nativeStop()
            return audioEngine.nativeStart()
        }

        fun getAudioInputLevels(): String {
            return audioEngine.nativeGetStats()
        }


        fun showDebugUi() {
            runOnUiThread {
                // The legacy Android debug screen is no longer a valid
                // destination now that Compose is the primary UI.
                showComposeUi()
            }
        }
    }


    // ========================================================
    // ROUTING
    // ========================================================

    private fun restoreRoutingSettings() {
        audioRoutingUseCase.restoreSavedRoutes()
        refreshRoutingUi()
    }

    private fun refreshRoutingUi() {
        routingText.text = "\n" + audioRoutingUseCase.routingInfo()
    }


    // ========================================================
    // DSP CHAIN
    // ========================================================

    private fun eqSeekListener(
        label: TextView,
        prefix: String,
        prefKey: String,
        nativeSetter: (Float) -> Unit
    ): SeekBar.OnSeekBarChangeListener {

        return object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {

                val db =
                    -12.0f +
                            progress *
                            0.5f


                label.text =
                    String.format(
                        Locale.US,
                        "\n%s: %.1f dB",
                        prefix,
                        db
                    )


                if (!applyingPresetUi) {

                    nativeSetter(db)
                }


                if (fromUser) {

                    prefs
                        .edit()
                        .putFloat(
                            prefKey,
                            db
                        )
                        .apply()
                }
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?
            ) {
            }

            override fun onStopTrackingTouch(
                seekBar: SeekBar?
            ) {
            }
        }
    }


    private fun restoreDspSettings() {

        gateEnabled =
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


        gateThresholdSlider.progress =
            (
                    (
                            gateThreshold +
                                    90.0f
                            ) *
                            2.0f
                    ).roundToInt()


        eqLowSlider.progress =
            (
                    (
                            lowDb +
                                    12.0f
                            ) *
                            2.0f
                    ).roundToInt()


        eqMidSlider.progress =
            (
                    (
                            midDb +
                                    12.0f
                            ) *
                            2.0f
                    ).roundToInt()


        eqHighSlider.progress =
            (
                    (
                            highDb +
                                    12.0f
                            ) *
                            2.0f
                    ).roundToInt()


        refreshDspChainUi()
    }


    private fun refreshDspChainUi() {

        gateButton.text =
            if (gateEnabled) {

                "GATE: ON"

            } else {

                "GATE: OFF"
            }


        dspChainText.text =
            "\n" +
                    audioEngine.nativeGetDspChainInfo()
    }


    // ========================================================
    // PRESETS
    // ========================================================

    private fun readPreset(slot: Int): PresetData? = presetRepository.read(slot)

    private fun presetLabel(slot: Int): String = presetRepository.label(slot)


    private fun refreshPresetUi(
        activeSlot: Int? = null
    ) {

        presetText.text =
            buildString {

                append(
                    "\nPRESET\n"
                )


                if (activeSlot != null) {

                    append(
                        "Active: "
                    )

                    append(
                        presetLabel(
                            activeSlot
                        )
                    )

                } else {

                    append(
                        "Active: current session"
                    )
                }
            }
    }


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

            status.text =
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
        status.text = "Saving preset $slot..."

        Thread {
            try {
                val savedLabel = savePresetUseCase.execute(slot, bypass)
                runOnUiThread {
                    refreshPresetUi(slot)
                    status.text = "PRESET $slot SAVED\n\n$savedLabel"
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Preset save failed", error)
                runOnUiThread {
                    status.text = "PRESET SAVE FAILED\n\n${error.message ?: error}"
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

            status.text =
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

                    status.text =
                        "PRESET LOAD FAILED\n\nPreset $slot is empty."

                    return
                }


        startButton.isEnabled =
            false


        status.text =
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

                    applyingPresetUi =
                        true


                    inputGainSlider.progress =
                        (
                                (
                                        preset.inputGainDb +
                                                24.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    outputGainSlider.progress =
                        (
                                (
                                        preset.outputGainDb +
                                                24.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    currentModelText.text =
                        buildString {

                            if (
                                !preset.toneTitle.isNullOrBlank()
                            ) {

                                append(
                                    "Tone: ${preset.toneTitle}\n"
                                )
                            }

                            append(
                                "Capture: ${preset.modelName}"
                            )

                            append(
                                "\nSize: ${preset.modelSize.uppercase()}\n"
                            )
                        }


                    gateEnabled =
                        preset.gateEnabled


                    gateThresholdSlider.progress =
                        (
                                (
                                        preset.gateThresholdDb +
                                                90.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    eqLowSlider.progress =
                        (
                                (
                                        preset.eqLowDb +
                                                12.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    eqMidSlider.progress =
                        (
                                (
                                        preset.eqMidDb +
                                                12.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    eqHighSlider.progress =
                        (
                                (
                                        preset.eqHighDb +
                                                12.0f
                                        ) *
                                        2.0f
                                ).roundToInt()


                    applyingPresetUi =
                        false


                    refreshDspChainUi()

                    refreshRoutingUi()

                    refreshPresetUi(
                        slot
                    )


                    bypass =
                        false

                    audioEngine.nativeSetBypass(
                        false
                    )

                    val audioResult = audioEngine.nativeStart()
                    status.text = status.text.toString() + "\n\n" + audioResult

                    updateBypassButton()


                    startButton.isEnabled =
                        true


                    status.text =
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

                    startButton.isEnabled =
                        false

                    status.text =
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


        inputGainSlider.progress =
            (
                    (
                            inputGain +
                                    24.0f
                            ) *
                            2.0f
                    ).roundToInt()


        outputGainSlider.progress =
            (
                    (
                            outputGain +
                                    24.0f
                            ) *
                            2.0f
                    ).roundToInt()
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
                        startButton.isEnabled = restored.startsWith("NAM CHAIN READY")
                        status.text = restored + "\n" + audio
                    }
                }.start()
                return
            }

            status.text =
                "Ready.\n\nNo saved model."

            currentModelText.text =
                "Capture: none\n"

            return
        }


        val file =
            File(
                path
            )


        if (!file.exists()) {

            clearPersistedModel()

            status.text =
                "Saved model file no longer exists."

            currentModelText.text =
                "Capture: none\n"

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


        status.text =
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
                        val loaded = audioEngine.nativeLoadFxImpulseResponse(slot, fx.optString("path"))
                        if (loaded.startsWith("FX LOADED")) {
                            audioEngine.nativeSetFxImpulseResponseBypass(slot, fx.optBoolean("bypass", false))
                            audioEngine.nativeSetFxImpulseResponseMix(slot, fx.optDouble("mix", 0.5).toFloat())
                            val namCount = audioEngine.nativeGetNamBlockCount()
                            audioEngine.nativeSetFxImpulseResponsePosition(slot, fx.optInt("position", namCount).coerceIn(0, namCount))
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

                    startButton.isEnabled =
                        true


                    currentModelText.text =
                        buildString {

                            if (
                                !toneTitle.isNullOrBlank()
                            ) {

                                append(
                                    "Tone: $toneTitle\n"
                                )
                            }

                            append(
                                "Capture: $name"
                            )

                            append(
                                "\nSize: ${size.uppercase()}"
                            )

                            append("\n")
                        }


                    status.text =
                        "SAVED MODEL LOADED\n\n" +
                                result +
                                "\n\n" +
                                extraRestore


                    bypass =
                        false

                    audioEngine.nativeSetBypass(
                        false
                    )

                    updateBypassButton()

                } else {

                    startButton.isEnabled =
                        false

                    status.text =
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

            status.text =
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

        startButton.isEnabled =
            false


        val verifier =
            randomBase64Url(
                32
            )


        val challenge =
            sha256Base64Url(
                verifier
            )


        val state =
            randomBase64Url(
                16
            )


        prefs
            .edit()
            .putString(
                PREF_PENDING_IMPORT_MODE,
                importMode
            )
            .putString(
                PREF_VERIFIER,
                verifier
            )
            .putString(
                PREF_STATE,
                state
            )
            .apply()


        val selectedModuleType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
        prefs.edit().putString(PREF_PENDING_TONE_TYPE, selectedModuleType).apply()
        val gears = when (selectedModuleType) {
            "PEDAL" -> "pedal"
            "FX" -> "space"
            "IR" -> "cab"
            else -> "amp-cab_amp"
        }
        val uri =
            Uri.parse(
                AUTHORIZE_URL
            )
                .buildUpon()

                .appendQueryParameter(
                    "client_id",
                    PUBLISHABLE_KEY
                )

                .appendQueryParameter(
                    "redirect_uri",
                    REDIRECT_URI
                )

                .appendQueryParameter(
                    "response_type",
                    "code"
                )

                .appendQueryParameter(
                    "code_challenge",
                    challenge
                )

                .appendQueryParameter(
                    "code_challenge_method",
                    "S256"
                )

                .appendQueryParameter(
                    "state",
                    state
                )

                .appendQueryParameter(
                    "prompt",
                    "select_tone"
                )

                .appendQueryParameter(
                    "format",
                    if (selectedModuleType == "IR" || selectedModuleType == "FX") "ir" else "nam"
                )
                .appendQueryParameter("gears", gears)
                .apply {
                    if (selectedModuleType != "IR" && selectedModuleType != "FX") {
                        appendQueryParameter("architecture", "2")
                    }
                }

                .appendQueryParameter(
                    "menubar",
                    "true"
                )

                .appendQueryParameter(
                    "preview",
                    "true"
                )

                .build()


        status.text =
            "Opening TONE3000...\n\n" +
                    "Current model preserved until a new capture is loaded."


        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(
                this,
                uri
            )
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


        val storedState =
            prefs.getString(
                PREF_STATE,
                null
            )


        val verifier =
            prefs.getString(
                PREF_VERIFIER,
                null
            )


        if (
            returnedState == null ||
            returnedState != storedState
        ) {

            restoreCurrentModelAvailability(
                "OAuth state mismatch."
            )

            return
        }


        if (
            canceled &&
            toneId == null
        ) {

            prefs
                .edit()
                .remove(
                    PREF_STATE
                )
                .remove(
                    PREF_VERIFIER
                )
                .apply()

            restoreCurrentModelAvailability(
                "TONE3000 selection canceled."
            )

            return
        }


        if (
            code == null ||
            verifier == null
        ) {

            restoreCurrentModelAvailability(
                "OAuth callback sem code/verifier."
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


        startButton.isEnabled =
            false


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


                val tokenResponse = tone3000ApiRepository.exchangeAuthorizationCode(code, verifier)
                prefs.edit()
                    .putString(PREF_ACCESS_TOKEN, tokenResponse.accessToken)
                    .putString(PREF_REFRESH_TOKEN, tokenResponse.refreshToken)
                    .apply()
                val token = tokenResponse.accessToken


                prefs
                    .edit()
                    .remove(
                        PREF_STATE
                    )
                    .remove(
                        PREF_VERIFIER
                    )
                    .apply()


                stage(
                    "2/3 - FETCHING TONE..."
                )


                val selectedType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
                prefs.edit()
                    .putString(PREF_SELECTED_ADD_TYPE, selectedType)
                    .putString(PREF_PENDING_TONE_TYPE, selectedType)
                    .apply()
                val architecture = if (selectedType == "IR" || selectedType == "FX") null else 2
                val tone = tone3000ApiRepository.getTone(toneId, token, architecture)


                stage(if (architecture == null) "3/3 - FETCHING IMPULSE RESPONSE..." else "3/3 - FETCHING A2 CAPTURES...")


                val models =
                    tone3000ApiRepository.listModels(
                        toneId,
                        token,
                        architecture
                    )


                Log.i(
                    API_TAG,
                    "OAuth flow complete: ${models.size} A2 captures"
                )


                runOnUiThread {
                    showComposeUi()
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

            status.text =
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


    private fun packageCaptureCacheKey(toneId: String, moduleType: String): String {
        val kind = when (moduleType.uppercase(Locale.US)) {
            "FX" -> "FX"
            "IR" -> "IR"
            else -> "NAM"
        }
        return "package_capture_cache_${kind}_$toneId"
    }

    private fun cachePackageCaptures(toneId: String, moduleType: String, models: List<OnlineModel>) {
        if (toneId.isBlank() || models.isEmpty()) return
        val json = JSONArray()
        models.distinctBy { it.id }.forEach { model ->
            json.put(
                JSONObject()
                    .put("id", model.id)
                    .put("name", model.name)
                    .put("size", model.size)
                    .put("modelUrl", model.modelUrl)
            )
        }
        prefs.edit().putString(packageCaptureCacheKey(toneId, moduleType), json.toString()).apply()
    }

    private fun readCachedPackageCaptures(toneId: String, moduleType: String): List<OnlineModel> {
        val json = prefs.getString(packageCaptureCacheKey(toneId, moduleType), null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", 0L)
                val modelUrl = item.optString("modelUrl").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                OnlineModel(
                    id = id,
                    name = item.optString("name", "capture-$id"),
                    size = item.optString("size", "custom"),
                    modelUrl = modelUrl
                )
            }.distinctBy { it.id }
        } catch (error: Exception) {
            Log.w(API_TAG, "Ignoring invalid cached capture list for tone $toneId", error)
            emptyList()
        }
    }

    private fun mergePackageCaptures(toneId: String, moduleType: String, freshModels: List<OnlineModel>): List<OnlineModel> {
        val cachedModels = readCachedPackageCaptures(toneId, moduleType)
        if (cachedModels.isEmpty()) return freshModels.distinctBy { it.id }
        val freshById = freshModels.associateBy { it.id }
        val merged = cachedModels.map { cached -> freshById[cached.id] ?: cached }
        val cachedIds = cachedModels.mapTo(mutableSetOf()) { it.id }
        return (merged + freshModels.filterNot { it.id in cachedIds }).distinctBy { it.id }
    }

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
        cachePackageCaptures(toneId, selectedModuleType, models)

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


            status.text =
                "1 compatible A2 capture found.\n\n" +
                        "Loading:\n${model.name}"


            startButton.isEnabled =
                false


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


        startButton.isEnabled =
            false


        status.text =
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


                    status.text =
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
            status.text = "$toneTitle\n\nSelected space capture:\n${model.name}\nLoading..."
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
        status.text = "$toneTitle\n\n${models.size} space captures found. Select one to continue."
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
            status.text = "$toneTitle\n\nSelected cabinet capture:\n${model.name}\nLoading..."
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

        startButton.isEnabled =
            false


        status.text =
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


                pendingFile =
                    File(
                        filesDir,
                        "pending-tone3000-model-${model.id}.nam"
                    )


                if (
                    pendingFile.exists() &&
                    !pendingFile.delete()
                ) {

                    throw RuntimeException(
                        "Could not clear previous pending model file."
                    )
                }


                val downloaded =
                    downloadModel(
                        model =
                            model,

                        token =
                            token,

                        destination =
                            pendingFile
                    )


                if (
                    importMode ==
                    "add"
                ) {

                    stage(
                        "ADDING NAM BLOCK...\n${model.name}"
                    )


                    val committedExtra =
                        commitExtraDownloadedModel(
                            pendingFile =
                                downloaded,

                            modelId =
                                model.id
                        )


                    pendingFile =
                        null


                    val addResult =
                        audioEngine.nativeAddChainModel(
                            committedExtra.absolutePath
                        )


                    if (
                        !addResult.startsWith(
                            "CHAIN NAM ADDED"
                        )
                    ) {

                        committedExtra.delete()


                        throw RuntimeException(
                            addResult
                        )
                    }

                    val addedChainIndex = audioEngine.nativeGetNamBlockCount() - 1
                    audioEngine.nativeSetChainNamBypass(addedChainIndex, false)
                    audioEngine.nativeSetChainNamInGainDb(addedChainIndex, 0.0f)
                    audioEngine.nativeSetChainNamMix(addedChainIndex, 1.0f)
                    val addedLevelDb = if (moduleType == "PEDAL") -10.0f else -15.0f
                    audioEngine.nativeSetChainNamGainDb(addedChainIndex, addedLevelDb)
                    for (band in 0 until 6) {
                        audioEngine.nativeSetChainNamEqDb(addedChainIndex, band, 0.0f)
                    }
                    audioEngine.nativeSetChainNamEqPre(addedChainIndex, false)
                    audioEngine.nativeSetChainNamNormalize(addedChainIndex, moduleType != "PEDAL")
                    audioEngine.nativeSetChainNamEqEnabled(addedChainIndex, true)
                    val addedA2Full = moduleType == "AMP"
                    audioEngine.nativeSetChainNamQuality(addedChainIndex, addedA2Full)


                    val entries =
                        readExtraNamChain()


                    entries.add(
                        ExtraNamEntry(
                            toneId =
                                toneId,

                            toneTitle =
                                toneTitle,

                            modelId =
                                model.id,

                            modelName =
                                model.name,

                            size =
                                model.size,

                            path =
                                committedExtra.absolutePath,

                            bypass =
                                false,
                            gainDb = addedLevelDb,
                            inGainDb = 0.0f,
                            mix = 1.0f,
                            eqLowDb = 0.0f,
                            eqMidDb = 0.0f,
                            eqHighDb = 0.0f,
                            eqBand3Db = 0.0f,
                            eqBand4Db = 0.0f,
                            eqBand5Db = 0.0f,
                            eqPre = false,
                            eqEnabled = true,
                            normalize = moduleType != "PEDAL",
                            imageUrl = imageUrl,
                            moduleType = moduleType,
                            a2Full = addedA2Full
                        )
                    )

                    val audioResult = audioEngine.nativeStart()


                    persistExtraNamChain(
                        entries
                    )


                    prefs
                        .edit()
                        .remove(
                            PREF_PENDING_IMPORT_MODE
                        )
                        .apply()


                    runOnUiThread {

                        startButton.isEnabled =
                            prefs.getString(
                                PREF_LAST_MODEL_PATH,
                                null
                            ) != null


                        status.text =
                            "NAM BLOCK ADDED\n\n" +
                                    "Tone: $toneTitle\n" +
                                    "Capture: ${model.name}\n" +
                                    "Size: ${model.size.uppercase()}\n\n" +
                                    addResult + "\n" + audioResult
                    }


                    return@Thread
                }


                if (replacementIndex != null && replacementIndex >= 0) {
                    stage("REPLACING NAM BLOCK ${replacementIndex + 1}...\n${model.name}")

                    val entries = readNamChainEntries()
                    if (replacementIndex !in entries.indices) {
                        throw RuntimeException("NAM block ${replacementIndex + 1} no longer exists.")
                    }

                    val previous = entries[replacementIndex]
                    val committed = commitExtraDownloadedModel(
                        pendingFile = downloaded,
                        modelId = model.id
                    )
                    pendingFile = null

                    // Replacing a capture must not reset the block's mixer/EQ
                    // state. Keep every per-block control and change only the
                    // capture metadata and file path.
                    val changedModuleType = moduleType != previous.moduleType
                    val newModuleGain = if (moduleType == "PEDAL") -10.0f else -15.0f
                    entries[replacementIndex] = previous.copy(
                        toneId = toneId,
                        toneTitle = toneTitle,
                        modelId = model.id,
                        modelName = model.name,
                        size = model.size,
                        path = committed.absolutePath,
                        imageUrl = imageUrl,
                        // A replacement can intentionally change the NAM
                        // role (AMP <-> PEDAL). Do not keep the old visual
                        // type or A2 quality setting with the new capture.
                        moduleType = moduleType,
                        a2Full = moduleType == "AMP",
                        gainDb = if (changedModuleType) newModuleGain else previous.gainDb,
                        inGainDb = if (changedModuleType) 0.0f else previous.inGainDb,
                        mix = if (changedModuleType) 1.0f else previous.mix,
                        eqLowDb = if (changedModuleType) 0.0f else previous.eqLowDb,
                        eqMidDb = if (changedModuleType) 0.0f else previous.eqMidDb,
                        eqHighDb = if (changedModuleType) 0.0f else previous.eqHighDb,
                        eqBand3Db = if (changedModuleType) 0.0f else previous.eqBand3Db,
                        eqBand4Db = if (changedModuleType) 0.0f else previous.eqBand4Db,
                        eqBand5Db = if (changedModuleType) 0.0f else previous.eqBand5Db,
                        eqPre = if (changedModuleType) false else previous.eqPre,
                        eqEnabled = if (changedModuleType) true else previous.eqEnabled,
                        normalize = if (changedModuleType) moduleType != "PEDAL" else previous.normalize
                    )

                    val replaceResult = rebuildNativeNamChain(entries)
                    if (!replaceResult.startsWith("NAM CHAIN READY")) {
                        committed.delete()
                        throw RuntimeException(replaceResult)
                    }

                    val audioResult = audioEngine.nativeStart()

                    persistNamChainEntries(entries)
                    if (previous.path != committed.absolutePath) {
                        File(previous.path).delete()
                    }

                    prefs.edit().remove(PREF_PENDING_IMPORT_MODE).apply()

                    runOnUiThread {
                        startButton.isEnabled = true
                        status.text = "NAM BLOCK ${replacementIndex + 1} REPLACED\n\n" +
                                "Tone: $toneTitle\n" +
                                "Capture: ${model.name}\n" +
                                "Size: ${model.size.uppercase()}\n\n" +
                                replaceResult + "\n" + audioResult
                    }

                    return@Thread
                }


                stage(
                    "LOADING CAPTURE...\n${model.name}"
                )


                val loadResult =
                    audioEngine.nativeLoadModel(
                        downloaded.absolutePath
                    )


                if (
                    !loadResult.startsWith(
                        "MODEL LOADED"
                    )
                ) {

                    throw RuntimeException(
                        "NAM rejected the selected capture:\n" +
                                loadResult
                    )
                }

                if (moduleType == "PEDAL") {
                    audioEngine.nativeSetChainNamGainDb(0, -10.0f)
                    audioEngine.nativeSetChainNamInGainDb(0, 0.0f)
                    audioEngine.nativeSetChainNamMix(0, 1.0f)
                    for (band in 0 until 6) audioEngine.nativeSetChainNamEqDb(0, band, 0.0f)
                    audioEngine.nativeSetChainNamEqPre(0, false)
                    audioEngine.nativeSetChainNamEqEnabled(0, true)
                    audioEngine.nativeSetChainNamNormalize(0, false)
                }
                audioEngine.nativeSetChainNamQuality(0, moduleType == "AMP")
                val audioResult = audioEngine.nativeStart()


                /*
                 * The new DSP is valid at this point.
                 *
                 * Only now replace current-tone3000-model.nam.
                 * Until this moment the previous file remained untouched.
                 */
                val committedFile =
                    commitDownloadedModel(
                        downloaded
                    )


                persistModel(
                    toneId =
                        toneId,

                    toneTitle =
                        toneTitle,

                    model =
                        model,

                    moduleType = moduleType,

                    file =
                        committedFile
                )


                prefs
                    .edit()
                    .remove(
                        PREF_PENDING_IMPORT_MODE
                    )
                    .apply()


                pendingFile =
                    null


                runOnUiThread {

                    startButton.isEnabled =
                        true


                    currentModelText.text =
                        "Tone: $toneTitle\n" +
                                "Capture: ${model.name}\n" +
                                "Size: ${model.size.uppercase()}\n" +
                                "Model ID: ${model.id}\n"


                    bypass =
                        false


                    audioEngine.nativeSetBypass(
                        false
                    )


                    updateBypassButton()


                    status.text =
                        "TONE3000 CAPTURE READY\n\n" +
                                loadResult + "\n" + audioResult
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

                    startButton.isEnabled =
                        previous.first


                    if (previous.first) {

                        bypass =
                            false

                        audioEngine.nativeSetBypass(
                            false
                        )

                        updateBypassButton()
                    }


                    status.text =
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

    private fun downloadModel(
        model: OnlineModel,
        token: String,
        destination: File
    ): File {

        if (
            destination.exists() &&
            !destination.delete()
        ) {

            throw RuntimeException(
                "Could not replace pending download file."
            )
        }


        var currentUrl =
            model.modelUrl


        repeat(
            5
        ) {

            val url =
                URL(
                    currentUrl
                )


            val connection =
                url.openConnection()
                        as HttpURLConnection


            try {

                connection.requestMethod =
                    "GET"

                connection.instanceFollowRedirects =
                    false

                connection.connectTimeout =
                    15_000

                connection.readTimeout =
                    60_000


                connection.setRequestProperty(
                    "Connection",
                    "close"
                )


                /*
                 * Never forward the TONE3000 bearer token to an external
                 * storage/CDN host after a redirect.
                 */
                if (
                    url.host ==
                    "www.tone3000.com" ||
                    url.host ==
                    "tone3000.com"
                ) {

                    connection.setRequestProperty(
                        "Authorization",
                        "Bearer $token"
                    )
                }


                val responseCode =
                    connection.responseCode


                Log.i(
                    API_TAG,
                    "MODEL DOWNLOAD id=${model.id} HTTP $responseCode"
                )


                if (
                    responseCode in
                    300..399
                ) {

                    val location =
                        connection.getHeaderField(
                            "Location"
                        )
                            ?: throw RuntimeException(
                                "Download redirect sem Location."
                            )


                    currentUrl =
                        URL(
                            url,
                            location
                        ).toString()


                    return@repeat
                }


                if (
                    responseCode !in
                    200..299
                ) {

                    val error =
                        readHttpResponse(
                            connection,
                            responseCode
                        )


                    throw RuntimeException(
                        "Model download failed " +
                                "HTTP $responseCode\n$error"
                    )
                }


                connection
                    .inputStream
                    .use { input ->

                        destination
                            .outputStream()
                            .use { output ->

                                input.copyTo(
                                    output
                                )
                            }
                    }


                if (
                    destination.length() ==
                    0L
                ) {

                    throw RuntimeException(
                        "Downloaded model is empty."
                    )
                }


                Log.i(
                    API_TAG,
                    "MODEL DOWNLOAD COMPLETE " +
                            "id=${model.id} " +
                            "bytes=${destination.length()}"
                )


                return destination

            } finally {

                connection.disconnect()
            }
        }


        throw RuntimeException(
            "Too many redirects downloading model."
        )
    }


    private fun commitDownloadedModel(
        pendingFile: File
    ): File {

        val destination =
            File(
                filesDir,
                "current-tone3000-model.nam"
            )


        try {

            Files.move(
                pendingFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )

        } catch (
            _: AtomicMoveNotSupportedException
        ) {

            Files.move(
                pendingFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }


        if (
            !destination.exists() ||
            destination.length() == 0L
        ) {

            throw RuntimeException(
                "Failed to commit selected model."
            )
        }


        return destination
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


        startButton.isEnabled =
            available


        status.text =
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


    private fun reloadPersistedModelAfterFailedSwitch():
            Pair<Boolean, String> {

        val path =
            prefs.getString(
                PREF_LAST_MODEL_PATH,
                null
            )
                ?: return Pair(
                    false,
                    "No persisted model path."
                )


        val file =
            File(
                path
            )


        if (!file.exists()) {

            return Pair(
                false,
                "Persisted model file does not exist."
            )
        }


        return try {

            val result =
                audioEngine.nativeLoadModel(
                    file.absolutePath
                )


            Pair(
                result.startsWith(
                    "MODEL LOADED"
                ),
                result
            )

        } catch (
            e: Exception
        ) {

            Pair(
                false,
                e.message
                    ?: e.toString()
            )
        }
    }

    // ========================================================
    // PERSIST MODEL
    // ========================================================

    private fun persistModel(
        toneId: String,
        toneTitle: String,
        model: OnlineModel,
        moduleType: String,
        file: File
    ) {

        prefs
            .edit()

            .putString(
                PREF_LAST_MODEL_PATH,
                file.absolutePath
            )

            .putString(
                PREF_LAST_MODEL_NAME,
                model.name
            )

            .putString(
                PREF_LAST_MODEL_SIZE,
                model.size
            )

            .putString(
                PREF_LAST_MODEL_TYPE,
                moduleType
            )

            .putString(
                PREF_LAST_TONE_ID,
                toneId
            )

            .putString(
                PREF_LAST_TONE_TITLE,
                toneTitle
            )

            .putFloat(PREF_NAM_GAIN_DB, if (moduleType == "PEDAL") -10.0f else -15.0f)
            .putFloat(PREF_NAM_IN_GAIN_DB, 0.0f)
            .putFloat(PREF_NAM_MIX, 1.0f)
            .putFloat(PREF_NAM_EQ_LOW_DB, 0.0f)
            .putFloat(PREF_NAM_EQ_MID_DB, 0.0f)
            .putFloat(PREF_NAM_EQ_HIGH_DB, 0.0f)
            .putFloat(PREF_NAM_EQ_BAND3_DB, 0.0f)
            .putFloat(PREF_NAM_EQ_BAND4_DB, 0.0f)
            .putFloat(PREF_NAM_EQ_BAND5_DB, 0.0f)
            .putBoolean(PREF_NAM_EQ_PRE, false)
            .putBoolean(PREF_NAM_EQ_ENABLED, true)
            .putBoolean(PREF_NAM_NORMALIZE, moduleType != "PEDAL")
            .putBoolean(PREF_NAM_A2_FULL, moduleType == "AMP")

            .apply()
    }


    private fun clearPersistedModel() {

        prefs
            .edit()

            .remove(
                PREF_LAST_MODEL_PATH
            )

            .remove(
                PREF_LAST_MODEL_NAME
            )

            .remove(
                PREF_LAST_MODEL_SIZE
            )

            .remove(
                PREF_LAST_TONE_ID
            )

            .remove(
                PREF_LAST_TONE_TITLE
            )

            .apply()
    }


    // ========================================================
    // PKCE
    // ========================================================

    private fun randomBase64Url(
        bytes: Int
    ): String {

        val data =
            ByteArray(
                bytes
            )


        SecureRandom()
            .nextBytes(
                data
            )


        return Base64.encodeToString(
            data,
            Base64.URL_SAFE or
                    Base64.NO_WRAP or
                    Base64.NO_PADDING
        )
    }


    private fun sha256Base64Url(
        value: String
    ): String {

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )
                .digest(
                    value.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )


        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or
                    Base64.NO_WRAP or
                    Base64.NO_PADDING
        )
    }


    // ========================================================
    // HTTP
    // ========================================================

    private fun readHttpResponse(
        connection: HttpURLConnection,
        responseCode: Int
    ): String {

        val stream =
            if (
                responseCode in
                200..299
            ) {

                connection.inputStream

            } else {

                connection.errorStream
            }


        if (stream == null) {
            return ""
        }


        return stream
            .bufferedReader()
            .use {

                it.readText()
            }
    }


    // ========================================================
    // UI STATE
    // ========================================================

    private fun updateBypassButton() {

        bypassButton.text =
            if (bypass) {

                "MODE: BYPASS"

            } else {

                "MODE: NAM"
            }
    }
}
