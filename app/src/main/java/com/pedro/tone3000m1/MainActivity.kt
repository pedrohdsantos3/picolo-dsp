package com.pedro.tone3000m1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

        private const val TOKEN_URL =
            "$API_BASE/api/v1/oauth/token"

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
            "last_model_path"

        private const val PREF_LAST_MODEL_NAME =
            "last_model_name"

        private const val PREF_LAST_MODEL_SIZE =
            "last_model_size"

        private const val PREF_LAST_TONE_ID =
            "last_tone_id"

        private const val PREF_LAST_TONE_TITLE =
            "last_tone_title"

        private const val PREF_LAST_TONE_IMAGE =
            "last_tone_image"

        private const val PREF_LAST_MODEL_TYPE =
            "last_model_type"

        private const val PREF_CABINET_IR_PATH =
            "cabinet_ir_path"

        private const val PREF_CABINET_IR_IMAGE =
            "cabinet_ir_image"

        private const val PREF_CABINET_IR_BYPASS =
            "cabinet_ir_bypass"

        private const val PREF_CABINET_IR_POSITION =
            "cabinet_ir_position"

        private const val PREF_CABINET_IR_IN_GAIN = "cabinet_ir_in_gain_db"
        private const val PREF_CABINET_IR_OUT_GAIN = "cabinet_ir_out_gain_db"
        private const val PREF_CABINET_IR_MIX = "cabinet_ir_mix"
        private const val PREF_CABINET_IR_EQ_PRE = "cabinet_ir_eq_pre"
        private const val PREF_CABINET_IR_EQ_PREFIX = "cabinet_ir_eq_"

        private const val PREF_INPUT_GAIN =
            "input_gain_db"

        private const val PREF_OUTPUT_GAIN =
            "output_gain_db"

        private const val PREF_INPUT_CHANNEL =
            "input_channel"

        private const val PREF_OUTPUT_PAIR =
            "output_pair"

        private const val PREF_GATE_ENABLED =
            "gate_enabled"

        private const val PREF_GATE_THRESHOLD =
            "gate_threshold_db"

        private const val PREF_EQ_LOW =
            "eq_low_db"

        private const val PREF_EQ_MID =
            "eq_mid_db"

        private const val PREF_EQ_HIGH =
            "eq_high_db"

        private const val PREF_EXTRA_NAM_CHAIN =
            "extra_nam_chain"

        private const val PREF_NAM_GAIN_DB = "nam_gain_db"
        private const val PREF_NAM_IN_GAIN_DB = "nam_in_gain_db"
        private const val PREF_NAM_MIX = "nam_mix"
        private const val PREF_NAM_EQ_LOW_DB = "nam_eq_low_db"
        private const val PREF_NAM_EQ_MID_DB = "nam_eq_mid_db"
        private const val PREF_NAM_EQ_HIGH_DB = "nam_eq_high_db"
        private const val PREF_NAM_EQ_BAND3_DB = "nam_eq_band3_db"
        private const val PREF_NAM_EQ_BAND4_DB = "nam_eq_band4_db"
        private const val PREF_NAM_EQ_BAND5_DB = "nam_eq_band5_db"
        private const val PREF_NAM_BYPASS = "nam_bypass"
        private const val PREF_NAM_EQ_PRE = "nam_eq_pre"
        private const val PREF_NAM_EQ_ENABLED = "nam_eq_enabled"
        private const val PREF_CABINET_IR_EQ_ENABLED = "cabinet_ir_eq_enabled"
        private const val PREF_NAM_NORMALIZE = "nam_normalize"
        private const val PREF_NAM_A2_FULL = "nam_a2_full"

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

        init {
            System.loadLibrary(
                "tone3000m1"
            )
        }
    }


    // ========================================================
    // UI
    // ========================================================

    private lateinit var pluginWebView: WebView
    private lateinit var legacyScrollView: ScrollView
    private lateinit var rootLayout: FrameLayout

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
    // NATIVE
    // ========================================================

    external fun nativeLoadModel(
        path: String
    ): String

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

    external fun nativeAddChainModel(
        path: String
    ): String

    external fun nativeClearExtraNamBlocks(): String

    external fun nativeClearNamChain(): String

    external fun nativeSetChainNamBypass(
        chainIndex: Int,
        bypass: Boolean
    )

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
        eqHighDb: Float
    ): String

    external fun nativeStart(): String

    external fun nativeIsRunning(): Boolean

    external fun nativeStop()

    external fun nativeSetBypass(
        bypass: Boolean
    )

    external fun nativeSetInputGainDb(
        gainDb: Float
    )

    external fun nativeSetOutputGainDb(
        gainDb: Float
    )

    external fun nativeSetGateEnabled(
        enabled: Boolean
    )

    external fun nativeSetGateThresholdDb(
        db: Float
    )

    external fun nativeSetEqLowDb(
        db: Float
    )

    external fun nativeSetEqMidDb(
        db: Float
    )

    external fun nativeSetEqHighDb(
        db: Float
    )

    external fun nativeGetDspChainInfo(): String

    external fun nativeSetInputChannel(
        channel: Int
    )

    external fun nativeSetOutputPair(
        pairIndex: Int
    )

    external fun nativeCycleInputChannel(): Int

    external fun nativeCycleOutputPair(): Int

    external fun nativeGetRoutingInfo(): String

    external fun nativeScanUsbAudio(): String

    external fun nativeGetAudioDeviceInfo(): String

    external fun nativeGetStats(): String


    // ========================================================
    // PREFS
    // ========================================================

    private val prefs by lazy {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
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
                    val result = nativeLoadImpulseResponse(destination.absolutePath)
                    if (result.startsWith("IR LOADED")) {
                        val position = if (prefs.contains(PREF_CABINET_IR_POSITION)) {
                            prefs.getInt(PREF_CABINET_IR_POSITION, nativeGetNamBlockCount())
                        } else {
                            nativeGetNamBlockCount()
                        }.coerceIn(0, nativeGetNamBlockCount())
                        nativeSetImpulseResponsePosition(position)
                        prefs.edit()
                            .putString(PREF_CABINET_IR_PATH, destination.absolutePath)
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

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        createUi()

        restoreGainSettings()
        restoreRoutingSettings()
        restoreDspSettings()
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

            status.text =
                "OAuth callback received...\n" +
                        "Waiting for app network..."

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
                // The official UI owns the PKCE/session state in WebView.
                // Always return this callback to it; stale native PKCE
                // preferences must not hijack the modern Select flow.
                deliverOAuthCallbackToWebView(pending.data)
            },
            500
        )
    }


    override fun onStop() {

        nativeStop()

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


        val returnToPluginButton =
            Button(this).apply {

                text =
                    "RETURN TO PLUGIN UI"

                setOnClickListener {

                    showPluginUi()
                }
            }


        val title =
            TextView(this).apply {

                text =
                    "Tone3000 Android"

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
                        nativeScanUsbAudio()
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

                    val selected =
                        nativeCycleInputChannel()

                    prefs
                        .edit()
                        .putInt(
                            PREF_INPUT_CHANNEL,
                            selected
                        )
                        .apply()

                    refreshRoutingUi()
                }
            }


        outputRouteButton =
            Button(this).apply {

                text =
                    "NEXT OUTPUT PAIR"

                setOnClickListener {

                    val selected =
                        nativeCycleOutputPair()

                    prefs
                        .edit()
                        .putInt(
                            PREF_OUTPUT_PAIR,
                            selected
                        )
                        .apply()

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

                                nativeSetInputGainDb(
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

                                nativeSetOutputGainDb(
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

                    nativeSetGateEnabled(
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

                                nativeSetGateThresholdDb(
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
                            ::nativeSetEqLowDb
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
                            ::nativeSetEqMidDb
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
                            ::nativeSetEqHighDb
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

                    val result =
                        nativeStart()

                    status.text =
                        result

                    audioDeviceText.text =
                        nativeGetAudioDeviceInfo()

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

                    nativeSetBypass(
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
                        nativeGetStats()
                }
            }


        val stopButton =
            Button(this).apply {

                text =
                    "STOP AUDIO"

                setOnClickListener {

                    nativeStop()

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


        container.addView(returnToPluginButton)
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


        pluginWebView =
            WebView(this).apply {

                setBackgroundColor(
                    Color.rgb(
                        13,
                        14,
                        18
                    )
                )


                settings.javaScriptEnabled =
                    true

                settings.domStorageEnabled =
                    true

                settings.allowFileAccess =
                    true

                // The bundled React build loads its JS/CSS chunks from the
                // same file:// origin. WebView disables cross-file access by
                // default, which leaves the official UI blank on Android.
                // Keep the bundle self-contained in app assets and explicitly
                // allow its local chunks to resolve.
                settings.allowFileAccessFromFileURLs =
                    true

                settings.allowUniversalAccessFromFileURLs =
                    true


                addJavascriptInterface(
                    PluginBridge(),
                    "Tone3000Android"
                )


                webViewClient =
                    object :
                        WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {

                            val uri =
                                request?.url
                                    ?: return false


                            if (
                                uri.scheme ==
                                "file"
                            ) {
                                return false
                            }


                            try {

                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        uri
                                    )
                                )

                            } catch (
                                e: Exception
                            ) {

                                Log.e(
                                    API_TAG,
                                    "Unable to open external WebView URL",
                                    e
                                )
                            }


                            return true
                        }
                    }


                // The React frontend is the primary UI; the previous HTML
                // implementation remains packaged as a rollback target.
                loadUrl(
                    "file:///android_asset/tone3000-official/index.html"
                )
            }


        rootLayout =
            FrameLayout(this)


        rootLayout.addView(
            pluginWebView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )


        rootLayout.addView(
            legacyScrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )


        setContentView(
            rootLayout
        )
    }


    // ========================================================
    // MULTI-NAM CHAIN
    // ========================================================

    private data class ExtraNamEntry(
        val toneId: String,
        val toneTitle: String,
        val modelId: Long,
        val modelName: String,
        val size: String,
        val path: String,
        val bypass: Boolean,
        val gainDb: Float = 0.0f,
        val inGainDb: Float = 0.0f,
        val mix: Float = 1.0f,
        val eqLowDb: Float = 0.0f,
        val eqMidDb: Float = 0.0f,
        val eqHighDb: Float = 0.0f,
        val eqBand3Db: Float = 0.0f,
        val eqBand4Db: Float = 0.0f,
        val eqBand5Db: Float = 0.0f,
        val eqPre: Boolean = false,
        val eqEnabled: Boolean = true,
        val normalize: Boolean = true,
        val a2Full: Boolean = false,
        val imageUrl: String = ""
        ,val moduleType: String = "AMP"
    )


    private fun readExtraNamChain():
            MutableList<ExtraNamEntry> {

        val raw =
            prefs.getString(
                PREF_EXTRA_NAM_CHAIN,
                null
            )
                ?: return mutableListOf()


        return try {

            val array =
                JSONArray(
                    raw
                )


            val result = mutableListOf<ExtraNamEntry>()
            // The first NAM is stored separately in the legacy preferences.
            // Older builds could also leave a copy of that same path in the
            // extra-chain JSON, which made the UI render a phantom duplicate.
            val firstPath = prefs.getString(PREF_LAST_MODEL_PATH, null)
            val seenPaths = mutableSetOf<String>()


            for (
            index in
            0 until array.length()
            ) {

                val item =
                    array.getJSONObject(
                        index
                    )


                val path =
                    item.optString(
                        "path"
                    )


                if (
                    path.isBlank() ||
                    !File(path).exists() ||
                    path == firstPath ||
                    !seenPaths.add(path)
                ) {
                    continue
                }


                result.add(
                    ExtraNamEntry(
                        toneId =
                            item.optString(
                                "toneId"
                            ),

                        toneTitle =
                            item.optString(
                                "toneTitle"
                            ),

                        modelId =
                            item.optLong(
                                "modelId"
                            ),

                        modelName =
                            item.optString(
                                "modelName",
                                File(path).name
                            ),

                        size =
                            item.optString(
                                "size",
                                "unknown"
                            ),

                        path =
                            path,

                        bypass =
                            item.optBoolean(
                                "bypass",
                                false
                            ),
                        gainDb = item.optDouble("gainDb", 0.0).toFloat(),
                        inGainDb = item.optDouble("inGainDb", 0.0).toFloat(),
                        mix = item.optDouble("mix", 1.0).toFloat(),
                        eqLowDb = item.optDouble("eqLowDb", 0.0).toFloat(),
                        eqMidDb = item.optDouble("eqMidDb", 0.0).toFloat(),
                        eqHighDb = item.optDouble("eqHighDb", 0.0).toFloat()
                        ,eqBand3Db = item.optDouble("eqBand3Db", 0.0).toFloat()
                        ,eqBand4Db = item.optDouble("eqBand4Db", 0.0).toFloat()
                        ,eqBand5Db = item.optDouble("eqBand5Db", 0.0).toFloat()
                        ,eqPre = item.optBoolean("eqPre", false)
                        ,eqEnabled = item.optBoolean("eqEnabled", true)
                        ,normalize = item.optBoolean("normalize", true)
                        ,a2Full = item.optBoolean("a2Full", false)
                        ,imageUrl = item.optString("imageUrl", "")
                        ,moduleType = item.optString("moduleType", "AMP").uppercase()
                    )
                )
            }


            result

        } catch (
            e: Exception
        ) {

            Log.e(
                API_TAG,
                "Unable to parse extra NAM chain",
                e
            )

            mutableListOf()
        }
    }


    private fun persistExtraNamChain(
        entries: List<ExtraNamEntry>
    ) {

        val array =
            JSONArray()


        val firstPath = prefs.getString(PREF_LAST_MODEL_PATH, null)
        val seenPaths = mutableSetOf<String>()
        entries.filter { entry ->
            entry.path != firstPath && seenPaths.add(entry.path)
        }.forEach { entry ->

            array.put(
                JSONObject()
                    .put(
                        "toneId",
                        entry.toneId
                    )
                    .put(
                        "toneTitle",
                        entry.toneTitle
                    )
                    .put(
                        "modelId",
                        entry.modelId
                    )
                    .put(
                        "modelName",
                        entry.modelName
                    )
                    .put(
                        "size",
                        entry.size
                    )
                    .put(
                        "path",
                        entry.path
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
                    .put("imageUrl", entry.imageUrl)
                    .put("moduleType", entry.moduleType)
            )
        }


        prefs
            .edit()
            .putString(
                PREF_EXTRA_NAM_CHAIN,
                array.toString()
            )
            .apply()
    }


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
                    gainDb = prefs.getFloat(PREF_NAM_GAIN_DB, 0.0f),
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
                )
            )
        }

        result.addAll(readExtraNamChain())
        return result
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
            nativeSetImpulseResponsePosition(0)
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
        nativeSetImpulseResponsePosition(cabinetPosition)
    }


    private fun rebuildNativeNamChain(entries: List<ExtraNamEntry>): String {
        val wasRunning = nativeIsRunning()
        nativeClearNamChain()

        entries.forEachIndexed { index, entry ->
            val result = if (index == 0) {
                nativeLoadModel(entry.path)
            } else {
                nativeAddChainModel(entry.path)
            }

            val loaded = if (index == 0) {
                result.startsWith("MODEL LOADED")
            } else {
                result.startsWith("CHAIN NAM ADDED")
            }

            if (!loaded) {
                return result
            }

            nativeSetChainNamBypass(index, entry.bypass)
            nativeSetChainNamGainDb(index, entry.gainDb)
            nativeSetChainNamInGainDb(index, entry.inGainDb)
            nativeSetChainNamMix(index, entry.mix)
            nativeSetChainNamEqDb(index, 0, entry.eqLowDb)
            nativeSetChainNamEqDb(index, 1, entry.eqMidDb)
            nativeSetChainNamEqDb(index, 2, entry.eqHighDb)
            nativeSetChainNamEqDb(index, 3, entry.eqBand3Db)
            nativeSetChainNamEqDb(index, 4, entry.eqBand4Db)
            nativeSetChainNamEqDb(index, 5, entry.eqBand5Db)
            nativeSetChainNamEqPre(index, entry.eqPre)
            nativeSetChainNamEqEnabled(index, entry.eqEnabled)
            nativeSetChainNamNormalize(index, entry.normalize && entry.moduleType != "PEDAL")
            if (entry.moduleType == "PEDAL") nativeSetChainNamEqEnabled(index, false)
            if (entry.a2Full) nativeSetChainNamQuality(index, true)
        }

        if (wasRunning) {
            val audioResult = nativeStart()
            if (!audioResult.startsWith("AUDIO ACTIVE")) {
                return audioResult
            }
        }

        return "NAM CHAIN READY\nblocks=${entries.size}" +
                if (wasRunning) "\nAUDIO ACTIVE" else ""
    }


    private fun applyPersistedNamControls() {
        readNamChainEntries().forEachIndexed { index, entry ->
            nativeSetChainNamGainDb(index, entry.gainDb)
            nativeSetChainNamInGainDb(index, entry.inGainDb)
            nativeSetChainNamMix(index, entry.mix)
            nativeSetChainNamEqDb(index, 0, entry.eqLowDb)
            nativeSetChainNamEqDb(index, 1, entry.eqMidDb)
            nativeSetChainNamEqDb(index, 2, entry.eqHighDb)
            nativeSetChainNamEqDb(index, 3, entry.eqBand3Db)
            nativeSetChainNamEqDb(index, 4, entry.eqBand4Db)
            nativeSetChainNamEqDb(index, 5, entry.eqBand5Db)
            nativeSetChainNamEqPre(index, entry.eqPre)
            nativeSetChainNamEqEnabled(index, entry.eqEnabled)
            nativeSetChainNamNormalize(index, entry.normalize && entry.moduleType != "PEDAL")
            if (entry.moduleType == "PEDAL") nativeSetChainNamEqEnabled(index, false)
            if (entry.a2Full) nativeSetChainNamQuality(index, true)
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
                startButton.isEnabled = entries.isNotEmpty()
                updateBypassButton()
                status.text = if (entries.isEmpty()) {
                    "NAM CHAIN EMPTY\n\nUse ADD NAM to insert a block."
                } else {
                    result
                }
            }
        }.start()
    }


    private fun restoreExtraNamChainNative(): String {

        nativeClearExtraNamBlocks()


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
                    nativeAddChainModel(
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


                    nativeSetChainNamBypass(
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

            nativeClearExtraNamBlocks()


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
    // PLUGIN WEB UI
    // ========================================================

    private fun showPluginUi() {

        legacyScrollView.visibility =
            View.GONE

        pluginWebView.visibility =
            View.VISIBLE
    }


    private fun showLegacyDebugUi() {

        pluginWebView.visibility =
            View.GONE

        legacyScrollView.visibility =
            View.VISIBLE
    }


    private fun pluginStateJson(): String {

        val result =
            JSONObject()


        result.put(
            "running",
            nativeIsRunning()
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
        result.put("cabinetIrName", cabinetPath?.let { File(it).name } ?: "")
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
                0.0f
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

        result.put(
            "routing",
            nativeGetRoutingInfo()
        )

        result.put(
            "audioDevice",
            nativeGetAudioDeviceInfo()
        )

        result.put(
            "namBlockCount",
            nativeGetNamBlockCount()
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
                .put("gainDb", prefs.getFloat(PREF_NAM_GAIN_DB, 0.0f))
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
        val irLoaded = cabinetPath != null && File(cabinetPath).exists()
        val irPosition = prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS)
            .coerceIn(0, namChain.length())
        for (index in 0 until namChain.length()) {
            if (irLoaded && irPosition == index) {
                signalChain.put(JSONObject()
                    .put("type", "CABINET_IR")
                    .put("position", index)
                    .put("name", cabinetPath?.let { File(it).name } ?: "Cabinet IR"))
            }
            signalChain.put(namChain.getJSONObject(index).put("type", "NAM"))
        }
        if (irLoaded && irPosition >= namChain.length()) {
            signalChain.put(JSONObject()
                .put("type", "CABINET_IR")
                .put("position", namChain.length())
                .put("name", cabinetPath?.let { File(it).name } ?: "Cabinet IR"))
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


    inner class PluginBridge {

        @JavascriptInterface
        fun getState(): String {

            return pluginStateJson()
        }


        @JavascriptInterface
        fun setInputGain(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -24.0f,
                        24.0f
                    )


            nativeSetInputGainDb(
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


        @JavascriptInterface
        fun setOutputGain(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -24.0f,
                        12.0f
                    )


            nativeSetOutputGainDb(
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


        @JavascriptInterface
        fun setGateEnabled(
            enabled: Boolean
        ) {

            gateEnabled =
                enabled


            nativeSetGateEnabled(
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


        @JavascriptInterface
        fun setGateThreshold(
            db: Double
        ) {

            val value =
                db
                    .toFloat()
                    .coerceIn(
                        -90.0f,
                        -20.0f
                    )


            nativeSetGateThresholdDb(
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


        @JavascriptInterface
        fun setEqLow(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_LOW,
                db,
                ::nativeSetEqLowDb,
                eqLowSlider
            )
        }


        @JavascriptInterface
        fun setEqMid(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_MID,
                db,
                ::nativeSetEqMidDb,
                eqMidSlider
            )
        }


        @JavascriptInterface
        fun setEqHigh(
            db: Double
        ) {

            setEqFromPlugin(
                PREF_EQ_HIGH,
                db,
                ::nativeSetEqHighDb,
                eqHighSlider
            )
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


        @JavascriptInterface
        fun cycleInput(): Int {

            val selected =
                nativeCycleInputChannel()


            prefs
                .edit()
                .putInt(
                    PREF_INPUT_CHANNEL,
                    selected
                )
                .apply()


            runOnUiThread {

                refreshRoutingUi()
            }


            return selected
        }


        @JavascriptInterface
        fun cycleOutput(): Int {

            val selected =
                nativeCycleOutputPair()


            prefs
                .edit()
                .putInt(
                    PREF_OUTPUT_PAIR,
                    selected
                )
                .apply()


            runOnUiThread {

                refreshRoutingUi()
            }


            return selected
        }


        @JavascriptInterface
        fun startAudio(): String {

            val result =
                nativeStart()


            runOnUiThread {

                status.text =
                    result

                audioDeviceText.text =
                    nativeGetAudioDeviceInfo()

                refreshRoutingUi()
            }


            return result
        }


        @JavascriptInterface
        fun stopAudio(): String {

            nativeStop()


            runOnUiThread {

                status.text =
                    "STOPPED"
            }


            return "STOPPED"
        }

        @JavascriptInterface
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


        @JavascriptInterface
        fun toggleBypass(): Boolean {

            bypass =
                !bypass


            nativeSetBypass(
                bypass
            )

            prefs.edit().putBoolean(PREF_NAM_BYPASS, bypass).apply()


            runOnUiThread {

                updateBypassButton()
            }


            return bypass
        }


        @JavascriptInterface
        fun browseTone3000() {

            runOnUiThread {

                startTone3000SelectFlow(
                    "replace"
                )
            }
        }


        @JavascriptInterface
        fun addNam() {

            if (
                nativeGetNamBlockCount() >=
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
                    if (nativeGetNamBlockCount() == 0) {
                        "replace"
                    } else {
                        "add"
                    }
                )
            }
        }

        /** Receives a local .nam payload from the official React UI. The
         * bridge deliberately accepts JSON text because WebView's
         * JavascriptInterface cannot marshal arrays of objects reliably. */
        @JavascriptInterface
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
                    bypass = false
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

        /** Starts the existing authenticated TONE3000 downloader for a tone
         * selected by the official React browser. The React payload already
         * contains compatible models, so no second browser/OAuth round-trip is
         * needed here. */
        @JavascriptInterface
        fun loadTone(toneJson: String, targetInsertId: String): Boolean {
            return try {
                val tone = JSONObject(toneJson)
                val toneId = tone.optString("id")
                val title = tone.optString("title", "Tone $toneId")
                val imageUrl = tone.optJSONArray("images")?.optString(0).orEmpty()
                val selectedType = prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") ?: "AMP"
                val models = tone.optJSONArray("models") ?: JSONArray()
                if (toneId.isBlank() || models.length() == 0) return false
                val model = models.getJSONObject(0)
                val modelUrl = model.optString("model_url", model.optString("modelUrl"))
                if (modelUrl.isBlank()) return false
                val token = prefs.getString(PREF_ACCESS_TOKEN, null) ?: return false
                if (selectedType == "IR") {
                    val onlineModel = OnlineModel(
                        id = model.optLong("id", 0L),
                        name = model.optString("name", "cabinet-${model.optLong("id", 0L)}"),
                        size = model.optString("size", "custom"),
                        modelUrl = modelUrl
                    )
                    Thread { downloadAndLoadCabinet(toneId, title, imageUrl, onlineModel, token) }.start()
                    return true
                }
                val mode = if (targetInsertId.startsWith("nam-")) {
                    "replace:${targetInsertId.removePrefix("nam-").toIntOrNull() ?: 0}"
                } else "add"
                prefs.edit()
                    .putString(PREF_PENDING_IMPORT_MODE, mode)
                    .putString(PREF_PENDING_TONE_IMAGE, imageUrl)
                    .putString(PREF_PENDING_TONE_TYPE, selectedType)
                    .apply()
                val onlineModel = OnlineModel(
                    id = model.optLong("id", 0L),
                    name = model.optString("name", "capture-${model.optLong("id", 0L)}"),
                    size = model.optString("size", "custom"),
                    modelUrl = modelUrl
                )
                runOnUiThread { downloadAndLoadModel(toneId, title, onlineModel, token) }
                true
            } catch (error: Exception) {
                Log.e(API_TAG, "React tone load failed", error)
                false
            }
        }

        private fun downloadAndLoadCabinet(
            toneId: String,
            toneTitle: String,
            imageUrl: String,
            model: OnlineModel,
            token: String
        ) {
            try {
                val destination = File(filesDir, "cabinet-${model.id}.wav")
                if (destination.exists()) destination.delete()
                val downloaded = downloadModel(model, token, destination)
                val normalized = normalizeImpulseResponseWav(downloaded)
                val result = nativeLoadImpulseResponse(normalized.absolutePath)
                if (!result.startsWith("IR LOADED")) throw RuntimeException(result)
                val position = nativeGetNamBlockCount()
                nativeSetImpulseResponsePosition(position)
                prefs.edit()
                    .putString(PREF_CABINET_IR_PATH, normalized.absolutePath)
                    .putString(PREF_CABINET_IR_IMAGE, imageUrl)
                    .putInt(PREF_CABINET_IR_POSITION, position)
                    .putBoolean(PREF_CABINET_IR_BYPASS, false)
                    .putFloat(PREF_CABINET_IR_MIX, 1.0f)
                    .apply()
                val audio = nativeStart()
                runOnUiThread {
                    status.text = "CABINET IR READY\n\n$toneTitle\n${model.name}\n$audio"
                    pluginWebView.postDelayed({ pluginWebView.reload() }, 150)
                }
            } catch (error: Exception) {
                Log.e(API_TAG, "Cabinet IR download/load failed", error)
                runOnUiThread { status.text = "CABINET IR LOAD FAILED\n\n${error.message}" }
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

        @JavascriptInterface
        fun addCabinetIr() {
            runOnUiThread {
                openIrFile.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
            }
        }

        @JavascriptInterface
        fun removeCabinetIr() {
            nativeClearImpulseResponse()
            nativeSetImpulseResponseBypass(false)
            nativeSetImpulseResponseInGainDb(0.0f)
            nativeSetImpulseResponseOutGainDb(0.0f)
            nativeSetImpulseResponseMix(1.0f)
            nativeSetImpulseResponseEqPre(false)
            nativeSetImpulseResponseEqEnabled(true)
            for (band in 0 until 6) nativeSetImpulseResponseEqDb(band, 0.0f)
            prefs.getString(PREF_CABINET_IR_PATH, null)?.let { path ->
                try { File(path).delete() } catch (_: Exception) { }
            }
            prefs.edit()
                .remove(PREF_CABINET_IR_PATH)
                .remove(PREF_CABINET_IR_IMAGE)
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
            runOnUiThread { status.text = "CABINET IR REMOVED" }
        }

        @JavascriptInterface
        fun setCabinetBypass(bypassed: Boolean) {
            nativeSetImpulseResponseBypass(bypassed)
            prefs.edit().putBoolean(PREF_CABINET_IR_BYPASS, bypassed).apply()
        }

        @JavascriptInterface
        fun moveCabinet(direction: Int) {
            val maxPosition = nativeGetNamBlockCount().coerceIn(0, MAX_NAM_BLOCKS)
            val current = prefs.getInt(PREF_CABINET_IR_POSITION, maxPosition).coerceIn(0, maxPosition)
            val next = (current + direction.coerceIn(-1, 1)).coerceIn(0, maxPosition)
            nativeSetImpulseResponsePosition(next)
            prefs.edit().putInt(PREF_CABINET_IR_POSITION, next).apply()
        }

        @JavascriptInterface
        fun setCabinetInGain(db: Double) {
            val value = db.toFloat().coerceIn(-24.0f, 24.0f)
            nativeSetImpulseResponseInGainDb(value)
            prefs.edit().putFloat(PREF_CABINET_IR_IN_GAIN, value).apply()
        }

        @JavascriptInterface
        fun setCabinetOutGain(db: Double) {
            val value = db.toFloat().coerceIn(-24.0f, 12.0f)
            nativeSetImpulseResponseOutGainDb(value)
            prefs.edit().putFloat(PREF_CABINET_IR_OUT_GAIN, value).apply()
        }

        @JavascriptInterface
        fun setCabinetMix(mix: Double) {
            val value = mix.toFloat().coerceIn(0.0f, 1.0f)
            nativeSetImpulseResponseMix(value)
            prefs.edit().putFloat(PREF_CABINET_IR_MIX, value).apply()
        }

        @JavascriptInterface
        fun setCabinetEq(band: Int, db: Double) {
            if (band !in 0 until 6) return
            val value = db.toFloat().coerceIn(-12.0f, 12.0f)
            nativeSetImpulseResponseEqDb(band, value)
            prefs.edit().putFloat(PREF_CABINET_IR_EQ_PREFIX + band, value).apply()
        }

        @JavascriptInterface
        fun setCabinetEqPosition(pre: Boolean) {
            nativeSetImpulseResponseEqPre(pre)
            prefs.edit().putBoolean(PREF_CABINET_IR_EQ_PRE, pre).apply()
        }

        @JavascriptInterface
        fun setCabinetEqEnabled(enabled: Boolean) {
            nativeSetImpulseResponseEqEnabled(enabled)
            prefs.edit().putBoolean(PREF_CABINET_IR_EQ_ENABLED, enabled).apply()
        }


        @JavascriptInterface
        fun changeNam(chainIndex: Int) {
            if (chainIndex !in 0 until nativeGetNamBlockCount()) {
                return
            }

            runOnUiThread {
                startTone3000SelectFlow("replace:$chainIndex")
            }
        }


        @JavascriptInterface
        fun removeNam(chainIndex: Int) {
            removeNamBlock(chainIndex)
        }


        @JavascriptInterface
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

        @JavascriptInterface
        fun reorderChain(blockIdsJson: String): Boolean {
            return try {
                val requested = JSONArray(blockIdsJson)
                    .let { array -> (0 until array.length()).map { array.optString(it) } }
                val entries = readNamChainEntries()
                val orderedIndices = requested
                    .filter { it.startsWith("nam-") }
                    .mapNotNull { it.removePrefix("nam-").toIntOrNull() }
                    .filter { it in entries.indices }
                    .distinct()
                    .toMutableList()
                entries.indices.forEach { if (it !in orderedIndices) orderedIndices.add(it) }
                val reordered = orderedIndices.map { entries[it] }
                val cabinetPosition = requested.indexOf("cabinet-ir")
                    .takeIf { it >= 0 }
                    ?.coerceIn(0, reordered.size)
                    ?: prefs.getInt(PREF_CABINET_IR_POSITION, reordered.size).coerceIn(0, reordered.size)
                Thread {
                    val result = rebuildNativeNamChain(reordered)
                    if (result.startsWith("NAM CHAIN READY")) {
                        persistNamChainEntries(reordered)
                        prefs.edit().putInt(PREF_CABINET_IR_POSITION, cabinetPosition).apply()
                        nativeSetImpulseResponsePosition(cabinetPosition)
                    }
                    runOnUiThread { status.text = result }
                }.start()
                true
            } catch (error: Exception) {
                Log.e(API_TAG, "Chain reorder failed", error)
                false
            }
        }

        @JavascriptInterface
        fun resetToDefault(): Boolean {
            return try {
                nativeClearNamChain()
                nativeClearImpulseResponse()
                clearPersistedModel()
                persistExtraNamChain(emptyList())
                prefs.edit()
                    .remove(PREF_CABINET_IR_PATH)
                    .remove(PREF_CABINET_IR_POSITION)
                    .remove(PREF_CABINET_IR_BYPASS)
                    .apply()
                bypass = false
                nativeSetBypass(false)
                true
            } catch (error: Exception) {
                Log.e(API_TAG, "Reset to default failed", error)
                false
            }
        }


        @JavascriptInterface
        fun setNamBypass(
            chainIndex: Int,
            enabled: Boolean
        ) {

            nativeSetChainNamBypass(
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


        @JavascriptInterface
        fun setNamGain(chainIndex: Int, db: Double) {
            setNamControl(chainIndex, db, 0)
        }

        @JavascriptInterface
        fun setNamInGain(chainIndex: Int, db: Double) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val value = db.toFloat().coerceIn(-24.0f, 24.0f)
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(inGainDb = value)
            persistNamChainEntries(all)
            nativeSetChainNamInGainDb(chainIndex, value)
        }

        @JavascriptInterface
        fun setNamMix(chainIndex: Int, mix: Double) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val value = mix.toFloat().coerceIn(0.0f, 1.0f)
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(mix = value)
            persistNamChainEntries(all)
            nativeSetChainNamMix(chainIndex, value)
        }

        @JavascriptInterface
        fun setNamEq(chainIndex: Int, band: Int, db: Double) {
            setNamControl(chainIndex, db, band + 1)
        }

        @JavascriptInterface
        fun setNamEqPosition(chainIndex: Int, pre: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(eqPre = pre)
            persistNamChainEntries(all)
            nativeSetChainNamEqPre(chainIndex, pre)
        }

        @JavascriptInterface
        fun setNamEqEnabled(chainIndex: Int, enabled: Boolean) {
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
            nativeSetChainNamEqEnabled(chainIndex, enabled)
        }

        @JavascriptInterface
        fun setNamNormalize(chainIndex: Int, enabled: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(normalize = enabled)
            persistNamChainEntries(all)
            nativeSetChainNamNormalize(chainIndex, enabled)
        }

        @JavascriptInterface
        fun setNamQuality(chainIndex: Int, full: Boolean) {
            val entries = readNamChainEntries()
            if (chainIndex !in entries.indices) return
            val result = nativeSetChainNamQuality(chainIndex, full)
            if (!result.startsWith("A2 ")) {
                runOnUiThread { status.text = result }
                return
            }
            val all = entries.toMutableList()
            all[chainIndex] = all[chainIndex].copy(a2Full = full)
            persistNamChainEntries(all)
            runOnUiThread {
                status.text = result
                pluginWebView.evaluateJavascript("refreshNow()", null)
            }
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
            if (control == 0) nativeSetChainNamGainDb(chainIndex, value)
            else nativeSetChainNamEqDb(chainIndex, control - 1, value)
        }


        @JavascriptInterface
        fun clearExtraNams() {

            clearExtraNamChain()
        }


        @JavascriptInterface
        fun loadPreset(
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


        @JavascriptInterface
        fun savePreset(
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

        @JavascriptInterface
        fun renamePreset(slot: Int, name: String): Boolean {
            if (slot !in 1..PRESET_COUNT) return false
            prefs.edit().putString(presetKey(slot, "custom_label"), name.trim()).apply()
            return true
        }

        @JavascriptInterface
        fun deletePreset(slot: Int): Boolean {
            if (slot !in 1..PRESET_COUNT) return false
            val prefix = "preset_${slot}_"
            prefs.edit().apply {
                prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
            }.apply()
            listOf(
                presetFile(slot),
                File(filesDir, "preset-$slot-cabinet.wav")
            ).forEach { file -> if (file.exists()) file.delete() }
            return true
        }

        @JavascriptInterface
        fun movePreset(slot: Int, delta: Int): Boolean {
            val target = slot + delta.coerceIn(-1, 1)
            if (slot !in 1..PRESET_COUNT || target !in 1..PRESET_COUNT || slot == target) return false
            val firstPrefix = "preset_${slot}_"
            val secondPrefix = "preset_${target}_"
            val first = prefs.all.filterKeys { it.startsWith(firstPrefix) }
                .mapKeys { it.key.removePrefix(firstPrefix) }
            val second = prefs.all.filterKeys { it.startsWith(secondPrefix) }
                .mapKeys { it.key.removePrefix(secondPrefix) }
            prefs.edit().apply {
                (first.keys + second.keys).forEach { field ->
                    remove(presetKey(slot, field))
                    remove(presetKey(target, field))
                }
                first.forEach { (field, value) -> putPresetValue(presetKey(target, field), value) }
                second.forEach { (field, value) -> putPresetValue(presetKey(slot, field), value) }
            }.apply()
            return true
        }

        private fun android.content.SharedPreferences.Editor.putPresetValue(key: String, value: Any?) {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }


        @JavascriptInterface
        fun scanUsbAudio(): String {

            return nativeScanUsbAudio()
        }


        @JavascriptInterface
        fun getStats(): String {

            return nativeGetStats()
        }

        @JavascriptInterface
        fun getAudioDeviceState(): String {
            return JSONObject()
                .put("running", nativeIsRunning())
                .put("standalone", true)
                .put("deviceType", "TinyALSA")
                .put("deviceName", nativeGetAudioDeviceInfo())
                .put("sampleRate", 48000)
                .put("bufferSize", 128)
                .put("inputChannels", 2)
                .put("outputChannels", 2)
                .put("supportsInput", true)
                .put("supportsOutput", true)
                .toString()
        }

        @JavascriptInterface
        fun restartAudioDevice(): String {
            nativeStop()
            return nativeStart()
        }

        @JavascriptInterface
        fun getAudioInputLevels(): String {
            return nativeGetStats()
        }


        @JavascriptInterface
        fun showDebugUi() {

            runOnUiThread {

                showLegacyDebugUi()
            }
        }
    }


    // ========================================================
    // ROUTING
    // ========================================================

    private fun restoreRoutingSettings() {

        val inputChannel =
            prefs.getInt(
                PREF_INPUT_CHANNEL,
                0
            )

        val outputPair =
            prefs.getInt(
                PREF_OUTPUT_PAIR,
                0
            )


        nativeSetInputChannel(
            inputChannel
        )

        nativeSetOutputPair(
            outputPair
        )


        refreshRoutingUi()
    }


    private fun refreshRoutingUi() {

        routingText.text =
            "\n" +
                    nativeGetRoutingInfo()
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

                    nativeSetter(
                        db
                    )
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


        nativeSetGateEnabled(
            gateEnabled
        )

        nativeSetGateThresholdDb(
            gateThreshold
        )

        nativeSetEqLowDb(
            lowDb
        )

        nativeSetEqMidDb(
            midDb
        )

        nativeSetEqHighDb(
            highDb
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
                    nativeGetDspChainInfo()
    }


    // ========================================================
    // PRESETS
    // ========================================================

    private data class PresetData(
        val slot: Int,
        val modelPath: String,
        val modelName: String,
        val modelSize: String,
        val toneId: String?,
        val toneTitle: String?,
        val inputGainDb: Float,
        val outputGainDb: Float,
        val inputChannel: Int,
        val outputPair: Int,
        val gateEnabled: Boolean,
        val gateThresholdDb: Float,
        val eqLowDb: Float,
        val eqMidDb: Float,
        val eqHighDb: Float,
        val extraNamChainJson: String,
        val cabinetIrPath: String?,
        val cabinetIrBypass: Boolean,
        val cabinetIrPosition: Int,
        val cabinetIrInGain: Float,
        val cabinetIrOutGain: Float,
        val cabinetIrMix: Float
    )


    private fun presetKey(
        slot: Int,
        field: String
    ): String {

        return "preset_${slot}_$field"
    }


    private fun presetFile(
        slot: Int
    ): File {

        return File(
            filesDir,
            "preset-$slot.nam"
        )
    }


    private fun readPreset(
        slot: Int
    ): PresetData? {

        val saved =
            prefs.getBoolean(
                presetKey(
                    slot,
                    "saved"
                ),
                false
            )


        if (!saved) {
            return null
        }


        val path =
            prefs.getString(
                presetKey(
                    slot,
                    "model_path"
                ),
                null
            )
                ?: return null


        val file =
            File(
                path
            )


        if (!file.exists()) {
            return null
        }


        return PresetData(
            slot =
                slot,

            modelPath =
                path,

            modelName =
                prefs.getString(
                    presetKey(
                        slot,
                        "model_name"
                    ),
                    file.name
                ) ?: file.name,

            modelSize =
                prefs.getString(
                    presetKey(
                        slot,
                        "model_size"
                    ),
                    "unknown"
                ) ?: "unknown",

            toneId =
                prefs.getString(
                    presetKey(
                        slot,
                        "tone_id"
                    ),
                    null
                ),

            toneTitle =
                prefs.getString(
                    presetKey(
                        slot,
                        "tone_title"
                    ),
                    null
                ),

            inputGainDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "input_gain_db"
                    ),
                    0.0f
                ),

            outputGainDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "output_gain_db"
                    ),
                    0.0f
                ),

            inputChannel =
                prefs.getInt(
                    presetKey(
                        slot,
                        "input_channel"
                    ),
                    0
                ),

            outputPair =
                prefs.getInt(
                    presetKey(
                        slot,
                        "output_pair"
                    ),
                    0
                ),

            gateEnabled =
                prefs.getBoolean(
                    presetKey(
                        slot,
                        "gate_enabled"
                    ),
                    false
                ),

            gateThresholdDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "gate_threshold_db"
                    ),
                    -65.0f
                ),

            eqLowDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "eq_low_db"
                    ),
                    0.0f
                ),

            eqMidDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "eq_mid_db"
                    ),
                    0.0f
                ),

            eqHighDb =
                prefs.getFloat(
                    presetKey(
                        slot,
                        "eq_high_db"
                    ),
                    0.0f
                )
        ,
            extraNamChainJson = prefs.getString(presetKey(slot, "extra_nam_chain"), "[]") ?: "[]",
            cabinetIrPath = prefs.getString(presetKey(slot, "cabinet_ir_path"), null),
            cabinetIrBypass = prefs.getBoolean(presetKey(slot, "cabinet_ir_bypass"), false),
            cabinetIrPosition = prefs.getInt(presetKey(slot, "cabinet_ir_position"), MAX_NAM_BLOCKS),
            cabinetIrInGain = prefs.getFloat(presetKey(slot, "cabinet_ir_in_gain"), 0.0f),
            cabinetIrOutGain = prefs.getFloat(presetKey(slot, "cabinet_ir_out_gain"), 0.0f),
            cabinetIrMix = prefs.getFloat(presetKey(slot, "cabinet_ir_mix"), 1.0f)
        )
    }


    private fun presetLabel(
        slot: Int
    ): String {

        prefs.getString(presetKey(slot, "custom_label"), null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val preset =
            readPreset(
                slot
            )


        if (preset == null) {
            return "Preset $slot • EMPTY"
        }


        return buildString {

            append(
                "Preset $slot • "
            )

            if (
                !preset.toneTitle.isNullOrBlank()
            ) {

                append(
                    preset.toneTitle
                )

                append(
                    " • "
                )
            }

            append(
                preset.modelName
            )
        }
    }


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


    private fun savePreset(
        slot: Int
    ) {

        val currentPath =
            prefs.getString(
                PREF_LAST_MODEL_PATH,
                null
            )
                ?: run {

                    status.text =
                        "PRESET SAVE FAILED\n\nNo current model path."

                    return
                }


        val source =
            File(
                currentPath
            )


        if (!source.exists()) {

            status.text =
                "PRESET SAVE FAILED\n\nCurrent model file does not exist."

            return
        }


        status.text =
            "Saving preset $slot..."


        Thread {

            try {

                val destination =
                    presetFile(
                        slot
                    )


                source
                    .inputStream()
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
                    !destination.exists() ||
                    destination.length() == 0L
                ) {

                    throw RuntimeException(
                        "Preset NAM copy is empty."
                    )
                }

                val currentCabinetPath = prefs.getString(PREF_CABINET_IR_PATH, null)
                val cabinetPresetPath = currentCabinetPath?.let { cabinetPath ->
                    val cabinetSource = File(cabinetPath)
                    if (!cabinetSource.exists()) {
                        null
                    } else {
                        val cabinetDestination = File(filesDir, "preset-$slot-cabinet.wav")
                        cabinetSource.inputStream().use { input ->
                            cabinetDestination.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (cabinetDestination.exists() && cabinetDestination.length() > 0L) {
                            cabinetDestination.absolutePath
                        } else {
                            null
                        }
                    }
                }


                val modelName =
                    prefs.getString(
                        PREF_LAST_MODEL_NAME,
                        source.name
                    ) ?: source.name


                val modelSize =
                    prefs.getString(
                        PREF_LAST_MODEL_SIZE,
                        "unknown"
                    ) ?: "unknown"


                val toneId =
                    prefs.getString(
                        PREF_LAST_TONE_ID,
                        null
                    )


                val toneTitle =
                    prefs.getString(
                        PREF_LAST_TONE_TITLE,
                        null
                    )


                prefs
                    .edit()

                    .putBoolean(
                        presetKey(
                            slot,
                            "saved"
                        ),
                        true
                    )

                    .putString(
                        presetKey(
                            slot,
                            "model_path"
                        ),
                        destination.absolutePath
                    )

                    .putString(
                        presetKey(
                            slot,
                            "model_name"
                        ),
                        modelName
                    )

                    .putString(
                        presetKey(
                            slot,
                            "model_size"
                        ),
                        modelSize
                    )

                    .putString(
                        presetKey(
                            slot,
                            "tone_id"
                        ),
                        toneId
                    )

                    .putString(
                        presetKey(
                            slot,
                            "tone_title"
                        ),
                        toneTitle
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "input_gain_db"
                        ),
                        prefs.getFloat(
                            PREF_INPUT_GAIN,
                            0.0f
                        )
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "output_gain_db"
                        ),
                        prefs.getFloat(
                            PREF_OUTPUT_GAIN,
                            0.0f
                        )
                    )

                    .putInt(
                        presetKey(
                            slot,
                            "input_channel"
                        ),
                        prefs.getInt(
                            PREF_INPUT_CHANNEL,
                            0
                        )
                    )

                    .putInt(
                        presetKey(
                            slot,
                            "output_pair"
                        ),
                        prefs.getInt(
                            PREF_OUTPUT_PAIR,
                            0
                        )
                    )

                    .putBoolean(
                        presetKey(
                            slot,
                            "gate_enabled"
                        ),
                        prefs.getBoolean(
                            PREF_GATE_ENABLED,
                            false
                        )
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "gate_threshold_db"
                        ),
                        prefs.getFloat(
                            PREF_GATE_THRESHOLD,
                            -65.0f
                        )
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "eq_low_db"
                        ),
                        prefs.getFloat(
                            PREF_EQ_LOW,
                            0.0f
                        )
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "eq_mid_db"
                        ),
                        prefs.getFloat(
                            PREF_EQ_MID,
                            0.0f
                        )
                    )

                    .putFloat(
                        presetKey(
                            slot,
                            "eq_high_db"
                        ),
                        prefs.getFloat(
                            PREF_EQ_HIGH,
                            0.0f
                        )
                    )

                    .putString(
                        presetKey(slot, "extra_nam_chain"),
                        prefs.getString(PREF_EXTRA_NAM_CHAIN, "[]") ?: "[]"
                    )
                    .putString(
                        presetKey(slot, "cabinet_ir_path"),
                        cabinetPresetPath
                    )
                    .putBoolean(
                        presetKey(slot, "cabinet_ir_bypass"),
                        prefs.getBoolean(PREF_CABINET_IR_BYPASS, false)
                    )
                    .putInt(
                        presetKey(slot, "cabinet_ir_position"),
                        prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS)
                    )
                    .putFloat(
                        presetKey(slot, "cabinet_ir_in_gain"),
                        prefs.getFloat(PREF_CABINET_IR_IN_GAIN, 0.0f)
                    )
                    .putFloat(
                        presetKey(slot, "cabinet_ir_out_gain"),
                        prefs.getFloat(PREF_CABINET_IR_OUT_GAIN, 0.0f)
                    )
                    .putFloat(
                        presetKey(slot, "cabinet_ir_mix"),
                        prefs.getFloat(PREF_CABINET_IR_MIX, 1.0f)
                    )
                    .putBoolean(presetKey(slot, "nam_bypass"), prefs.getBoolean(PREF_NAM_BYPASS, bypass))
                    .putFloat(presetKey(slot, "nam_gain_db"), prefs.getFloat(PREF_NAM_GAIN_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_in_gain_db"), prefs.getFloat(PREF_NAM_IN_GAIN_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_mix"), prefs.getFloat(PREF_NAM_MIX, 1.0f))
                    .putFloat(presetKey(slot, "nam_eq_low_db"), prefs.getFloat(PREF_NAM_EQ_LOW_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_eq_mid_db"), prefs.getFloat(PREF_NAM_EQ_MID_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_eq_high_db"), prefs.getFloat(PREF_NAM_EQ_HIGH_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_eq_band3_db"), prefs.getFloat(PREF_NAM_EQ_BAND3_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_eq_band4_db"), prefs.getFloat(PREF_NAM_EQ_BAND4_DB, 0.0f))
                    .putFloat(presetKey(slot, "nam_eq_band5_db"), prefs.getFloat(PREF_NAM_EQ_BAND5_DB, 0.0f))
                    .putBoolean(presetKey(slot, "nam_eq_pre"), prefs.getBoolean(PREF_NAM_EQ_PRE, false))
                    .putBoolean(presetKey(slot, "nam_normalize"), prefs.getBoolean(PREF_NAM_NORMALIZE, true))
                    .putBoolean(presetKey(slot, "nam_a2_full"), prefs.getBoolean(PREF_NAM_A2_FULL, false))
                    .putBoolean(presetKey(slot, "cabinet_ir_eq_pre"), prefs.getBoolean(PREF_CABINET_IR_EQ_PRE, false))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_0"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 0, 0.0f))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_1"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 1, 0.0f))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_2"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 2, 0.0f))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_3"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 3, 0.0f))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_4"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 4, 0.0f))
                    .putFloat(presetKey(slot, "cabinet_ir_eq_5"), prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + 5, 0.0f))

                    .apply()


                runOnUiThread {

                    refreshPresetUi(
                        slot
                    )

                    status.text =
                        "PRESET $slot SAVED\n\n" +
                                presetLabel(
                                    slot
                                )
                }


            } catch (
                e: Exception
            ) {

                Log.e(
                    API_TAG,
                    "Preset save failed",
                    e
                )


                runOnUiThread {

                    status.text =
                        "PRESET SAVE FAILED\n\n" +
                                (
                                        e.message
                                            ?: e.toString()
                                        )
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

                val result =
                    nativeSwitchPresetGapless(
                        preset.modelPath,
                        preset.inputGainDb,
                        preset.outputGainDb,
                        preset.inputChannel,
                        preset.outputPair,
                        preset.gateEnabled,
                        preset.gateThresholdDb,
                        preset.eqLowDb,
                        preset.eqMidDb,
                        preset.eqHighDb
                    )


                val switchAccepted =
                    result.startsWith(
                        "PRESET SWITCH QUEUED"
                    ) ||
                            result.startsWith(
                                "PRESET LOADED"
                            ) ||
                            result.startsWith(
                                "MODEL LOADED"
                            )


                if (!switchAccepted) {

                    throw RuntimeException(
                        result
                    )
                }


                prefs
                    .edit()

                    .putString(
                        PREF_LAST_MODEL_PATH,
                        preset.modelPath
                    )

                    .putString(
                        PREF_LAST_MODEL_NAME,
                        preset.modelName
                    )

                    .putString(
                        PREF_LAST_MODEL_SIZE,
                        preset.modelSize
                    )

                    .putString(
                        PREF_LAST_TONE_ID,
                        preset.toneId
                    )

                    .putString(
                        PREF_LAST_TONE_TITLE,
                        preset.toneTitle
                    )

                    .putFloat(
                        PREF_INPUT_GAIN,
                        preset.inputGainDb
                    )

                    .putFloat(
                        PREF_OUTPUT_GAIN,
                        preset.outputGainDb
                    )

                    .putInt(
                        PREF_INPUT_CHANNEL,
                        preset.inputChannel
                    )

                    .putInt(
                        PREF_OUTPUT_PAIR,
                        preset.outputPair
                    )

                    .putBoolean(
                        PREF_GATE_ENABLED,
                        preset.gateEnabled
                    )

                    .putFloat(
                        PREF_GATE_THRESHOLD,
                        preset.gateThresholdDb
                    )

                    .putFloat(
                        PREF_EQ_LOW,
                        preset.eqLowDb
                    )

                    .putFloat(
                        PREF_EQ_MID,
                        preset.eqMidDb
                    )

                    .putFloat(
                        PREF_EQ_HIGH,
                        preset.eqHighDb
                    )

                    .putString(PREF_EXTRA_NAM_CHAIN, preset.extraNamChainJson)
                    .putString(PREF_CABINET_IR_PATH, preset.cabinetIrPath)
                    .putBoolean(PREF_CABINET_IR_BYPASS, preset.cabinetIrBypass)
                    .putInt(PREF_CABINET_IR_POSITION, preset.cabinetIrPosition)
                    .putFloat(PREF_CABINET_IR_IN_GAIN, preset.cabinetIrInGain)
                    .putFloat(PREF_CABINET_IR_OUT_GAIN, preset.cabinetIrOutGain)
                    .putFloat(PREF_CABINET_IR_MIX, preset.cabinetIrMix)
                    .putBoolean(PREF_NAM_BYPASS, prefs.getBoolean(presetKey(slot, "nam_bypass"), false))
                    .putFloat(PREF_NAM_GAIN_DB, prefs.getFloat(presetKey(slot, "nam_gain_db"), 0.0f))
                    .putFloat(PREF_NAM_IN_GAIN_DB, prefs.getFloat(presetKey(slot, "nam_in_gain_db"), 0.0f))
                    .putFloat(PREF_NAM_MIX, prefs.getFloat(presetKey(slot, "nam_mix"), 1.0f))
                    .putFloat(PREF_NAM_EQ_LOW_DB, prefs.getFloat(presetKey(slot, "nam_eq_low_db"), 0.0f))
                    .putFloat(PREF_NAM_EQ_MID_DB, prefs.getFloat(presetKey(slot, "nam_eq_mid_db"), 0.0f))
                    .putFloat(PREF_NAM_EQ_HIGH_DB, prefs.getFloat(presetKey(slot, "nam_eq_high_db"), 0.0f))
                    .putFloat(PREF_NAM_EQ_BAND3_DB, prefs.getFloat(presetKey(slot, "nam_eq_band3_db"), 0.0f))
                    .putFloat(PREF_NAM_EQ_BAND4_DB, prefs.getFloat(presetKey(slot, "nam_eq_band4_db"), 0.0f))
                    .putFloat(PREF_NAM_EQ_BAND5_DB, prefs.getFloat(presetKey(slot, "nam_eq_band5_db"), 0.0f))
                    .putBoolean(PREF_NAM_EQ_PRE, prefs.getBoolean(presetKey(slot, "nam_eq_pre"), false))
                    .putBoolean(PREF_NAM_NORMALIZE, prefs.getBoolean(presetKey(slot, "nam_normalize"), true))
                    .putBoolean(PREF_NAM_A2_FULL, prefs.getBoolean(presetKey(slot, "nam_a2_full"), false))
                    .putBoolean(PREF_CABINET_IR_EQ_PRE, prefs.getBoolean(presetKey(slot, "cabinet_ir_eq_pre"), false))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 0, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_0"), 0.0f))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 1, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_1"), 0.0f))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 2, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_2"), 0.0f))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 3, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_3"), 0.0f))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 4, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_4"), 0.0f))
                    .putFloat(PREF_CABINET_IR_EQ_PREFIX + 5, prefs.getFloat(presetKey(slot, "cabinet_ir_eq_5"), 0.0f))

                    .apply()

                val restoredChain = rebuildNativeNamChain(readNamChainEntries())
                val irPath = preset.cabinetIrPath
                if (irPath != null && File(irPath).exists()) {
                    nativeLoadImpulseResponse(irPath)
                    nativeSetImpulseResponseBypass(preset.cabinetIrBypass)
                    nativeSetImpulseResponsePosition(preset.cabinetIrPosition)
                    nativeSetImpulseResponseInGainDb(preset.cabinetIrInGain)
                    nativeSetImpulseResponseOutGainDb(preset.cabinetIrOutGain)
                    nativeSetImpulseResponseMix(preset.cabinetIrMix)
                    nativeSetImpulseResponseEqPre(presetCabinetEqPre(preset.slot))
                    for (band in 0 until 6) nativeSetImpulseResponseEqDb(band, presetCabinetEq(preset.slot, band))
                } else {
                    nativeClearImpulseResponse()
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

                    nativeSetBypass(
                        false
                    )

                    val audioResult = nativeStart()
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

    private fun presetCabinetEqPre(slot: Int): Boolean =
        prefs.getBoolean(presetKey(slot, "cabinet_ir_eq_pre"), false)

    private fun presetCabinetEq(slot: Int, band: Int): Float =
        prefs.getFloat(presetKey(slot, "cabinet_ir_eq_$band"), 0.0f)


    // ========================================================
    // GAIN
    // ========================================================

    private fun restoreGainSettings() {

        val inputGain =
            prefs.getFloat(
                PREF_INPUT_GAIN,
                0.0f
            )

        val outputGain =
            prefs.getFloat(
                PREF_OUTPUT_GAIN,
                0.0f
            )


        nativeSetInputGainDb(
            inputGain
        )

        nativeSetOutputGainDb(
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
                    val audio = if (restored.startsWith("NAM CHAIN READY")) nativeStart() else restored
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
                nativeLoadModel(
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
                        nativeLoadImpulseResponse(irPath)
                        nativeSetImpulseResponseBypass(prefs.getBoolean(PREF_CABINET_IR_BYPASS, false))
                        nativeSetImpulseResponsePosition(prefs.getInt(PREF_CABINET_IR_POSITION, MAX_NAM_BLOCKS))
                        nativeSetImpulseResponseInGainDb(prefs.getFloat(PREF_CABINET_IR_IN_GAIN, 0.0f))
                        nativeSetImpulseResponseOutGainDb(prefs.getFloat(PREF_CABINET_IR_OUT_GAIN, 0.0f))
                        nativeSetImpulseResponseMix(prefs.getFloat(PREF_CABINET_IR_MIX, 1.0f))
                        nativeSetImpulseResponseEqPre(prefs.getBoolean(PREF_CABINET_IR_EQ_PRE, false))
                        nativeSetImpulseResponseEqEnabled(prefs.getBoolean(PREF_CABINET_IR_EQ_ENABLED, true))
                        for (band in 0 until 6) nativeSetImpulseResponseEqDb(band, prefs.getFloat(PREF_CABINET_IR_EQ_PREFIX + band, 0.0f))
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

                    nativeSetBypass(
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
        val labels = arrayOf("PEDAL (NAM ultraleve)", "AMP (NAM A2-Lite)", "IR (somente cabinet)")
        AlertDialog.Builder(this)
            .setTitle("Adicionar módulo")
            .setItems(labels) { _, which ->
                val type = when (which) { 0 -> "PEDAL"; 1 -> "AMP"; else -> "IR" }
                prefs.edit().putString(PREF_SELECTED_ADD_TYPE, type).apply()
                openTone3000SelectFlow(importMode)
            }
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
        nativeStop()

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
                    if (prefs.getString(PREF_SELECTED_ADD_TYPE, "AMP") == "IR") "ir" else "nam"
                )

                .appendQueryParameter(
                    "architecture",
                    "2"
                )

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


                val token =
                    exchangeAuthorizationCode(
                        code,
                        verifier
                    )


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


                val tone =
                    getTone(
                        toneId,
                        token
                    )


                stage(
                    "3/3 - FETCHING A2 CAPTURES..."
                )


                val models =
                    listA2Models(
                        toneId,
                        token
                    )


                Log.i(
                    API_TAG,
                    "OAuth flow complete: ${models.size} A2 captures"
                )


                runOnUiThread {

                    showModelSelectionDialog(
                        toneId =
                            toneId,

                        toneTitle =
                            tone.title,

                        models =
                            models,

                        token =
                            token
                    )
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

    private fun deliverOAuthCallbackToWebView(uri: Uri?) {
        if (!isOAuthCallback(uri)) return
        val query = uri?.encodedQuery.orEmpty()
        val target = "file:///android_asset/tone3000-official/index.html" +
                if (query.isBlank()) "" else "?$query"
        Log.i(API_TAG, "Delivering OAuth callback to official WebView")
        runOnUiThread {
            pluginWebView.loadUrl(target)
        }
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


    // ========================================================
    // TOKEN
    // ========================================================

    private fun exchangeAuthorizationCode(
        code: String,
        verifier: String
    ): String {

        val fields =
            linkedMapOf(

                "grant_type" to
                        "authorization_code",

                "code" to
                        code,

                "code_verifier" to
                        verifier,

                "redirect_uri" to
                        REDIRECT_URI,

                "client_id" to
                        PUBLISHABLE_KEY
            )


        val body =
            fields
                .entries
                .joinToString(
                    "&"
                ) {

                    urlEncode(
                        it.key
                    ) +
                            "=" +
                            urlEncode(
                                it.value
                            )
                }


        val bodyBytes =
            body.toByteArray(
                StandardCharsets.UTF_8
            )


        val started =
            SystemClock.elapsedRealtime()


        Log.i(
            API_TAG,
            "POST /oauth/token START"
        )


        val connection =
            URL(
                TOKEN_URL
            ).openConnection()
                    as HttpURLConnection


        try {

            connection.requestMethod =
                "POST"

            connection.doOutput =
                true

            connection.connectTimeout =
                15_000

            connection.readTimeout =
                30_000


            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded"
            )


            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            connection.setRequestProperty(
                "Connection",
                "close"
            )


            connection.setFixedLengthStreamingMode(
                bodyBytes.size
            )


            connection
                .outputStream
                .use {

                    it.write(
                        bodyBytes
                    )
                }


            val responseCode =
                connection.responseCode


            Log.i(
                API_TAG,
                "POST /oauth/token HTTP $responseCode " +
                        "in ${SystemClock.elapsedRealtime() - started}ms"
            )


            val response =
                readHttpResponse(
                    connection,
                    responseCode
                )


            if (
                responseCode !in
                200..299
            ) {

                throw RuntimeException(
                    "Token exchange failed " +
                            "HTTP $responseCode\n" +
                            response
                )
            }


            val json =
                JSONObject(
                    response
                )


            val accessToken =
                json.getString(
                    "access_token"
                )


            prefs
                .edit()
                .putString(
                    PREF_ACCESS_TOKEN,
                    accessToken
                )
                .putString(
                    PREF_REFRESH_TOKEN,
                    json.optString(
                        "refresh_token"
                    )
                )
                .apply()


            return accessToken

        } finally {

            connection.disconnect()
        }
    }


    // ========================================================
    // TONE
    // ========================================================

    private data class OnlineTone(
        val title: String
    )


    private fun getTone(
        toneId: String,
        token: String
    ): OnlineTone {

        val url =
            "$API_BASE/api/v1/tones/" +
                    urlEncode(
                        toneId
                    ) +
                    "?architecture=2"


        val started =
            SystemClock.elapsedRealtime()


        Log.i(
            API_TAG,
            "GET /tones/$toneId START"
        )


        val connection =
            URL(
                url
            ).openConnection()
                    as HttpURLConnection


        try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                15_000

            connection.readTimeout =
                30_000


            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )


            connection.setRequestProperty(
                "Accept",
                "application/json"
            )


            connection.setRequestProperty(
                "Connection",
                "close"
            )


            val responseCode =
                connection.responseCode


            Log.i(
                API_TAG,
                "GET /tones/$toneId HTTP $responseCode " +
                        "in ${SystemClock.elapsedRealtime() - started}ms"
            )


            val response =
                readHttpResponse(
                    connection,
                    responseCode
                )


            if (
                responseCode !in
                200..299
            ) {

                throw RuntimeException(
                    "Get tone failed " +
                            "HTTP $responseCode\n" +
                            response
                )
            }


            val json =
                JSONObject(
                    response
                )


            return OnlineTone(
                title =
                    json.optString(
                        "title",
                        "Tone $toneId"
                    )
            )

        } finally {

            connection.disconnect()
        }
    }


    // ========================================================
    // MODELS
    // ========================================================

    private data class OnlineModel(
        val id: Long,
        val name: String,
        val size: String,
        val modelUrl: String
    )


    private fun listA2Models(
        toneId: String,
        token: String
    ): List<OnlineModel> {

        /*
         * A tone may contain many captures/models.
         *
         * TONE3000 paginates /models. Fetch every page instead of taking
         * only the first/default page, otherwise a multi-capture tone can
         * look like a single-capture tone in the app.
         */
        val result =
            mutableListOf<OnlineModel>()

        var page =
            1

        var totalPages =
            1


        do {

            val url =
                "$API_BASE/api/v1/models" +
                        "?tone_id=" +
                        urlEncode(toneId) +
                        "&architecture=2" +
                        "&page=$page" +
                        "&page_size=300"


            val started =
                SystemClock.elapsedRealtime()


            Log.i(
                API_TAG,
                "GET /models START page=$page url=$url"
            )


            val connection =
                URL(url).openConnection()
                        as HttpURLConnection


            try {

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    10_000

                connection.readTimeout =
                    30_000


                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )


                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )


                Log.i(
                    API_TAG,
                    "GET /models connecting page=$page..."
                )


                connection.connect()


                val responseCode =
                    connection.responseCode


                Log.i(
                    API_TAG,
                    "GET /models page=$page HTTP $responseCode " +
                            "in ${SystemClock.elapsedRealtime() - started}ms"
                )


                val response =
                    readHttpResponse(
                        connection,
                        responseCode
                    )


                Log.i(
                    API_TAG,
                    "GET /models page=$page body received: " +
                            "${response.length} chars"
                )


                if (
                    responseCode !in
                    200..299
                ) {

                    throw RuntimeException(
                        "List models failed " +
                                "HTTP $responseCode\n" +
                                response
                    )
                }


                val root =
                    JSONObject(
                        response
                    )


                val data =
                    root.getJSONArray(
                        "data"
                    )


                totalPages =
                    root.optInt(
                        "total_pages",
                        1
                    ).coerceAtLeast(
                        1
                    )


                Log.i(
                    API_TAG,
                    "GET /models page=$page parsed: " +
                            "${data.length()} captures; " +
                            "totalPages=$totalPages"
                )


                for (
                index in
                0 until data.length()
                ) {

                    val item =
                        data.getJSONObject(
                            index
                        )


                    result.add(
                        OnlineModel(
                            id =
                                item.getLong(
                                    "id"
                                ),

                            name =
                                item.optString(
                                    "name",
                                    "capture-${item.optLong("id", index.toLong())}"
                                ),

                            size =
                                item.optString(
                                    "size",
                                    "custom"
                                ),

                            modelUrl =
                                item.getString(
                                    "model_url"
                                )
                        )
                    )
                }

            } finally {

                connection.disconnect()
            }


            page +=
                1


            /*
             * Defensive limit against a malformed pagination response.
             * 100 * 300 captures is already far beyond a realistic tone.
             */
            if (page > 100) {

                throw RuntimeException(
                    "Too many model pages returned by TONE3000."
                )
            }

        } while (
            page <= totalPages
        )


        return result
            .distinctBy {
                it.id
            }
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


        val labels =
            sorted
                .map {

                    "${it.size.uppercase()} • ${it.name} • #${it.id}"
                }
                .toTypedArray()


        startButton.isEnabled =
            false


        status.text =
            "$toneTitle\n\n" +
                    "${sorted.size} compatible A2 captures found.\n" +
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


        val actionTitle =
            if (importMode == "add") {

                "ADD NAM — SELECT CAPTURE"

            } else {

                val replacementIndex = importMode
                    .removePrefix("replace:")
                    .toIntOrNull()

                if (replacementIndex != null) {
                    "REPLACE NAM ${replacementIndex + 1} — SELECT CAPTURE"
                } else {
                    "REPLACE NAM 1 — SELECT CAPTURE"
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
                                "${selected.name}\n" +
                                "${selected.size.uppercase()} • #${selected.id}"


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


                nativeStop()


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
                        nativeAddChainModel(
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

                    val audioResult = nativeStart()

                    val addedChainIndex = nativeGetNamBlockCount() - 1
                    nativeSetChainNamBypass(addedChainIndex, false)
                    nativeSetChainNamInGainDb(addedChainIndex, 0.0f)
                    nativeSetChainNamMix(addedChainIndex, 1.0f)
                    nativeSetChainNamGainDb(addedChainIndex, 0.0f)
                    for (band in 0 until 6) {
                        nativeSetChainNamEqDb(addedChainIndex, band, 0.0f)
                    }
                    nativeSetChainNamEqPre(addedChainIndex, false)
                    nativeSetChainNamNormalize(addedChainIndex, moduleType != "PEDAL")
                    if (moduleType == "PEDAL") nativeSetChainNamEqEnabled(addedChainIndex, false)
                    nativeSetChainNamQuality(addedChainIndex, false)


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
                                false
                            ,imageUrl = imageUrl
                            ,moduleType = moduleType
                        )
                    )


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
                        pluginWebView.postDelayed({ pluginWebView.reload() }, 150)
                    }


                    return@Thread
                }


                if (replacementIndex != null && replacementIndex > 0) {
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
                    entries[replacementIndex] = previous.copy(
                        toneId = toneId,
                        toneTitle = toneTitle,
                        modelId = model.id,
                        modelName = model.name,
                        size = model.size,
                        path = committed.absolutePath,
                        imageUrl = imageUrl
                    )

                    val replaceResult = rebuildNativeNamChain(entries)
                    if (!replaceResult.startsWith("NAM CHAIN READY")) {
                        committed.delete()
                        throw RuntimeException(replaceResult)
                    }

                    val audioResult = nativeStart()

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
                        pluginWebView.postDelayed({ pluginWebView.reload() }, 150)
                    }

                    return@Thread
                }


                stage(
                    "LOADING CAPTURE...\n${model.name}"
                )


                val loadResult =
                    nativeLoadModel(
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

                val audioResult = nativeStart()


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


                    nativeSetBypass(
                        false
                    )


                    updateBypassButton()


                    status.text =
                        "TONE3000 CAPTURE READY\n\n" +
                                loadResult + "\n" + audioResult
                    pluginWebView.postDelayed({ pluginWebView.reload() }, 150)
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

                        nativeSetBypass(
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
                nativeLoadModel(
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

    private fun urlEncode(
        value: String
    ): String {

        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        )
    }


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
