package com.pedro.tone3000m1

import com.pedro.tone3000m1.ui.actions.PicoloActions
import com.pedro.tone3000m1.ui.model.PicoloUiState
import com.pedro.tone3000m1.ui.model.UiModule
import com.pedro.tone3000m1.ui.model.UiPreset
import com.pedro.tone3000m1.ui.model.PicoloStateSnapshot
import com.pedro.tone3000m1.ui.state.PicoloStateRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.roundToInt

private val PicoloBackground = Color(0xFF090D10)
private val PicoloSurface = Color(0xFF11171C)
private val PicoloSurfaceRaised = Color(0xFF171F25)
private val PicoloText = Color(0xFFF4F7F9)
private val PicoloSecondary = Color(0xFF9AA6AE)
private val PicoloOrange = Color(0xFFFF6A1A)
private val PicoloTeal = Color(0xFF23D6C5)
private val PicoloYellow = Color(0xFFFFC43D)
private val PicoloPurple = Color(0xFFA75AF2)
private val PicoloBlue = Color(0xFF3C9BFF)

private enum class PicoloPage(val title: String, val icon: Int) {
    PRESETS("Presets", R.drawable.ic_picolo_presets),
    EDITOR("Editor", R.drawable.ic_picolo_editor),
    FOOTSWITCH("Footswitch", R.drawable.ic_picolo_footswitch),
    SETTINGS("Settings", R.drawable.ic_picolo_settings)
}

internal class PicoloComposeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PicoloUiState())
    val state = mutableState.asStateFlow()
    private var previousBlocks: Long? = null
    private var previousProcessNs: Long? = null
    private var observationJob: Job? = null

    fun observe(repository: PicoloStateRepository) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observe().collect(::refresh)
        }
    }

    private fun refresh(snapshot: PicoloStateSnapshot) {
        val screenState = snapshot.state
        val stats = snapshot.stats
        val blocks = stats.metricLong("blocks")
        val totalProcessNs = stats.metricLong("totalProcessNs")
        val budgetUs = stats.metricFloat("budget")
        val blockDelta = blocks?.let { current -> previousBlocks?.let { current - it } }
        val processDeltaNs = totalProcessNs?.let { current -> previousProcessNs?.let { current - it } }
        val livePercent = if (blockDelta != null && blockDelta > 0 && processDeltaNs != null && processDeltaNs >= 0L && budgetUs != null && budgetUs > 0f) {
            (processDeltaNs / 1000.0 / (blockDelta * budgetUs) * 100.0).toFloat()
        } else null
        val liveAverageUs = if (blockDelta != null && blockDelta > 0 && processDeltaNs != null && processDeltaNs >= 0L) {
            (processDeltaNs / 1000.0 / blockDelta).toFloat()
        } else null
        if (blocks != null) previousBlocks = blocks
        if (totalProcessNs != null) previousProcessNs = totalProcessNs

        mutableState.update {
            screenState.copy(
                inputDbFs = stats.metricDbFs("capturePeak"),
                outputDbFs = stats.metricDbFs("postEqPeak"),
                processingPercent = livePercent ?: if (!it.running) null else it.processingPercent,
                processingAvgUs = liveAverageUs ?: if (!it.running) null else it.processingAvgUs,
                processingMaxUs = (stats.metricFloat("maxProcess") ?: 0f),
                processingBudgetUs = budgetUs ?: 0f,
                overBudgetCount = stats.metricLong("overBudget") ?: 0L,
                audioIoErrors = (stats.metricLong("captureErrors") ?: 0L) + (stats.metricLong("playbackErrors") ?: 0L),
            )
        }
    }
}

private fun String.metricLong(name: String): Long? =
    Regex("(?m)^${Regex.escape(name)}=([0-9]+)$").find(this)?.groupValues?.getOrNull(1)?.toLongOrNull()

private fun String.metricFloat(name: String): Float? =
    Regex("(?m)^${Regex.escape(name)}=([0-9]+(?:\\.[0-9]+)?)").find(this)?.groupValues?.getOrNull(1)?.toFloatOrNull()

private fun String.metricDbFs(name: String): Float? =
    Regex("(?m)^${Regex.escape(name)}=.*\\((-?[0-9]+(?:\\.[0-9]+)?) dBFS\\)")
        .find(this)?.groupValues?.getOrNull(1)?.toFloatOrNull()

@Composable
internal fun PicoloComposeApp(
    actions: PicoloActions,
    viewModel: PicoloComposeViewModel,
    onBrowse: (String) -> Unit,
) {
    var page by remember { mutableStateOf(PicoloPage.EDITOR) }
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(
        primary = PicoloOrange, onPrimary = PicoloBackground,
        secondary = PicoloTeal, onSecondary = PicoloBackground,
        background = PicoloBackground, onBackground = PicoloText,
        surface = PicoloSurface, onSurface = PicoloText,
        surfaceVariant = PicoloSurfaceRaised, onSurfaceVariant = PicoloSecondary,
    )) {
        Column(Modifier.fillMaxSize().background(PicoloBackground).statusBarsPadding()) {
            TopBar(
                state, actions,
                onOpenPresets = { page = PicoloPage.PRESETS },
                onOpenFootswitch = { page = PicoloPage.FOOTSWITCH },
                onOpenSettings = { page = PicoloPage.SETTINGS },
            )
            when (page) {
                PicoloPage.EDITOR -> EditorPage(state, actions, onBrowse, selectedModuleId, { selectedModuleId = it }, { page = PicoloPage.PRESETS }, Modifier.weight(1f))
                PicoloPage.PRESETS -> PresetsPage(state, actions, Modifier.weight(1f))
                PicoloPage.FOOTSWITCH -> FootswitchPage(state, Modifier.weight(1f))
                PicoloPage.SETTINGS -> SettingsPage(state, actions, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).background(PicoloSurface), verticalAlignment = Alignment.CenterVertically) {
                PicoloPage.entries.forEach { destination ->
                    val selected = page == destination
                    Column(
                        Modifier.weight(1f).height(64.dp).clickable { page = destination }.padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Image(
                            painter = painterResource(destination.icon),
                            contentDescription = destination.title,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(if (selected) PicoloOrange else PicoloSecondary),
                        )
                        Text(destination.title, color = if (selected) PicoloOrange else PicoloSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    state: PicoloUiState,
    actions: PicoloActions,
    onOpenPresets: () -> Unit,
    onOpenFootswitch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(PicoloSurface).height(60.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PicoloMark(Modifier.size(34.dp))
            Text("PicoloDSP", color = PicoloText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onOpenPresets) {
                Image(painterResource(R.drawable.ic_picolo_folder), contentDescription = "Presets", Modifier.size(24.dp), colorFilter = ColorFilter.tint(PicoloSecondary))
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) { Text("⋮", color = PicoloSecondary, fontSize = 22.sp) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.running) "Parar áudio" else "Iniciar áudio") },
                        onClick = { menuExpanded = false; if (state.running) actions.stopAudio() else actions.startAudio() },
                    )
                    DropdownMenuItem(text = { Text("Presets") }, onClick = { menuExpanded = false; onOpenPresets() })
                    DropdownMenuItem(text = { Text("Footswitch") }, onClick = { menuExpanded = false; onOpenFootswitch() })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menuExpanded = false; onOpenSettings() })
                }
            }
        }
    }
}

@Composable
private fun PicoloMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 3.2.dp.toPx()
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .12f, size.height * .90f)
            lineTo(size.width * .12f, size.height * .12f)
            lineTo(size.width * .72f, size.height * .12f)
            cubicTo(size.width * .98f, size.height * .12f, size.width * .98f, size.height * .56f, size.width * .72f, size.height * .56f)
            lineTo(size.width * .32f, size.height * .56f)
            lineTo(size.width * .32f, size.height * .39f)
            lineTo(size.width * .69f, size.height * .39f)
            cubicTo(size.width * .76f, size.height * .39f, size.width * .76f, size.height * .29f, size.width * .69f, size.height * .29f)
            lineTo(size.width * .29f, size.height * .29f)
            lineTo(size.width * .29f, size.height * .90f)
        }
        drawPath(p, PicoloOrange, style = Stroke(width = stroke, cap = StrokeCap.Square, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun EditorPage(
    state: PicoloUiState,
    actions: PicoloActions,
    onBrowse: (String) -> Unit,
    selectedModuleId: String?,
    onSelectModule: (String) -> Unit,
    onOpenPresets: () -> Unit,
    modifier: Modifier,
) {
    val selected = state.modules.firstOrNull { it.id == selectedModuleId } ?: state.modules.firstOrNull()
    val selectedChainId = if (selectedModuleId == "input" || selectedModuleId == "output") selectedModuleId else selected?.id
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PresetSelector(state, actions, onOpenPresets) }
        item { ProcessingMonitor(state) }
        item { SignalChain(state, selectedChainId, onSelectModule, onBrowse, actions) }
        if (selectedModuleId == "input") item { InputOutputCard(isInput = true, state = state, actions = actions) }
        else if (selectedModuleId == "output") item { InputOutputCard(isInput = false, state = state, actions = actions) }
        else if (selected != null) item { ModuleCard(selected, actions, state, onBrowse) }
        else item { EmptyChainCard(onBrowse) }
        item { GlobalControls(state, actions) }
        item { Text(state.status, color = PicoloSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
    }
}

@Composable
private fun ProcessingMonitor(state: PicoloUiState) {
    val load = (state.processingPercent ?: 0f).coerceIn(0f, 100f)
    val loadColor = when {
        load >= 90f -> Color(0xFFFF4D4D)
        load >= 70f -> PicoloYellow
        else -> PicoloTeal
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = PicoloSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PROCESSING", color = PicoloText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (state.running) "LIVE · 700 ms" else "ENGINE STOPPED", color = if (state.running) PicoloTeal else PicoloSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(if (state.running && state.processingPercent != null) String.format(Locale.US, "%.1f%%", load) else "—", color = loadColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("  DSP budget", color = PicoloSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(20) { index ->
                    val active = state.running && load >= (index + 1) * 5f
                    Box(Modifier.weight(1f).height(6.dp).background(if (active) loadColor else PicoloSurfaceRaised, RoundedCornerShape(3.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val avg = state.processingAvgUs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                val peak = String.format(Locale.US, "%.0f", state.processingMaxUs)
                Text("Block: $avg µs avg · $peak µs peak", color = PicoloSecondary, fontSize = 10.sp)
                Text("Budget ${String.format(Locale.US, "%.0f", state.processingBudgetUs)} µs", color = PicoloSecondary, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Over budget: ${state.overBudgetCount}", color = if (state.overBudgetCount > 0) PicoloYellow else PicoloSecondary, fontSize = 10.sp)
                Text("Audio I/O errors: ${state.audioIoErrors}", color = if (state.audioIoErrors > 0) Color(0xFFFF4D4D) else PicoloSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PresetSelector(state: PicoloUiState, actions: PicoloActions, onOpenPresets: () -> Unit) {
    val preset = state.presets.firstOrNull { it.slot == state.activePresetSlot && it.saved }
    Card(
        colors = CardDefaults.cardColors(containerColor = PicoloSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onOpenPresets),
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(preset?.slot?.toString()?.padStart(2, '0') ?: "—", color = PicoloText, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
            androidx.compose.foundation.layout.Box(Modifier.size(1.dp, 48.dp).background(PicoloSurfaceRaised))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(preset?.label ?: state.toneTitle.ifBlank { state.modelName }.take(26), color = PicoloText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (preset != null) "${state.modelName} · PRESET" else "CURRENT SESSION · NAM + FX + IR", color = PicoloSecondary, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = {
                val saved = state.presets.filter { it.saved }
                (saved.lastOrNull { it.slot < state.activePresetSlot } ?: saved.lastOrNull())?.let { actions.loadPreset(it.slot) }
            }) { Text("‹", color = PicoloText, fontSize = 24.sp) }
            IconButton(onClick = onOpenPresets) { Text("⋮", color = PicoloSecondary, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun SignalChain(
    state: PicoloUiState,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBrowse: (String) -> Unit,
    actions: PicoloActions,
) {
    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dropTargetIndex by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    val moduleStepPx = with(density) { 72.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SIGNAL CHAIN", color = PicoloText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("HOLD + DRAG TO MOVE", color = PicoloSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(12.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChainTile("IN", Color(0xFF68737A), selectedId == "input", null, onClick = { onSelect("input") })
                state.modules.forEachIndexed { moduleIndex, module ->
                    ChainConnector(highlighted = dropTargetIndex == moduleIndex)
                    val accent = moduleAccent(module, state)
                    val title = when (module.type) {
                        "FX_NATIVE" -> "NATIVE"
                        "CABINET_IR" -> if (state.cabinetType == "FX") "FX" else "IR"
                        "FX" -> "FX"
                        else -> if (module.moduleType == "PEDAL") "PEDAL" else "NAM"
                    }
                    val icon = when {
                        module.type == "NAM" && module.moduleType == "PEDAL" -> R.drawable.ic_picolo_drive
                        module.type == "NAM" -> R.drawable.ic_picolo_nam
                        module.type == "FX_NATIVE" -> R.drawable.ic_picolo_ir
                        else -> R.drawable.ic_picolo_ir
                    }
                    ChainTile(
                        label = title,
                        accent = accent,
                        selected = selectedId == module.id,
                        icon = icon,
                        onClick = { onSelect(module.id) },
                        isDragging = draggingBlockId == module.id,
                        dragOffsetX = if (draggingBlockId == module.id) dragOffsetX else 0f,
                        onDragStart = {
                            draggingBlockId = module.id
                            dragOffsetX = 0f
                            dropTargetIndex = moduleIndex
                        },
                        onDragDelta = { delta ->
                            if (draggingBlockId == module.id) {
                                dragOffsetX += delta
                                val targetIndex = (moduleIndex + (dragOffsetX / moduleStepPx).roundToInt())
                                    .coerceIn(0, state.modules.lastIndex)
                                // The placeholder still occupies the source slot, so shift
                                // right-side insertion markers by one to show the true landing spot.
                                dropTargetIndex = targetIndex + if (targetIndex > moduleIndex) 1 else 0
                            }
                        },
                        onDragEnd = {
                            val targetIndex = if (dropTargetIndex > moduleIndex) dropTargetIndex - 1 else dropTargetIndex
                            draggingBlockId = null
                            dragOffsetX = 0f
                            dropTargetIndex = -1
                            if (targetIndex >= 0 && targetIndex != moduleIndex) {
                                actions.moveModule(module.id, targetIndex - moduleIndex)
                            }
                        },
                        onDragCancel = {
                            draggingBlockId = null
                            dragOffsetX = 0f
                            dropTargetIndex = -1
                        },
                    )
                }
                ChainConnector(highlighted = dropTargetIndex == state.modules.size)
                androidx.compose.foundation.layout.Box {
                    var addMenuExpanded by remember { mutableStateOf(false) }
                    ChainTile("+", PicoloTeal, false, null, onClick = { addMenuExpanded = true })
                    DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Browse TONE3000…") }, onClick = { addMenuExpanded = false; onBrowse("add") })
                        DropdownMenuItem(text = { Text("FXNative · ChowMatrix Delay") }, onClick = { addMenuExpanded = false; actions.addFxNative(0) })
                        DropdownMenuItem(text = { Text("FXNative · BYOD BBD Delay") }, onClick = { addMenuExpanded = false; actions.addFxNative(1) })
                        DropdownMenuItem(text = { Text("FXNative · BYOD Smooth Reverb") }, onClick = { addMenuExpanded = false; actions.addFxNative(2) })
                        DropdownMenuItem(text = { Text("FXNative · BYOD Shimmer Reverb") }, onClick = { addMenuExpanded = false; actions.addFxNative(3) })
                    }
                }
                ChainConnector()
                ChainTile("OUT", Color(0xFF68737A), selectedId == "output", null, onClick = { onSelect("output") })
            }
        }
    }
}

@Composable
private fun ChainConnector(highlighted: Boolean = false) {
    Canvas(Modifier.size(width = 12.dp, height = 16.dp)) {
        drawLine(
            if (highlighted) PicoloOrange else Color(0xFF3A6662),
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            (if (highlighted) 3.dp else 1.5.dp).toPx(),
        )
    }
}

@Composable
private fun ChainTile(
    label: String,
    accent: Color,
    selected: Boolean,
    icon: Int?,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    dragOffsetX: Float = 0f,
    onDragStart: (() -> Unit)? = null,
    onDragDelta: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val startNow by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val deltaNow by androidx.compose.runtime.rememberUpdatedState(onDragDelta)
    val endNow by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val cancelNow by androidx.compose.runtime.rememberUpdatedState(onDragCancel)
    val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "blockLift")
    val tileWidth = if (label == "OUT") 48.dp else 54.dp
    val dragModifier = if (onDragStart == null || label == "NATIVE") Modifier else Modifier.pointerInput(label) {
        detectDragGesturesAfterLongPress(
            onDragStart = { startNow?.invoke() },
            onDragEnd = { endNow?.invoke() },
            onDragCancel = { cancelNow?.invoke() },
        ) { change, dragAmount ->
            change.consume()
            deltaNow?.invoke(dragAmount.x)
        }
    }
    Box(Modifier.size(width = tileWidth, height = 64.dp), contentAlignment = Alignment.Center) {
        if (isDragging) {
            Box(
                Modifier.fillMaxSize()
                    .background(accent.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("···", color = accent.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val liftPx = with(density) { 13.dp.toPx() } * lift
                    translationX = if (isDragging) dragOffsetX else 0f
                    translationY = -liftPx
                    scaleX = 1f + (0.08f * lift)
                    scaleY = 1f + (0.08f * lift)
                    shadowElevation = with(density) { 16.dp.toPx() } * lift
                    clip = false
                }
                .background(accent.copy(alpha = if (selected) 0.22f else 0.10f), RoundedCornerShape(8.dp))
                .border(1.dp, if (isDragging) PicoloOrange else if (selected) accent else accent.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == null) Text(label, color = accent, fontSize = if (label == "+") 20.sp else 11.sp, fontWeight = FontWeight.Bold)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Image(painterResource(icon), contentDescription = null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(accent))
                Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Text(title, color = PicoloText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(subtitle, color = PicoloSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyChainCard(onBrowse: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).clickable { onBrowse("replace") }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.ic_picolo_nam), null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(PicoloOrange))
            Column(Modifier.weight(1f)) {
                Text("No modules loaded", color = PicoloText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Tap + in the chain to add NAM, FX or IR", color = PicoloSecondary, fontSize = 12.sp)
            }
            Text("›", color = PicoloSecondary, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ModuleCard(module: UiModule, actions: PicoloActions, state: PicoloUiState, onBrowse: (String) -> Unit) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    var menuExpanded by remember(module.id) { mutableStateOf(false) }
    val color = moduleAccent(module, state)
    Card(
        colors = CardDefaults.cardColors(containerColor = when {
            module.type == "NAM" && module.moduleType == "PEDAL" -> Color(0xFF0D302D)
            module.type == "NAM" -> Color(0xFF21150F)
            module.type == "FX_NATIVE" -> Color(0xFF10201F)
                    module.type == "CABINET_IR" && state.cabinetType != "FX" -> Color(0xFF211D0C)
            else -> Color(0xFF10201F)
        }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painterResource(when {
                            module.type == "NAM" && module.moduleType == "PEDAL" -> R.drawable.ic_picolo_drive
                            module.type == "NAM" -> R.drawable.ic_picolo_nam
                            module.type == "FX_NATIVE" -> R.drawable.ic_picolo_ir
                            else -> R.drawable.ic_picolo_ir
                        }),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(color),
                    )
                    Column {
                        Text(when (module.type) {
                            "CABINET_IR" -> if (state.cabinetType == "FX") "FX / SPACE" else "IR / CABINET"
                            "FX_NATIVE" -> "FX NATIVE · CHOW"
                            "FX" -> "FX / SPACE"
                            else -> if (module.moduleType == "PEDAL") "DRIVE" else "NAM"
                        }, color = PicoloText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val enabled = !module.bypass
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        when (module.type) {
                            "CABINET_IR" -> actions.setCabinetBypass(enabled)
                            "FX_NATIVE" -> actions.setFxNativeBypass(module.index, enabled)
                            "FX" -> actions.setFxBypass(module.index, enabled)
                            else -> actions.setNamBypass(module.index, enabled)
                        }
                    }) {
                        Image(
                            painterResource(R.drawable.ic_picolo_power),
                            contentDescription = if (enabled) "Bypass" else "Enable",
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(if (enabled) color else PicoloSecondary),
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuExpanded = true }) { Text("⋮", color = PicoloSecondary, fontSize = 20.sp) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Move left") }, onClick = {
                                menuExpanded = false
                                actions.moveModule(module.id, -1)
                            })
                            DropdownMenuItem(text = { Text("Move right") }, onClick = {
                                menuExpanded = false
                                actions.moveModule(module.id, 1)
                            })
                            if (module.moduleType != "PEDAL") {
                                DropdownMenuItem(text = { Text("Advanced parameters") }, onClick = { menuExpanded = false; expanded = !expanded })
                            }
                            if (module.type != "FX_NATIVE") DropdownMenuItem(text = { Text("Replace") }, onClick = {
                                menuExpanded = false
                                onBrowse(if (module.type == "FX") "replace-fx:${module.index}" else "replace:${module.index}")
                            })
                            DropdownMenuItem(text = { Text("Remove") }, onClick = {
                                menuExpanded = false
                                when (module.type) {
                                    "CABINET_IR" -> actions.removeCabinetIr()
                                    "FX_NATIVE" -> actions.removeFxNative(module.index)
                                    "FX" -> actions.removeFx(module.index)
                                    else -> actions.removeNam(module.index)
                                }
                            })
                        }
                    }
                }
            }
            if (module.type == "CABINET_IR") {
                ModelSelector(module.name, "Captures from this package", color) { actions.selectPackageCaptures(module.id) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RotaryKnob("INPUT", state.cabinetInGain, -24f..24f, "dB", color) { actions.setCabinetInGain(it.toDouble()) }
                    RotaryKnob("OUTPUT", state.cabinetOutGain, -24f..12f, "dB", color) { actions.setCabinetOutGain(it.toDouble()) }
                    RotaryKnob("MIX", state.cabinetMix, 0f..1f, "%", color) { actions.setCabinetMix(it.toDouble()) }
                }
            } else if (module.type == "FX") {
                ModelSelector(module.name, "Captures from this package", color) { actions.selectPackageCaptures(module.id) }
                RotaryKnob("MIX", module.mix, 0f..1f, "%", color) { actions.setFxMix(module.index, it.toDouble()) }
            } else if (module.type == "FX_NATIVE") {
                val effects = listOf("ChowMatrix Delay", "BYOD BBD Delay", "BYOD Smooth Reverb", "BYOD Shimmer Reverb")
                var effectMenuExpanded by remember(module.id) { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box {
                    ModelSelector(effects.getOrElse(module.nativeEffect) { effects.first() }, "STEREO · AFTER NAM / CAB", color) { effectMenuExpanded = true }
                    DropdownMenu(expanded = effectMenuExpanded, onDismissRequest = { effectMenuExpanded = false }) {
                        effects.forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { effectMenuExpanded = false; actions.setFxNativeType(module.index, index) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RotaryKnob("MIX", module.mix, 0f..1f, "%", color) { actions.setFxNativeMix(module.index, it.toDouble()) }
                    RotaryKnob(when (module.nativeEffect) { 0, 1 -> "TIME"; 2 -> "DECAY"; else -> "SIZE" }, module.nativeParam1,
                        when (module.nativeEffect) { 0, 1 -> 20f..2000f; 2 -> 500f..5000f; else -> 50f..250f },
                        if (module.nativeEffect == 2) "ms" else "ms", color) {
                        actions.setFxNativeParameter(module.index, 0, it.toDouble())
                    }
                    RotaryKnob(when (module.nativeEffect) { 0, 1 -> "FEEDBACK"; 2 -> "RELAX"; else -> "DECAY" }, module.nativeParam2,
                        if (module.nativeEffect < 2) 0f..0.94f else if (module.nativeEffect == 2) 0f..1f else 1000f..10000f,
                        if (module.nativeEffect == 3) "ms" else "", color) {
                        actions.setFxNativeParameter(module.index, 1, it.toDouble())
                    }
                    if (module.nativeEffect == 3) RotaryKnob("SHIFT", module.nativeParam3, -12f..12f, "st", color) {
                        actions.setFxNativeParameter(module.index, 2, it.toDouble())
                    }
                }
            } else if (module.moduleType == "PEDAL") {
                ModelSelector(module.name, "SELECT CAPTURE", color) { actions.selectPackageCaptures(module.id) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RotaryKnob("DRIVE", ((module.inGainDb / 2.4f).coerceIn(0f, 10f)), 0f..10f, "", Color(0xFFFF5266)) {
                        actions.setNamInGain(module.index, (it * 2.4f).toDouble())
                    }
                    RotaryKnob("BASS", module.eqBands[0], -12f..12f, "dB", Color(0xFFD6E0E4)) {
                        actions.setNamEq(module.index, 0, it.toDouble())
                        actions.setNamEqEnabled(module.index, true)
                    }
                    RotaryKnob("MIDDLE", module.eqBands[1], -12f..12f, "dB", Color(0xFFD6E0E4)) {
                        actions.setNamEq(module.index, 1, it.toDouble())
                        actions.setNamEqEnabled(module.index, true)
                    }
                    RotaryKnob("TREBLE", module.eqBands[2], -12f..12f, "dB", Color(0xFFD6E0E4)) {
                        actions.setNamEq(module.index, 2, it.toDouble())
                        actions.setNamEqEnabled(module.index, true)
                    }
                    RotaryKnob("LEVEL", (((module.gainDb + 24f) / 36f) * 10f).coerceIn(0f, 10f), 0f..10f, "", PicoloBlue) {
                        actions.setNamGain(module.index, ((it / 10f) * 36f - 24f).toDouble())
                    }
                }
            } else {
                ModelSelector(module.name, "Captures from this package", color) { actions.selectPackageCaptures(module.id) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RotaryKnob("GAIN", module.inGainDb, -24f..24f, "dB", color) { actions.setNamInGain(module.index, it.toDouble()) }
                    RotaryKnob("BASS", module.eqBands[0], -12f..12f, "dB", color) { actions.setNamEq(module.index, 0, it.toDouble()) }
                    RotaryKnob("MIDDLE", module.eqBands[1], -12f..12f, "dB", color) { actions.setNamEq(module.index, 1, it.toDouble()) }
                    RotaryKnob("TREBLE", module.eqBands[2], -12f..12f, "dB", color) { actions.setNamEq(module.index, 2, it.toDouble()) }
                    RotaryKnob("LEVEL", module.gainDb, -24f..12f, "dB", color) { actions.setNamGain(module.index, it.toDouble()) }
                }
            }
            if (expanded) {
                if (module.type == "CABINET_IR") {
                    ToggleRow("EQ", state.cabinetEqEnabled, PicoloPurple) { actions.setCabinetEqEnabled(it) }
                    if (state.cabinetEqEnabled) state.cabinetEq.forEachIndexed { band, value ->
                        ValueSlider(listOf("LOW", "LOW-MID", "MID", "HIGH-MID", "PRESENCE", "HIGH")[band], value, -12f..12f, "dB") { actions.setCabinetEq(band, it.toDouble()) }
                    }
                } else if (module.type == "FX") {
                    Text("Space IR convolution", color = PicoloSecondary, fontSize = 12.sp)
                } else if (module.moduleType != "PEDAL") {
                    RotaryKnob("MIX", module.mix, 0f..1f, "%", color) { actions.setNamMix(module.index, it.toDouble()) }
                    ToggleRow("EQ", module.eqEnabled, PicoloPurple) { actions.setNamEqEnabled(module.index, it) }
                    ToggleRow("NORMALIZE", module.normalize, PicoloTeal) { actions.setNamNormalize(module.index, it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (module.eqPre) "EQ PRE ✓" else "EQ PRE", modifier = Modifier.clickable { actions.setNamEqPosition(module.index, true) }.padding(8.dp), color = PicoloSecondary)
                        Text(if (!module.eqPre) "EQ POST ✓" else "EQ POST", modifier = Modifier.clickable { actions.setNamEqPosition(module.index, false) }.padding(8.dp), color = PicoloSecondary)
                        Text(if (module.a2Full) "A2 FULL ✓" else "A2 LITE ✓", modifier = Modifier.clickable { actions.setNamQuality(module.index, !module.a2Full) }.padding(8.dp), color = PicoloSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelector(name: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp)
            .background(PicoloSurfaceRaised, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = .45f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { "Selecionar modelo" }, color = PicoloText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = PicoloSecondary, fontSize = 11.sp, maxLines = 1)
        }
        Text("⌄", color = PicoloText, fontSize = 22.sp)
    }
}

private fun moduleAccent(module: UiModule, state: PicoloUiState): Color = when {
    module.type == "NAM" && module.moduleType == "PEDAL" -> PicoloTeal
    module.type == "FX_NATIVE" -> PicoloTeal
    module.type == "FX" -> PicoloBlue
    module.type == "CABINET_IR" && state.cabinetType == "FX" -> PicoloBlue
    module.type == "CABINET_IR" -> PicoloYellow
    module.type == "NAM" -> PicoloOrange
    else -> PicoloTeal
}

@Composable
private fun RotaryKnob(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, accent: Color, onValueChange: (Float) -> Unit) {
    val callbackNow by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    var previewValue by remember(range) { mutableStateOf(value) }
    LaunchedEffect(value) { previewValue = value }
    val displayValue = if (unit == "%") String.format(Locale.US, "%.0f", previewValue * 100f) else String.format(Locale.US, "%.1f", previewValue)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 1.dp)) {
        Canvas(Modifier.size(60.dp).pointerInput(range) {
            detectDragGestures { change, drag ->
                change.consume()
                val step = (range.endInclusive - range.start) / 130f
                previewValue = (previewValue - drag.y * step).coerceIn(range)
                callbackNow(previewValue)
            }
        }) {
            val stroke = 4.dp.toPx()
            val diameter = size.minDimension - stroke * 2
            val offset = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            drawArc(Color(0xFF303940), 135f, 270f, false, offset, arcSize, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val progress = ((previewValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            drawArc(accent, 135f, 270f * progress, false, offset, arcSize, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(PicoloSurfaceRaised, radius = diameter * 0.34f, center = center)
            val angle = Math.toRadians(135.0 + 270.0 * progress)
            val radius = diameter * 0.25f
            drawLine(accent, center, Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Text(label, color = PicoloText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(if (unit.isBlank()) displayValue else "$displayValue $unit", color = PicoloSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun GlobalControls(state: PicoloUiState, actions: PicoloActions) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SIGNAL METERS", fontWeight = FontWeight.Bold, color = PicoloText, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().height(94.dp), verticalAlignment = Alignment.CenterVertically) {
                MeterChannel("INPUT", if (state.running) state.inputDbFs else null, Modifier.weight(1f))
                Spacer(Modifier.size(1.dp, 62.dp).background(PicoloSurfaceRaised))
                MeterChannel("OUTPUT", if (state.running) state.outputDbFs else null, Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("GATE / GLOBAL EQ", fontWeight = FontWeight.Bold, color = PicoloSecondary, fontSize = 12.sp)
                Text(if (expanded) "⌃" else "⌄", color = PicoloSecondary, fontSize = 20.sp)
            }
            if (expanded) {
                ToggleRow("GATE", state.gateEnabled, PicoloTeal) { actions.setGateEnabled(it) }
                if (state.gateEnabled) ValueSlider("THRESHOLD", state.gateThreshold, -90f..-20f, "dB") { actions.setGateThreshold(it.toDouble()) }
                ToggleRow("3-BAND EQ", state.eqEnabled, PicoloPurple) { actions.setEqEnabled(it) }
                if (state.eqEnabled) {
                    ValueSlider("LOW", state.eqLow, -12f..12f, "dB") { actions.setEqLow(it.toDouble()) }
                    ValueSlider("MID", state.eqMid, -12f..12f, "dB") { actions.setEqMid(it.toDouble()) }
                    ValueSlider("HIGH", state.eqHigh, -12f..12f, "dB") { actions.setEqHigh(it.toDouble()) }
                }
            }
        }
    }
}

@Composable
private fun InputOutputCard(isInput: Boolean, state: PicoloUiState, actions: PicoloActions) {
    val accent = if (isInput) PicoloTeal else PicoloBlue
    val label = if (isInput) "INPUT" else "OUTPUT"
    val gain = if (isInput) state.inputGain else state.outputGain
    val meter = if (isInput) state.inputDbFs else state.outputDbFs
    Card(
        colors = CardDefaults.cardColors(containerColor = PicoloSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, accent.copy(alpha = .65f), RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$label · LEVEL", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            MeterChannel("${label} METER", if (state.running) meter else null)
            ValueSlider(
                "$label GAIN",
                gain,
                if (isInput) -24f..24f else -24f..12f,
                "dB",
            ) { value ->
                if (isInput) actions.setInputGain(value.toDouble()) else actions.setOutputGain(value.toDouble())
            }
        }
    }
}

@Composable
private fun MeterChannel(label: String, dbFs: Float?, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, color = PicoloText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val activeSegments = dbFs?.let { (((it + 60f) / 60f) * 12f).toInt().coerceIn(0, 12) } ?: 0
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(Color(0xFF080B0D), RoundedCornerShape(3.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(12) { index ->
                val color = when {
                    index >= activeSegments -> Color(0xFF20282D)
                    index >= 11 -> Color(0xFFFF4D5E)
                    index >= 9 -> PicoloYellow
                    else -> PicoloTeal
                }
                Spacer(Modifier.weight(1f).height(16.dp).background(color, RoundedCornerShape(1.dp)))
            }
        }
        val readout = dbFs?.let { String.format(Locale.US, "%.1f dBFS", it) } ?: "— dBFS"
        Text(readout, color = PicoloText, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(8.dp), shape = CircleShape, color = accent) {}
            Text(label, color = PicoloText, fontWeight = FontWeight.SemiBold)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onValueChange: (Float) -> Unit) {
    var dragging by remember(label) { mutableStateOf(value) }
    LaunchedEffect(value) { if (kotlin.math.abs(dragging - value) > 0.02f) dragging = value }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = PicoloSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val shown = if (unit == "%") dragging * 100f else dragging
            val format = if (unit == "%") "%.0f %s" else "%.1f %s"
            Text(String.format(Locale.US, format, shown, unit), color = PicoloText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Slider(value = dragging.coerceIn(range), onValueChange = { dragging = it; onValueChange(it) }, valueRange = range)
    }
}

@Composable
private fun PresetsPage(state: PicoloUiState, actions: PicoloActions, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    val visiblePresets = state.presets.filter { preset ->
        query.isBlank() || (preset.saved && preset.label.contains(query, ignoreCase = true))
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Presets", color = PicoloText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search presets…", color = PicoloSecondary) },
                leadingIcon = {
                    Image(painterResource(R.drawable.ic_picolo_search), contentDescription = null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(PicoloSecondary))
                },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PicoloSurface,
                    unfocusedContainerColor = PicoloSurface,
                    focusedTextColor = PicoloText,
                    unfocusedTextColor = PicoloText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PicoloOrange,
                ),
            )
        }
        items(visiblePresets, key = { it.slot }) { preset ->
            var menuExpanded by remember(preset.slot) { mutableStateOf(false) }
            val active = preset.saved && preset.slot == state.activePresetSlot
            Card(
                colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFF21150F) else PicoloSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .then(if (active) Modifier.border(1.dp, PicoloOrange, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { if (preset.saved) actions.loadPreset(preset.slot) else actions.savePreset(preset.slot) },
            ) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.slot.toString().padStart(2, '0'), color = if (active) PicoloOrange else PicoloText, fontSize = 16.sp, modifier = Modifier.padding(end = 14.dp))
                    Spacer(Modifier.size(1.dp, 44.dp).background(PicoloSurfaceRaised))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(preset.label, color = PicoloText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                        Text(if (preset.saved) "NAM · FX · IR" else "Empty slot · Tap to save current sound", color = PicoloSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                    if (!preset.saved) Text("SAVE", color = PicoloOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    else androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuExpanded = true }) { Text("⋮", color = PicoloSecondary, fontSize = 20.sp) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Load") }, onClick = { menuExpanded = false; actions.loadPreset(preset.slot) })
                            DropdownMenuItem(text = { Text("Overwrite") }, onClick = { menuExpanded = false; actions.savePreset(preset.slot) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FootswitchPage(state: PicoloUiState, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Footswitch", color = PicoloText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.ic_picolo_footswitch), contentDescription = null, Modifier.size(30.dp), colorFilter = ColorFilter.tint(PicoloTeal))
                    Column(Modifier.weight(1f)) {
                        Text("MIDI Controller", color = PicoloText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("MIDI discovery unavailable", color = PicoloSecondary, fontSize = 13.sp)
                    }
                    Text("DISCONNECTED", color = PicoloSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("MAPPINGS", color = PicoloSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(listOf("Preset Down", "Preset Up", "Toggle Tuner").mapIndexed { index, label -> ("ABC"[index].toString() to label) }) { (button, action) ->
            Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(36.dp), shape = RoundedCornerShape(7.dp), color = PicoloSurfaceRaised) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text(button, color = PicoloText, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(action, Modifier.weight(1f).padding(start = 12.dp), color = PicoloText, fontSize = 14.sp)
                    Text("—", color = PicoloSecondary, fontSize = 14.sp)
                }
            }
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = PicoloSurfaceRaised, disabledContentColor = PicoloSecondary)) {
                Text("MIDI LEARN · NOT AVAILABLE")
            }
        }
        item {
            Text(if (state.running) "Audio engine active" else "Audio engine stopped", color = if (state.running) PicoloTeal else PicoloSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsPage(state: PicoloUiState, actions: PicoloActions, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", color = PicoloText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
        item {
            SettingsGroup("Audio") {
                SettingsRow("Audio device", state.device)
                SettingsRow("Routing", state.routing.ifBlank { "Automatic" })
                SettingsRow("Sample rate", "48 kHz target")
                SettingsRow("Engine", if (state.running) "Running" else "Stopped")
            }
        }
        item {
            SettingsGroup("MIDI") {
                SettingsRow("MIDI devices", "Not available in this build")
            }
        }
        item {
            SettingsGroup("Appearance") {
                SettingsRow("Theme", "Dark")
                SettingsRow("Accent color", "Picolo Orange")
            }
        }
        item {
            SettingsGroup("About") {
                SettingsRow("PicoloDSP", "Open DSP · Native Android")
                SettingsRow("Audio engine", "TinyALSA · 48 kHz")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.scanUsbAudio() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("SCAN USB") }
                Button(onClick = { actions.cycleOutput() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("CYCLE OUTPUT") }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PicoloSurface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(title, color = PicoloText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PicoloSecondary, fontSize = 13.sp)
        Text(value, color = PicoloText, fontSize = 13.sp, maxLines = 1)
    }
}
