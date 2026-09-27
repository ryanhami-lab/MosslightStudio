package com.ryanhami.mosslightstudio

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryanhami.mosslightstudio.core.*
import com.ryanhami.mosslightstudio.data.SaveProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Paper = Color(0xFFF5F0E5)
private val Ink = Color(0xFF183E38)
private val Moss = Color(0xFF44634A)
private val Muted = Color(0xFF586B5B)
private val Line = Color(0xFFD4DCCB)
private val Cream = Color(0xFFFFFCF3)
private val Clay = Color(0xFFD97C60)

@Composable
fun StudioApp(model: StudioViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val shortWindow = configuration.screenHeightDp < 680
    val narrowLargeText = configuration.screenWidthDp < 360 && LocalDensity.current.fontScale > 1.3f
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Moss, background = Paper,
        surface = Cream, onSurface = Ink, onPrimary = Cream, secondaryContainer = Color(0xFFDCE6CD),
        onSecondaryContainer = Ink, surfaceVariant = Color(0xFFE6E8DC), onSurfaceVariant = Muted,
        outline = Color(0xFF7A8D78), outlineVariant = Line)) {
        val context = LocalContext.current
        val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.ui.value.shareRequest?.let { model.shareFinished(it.id, unavailable = false) }
        }
        LaunchedEffect(state.shareRequest?.id) {
            val request = state.shareRequest ?: return@LaunchedEffect
            if (!model.shouldPrepareShare(request.id)) return@LaunchedEffect
            try {
                val intent = PrintExport.prepare(context.applicationContext, request.design)
                if (model.claimShare(request.id)) share.launch(Intent.createChooser(intent, "Share your print"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { model.shareFinished(request.id, unavailable = true) }
        }
        BackHandler(state.panel != Panel.ROOM && !state.saving && state.shareRequest == null) { model.open(Panel.ROOM) }
        Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            BrandHeader(state.panel)
            Spacer(Modifier.height(14.dp))
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Moss) }
                state.problem != null -> Recovery(state, model)
                state.panel == Panel.PRINT -> PrintPanel(state, model, Modifier.weight(1f))
                else -> {
                    val room = model.session
                    if (room != null) {
                      Column(Modifier.weight(1f).then(if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        val sceneSize = if (shortWindow) Modifier.height(if (state.panel == Panel.ARRANGE) 180.dp else 240.dp) else Modifier.weight(1f)
                        Box(sceneSize.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink).border(1.dp, Line, RoundedCornerShape(22.dp))) {
                            key(room) { AndroidView(factory = { StudioGameView(it, room, model::preview) }, modifier = Modifier.fillMaxSize()) }
                        }
                        if (narrowLargeText) {
                            Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
                                Text("THE CONSERVATORY", Modifier.fillMaxWidth(), fontSize = 10.sp, letterSpacing = 1.2.sp,
                                    color = Muted, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                Text("${room.layout.placements.size} / 6 pieces", Modifier.align(Alignment.End).padding(top = 2.dp),
                                    fontSize = 11.sp, color = Muted)
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("THE CONSERVATORY", Modifier.weight(1f).padding(end = 8.dp), fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Bold)
                                Text("${room.layout.placements.size} / 6 pieces", fontSize = 11.sp, color = Muted)
                            }
                        }
                        if (state.panel == Panel.ARRANGE) ArrangePanel(state, model)
                        else RoomControls(state, model, stackActions = narrowLargeText)
                      }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(panel: Panel) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val compact = panel == Panel.PRINT || LocalConfiguration.current.screenHeightDp < 680
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp)) {
                    drawRect(Moss, Offset(size.width * .44f, size.height * .38f), Size(size.width * .12f, size.height * .6f))
                    drawOval(Moss, Offset(0f, size.height * .1f), Size(size.width * .5f, size.height * .38f))
                    drawOval(Clay, Offset(size.width * .5f, 0f), Size(size.width * .5f, size.height * .38f))
                }
                Spacer(Modifier.width(8.dp))
                Text("Mosslight", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = if (compact) 24.sp else 30.sp, color = Ink)
            }
            if (!compact) Text("S T U D I O", Modifier.padding(start = 28.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Moss)
        }
        if (!largeText) Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFFE5E9DA)) {
            Text(if (panel == Panel.PRINT) "PRINT DESK" else "A QUIET SPACE", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Moss)
        }
    }
    if (!compact) Text("A little room to make your own.", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Muted)
}

@Composable
private fun RoomControls(state: StudioUi, model: StudioViewModel, stackActions: Boolean) {
    val room = model.session ?: return
    Column(Modifier.heightIn(max = if (stackActions) 320.dp else 240.dp).verticalScroll(rememberScrollState())) {
        if (stackActions) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DirectionPad(room)
                RoomActionButtons(model, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DirectionPad(room)
                RoomActionButtons(model, Modifier.weight(1f))
            }
        }
        Text(state.status, Modifier.fillMaxWidth().padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, textAlign = TextAlign.Center, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun DirectionPad(room: StudioSession) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Direction("↑", "Move up", 0.0, -1.0, room)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Direction("←", "Move left", -1.0, 0.0, room)
            Direction("↓", "Move down", 0.0, 1.0, room)
            Direction("→", "Move right", 1.0, 0.0, room)
        }
    }
}

@Composable
private fun RoomActionButtons(model: StudioViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ model.open(Panel.ARRANGE) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Arrange", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton({ model.open(Panel.PRINT) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Make a print", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Direction(symbol: String, label: String, x: Double, y: Double, room: StudioSession) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE6E8DC))
        .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button; onClick { room.step(x, y); true } }
        .onKeyEvent { event ->
            if (event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.DirectionCenter) {
                if (event.type == KeyEventType.KeyUp) room.step(x, y)
                true
            } else false
        }.focusable()
        .pointerInput(room, x, y) {
            detectTapGestures(onPress = {
                coroutineScope {
                    var held = false
                    val job = launch { delay(130); held = true; room.move(x, y) }
                    try {
                        val released = tryAwaitRelease()
                        if (released && !held) room.step(x, y)
                    } finally { job.cancel(); room.neutralize() }
                }
            })
        }, contentAlignment = Alignment.Center) {
        Text(symbol, fontSize = 24.sp, color = Ink)
    }
}

@Composable
private fun ArrangePanel(state: StudioUi, model: StudioViewModel) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().heightIn(max = 345.dp).clip(RoundedCornerShape(20.dp)).background(Cream)
        .semantics { paneTitle = "Arrange the conservatory" }.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (largeText) "Arrange" else "Make room for a little green", Modifier.weight(1f).semantics { heading() }, fontFamily = FontFamily.Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            TextButton({ model.open(Panel.ROOM) }, enabled = !state.saving) { Text("Done") }
        }
        DecorItem.entries.chunked(if (largeText) 2 else 3).forEach { items ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    FilterChip(selected = state.selected == item, onClick = { model.select(item) }, enabled = !state.saving,
                        label = { Text(item.label, fontSize = 11.sp) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Nudge("←", "Preview left", !state.saving) { model.nudge(-1, 0) }
            Nudge("↑", "Preview up", !state.saving) { model.nudge(0, -1) }
            Text("Col ${state.preview.column - 1}\nRow ${state.preview.row - 6}", textAlign = TextAlign.Center, fontSize = 11.sp, modifier = Modifier.semantics { contentDescription = "Column ${state.preview.column - 1}, row ${state.preview.row - 6}" })
            Nudge("↓", "Preview down", !state.saving) { model.nudge(0, 1) }
            Nudge("→", "Preview right", !state.saving) { model.nudge(1, 0) }
        }
        Text(state.placementProblem ?: "Tap a floor tile or use the arrows.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, color = if (state.placementProblem != null) Color(0xFF974B37) else Muted)
        if (largeText) Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(model::putAway, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.saving) { Text("Put away") }
            Button(model::place, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.saving && state.placementProblem == null) { Text(if (state.saving) "Saving…" else "Place here") }
        } else Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(model::putAway, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !state.saving) { Text("Put away") }
            Button(model::place, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !state.saving && state.placementProblem == null) { Text(if (state.saving) "Saving…" else "Place here") }
        }
        Text(state.status, Modifier.padding(top = 5.dp).semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun Nudge(symbol: String, label: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(action, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label }, enabled = enabled, contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp)) { Text(symbol, fontSize = 18.sp) }
}

@Composable
private fun PrintPanel(state: StudioUi, model: StudioViewModel, modifier: Modifier) {
    val busy = state.shareRequest != null
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth().semantics { paneTitle = "Print desk" }.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Botanical impressions", Modifier.weight(1f).semantics { heading() }, fontFamily = FontFamily.Serif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton({ model.open(Panel.ROOM) }, enabled = !busy) { Text("Done") }
        }
        Text("Choose a stamp, then touch the paper.", Modifier.fillMaxWidth().padding(bottom = 8.dp), fontSize = 13.sp, color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stamp.entries.forEach { stamp ->
                FilterChip(state.stamp == stamp, { model.selectStamp(stamp) }, label = { Text(stamp.label) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.width(352.dp).aspectRatio(PrintPixels.WIDTH.toFloat() / PrintPixels.HEIGHT)) {
                val unit = maxWidth / PrintPixels.WIDTH
                val blocks = remember(state.print) { PrintPixels.compose(state.print) }
                Canvas(Modifier.fillMaxSize()) {
                    val s = size.width / PrintPixels.WIDTH
                    for (block in blocks) drawRect(Color(block.argb), Offset(block.x * s, block.y * s), Size(block.width * s, block.height * s))
                }
                for (row in 0 until 6) for (column in 0 until 6) {
                    val mark = state.print.marks.find { it.column == column && it.row == row }
                    Box(Modifier.offset(unit * (12 + column * 20), unit * (36 + row * 20)).size(unit * 20)
                        .semantics { contentDescription = "Print cell ${column + 1}, ${row + 1}, ${mark?.stamp?.label ?: "empty"}" }
                        .clickable(enabled = !busy, role = Role.Button) { model.stampAt(column, row) })
                }
            }
        }
        if (largeText) Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(model::undoPrint, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && state.print.canUndo) { Text("Undo") }
            OutlinedButton(model::clearPrint, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && state.print.marks.isNotEmpty()) { Text("Clear") }
            Button(model::requestShare, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && state.print.marks.isNotEmpty()) { Text(if (busy) "Preparing…" else "Share PNG") }
        } else Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(model::undoPrint, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy && state.print.canUndo) { Text("Undo") }
            OutlinedButton(model::clearPrint, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy && state.print.marks.isNotEmpty()) { Text("Clear") }
            Button(model::requestShare, Modifier.weight(1.3f).heightIn(min = 48.dp), enabled = !busy && state.print.marks.isNotEmpty()) { Text(if (busy) "Preparing…" else "Share PNG") }
        }
        Text("Shares only this image. Drafts aren't saved.", Modifier.padding(top = 10.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = Muted)
        Text(state.status, Modifier.padding(vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun Recovery(state: StudioUi, model: StudioViewModel) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 32.dp).semantics { paneTitle = "Saved layout recovery" }) {
        Text("Your layout is being kept safe", Modifier.semantics { heading() }, fontFamily = FontFamily.Serif, fontSize = 26.sp)
        Text(when (state.problem) {
            SaveProblem.FUTURE_VERSION -> "This layout was made with a newer version. Update the app or retry. It has not been replaced."
            SaveProblem.UNREADABLE -> "The saved layout could not be read. Retry, or deliberately start a new room. The existing file has not been changed."
            else -> "Storage is temporarily unavailable. Retry when it is available."
        }, Modifier.padding(vertical = 16.dp))
        Button(model::load, enabled = !state.saving) { Text("Retry") }
        TextButton({ model.confirmReset(true) }, enabled = !state.saving) { Text("Start a new room…") }
        if (state.resetConfirm) {
            Text("Replace the saved layout? This cannot be undone.", fontWeight = FontWeight.Bold)
            Row {
                TextButton({ model.confirmReset(false) }, enabled = !state.saving) { Text("Keep my layout") }
                Button(model::reset, enabled = !state.saving) { Text("Replace layout") }
            }
        }
    }
}
