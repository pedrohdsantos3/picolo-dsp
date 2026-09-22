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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

        private const val PREF_PENDING_IMPORT_MODE =
            "pending_import_mode"

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

    external fun nativeAddChainModel(
        path: String
    ): String

    external fun nativeClearExtraNamBlocks(): String

    external fun nativeSetChainNamBypass(
        chainIndex: Int,
        bypass: Boolean
    )

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
                "OAuth callback received; waiting for onResume"
            )

            status.text =
                "OAuth callback received...\n" +
                        "Waiting for app network..."
        }
    }


    override fun onResume() {

        super.onResume()

        val pending =
            pendingOAuthIntent
                ?: return

        pendingOAuthIntent =
            null

        mainHandler.postDelayed(
            {
                handleOAuthIntent(
                    pending
                )
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


                loadUrl(
                    "file:///android_asset/tone3000/index.html"
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
        val bypass: Boolean
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


            val result =
                mutableListOf<ExtraNamEntry>()


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
                    !File(path).exists()
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
                            )
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


        entries.forEach { entry ->

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


        namChain.put(
            JSONObject()
                .put(
                    "chainIndex",
                    0
                )
                .put(
                    "primary",
                    true
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
        )


        readExtraNamChain()
            .forEachIndexed { index, entry ->

                namChain.put(
                    JSONObject()
                        .put(
                            "chainIndex",
                            index +
                                    1
                        )
                        .put(
                            "primary",
                            false
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
                )
            }


        result.put(
            "namChain",
            namChain
        )


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
        fun toggleBypass(): Boolean {

            bypass =
                !bypass


            nativeSetBypass(
                bypass
            )


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
                    "add"
                )
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
        fun scanUsbAudio(): String {

            return nativeScanUsbAudio()
        }


        @JavascriptInterface
        fun getStats(): String {

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
        val eqHighDb: Float
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
        )
    }


    private fun presetLabel(
        slot: Int
    ): String {

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

                    .apply()


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

                    updateBypassButton()


                    startButton.isEnabled =
                        true


                    status.text =
                        "PRESET $slot READY\n\n" +
                                result
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

                    restoreExtraNamChainNative()

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
                    "nam"
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
            if (
                importMode ==
                "add"
            ) {

                "ADD NAM — SELECT CAPTURE"

            } else {

                "REPLACE NAM 1 — SELECT CAPTURE"
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
                                    addResult
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
                                loadResult
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