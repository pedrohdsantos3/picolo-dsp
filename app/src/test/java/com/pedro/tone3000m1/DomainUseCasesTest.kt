package com.pedro.tone3000m1

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.ui.model.PicoloUiState
import com.pedro.tone3000m1.ui.state.PicoloStateRepository
import com.pedro.tone3000m1.domain.engine.PresetAudioEngine
import com.pedro.tone3000m1.domain.engine.PrimaryToneCaptureEngine
import com.pedro.tone3000m1.domain.engine.FxImpulseEngine
import com.pedro.tone3000m1.domain.engine.CabinetImpulseEngine
import com.pedro.tone3000m1.domain.engine.ExtraNamChainEngine
import com.pedro.tone3000m1.domain.engine.NamChainRebuildEngine
import com.pedro.tone3000m1.domain.engine.ToneModelEngine
import com.pedro.tone3000m1.domain.model.*
import com.pedro.tone3000m1.domain.repository.*
import com.pedro.tone3000m1.domain.usecase.*
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class DomainUseCasesTest {
    @Test fun prepareAuthorization_persistsVerifierAndMatchingChallenge() {
        val session = FakeSession()
        val (challenge, pending) = runBlocking {
            PrepareToneAuthorizationUseCase(session).execute() to session.pendingAuthorization()!!
        }
        assertEquals(challenge.state, pending.state)
        assertEquals(22, challenge.state.length)
        assertEquals(43, pending.verifier.length)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(pending.verifier.toByteArray(Charsets.UTF_8))), challenge.codeChallenge)
    }

    @Test fun completeToneSelection_rejectsMismatchedStateBeforeExchangingCode() {
        val session = FakeSession(PendingToneAuthorization("verifier", "expected"))
        val tones = FakeTones()
        val error = suspendFailure { CompleteToneSelectionUseCase(tones, session).execute("code", "wrong", "id", "NAM") }
        assertEquals("OAuth state mismatch.", error.message)
        assertNull(tones.exchangedVerifier)
        assertEquals(0, session.savedTokenCount)
    }

    @Test fun completeToneSelection_persistsTokensAndSelectsArchitectureByType() {
        val session = FakeSession(PendingToneAuthorization("verifier", "state"))
        val tones = FakeTones()
        val progress = mutableListOf<String>()
        val selection = runBlocking { CompleteToneSelectionUseCase(tones, session).execute("code", "state", "id", "ir", progress::add) }
        assertEquals("verifier", tones.exchangedVerifier)
        runBlocking {
            assertEquals("access", session.accessToken())
            assertNull(session.pendingAuthorization())
        }
        assertEquals(listOf(null, null), tones.architectures)
        assertEquals("IR", selection.moduleType)
        assertEquals(listOf("2/3 - FETCHING TONE...", "3/3 - FETCHING IMPULSE RESPONSE..."), progress)
    }

    @Test fun completeToneSelection_usesA2ArchitectureForNamModels() {
        val session = FakeSession(PendingToneAuthorization("verifier", "state"))
        val tones = FakeTones()

        runBlocking { CompleteToneSelectionUseCase(tones, session).execute("code", "state", "id", "nam") }

        assertEquals(listOf(2, 2), tones.architectures)
    }

    @Test fun downloadModel_returnsDownloadedFileOnSuccess() {
        val folder = Files.createTempDirectory("download-success-").toFile()
        try {
            val target = File(folder, "pending.nam")
            val files = FakeFiles(target)
            val tones = FakeTones().apply { onDownload = { _, _, file -> file.writeText("complete model"); file } }

            val downloaded = DownloadToneModelUseCase(tones, files).execute(
                OnlineModel(1, "m", "s", "url"), "token", "m.nam",
            )

            assertTrue(files.prepared)
            assertEquals(target, downloaded)
            assertEquals("complete model", downloaded.readText())
        } finally { folder.deleteRecursively() }
    }

    @Test fun downloadModel_deletesPartialFileWhenDownloadFails() {
        val folder = Files.createTempDirectory("download-test-").toFile()
        try {
            val target = File(folder, "pending.nam")
            val files = FakeFiles(target)
            val tones = FakeTones().apply { onDownload = { _, _, file -> file.writeText("partial"); error("network failed") } }
            val error = failure { DownloadToneModelUseCase(tones, files).execute(OnlineModel(1, "m", "s", "url"), "token", "m.nam") }
            assertEquals("network failed", error.message)
            assertTrue(files.prepared)
            assertFalse(target.exists())
        } finally { folder.deleteRecursively() }
    }

    @Test fun mergePackageCaptures_keepsCachedEntriesAndRefreshesMatches() {
        val cached = listOf(
            OnlineModel(1, "old name", "small", "old-url"),
            OnlineModel(2, "still cached", "medium", "cached-url"),
        )
        val repository = object : TonePackageCaptureRepository {
            override suspend fun save(toneId: String, moduleType: String, models: List<OnlineModel>) = Unit
            override suspend fun read(toneId: String, moduleType: String) = cached
        }
        val fresh = listOf(
            OnlineModel(1, "refreshed name", "large", "new-url"),
            OnlineModel(3, "new capture", "small", "new-capture-url"),
        )

        val merged = runBlocking { MergePackageCapturesUseCase(repository).execute("tone", "AMP", fresh) }

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals("refreshed name", merged[0].name)
        assertEquals("new-url", merged[0].modelUrl)
        assertEquals("still cached", merged[1].name)
    }

    @Test fun loadPackageCaptures_fetchesNamModelsMergesAndRefreshesCache() {
        val cached = OnlineModel(2, "cached capture", "medium", "cached-url")
        val refreshed = OnlineModel(1, "fresh capture", "large", "fresh-url")
        val tones = FakeTones().apply { listedModels = listOf(refreshed) }
        val captures = FakePackageCaptures(listOf(cached))
        val useCase = LoadPackageCapturesUseCase(
            ListToneModelsUseCase(tones),
            MergePackageCapturesUseCase(captures),
            captures,
        )

        val models = runBlocking { useCase.execute("tone-1", "NAM", "token") }

        assertEquals(listOf(2), tones.architectures)
        assertEquals("tone-1", tones.listedToneId)
        assertEquals("token", tones.listedToken)
        assertEquals(listOf(2L, 1L), models.map { it.id })
        assertEquals(models, captures.savedModels)
    }

    @Test fun loadPackageCaptures_requestsUnspecifiedArchitectureForFx() {
        val tones = FakeTones()
        val captures = FakePackageCaptures()

        runBlocking {
            LoadPackageCapturesUseCase(
                ListToneModelsUseCase(tones),
                MergePackageCapturesUseCase(captures),
                captures,
            ).execute("tone-2", "FX", "token")
        }

        assertEquals(listOf(null), tones.architectures)
    }

    @Test fun importLocalNamFile_validatesBeforeCallingStorage() {
        val files = FakeFiles()
        val useCase = ImportLocalNamFileUseCase(files)
        useCase.execute("AMP.NAM", "encoded")
        assertEquals("AMP.NAM", files.writtenName)
        assertEquals("encoded", files.writtenData)
        assertEquals("Only .nam files are supported on Android", failure { useCase.execute("amp.txt", "data") }.message)
        assertEquals("Empty local file", failure { useCase.execute("amp.nam", " ") }.message)
        assertEquals(1, files.writeCount)
    }

    @Test fun picoloStateRepositoryPublishesStateChangesWithoutWaitingForMetricsTick() = runBlocking {
        val repository = PicoloStateRepository(readStats = { "blocks=1" }, intervalMs = 60_000)
        repository.publish(PicoloUiState(running = true, status = "Audio active"))
        val snapshot = withTimeout(1_000) {
            repository.observe().first { it.state.running }
        }

        assertEquals("Audio active", snapshot.state.status)
        assertEquals("blocks=1", snapshot.stats)
    }

    @Test fun importFxCapture_downloadsLoadsAndPersistsNewEffect() {
        val folder = Files.createTempDirectory("fx-import-").toFile()
        try {
            val pending = File(folder, "pending.wav")
            val files = FakeFiles(pending)
            val tones = FakeTones().apply { onDownload = { _, _, target -> target.writeText("wav"); target } }
            val effects = FakeFxEffects()
            val engine = RecordingFxImpulseEngine()
            val model = OnlineModel(50, "Reverb", "small", "url")

            val result = ImportFxCaptureUseCase(
                DownloadToneModelUseCase(tones, files),
                PrepareImpulseResponseUseCase(files),
                effects,
                engine,
            ).execute("tone", "Space", "image", model, "token")

            assertEquals("FX LOADED", engine.loadResult)
            assertEquals(0, engine.loadedSlot)
            assertEquals(pending.absolutePath, result.entry.path)
            assertEquals(listOf(result.entry), effects.persisted)
            assertEquals(listOf("bypass:0:false", "mix:0:0.5", "position:0:3", "start"), engine.events)
        } finally { folder.deleteRecursively() }
    }

    @Test fun importFxCapture_replacesSelectedSlotAndDeletesOldFileOnlyAfterLoad() {
        val folder = Files.createTempDirectory("fx-replace-").toFile()
        try {
            val pending = File(folder, "pending.wav")
            val old = File(folder, "old.wav").apply { writeText("old") }
            val files = FakeFiles(pending)
            val tones = FakeTones().apply { onDownload = { _, _, target -> target.writeText("new"); target } }
            val effects = FakeFxEffects(mutableListOf(testFxEntry(old.absolutePath)))
            val engine = RecordingFxImpulseEngine(namCount = 2)

            val result = ImportFxCaptureUseCase(
                DownloadToneModelUseCase(tones, files),
                PrepareImpulseResponseUseCase(files),
                effects,
                engine,
            ).execute("tone", "Updated", "image", OnlineModel(51, "New FX", "medium", "url"), "token", 0)

            assertEquals(0, engine.loadedSlot)
            assertEquals(old.absolutePath, result.replacedPath)
            assertFalse(old.exists())
            assertEquals(1, effects.persisted.size)
            assertEquals("Updated", effects.persisted.single().title)
        } finally { folder.deleteRecursively() }
    }

    @Test fun importFxCapture_failedEngineLoadKeepsExistingChainAndCleansDownload() {
        val folder = Files.createTempDirectory("fx-failure-").toFile()
        try {
            val pending = File(folder, "pending.wav")
            val files = FakeFiles(pending)
            val tones = FakeTones().apply { onDownload = { _, _, target -> target.writeText("bad"); target } }
            val oldEntry = testFxEntry("/old.wav")
            val effects = FakeFxEffects(mutableListOf(oldEntry))
            val engine = RecordingFxImpulseEngine(loadResult = "ERROR: invalid IR")

            val error = failure {
                ImportFxCaptureUseCase(
                    DownloadToneModelUseCase(tones, files),
                    PrepareImpulseResponseUseCase(files),
                    effects,
                    engine,
                ).execute("tone", "Space", "", OnlineModel(52, "Broken", "small", "url"), "token")
            }

            assertEquals("ERROR: invalid IR", error.message)
            assertEquals(listOf(oldEntry), effects.persisted)
            assertFalse(pending.exists())
            assertTrue(engine.events.isEmpty())
        } finally { folder.deleteRecursively() }
    }

    @Test fun importCabinetImpulse_configuresPersistsAndStartsWithModuleDefaults() {
        val folder = Files.createTempDirectory("cabinet-import-").toFile()
        try {
            val pending = File(folder, "pending.wav")
            val files = FakeFiles(pending)
            val tones = FakeTones().apply { onDownload = { _, _, target -> target.writeText("wav"); target } }
            val repository = RecordingCabinetImpulseRepository()
            val engine = RecordingCabinetImpulseEngine()

            val result = ImportCabinetImpulseUseCase(
                DownloadToneModelUseCase(tones, files), PrepareImpulseResponseUseCase(files), repository, engine,
            ).execute("tone", "Cab", "image", OnlineModel(70, "IR", "small", "url"), "token", "FX")

            assertEquals("AUDIO ACTIVE", result)
            assertEquals(pending.absolutePath, repository.path)
            assertEquals(0.5f, repository.mix)
            assertEquals(listOf("load", "position:3", "bypass:false", "in:0.0", "out:0.0", "mix:0.5", "eqPre:false", "eqEnabled:true", "eq:0:0.0", "eq:1:0.0", "eq:2:0.0", "eq:3:0.0", "eq:4:0.0", "eq:5:0.0", "start"), engine.events)
        } finally { folder.deleteRecursively() }
    }

    @Test fun addExtraNamCapture_commitsConfiguresAndPersistsAmpDefaults() {
        val chain = RecordingExtraNamRepository()
        val engine = RecordingExtraNamEngine()
        val pending = File("pending.nam")

        val result = AddExtraNamCaptureUseCase(
            CommitExtraToneModelUseCase(FakeFiles()), chain, engine,
        ).execute(pending, "tone", "Amp tone", "image", OnlineModel(71, "Amp capture", "large", "url"), "AMP")

        assertEquals("CHAIN NAM ADDED", result.chainResult)
        assertEquals("AUDIO ACTIVE", result.audioResult)
        assertEquals(1, chain.persisted.size)
        assertEquals(pending.absolutePath, chain.persisted.single().path)
        assertEquals(-15f, chain.persisted.single().gainDb)
        assertTrue(chain.persisted.single().normalize)
        assertTrue(chain.persisted.single().a2Full)
        assertEquals(pending.absolutePath, engine.addedPath)
        assertEquals(listOf("bypass:3:false", "in:3:0.0", "mix:3:1.0", "gain:3:-15.0", "eq:3:0:0.0", "eq:3:1:0.0", "eq:3:2:0.0", "eq:3:3:0.0", "eq:3:4:0.0", "eq:3:5:0.0", "eqPre:3:false", "normalize:3:true", "eqEnabled:3:true", "quality:3:true", "start"), engine.events)
    }

    @Test fun addExtraNamCapture_rejectedEngineLoadDoesNotPersistEntry() {
        val chain = RecordingExtraNamRepository()
        val engine = RecordingExtraNamEngine(addResult = "ERROR: invalid model")
        val pending = File("pending.nam")

        assertEquals("ERROR: invalid model", failure {
            AddExtraNamCaptureUseCase(CommitExtraToneModelUseCase(FakeFiles()), chain, engine)
                .execute(pending, "tone", "Amp tone", "", OnlineModel(72, "bad", "small", "url"), "AMP")
        }.message)
        assertTrue(chain.persisted.isEmpty())
        assertFalse(pending.exists())
    }

    @Test fun rebuildNamChain_restoresBlocksAndStartsOnlyWhenPreviouslyRunning() {
        val engine = RecordingNamChainRebuildEngine()
        val entries = listOf(
            testExtraNamEntry().copy(path = "/primary.nam", normalize = true, a2Full = true),
            testExtraNamEntry().copy(path = "/pedal.nam", moduleType = "PEDAL", normalize = true, a2Full = true),
        )

        val result = RebuildNamChainUseCase(engine).execute(entries)

        assertEquals("NAM CHAIN READY\nblocks=2\nAUDIO ACTIVE", result)
        assertEquals(2, engine.loadedPaths.size)
        assertEquals("/primary.nam", engine.loadedPaths.first())
        assertEquals("/pedal.nam", engine.loadedPaths.last())
        assertTrue(engine.events.contains("quality:0:true"))
        assertTrue(engine.events.contains("normalize:0:true"))
        assertTrue(engine.events.contains("quality:1:false"))
        assertTrue(engine.events.contains("normalize:1:false"))
        assertEquals("start", engine.events.last())
    }

    @Test fun rebuildNamChain_returnsLoadFailureWithoutStartingAudio() {
        val engine = RecordingNamChainRebuildEngine(primaryResult = "ERROR: invalid model")
        assertEquals("ERROR: invalid model", RebuildNamChainUseCase(engine).execute(listOf(testExtraNamEntry())))
        assertFalse(engine.events.contains("start"))
    }

    @Test fun replaceNamCapture_commitsRebuildsAndStartsReplacementChain() {
        val engine = RecordingNamChainRebuildEngine().apply { running = false }
        val old = testExtraNamEntry().copy(path = "/old.nam", gainDb = -6f, eqLowDb = 2f)
        val pending = File("pending-replacement.nam")

        val result = ReplaceNamCaptureUseCase(
            CommitExtraToneModelUseCase(FakeFiles()),
            PrepareNamBlockReplacementUseCase(),
            RebuildNamChainUseCase(engine),
            engine,
        ).execute(
            currentEntries = listOf(old),
            replacementIndex = 0,
            pendingFile = pending,
            toneId = "new-tone",
            toneTitle = "New tone",
            imageUrl = "new-image",
            model = OnlineModel(73, "New capture", "medium", "url"),
            moduleType = "AMP",
        )

        assertEquals("/old.nam", result.previousPath)
        assertEquals(pending.absolutePath, result.replacement.path)
        assertEquals(-6f, result.replacement.gainDb)
        assertEquals(2f, result.entries.single().eqLowDb)
        assertEquals("NAM CHAIN READY\nblocks=1", result.rebuildResult)
        assertEquals("AUDIO ACTIVE", result.audioResult)
        assertEquals("start", engine.events.last())
    }

    @Test fun replaceNamCapture_rejectsStaleIndexBeforeCommittingFile() {
        val files = FakeFiles()
        val error = failure {
            ReplaceNamCaptureUseCase(
                CommitExtraToneModelUseCase(files),
                PrepareNamBlockReplacementUseCase(),
                RebuildNamChainUseCase(RecordingNamChainRebuildEngine()),
                RecordingNamChainRebuildEngine(),
            ).execute(
                emptyList(), 0, File("pending.nam"), "tone", "title", "", OnlineModel(74, "capture", "small", "url"), "AMP",
            )
        }
        assertEquals("NAM block 1 no longer exists.", error.message)
        assertEquals(0, files.extraCommitCount)
    }

    @Test fun prepareNamBlockReplacement_preservesControlsForSameModuleRole() {
        val previous = testExtraNamEntry().copy(
            gainDb = -8f, inGainDb = 2f, mix = 0.4f, eqLowDb = 3f,
            eqPre = true, eqEnabled = false, normalize = false, bypass = true,
        )
        val result = PrepareNamBlockReplacementUseCase().execute(
            previous, "new-tone", "New title", OnlineModel(8, "New capture", "large", "url"),
            "/new.nam", "image", "AMP",
        )

        assertEquals("new-tone", result.toneId)
        assertEquals("/new.nam", result.path)
        assertEquals(-8f, result.gainDb)
        assertEquals(2f, result.inGainDb)
        assertEquals(0.4f, result.mix)
        assertEquals(3f, result.eqLowDb)
        assertTrue(result.eqPre)
        assertFalse(result.eqEnabled)
        assertFalse(result.normalize)
        assertTrue(result.bypass)
    }

    @Test fun prepareNamBlockReplacementResetsRoleSpecificControlsWhenChangingToPedal() {
        val previous = testExtraNamEntry().copy(
            moduleType = "AMP", a2Full = true, gainDb = -3f, inGainDb = 4f,
            mix = 0.2f, eqLowDb = 2f, eqMidDb = 3f, eqHighDb = 4f,
            eqBand3Db = 5f, eqBand4Db = 6f, eqBand5Db = 7f,
            eqPre = true, eqEnabled = false, normalize = true,
        )

        val result = PrepareNamBlockReplacementUseCase().execute(
            previous, "tone", "Title", OnlineModel(9, "Pedal", "small", "url"),
            "/pedal.nam", "image", "PEDAL",
        )

        assertEquals("PEDAL", result.moduleType)
        assertFalse(result.a2Full)
        assertEquals(-10f, result.gainDb)
        assertEquals(0f, result.inGainDb)
        assertEquals(1f, result.mix)
        assertEquals(List(6) { 0f }, listOf(result.eqLowDb, result.eqMidDb, result.eqHighDb,
            result.eqBand3Db, result.eqBand4Db, result.eqBand5Db))
        assertFalse(result.eqPre)
        assertTrue(result.eqEnabled)
        assertFalse(result.normalize)
    }

    @Test fun loadPrimaryToneCapture_commitsOnlyAfterEngineAcceptsTheModel() {
        val folder = Files.createTempDirectory("primary-capture-").toFile()
        try {
            val downloaded = File(folder, "pending.nam").apply { writeText("nam") }
            val files = FakeFiles(downloaded)
            val activeTone = FakeActiveToneRepository()
            val engine = RecordingPrimaryToneCaptureEngine("MODEL LOADED")
            val useCase = LoadPrimaryToneCaptureUseCase(
                CommitCurrentToneModelUseCase(files), activeTone, engine,
            )
            val model = OnlineModel(5, "Pedal", "small", "url")

            val result = useCase.execute("tone", "Pedal tone", model, "PEDAL", downloaded)

            assertEquals("MODEL LOADED", result.loadResult)
            assertEquals("AUDIO ACTIVE", result.audioResult)
            assertEquals(listOf("load", "defaults:PEDAL", "start"), engine.events)
            assertEquals(downloaded, files.currentCommit)
            assertEquals(model, activeTone.model)
            assertEquals("tone", activeTone.toneId)
            assertEquals("PEDAL", activeTone.moduleType)
        } finally { folder.deleteRecursively() }
    }

    @Test fun loadPrimaryToneCapture_rejectedModelDoesNotCommitOrPersist() {
        val folder = Files.createTempDirectory("rejected-capture-").toFile()
        try {
            val downloaded = File(folder, "pending.nam").apply { writeText("invalid") }
            val files = FakeFiles(downloaded)
            val activeTone = FakeActiveToneRepository()
            val engine = RecordingPrimaryToneCaptureEngine("ERROR: invalid model")

            val error = failure {
                LoadPrimaryToneCaptureUseCase(
                    CommitCurrentToneModelUseCase(files), activeTone, engine,
                ).execute("tone", "title", OnlineModel(5, "Model", "small", "url"), "AMP", downloaded)
            }

            assertEquals("NAM rejected the selected capture:\nERROR: invalid model", error.message)
            assertNull(files.currentCommit)
            assertNull(activeTone.model)
            assertEquals(listOf("load"), engine.events)
        } finally { folder.deleteRecursively() }
    }

    @Test fun restorePreviousToneModel_loadsExistingPersistedModel() {
        val model = Files.createTempFile("previous-model", ".nam").toFile()
        try {
            var loadedPath: String? = null
            val repository = object : CurrentToneRepository {
                override fun currentModelFile() = model
            }
            val engine = object : ToneModelEngine {
                override fun loadModel(path: String): String { loadedPath = path; return "MODEL LOADED" }
            }

            val result = RestorePreviousToneModelUseCase(repository, engine).execute()

            assertTrue(result.restored)
            assertEquals("MODEL LOADED", result.details)
            assertEquals(model.absolutePath, loadedPath)
        } finally { model.delete() }
    }

    @Test fun restorePreviousToneModel_reportsMissingPathOrFileWithoutCallingEngine() {
        var loadCalled = false
        val engine = object : ToneModelEngine {
            override fun loadModel(path: String): String { loadCalled = true; return "MODEL LOADED" }
        }
        val noPath = object : CurrentToneRepository {
            override fun currentModelFile() = null
        }
        val missingFile = object : CurrentToneRepository {
            override fun currentModelFile() = File("/does-not-exist/model.nam")
        }

        val noPathResult = RestorePreviousToneModelUseCase(noPath, engine).execute()
        val missingFileResult = RestorePreviousToneModelUseCase(missingFile, engine).execute()

        assertFalse(noPathResult.restored)
        assertEquals("No persisted model path.", noPathResult.details)
        assertFalse(missingFileResult.restored)
        assertEquals("Persisted model file does not exist.", missingFileResult.details)
        assertFalse(loadCalled)
    }

    @Test fun restorePreviousToneModel_reportsRejectedModelAndEngineExceptions() {
        val model = Files.createTempFile("rejected-model", ".nam").toFile()
        try {
            val repository = object : CurrentToneRepository {
                override fun currentModelFile() = model
            }
            val rejectedEngine = object : ToneModelEngine {
                override fun loadModel(path: String) = "ERROR: invalid model"
            }
            val throwingEngine = object : ToneModelEngine {
                override fun loadModel(path: String): String = error("engine offline")
            }

            val rejected = RestorePreviousToneModelUseCase(repository, rejectedEngine).execute()
            val thrown = RestorePreviousToneModelUseCase(repository, throwingEngine).execute()

            assertFalse(rejected.restored)
            assertEquals("ERROR: invalid model", rejected.details)
            assertFalse(thrown.restored)
            assertEquals("engine offline", thrown.details)
        } finally { model.delete() }
    }

    @Test fun loadPreset_activatesOnlyWhenEngineAcceptsSwitch() {
        val preset = testPreset()
        val repository = FakePresets()
        val loaded = LoadPresetUseCase(repository, FakeEngine("PRESET SWITCH QUEUED")).execute(preset)
        assertSame(preset, loaded.preset)
        assertSame(preset, repository.activated)
    }

    @Test fun loadPreset_doesNotActivateWhenEngineRejectsSwitch() {
        val repository = FakePresets()
        val error = failure { LoadPresetUseCase(repository, FakeEngine("ERROR: invalid model")).execute(testPreset()) }
        assertEquals("ERROR: invalid model", error.message)
        assertNull(repository.activated)
    }
}

private class FakeSession(private var pending: PendingToneAuthorization? = null) : ToneSessionRepository {
    private var tokens: OAuthTokenResponse? = null
    var savedTokenCount = 0
    override suspend fun saveTokens(tokens: OAuthTokenResponse) { this.tokens = tokens; savedTokenCount++ }
    override suspend fun saveAccessToken(token: String) = true
    override suspend fun accessToken() = tokens?.accessToken
    override suspend fun clearTokens() { tokens = null }
    override suspend fun savePendingAuthorization(verifier: String, state: String) { pending = PendingToneAuthorization(verifier, state) }
    override suspend fun pendingAuthorization() = pending
    override suspend fun clearPendingAuthorization() { pending = null }
}

private class FakeTones : Tone3000Repository {
    var exchangedVerifier: String? = null
    val architectures = mutableListOf<Int?>()
    var listedModels: List<OnlineModel> = emptyList()
    var listedToneId: String? = null
    var listedToken: String? = null
    var onDownload: ((OnlineModel, String, File) -> File)? = null
    override fun exchangeAuthorizationCode(code: String, verifier: String) = OAuthTokenResponse("access", "refresh").also { exchangedVerifier = verifier }
    override fun getTone(toneId: String, token: String, architecture: Int?): Tone3000Tone { architectures += architecture; return Tone3000Tone("Tone") }
    override fun listModels(toneId: String, token: String, architecture: Int?): List<OnlineModel> {
        architectures += architecture
        listedToneId = toneId
        listedToken = token
        return listedModels
    }
    override fun downloadModel(model: OnlineModel, token: String, destination: File) = onDownload?.invoke(model, token, destination) ?: destination
}

private class FakeFxEffects(
    private val entries: MutableList<FxImpulseEntry> = mutableListOf(),
) : FxEffectsRepository {
    var persisted: List<FxImpulseEntry> = entries.toList()
    override fun readImpulseChain() = entries.toMutableList()
    override fun persistImpulseChain(entries: List<FxImpulseEntry>) { persisted = entries.toList(); this.entries.clear(); this.entries.addAll(entries) }
}

private class RecordingFxImpulseEngine(
    val loadResult: String = "FX LOADED",
    private val namCount: Int = 3,
) : FxImpulseEngine {
    var loadedSlot: Int? = null
    val events = mutableListOf<String>()
    override fun loadImpulseResponse(slot: Int, path: String) = loadResult.also { loadedSlot = slot }
    override fun setImpulseResponseBypass(slot: Int, bypass: Boolean) { events += "bypass:$slot:$bypass" }
    override fun setImpulseResponseMix(slot: Int, mix: Float) { events += "mix:$slot:$mix" }
    override fun setImpulseResponsePosition(slot: Int, namBlocksBefore: Int) { events += "position:$slot:$namBlocksBefore" }
    override fun namBlockCount() = namCount
    override fun start() = "AUDIO ACTIVE".also { events += "start" }
}

private fun testFxEntry(path: String) = FxImpulseEntry(
    toneId = "old-tone", title = "Old FX", image = "", modelId = 1L,
    modelName = "Old", path = path,
)

private class RecordingCabinetImpulseRepository : CabinetImpulseRepository {
    var path: String? = null
    var mix: Float? = null
    override fun save(path: File, imageUrl: String, title: String, toneId: String, moduleType: String, position: Int, mix: Float) {
        this.path = path.absolutePath
        this.mix = mix
    }
}

private class RecordingCabinetImpulseEngine : CabinetImpulseEngine {
    val events = mutableListOf<String>()
    override fun loadImpulseResponse(path: String) = "IR LOADED".also { events += "load" }
    override fun namBlockCount() = 3
    override fun setPosition(namBlocksBefore: Int) { events += "position:$namBlocksBefore" }
    override fun setBypass(bypass: Boolean) { events += "bypass:$bypass" }
    override fun setInGain(db: Float) { events += "in:$db" }
    override fun setOutGain(db: Float) { events += "out:$db" }
    override fun setMix(mix: Float) { events += "mix:$mix" }
    override fun setEqPre(pre: Boolean) { events += "eqPre:$pre" }
    override fun setEqEnabled(enabled: Boolean) { events += "eqEnabled:$enabled" }
    override fun setEqDb(band: Int, db: Float) { events += "eq:$band:$db" }
    override fun start() = "AUDIO ACTIVE".also { events += "start" }
}

private class RecordingExtraNamRepository : ExtraNamChainRepository {
    var persisted: List<ExtraNamEntry> = emptyList()
    override fun readExtraNamChain() = persisted.toMutableList()
    override fun persistExtraNamChain(entries: List<ExtraNamEntry>) { persisted = entries.toList() }
}

private class RecordingExtraNamEngine(
    private val addResult: String = "CHAIN NAM ADDED",
) : ExtraNamChainEngine {
    var addedPath: String? = null
    val events = mutableListOf<String>()
    override fun addChainModel(path: String) = addResult.also { addedPath = path }
    override fun namBlockCount() = 4
    override fun setBypass(index: Int, bypass: Boolean) { events += "bypass:$index:$bypass" }
    override fun setInGain(index: Int, db: Float) { events += "in:$index:$db" }
    override fun setMix(index: Int, mix: Float) { events += "mix:$index:$mix" }
    override fun setGain(index: Int, db: Float) { events += "gain:$index:$db" }
    override fun setEqDb(index: Int, band: Int, db: Float) { events += "eq:$index:$band:$db" }
    override fun setEqPre(index: Int, pre: Boolean) { events += "eqPre:$index:$pre" }
    override fun setNormalize(index: Int, normalize: Boolean) { events += "normalize:$index:$normalize" }
    override fun setEqEnabled(index: Int, enabled: Boolean) { events += "eqEnabled:$index:$enabled" }
    override fun setQuality(index: Int, full: Boolean) = "QUALITY READY".also { events += "quality:$index:$full" }
    override fun start() = "AUDIO ACTIVE".also { events += "start" }
}

private class RecordingNamChainRebuildEngine(
    private val primaryResult: String = "MODEL LOADED",
) : NamChainRebuildEngine {
    var running = true
    val loadedPaths = mutableListOf<String>()
    val events = mutableListOf<String>()
    override fun isRunning() = running
    override fun clearChain() { events += "clear" }
    override fun loadPrimary(path: String) = primaryResult.also { loadedPaths += path }
    override fun addBlock(path: String) = "CHAIN NAM ADDED".also { loadedPaths += path }
    override fun setBlockBypass(index: Int, bypass: Boolean) { events += "bypass:$index:$bypass" }
    override fun setBlockGain(index: Int, db: Float) { events += "gain:$index:$db" }
    override fun setBlockInGain(index: Int, db: Float) { events += "in:$index:$db" }
    override fun setBlockMix(index: Int, mix: Float) { events += "mix:$index:$mix" }
    override fun setBlockEqDb(index: Int, band: Int, db: Float) { events += "eq:$index:$band:$db" }
    override fun setBlockEqPre(index: Int, pre: Boolean) { events += "eqPre:$index:$pre" }
    override fun setBlockEqEnabled(index: Int, enabled: Boolean) { events += "eqEnabled:$index:$enabled" }
    override fun setBlockNormalize(index: Int, enabled: Boolean) { events += "normalize:$index:$enabled" }
    override fun setBlockQuality(index: Int, full: Boolean) = "QUALITY READY".also { events += "quality:$index:$full" }
    override fun startChain() = "AUDIO ACTIVE".also { events += "start" }
}

private class FakeActiveToneRepository : ActiveToneRepository {
    var toneId: String? = null
    var model: OnlineModel? = null
    var moduleType: String? = null
    override fun save(toneId: String, toneTitle: String, model: OnlineModel, moduleType: String, file: File) {
        this.toneId = toneId
        this.model = model
        this.moduleType = moduleType
    }
    override fun clear() = Unit
}

private class RecordingPrimaryToneCaptureEngine(private val loadResult: String) : PrimaryToneCaptureEngine {
    val events = mutableListOf<String>()
    override fun loadModel(path: String) = loadResult.also { events += "load" }
    override fun applyModuleDefaults(moduleType: String) { events += "defaults:$moduleType" }
    override fun start() = "AUDIO ACTIVE".also { events += "start" }
}

private class FakePackageCaptures(
    private val cachedModels: List<OnlineModel> = emptyList(),
) : TonePackageCaptureRepository {
    var savedModels: List<OnlineModel>? = null
    override suspend fun save(toneId: String, moduleType: String, models: List<OnlineModel>) { savedModels = models }
    override suspend fun read(toneId: String, moduleType: String) = cachedModels
}

private class FakeFiles(private val target: File = File("pending.nam")) : ToneImportRepository {
    var prepared = false
    var writtenName: String? = null
    var writtenData: String? = null
    var writeCount = 0
    var currentCommit: File? = null
    var extraCommitCount = 0
    override fun writeLocalNam(originalName: String, encodedData: String) = ImportedNamFile(originalName, target).also { writtenName = originalName; writtenData = encodedData; writeCount++ }
    override fun preparePendingDownload(fileName: String) = target.also { prepared = true }
    override fun normalizeImpulseResponseWav(source: File) = source
    override fun commitCurrentModel(pendingFile: File) = pendingFile.also { currentCommit = it }
    override fun commitExtraModel(pendingFile: File, modelId: Long) = pendingFile.also { extraCommitCount++ }
}

private class FakePresets : PresetRepository {
    var activated: PresetData? = null
    override fun read(slot: Int): PresetData? = null
    override fun saveCurrent(slot: Int, namBypassFallback: Boolean) = ""
    override fun activate(preset: PresetData) { activated = preset }
    override fun label(slot: Int) = ""
    override fun rename(slot: Int, name: String) = false
    override fun delete(slot: Int) = false
    override fun move(slot: Int, delta: Int) = false
}

private class FakeEngine(private val result: String) : PresetAudioEngine {
    override fun switchPresetGapless(path: String, inputGainDb: Float, outputGainDb: Float, inputChannel: Int,
        outputPair: Int, gateEnabled: Boolean, gateThresholdDb: Float, eqLowDb: Float, eqMidDb: Float, eqHighDb: Float) = result
}

private fun testExtraNamEntry() = ExtraNamEntry(
    toneId = "tone", toneTitle = "Title", modelId = 1L, modelName = "Capture", size = "small",
    path = "/old.nam", bypass = false,
)

private fun testPreset() = PresetData(1, "/model.nam", "Model", "small", null, null, 0f, 0f, 0, 0,
    false, -65f, 0f, 0f, 0f, false, -15f, 0f, 1f, List(6) { 0f }, false, true, false, "[]",
    null, "", "", "IR", false, 0, 0f, 0f, 1f, false, List(6) { 0f })

private fun failure(block: () -> Unit): Throwable = try { block(); error("Expected failure") } catch (error: Throwable) { error }
private fun suspendFailure(block: suspend () -> Unit): Throwable = runBlocking {
    try { block(); error("Expected failure") } catch (error: Throwable) { error }
}
