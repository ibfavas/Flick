package com.ibfavas.flick.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ibfavas.flick.R
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.actions.Feedback
import com.ibfavas.flick.actions.FlickAccessibilityService
import com.ibfavas.flick.actions.FlowCodec
import com.ibfavas.flick.actions.FlowStep
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Backup
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { LIST, RECORD, TEST, SETTINGS }

/** Home-screen categories, inspired by Glyf's tabs but Flick's own set. */
private enum class GestureCategory(val label: String) {
    ALL("All"),
    APPS("Apps"),
    ACTIONS("Actions"),
    WEBPAGES("Webpages");

    fun matches(gesture: Gesture): Boolean = when (this) {
        ALL -> true
        APPS -> gesture.actionType == ActionType.LAUNCH_APP
        WEBPAGES -> gesture.actionType == ActionType.OPEN_URL
        ACTIONS -> gesture.actionType != ActionType.LAUNCH_APP &&
            gesture.actionType != ActionType.OPEN_URL
    }
}

/**
 * Flick v1.0.8: AMOLED. Pitch black, white text, one bright cyan
 * accent; the wordmark is the header. The home screen is the gesture
 * list plus a floating add button and a settings entry. No bottom
 * navigation. The draw pad is the centerpiece: a full-screen pad for
 * testing gestures, and a large pad on the record screen.
 */
@Composable
fun MainScreen(dao: GestureDao, settings: SettingsStore, openSettings: Boolean = false) {
    var screen by remember { mutableStateOf(if (openSettings) Screen.SETTINGS else Screen.LIST) }
    // The gesture a sample is being recorded for. While set, the add-sample
    // screen replaces the normal screen flow.
    var addingSampleFor by remember { mutableStateOf<Gesture?>(null) }
    BackHandler(enabled = addingSampleFor != null || screen != Screen.LIST) {
        if (addingSampleFor != null) {
            addingSampleFor = null
        } else {
            screen = Screen.LIST
        }
    }
    // Every screen sits inside a Surface: without one, Text without an
    // explicit colour falls back to LocalContentColor, which defaults to
    // black — dark text on the dark background, which is how the
    // record screen's labels became unreadable in v1.0.6. The Surface
    // provides the theme's onBackground (white) as the content colour.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    if (addingSampleFor != null) {
        AddSampleScreen(
            dao = dao,
            gesture = addingSampleFor!!,
            onBack = { addingSampleFor = null },
            onSaved = { addingSampleFor = null }
        )
    } else {
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) +
                slideInHorizontally(animationSpec = tween(220)) { it / 5 }) togetherWith
            (fadeOut(animationSpec = tween(180)) +
                slideOutHorizontally(animationSpec = tween(180)) { -it / 5 })
        },
        label = "screen"
    ) { current ->
        when (current) {
            Screen.LIST -> GestureListScreen(
                dao = dao,
                onRecord = { screen = Screen.RECORD },
                onTest = { screen = Screen.TEST },
                onSettings = { screen = Screen.SETTINGS },
                onAddSample = { gesture ->
                    addingSampleFor = gesture
                }
            )
            Screen.RECORD -> RecordScreen(
                dao = dao,
                settings = settings,
                onBack = { screen = Screen.LIST },
                onSaved = { screen = Screen.LIST }
            )
            Screen.TEST -> TestPadScreen(
                dao = dao,
                settings = settings,
                onBack = { screen = Screen.LIST }
            )
            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                dao = dao,
                onBack = { screen = Screen.LIST }
            )
        }
    }
    }
    }
}

// ---------------------------------------------------------------- list

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GestureListScreen(
    dao: GestureDao,
    onRecord: () -> Unit,
    onTest: () -> Unit,
    onSettings: () -> Unit,
    onAddSample: (Gesture) -> Unit
) {
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    var deleting by remember { mutableStateOf<Gesture?>(null) }
    var category by remember { mutableStateOf(GestureCategory.ALL) }
    val filtered = remember(gestures, category) {
        gestures.filter { category.matches(it) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRecord,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = FlickBrand.OnCyan,
                modifier = Modifier.pressScale()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add gesture")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.flick_wordmark),
                    contentDescription = "Flick",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.height(34.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale()
            ) {
                Icon(Icons.Filled.Create, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Draw a gesture", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tap a gesture to edit. Long-press to delete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "GESTURES (${gestures.size} saved)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = FlickBrand.TextWhite
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GestureCategory.entries.forEach { tab ->
                    CategoryTab(
                        label = tab.label,
                        selected = category == tab,
                        onClick = { category = tab }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (gestures.isEmpty()) {
                        Text(
                            "No gestures yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FlickBrand.TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap + to draw your first gesture, then pick what it should do.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "No gestures in this category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FlickBrand.TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Try another tab, or tap + to add one here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { gesture ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(250)) +
                                expandVertically(animationSpec = tween(250)),
                            modifier = Modifier.animateItem()
                        ) {
                            GestureCard(
                                gesture = gesture,
                                onEdit = { editing = gesture },
                                onDelete = { deleting = gesture }
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { gesture ->
        EditGestureDialog(
            gesture = gesture,
            sampleCount = gesture.sampleCount(),
            onAddSample = if (gesture.sampleCount() < Gesture.MAX_SAMPLES) {
                {
                    editing = null
                    onAddSample(gesture)
                }
            } else {
                null
            },
            onDismiss = { editing = null },
            onSave = { name, type, payload ->
                scope.launch {
                    dao.update(gesture.copy(name = name, actionType = type, payload = payload))
                    editing = null
                }
            }
        )
    }

    deleting?.let { gesture ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete gesture?") },
            text = { Text("Delete \"${gesture.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.delete(gesture)
                        deleting = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * A category pill for the home screen. Selected: cyan fill, dark text.
 * Unselected: near-black surface, white text.
 */
@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) FlickBrand.Cyan else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) FlickBrand.OnCyan else FlickBrand.TextWhite
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GestureCard(
    gesture: Gesture,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val description = remember(gesture) { gestureDescription(gesture) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, FlickBrand.Outline, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onEdit, onLongClick = onDelete)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GestureGlyph(
            template = gesture.template,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                gesture.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ActionIcon(gesture)
    }
}

/**
 * The gesture itself, drawn small: the stored template strokes scaled
 * into a chip, in brand cyan. Each card shows the shape to draw, so the
 * list is readable at a glance.
 */
@Composable
private fun GestureGlyph(template: String, modifier: Modifier = Modifier) {
    val strokes = remember(template) { Recognizer.strokesFromString(template) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, FlickBrand.Outline, RoundedCornerShape(14.dp))
            .background(FlickBrand.AmoledSurfaceVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (strokes.isEmpty()) return@Canvas
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for (stroke in strokes) {
                for (p in stroke) {
                    if (p.x < minX) minX = p.x
                    if (p.x > maxX) maxX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.y > maxY) maxY = p.y
                }
            }
            val bw = (maxX - minX).coerceAtLeast(1f)
            val bh = (maxY - minY).coerceAtLeast(1f)
            val scale = minOf(size.width / bw, size.height / bh) * 0.8f
            val ox = (size.width - bw * scale) / 2f - minX * scale
            val oy = (size.height - bh * scale) / 2f - minY * scale
            val style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            for (stroke in strokes) {
                if (stroke.size < 2) continue
                val path = Path().apply {
                    moveTo(stroke[0].x * scale + ox, stroke[0].y * scale + oy)
                    for (i in 1 until stroke.size) {
                        lineTo(stroke[i].x * scale + ox, stroke[i].y * scale + oy)
                    }
                }
                drawPath(path, FlickBrand.Cyan, style = style)
            }
        }
    }
}

private fun gestureDescription(gesture: Gesture): String {
    val base = ActionExecutor.describe(gesture)
    val strokes = Recognizer.strokesFromString(gesture.template).size
    val samples = gesture.sampleCount()
    var text = base
    if (strokes > 1) text += ", $strokes strokes"
    if (samples > 1) text += ", $samples samples"
    return text
}

// Only material-icons-core is in the build deps (see tools/seeds.txt), so
// only core icons are used here: Apps, Link, CameraAlt and the flashlight
// icons live in material-icons-extended and would not compile. Wi-Fi and
// Bluetooth have no core icon; both open a system panel, so the gear is
// honest for them.
private fun iconFor(type: ActionType): ImageVector = when (type) {
    ActionType.LAUNCH_APP -> Icons.AutoMirrored.Filled.ExitToApp
    ActionType.OPEN_URL -> Icons.Filled.Share
    ActionType.FLASHLIGHT_ON -> Icons.Filled.Star
    ActionType.FLASHLIGHT_OFF -> Icons.Filled.Close
    ActionType.OPEN_CAMERA -> Icons.Filled.Face
    ActionType.MEDIA_PLAY_PAUSE -> Icons.Filled.PlayArrow
    ActionType.CALL_CONTACT -> Icons.Filled.Call
    ActionType.LOCK_SCREEN -> Icons.Filled.Lock
    ActionType.TAKE_SCREENSHOT -> Icons.Filled.Create
    ActionType.TOGGLE_WIFI -> Icons.Filled.Settings
    ActionType.TOGGLE_BLUETOOTH -> Icons.Filled.Settings
    ActionType.TOGGLE_DND -> Icons.Filled.Notifications
    ActionType.RUN_FLOW -> Icons.AutoMirrored.Filled.List
}

@Composable
private fun ActionIcon(gesture: Gesture) {
    val context = LocalContext.current
    if (gesture.actionType == ActionType.LAUNCH_APP) {
        var bitmap by remember(gesture.payload) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(gesture.payload) {
            bitmap = try {
                context.packageManager.getApplicationIcon(gesture.payload)
                    .toBitmap(width = 96, height = 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            FallbackIcon(Icons.AutoMirrored.Filled.ExitToApp)
        }
    } else {
        FallbackIcon(iconFor(gesture.actionType))
    }
}

@Composable
private fun FallbackIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun EditGestureDialog(
    gesture: Gesture,
    sampleCount: Int,
    onAddSample: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: (String, ActionType, String) -> Unit
) {
    var name by remember { mutableStateOf(gesture.name) }
    var action by remember { mutableStateOf(ActionSelection(gesture.actionType, gesture.payload)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit gesture") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionPicker(action = action, onChange = { action = it })
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Samples: $sampleCount of ${Gesture.MAX_SAMPLES}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlickBrand.TextWhite
                )
                Text(
                    "More samples make recognition more accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onAddSample != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAddSample,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Record another sample")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && action.isValid(),
                onClick = { onSave(name.trim(), action.type, action.payload) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------- record

@Composable
private fun RecordScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    // Samples recorded for this gesture: the first is the template, the
    // rest are extra samples (v1.0.8). The recognizer matches against
    // every sample, so the same gesture drawn a little differently still
    // matches. Up to Gesture.MAX_SAMPLES in total.
    var samples by remember { mutableStateOf<List<List<List<StrokePoint>>>>(emptyList()) }
    var captured by remember { mutableStateOf<List<List<StrokePoint>>?>(null) }
    var padKey by remember { mutableStateOf(0) }
    var action by remember { mutableStateOf(ActionSelection(ActionType.LAUNCH_APP, "")) }
    val totalSamples = samples.size + if (captured != null) 1 else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "New gesture",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Gesture name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                "Draw your gesture in the box:",
                color = FlickBrand.TextWhite
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // A fresh pad per sample: key() drops the old DrawPad
                // state (its strokes, timer and hint) when a sample is kept.
                key(padKey) {
                    DrawPad(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        onGestureFinished = { captured = it },
                        onCleared = { captured = null }
                    )
                }
            }
            Text(
                when {
                    captured == null && samples.isEmpty() -> "Nothing drawn yet"
                    captured == null -> "$totalSamples sample${if (totalSamples == 1) "" else "s"} recorded"
                    else -> {
                        val strokes = captured!!.size
                        val points = captured!!.sumOf { it.size }
                        "Sample $totalSamples captured: $strokes stroke${if (strokes == 1) "" else "s"}, $points points"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            // Extra samples make recognition more accurate: draw the same
            // gesture again, a little differently if you like.
            if (captured != null && samples.size < Gesture.MAX_SAMPLES - 1) {
                OutlinedButton(
                    onClick = {
                        samples = samples + listOf(captured!!)
                        captured = null
                        padKey++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add another sample (${totalSamples + 1} of ${Gesture.MAX_SAMPLES})")
                }
            }
        }
        item {
            Text(
                "What should it do?",
                style = MaterialTheme.typography.titleMedium,
                color = FlickBrand.TextWhite
            )
            ActionPicker(action = action, onChange = { action = it })
        }
        item {
            val allSamples = samples + listOfNotNull(captured)
            Button(
                enabled = name.isNotBlank() && allSamples.isNotEmpty() && action.isValid(),
                onClick = {
                    scope.launch {
                        dao.insert(
                            Gesture(
                                name = name.trim(),
                                template = Recognizer.strokesToString(allSamples[0]),
                                actionType = action.type,
                                payload = action.payload,
                                extraTemplates = allSamples.drop(1)
                                    .joinToString("\n") { Recognizer.strokesToString(it) }
                            )
                        )
                        onSaved()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save gesture", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- add sample

/**
 * Record one more sample of an existing gesture (v1.0.8). The new sample
 * is appended to the gesture's extra samples; the recognizer matches
 * against every sample and takes the best score.
 */
@Composable
private fun AddSampleScreen(
    dao: GestureDao,
    gesture: Gesture,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var captured by remember { mutableStateOf<List<List<StrokePoint>>?>(null) }
    val current = gesture.sampleCount()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Add a sample",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "\"${gesture.name}\" has $current sample${if (current == 1) "" else "s"} " +
                    "of ${Gesture.MAX_SAMPLES}. Draw the same gesture again, " +
                    "a little differently if you like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text(
                "Draw the gesture in the box:",
                color = FlickBrand.TextWhite
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                DrawPad(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    onGestureFinished = { captured = it },
                    onCleared = { captured = null }
                )
            }
            Text(
                if (captured == null) "Nothing drawn yet" else "Sample captured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Button(
                enabled = captured != null,
                onClick = {
                    scope.launch {
                        val extras = gesture.allTemplates().drop(1) +
                            Recognizer.strokesToString(captured!!)
                        dao.update(
                            gesture.copy(extraTemplates = extras.joinToString("\n"))
                        )
                        onSaved()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save sample", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- action picker

private data class ActionSelection(val type: ActionType, val payload: String) {
    fun isValid(): Boolean = when (type) {
        ActionType.LAUNCH_APP -> payload.isNotBlank()
        ActionType.OPEN_URL -> payload.isNotBlank()
        ActionType.CALL_CONTACT -> ActionExecutor.contactNumber(payload).isNotBlank()
        ActionType.RUN_FLOW -> FlowCodec.decode(payload).size >= 2
        else -> true
    }
}

@Composable
private fun ActionPicker(
    action: ActionSelection,
    onChange: (ActionSelection) -> Unit,
    allowFlow: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAppPicker by remember { mutableStateOf(false) }
    var pickedAppLabel by remember { mutableStateOf<String?>(null) }
    var pickedContactLabel by remember { mutableStateOf<String?>(null) }

    // Contact picking needs two permissions: READ_CONTACTS to read the
    // contact, CALL_PHONE to place the call. Both are requested together;
    // picking still works if only the first is granted, and placing the
    // call guides the user if the second is missing.
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contact = withContext(Dispatchers.IO) { readContact(context, uri) }
                if (contact != null) {
                    pickedContactLabel = contact.first
                    onChange(
                        ActionSelection(
                            ActionType.CALL_CONTACT,
                            "${contact.first}\n${contact.second}"
                        )
                    )
                }
            }
        }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_CONTACTS] == true) {
            contactPicker.launch(null)
        }
    }

    Column {
        ActionOption(
            label = "Launch an app",
            selected = action.type == ActionType.LAUNCH_APP,
            onClick = { onChange(ActionSelection(ActionType.LAUNCH_APP, action.payload)) }
        )
        if (action.type == ActionType.LAUNCH_APP) {
            OutlinedButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
            ) {
                Text(pickedAppLabel ?: if (action.payload.isBlank()) "Choose app" else action.payload)
            }
        }
        ActionOption(
            label = "Open a link",
            selected = action.type == ActionType.OPEN_URL,
            onClick = { onChange(ActionSelection(ActionType.OPEN_URL, "")) }
        )
        if (action.type == ActionType.OPEN_URL) {
            OutlinedTextField(
                value = action.payload,
                onValueChange = { onChange(ActionSelection(ActionType.OPEN_URL, it)) },
                label = { Text("URL, e.g. example.com") },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
            )
        }
        ActionOption(
            label = "Flashlight on",
            selected = action.type == ActionType.FLASHLIGHT_ON,
            onClick = { onChange(ActionSelection(ActionType.FLASHLIGHT_ON, "")) }
        )
        ActionOption(
            label = "Flashlight off",
            selected = action.type == ActionType.FLASHLIGHT_OFF,
            onClick = { onChange(ActionSelection(ActionType.FLASHLIGHT_OFF, "")) }
        )
        ActionOption(
            label = "Open camera",
            selected = action.type == ActionType.OPEN_CAMERA,
            onClick = { onChange(ActionSelection(ActionType.OPEN_CAMERA, "")) }
        )
        ActionOption(
            label = "Media play / pause",
            selected = action.type == ActionType.MEDIA_PLAY_PAUSE,
            onClick = { onChange(ActionSelection(ActionType.MEDIA_PLAY_PAUSE, "")) }
        )
        ActionOption(
            label = "Call a contact",
            selected = action.type == ActionType.CALL_CONTACT,
            onClick = { onChange(ActionSelection(ActionType.CALL_CONTACT, action.payload)) }
        )
        if (action.type == ActionType.CALL_CONTACT) {
            OutlinedButton(
                onClick = {
                    contactPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
            ) {
                Text(
                    pickedContactLabel
                        ?: ActionExecutor.contactName(action.payload)
                            .takeIf { it.isNotBlank() }
                        ?: "Choose contact"
                )
            }
        }
        ActionOption(
            label = "Lock screen",
            selected = action.type == ActionType.LOCK_SCREEN,
            onClick = { onChange(ActionSelection(ActionType.LOCK_SCREEN, "")) }
        )
        if (action.type == ActionType.LOCK_SCREEN) {
            Text(
                "Needs Flick's accessibility service, turned on in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
        ActionOption(
            label = "Take a screenshot",
            selected = action.type == ActionType.TAKE_SCREENSHOT,
            onClick = { onChange(ActionSelection(ActionType.TAKE_SCREENSHOT, "")) }
        )
        if (action.type == ActionType.TAKE_SCREENSHOT) {
            Text(
                "Needs Flick's accessibility service, turned on in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
        ActionOption(
            label = "Open Wi-Fi settings",
            selected = action.type == ActionType.TOGGLE_WIFI,
            onClick = { onChange(ActionSelection(ActionType.TOGGLE_WIFI, "")) }
        )
        if (action.type == ActionType.TOGGLE_WIFI) {
            Text(
                "Android does not let an app flip Wi-Fi silently, so this opens the Wi-Fi panel for you to switch it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
        ActionOption(
            label = "Open Bluetooth settings",
            selected = action.type == ActionType.TOGGLE_BLUETOOTH,
            onClick = { onChange(ActionSelection(ActionType.TOGGLE_BLUETOOTH, "")) }
        )
        if (action.type == ActionType.TOGGLE_BLUETOOTH) {
            Text(
                "Same as Wi-Fi: Android does not let an app flip Bluetooth silently, so this opens the Bluetooth panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
        ActionOption(
            label = "Toggle Do Not Disturb",
            selected = action.type == ActionType.TOGGLE_DND,
            onClick = { onChange(ActionSelection(ActionType.TOGGLE_DND, "")) }
        )
        if (action.type == ActionType.TOGGLE_DND) {
            Text(
                "Needs notification-policy access, granted once in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
        if (allowFlow) {
            ActionOption(
                label = "Run a flow",
                selected = action.type == ActionType.RUN_FLOW,
                onClick = { onChange(ActionSelection(ActionType.RUN_FLOW, action.payload)) }
            )
            if (action.type == ActionType.RUN_FLOW) {
                FlowEditor(
                    steps = FlowCodec.decode(action.payload),
                    onChange = { steps ->
                        onChange(ActionSelection(ActionType.RUN_FLOW, FlowCodec.encode(steps)))
                    }
                )
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            context = context,
            onDismiss = { showAppPicker = false },
            onPick = { label, packageName ->
                pickedAppLabel = label
                onChange(ActionSelection(ActionType.LAUNCH_APP, packageName))
                showAppPicker = false
            }
        )
    }
}

/**
 * Read a contact's display name and first phone number from the Uri
 * returned by the system contact picker. Runs on a background thread.
 */
private fun readContact(context: Context, uri: Uri): Pair<String, String>? {
    val resolver = context.contentResolver
    var id: String? = null
    var name: String? = null
    resolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            id = cursor.getString(0)
            name = cursor.getString(1)
        }
    }
    if (id == null) return null
    var number: String? = null
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(id),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) number = cursor.getString(0)
    }
    return if (number.isNullOrBlank()) null else Pair(name ?: number!!, number!!)
}

/**
 * A flow editor: the steps in order, with move-up, move-down and remove
 * controls, and an "Add step" button. A flow needs at least two steps.
 */
@Composable
private fun FlowEditor(steps: List<FlowStep>, onChange: (List<FlowStep>) -> Unit) {
    var showStepPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(start = 32.dp)) {
        if (steps.isEmpty()) {
            Text(
                "A flow runs two or more actions in order. Add the first step.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}. ${ActionExecutor.describeAction(step.type, step.payload)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlickBrand.TextWhite,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = index > 0,
                    onClick = {
                        val reordered = steps.toMutableList()
                        val moved = reordered.removeAt(index)
                        reordered.add(index - 1, moved)
                        onChange(reordered)
                    }
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (index > 0) FlickBrand.TextWhite
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    enabled = index < steps.size - 1,
                    onClick = {
                        val reordered = steps.toMutableList()
                        val moved = reordered.removeAt(index)
                        reordered.add(index + 1, moved)
                        onChange(reordered)
                    }
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (index < steps.size - 1) FlickBrand.TextWhite
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onChange(steps.filterIndexed { i, _ -> i != index })
                }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove step",
                        tint = FlickBrand.TextWhite
                    )
                }
            }
        }
        if (steps.size < 2) {
            Text(
                "Add at least two steps to save this flow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { showStepPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add step")
        }
    }
    if (showStepPicker) {
        FlowStepDialog(
            onDismiss = { showStepPicker = false },
            onAdd = { step ->
                onChange(steps + step)
                showStepPicker = false
            }
        )
    }
}

/**
 * Pick one leaf action to add to a flow. Flows never contain flows, so
 * the "Run a flow" option is hidden here.
 */
@Composable
private fun FlowStepDialog(onDismiss: () -> Unit, onAdd: (FlowStep) -> Unit) {
    var step by remember { mutableStateOf(ActionSelection(ActionType.LAUNCH_APP, "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a step") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ActionPicker(
                    action = step,
                    onChange = { step = it },
                    allowFlow = false
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = step.isValid(),
                onClick = { onAdd(FlowStep(step.type, step.payload)) }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ActionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = FlickBrand.TextWhite)
    }
}

private data class LaunchableApp(val label: String, val packageName: String)

/**
 * The app list, cached for the life of the process. The PackageManager
 * query is slow (hundreds of apps), so it runs once on a background
 * thread and every later opening of the picker is instant. Icons are
 * loaded lazily per visible row, also off the main thread, and cached
 * the same way. v1.0.6 queried and decoded every icon up front on the
 * main thread, which is why opening the picker stalled.
 */
private object AppListCache {
    var apps: List<LaunchableApp>? = null
    val icons = mutableStateMapOf<String, ImageBitmap?>()
}

@Composable
private fun AppPickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit
) {
    var apps by remember { mutableStateOf(AppListCache.apps) }
    LaunchedEffect(Unit) {
        if (apps == null) {
            val loaded = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(main, 0)
                }
                // Dedupe by package: some apps expose several launcher
                // activities (Amazon is one), and a repeated package name
                // breaks LazyColumn, which needs a unique key per item.
                // Keep the first launcher entry per package, so each
                // package appears exactly once. No icons here: loading
                // and decoding every icon up front is what made the
                // picker slow. Icons load lazily per visible row.
                infos.mapNotNull { info ->
                    val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                    LaunchableApp(
                        label = info.loadLabel(pm).toString(),
                        packageName = packageName
                    )
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }
            AppListCache.apps = loaded
            apps = loaded
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            val list = apps
            if (list == null) {
                Text("Loading apps...")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPick(app.label, app.packageName)
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppRowIcon(context = context, packageName = app.packageName)
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * One app icon, loaded off the main thread the first time its row
 * becomes visible, then cached. Reading the cache map inside composition
 * subscribes the row to it, so the icon appears as soon as it loads.
 */
@Composable
private fun AppRowIcon(context: Context, packageName: String) {
    val bitmap = AppListCache.icons[packageName]
    if (bitmap == null) {
        LaunchedEffect(packageName) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(width = 96, height = 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (loaded != null) AppListCache.icons[packageName] = loaded
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(32.dp).padding(end = 8.dp)
        )
    }
}

// ---------------------------------------------------------------- test pad

@Composable
private fun TestPadScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_VIBRATE_ON_RUN
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    var result by remember { mutableStateOf<String?>(null) }
    var matched by remember { mutableStateOf<Gesture?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Draw a gesture",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "Draw here to see what Flick recognises. Nothing runs until you tap Run.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DrawPad(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                onGestureFinished = { raw ->
                    scope.launch {
                        val gestures = dao.getAll()
                        // Every recorded sample of every gesture: the
                        // recognizer matches against all of them and
                        // takes the best score.
                        val templates = gestures.flatMap { it.multiTemplates() }
                        val match = Recognizer.recognizeMulti(raw, templates)
                        val gesture = match?.let { m ->
                            gestures.firstOrNull { it.name == m.template.name }
                        }
                        matched = gesture
                        result = if (gesture == null) {
                            "No match, try again"
                        } else {
                            val score = match?.let { (it.score * 100).toInt() } ?: 0
                            "Matched ${gesture.name}, $score%\n" +
                                ActionExecutor.describe(gesture)
                        }
                    }
                },
                onCleared = {
                    result = null
                    matched = null
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = matched != null,
            onClick = {
                matched?.let {
                    ActionExecutor.execute(context, it, Feedback(vibrateOnRun, soundOnRun))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressScale()
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run action", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- settings

// ---------------------------------------------------------------- settings

/**
 * Settings, in Flick's own design but with the same depth of features
 * Glyf has: Permissions, Overlay, Triggers and Reliability. Every label
 * is white or light grey on the black background, never dark text
 * on a dark surface.
 */
@Composable
private fun SettingsScreen(settings: SettingsStore, dao: GestureDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurPx by settings.overlayBlur.collectAsState(initial = SettingsStore.DEFAULT_BLUR_PX)
    val opacity by settings.overlayOpacity.collectAsState(initial = SettingsStore.DEFAULT_OPACITY)
    val timeoutSeconds by settings.overlayTimeout.collectAsState(initial = SettingsStore.DEFAULT_TIMEOUT_S)
    val autoCloseOnFail by settings.autoCloseOnFail.collectAsState(
        initial = SettingsStore.DEFAULT_AUTO_CLOSE_ON_FAIL
    )
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_VIBRATE_ON_RUN
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    // Backup result, shown under the buttons until the next backup action.
    var backupMessage by remember { mutableStateOf<String?>(null) }
    // Backup export: the user picks where the JSON file goes.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val json = Backup.export(dao.getAll())
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) { backupMessage = "Backup saved" }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { backupMessage = "Could not save the backup" }
                }
            }
        }
    }
    // Backup import: the user picks a Flick backup file. Duplicates are
    // skipped by name, invalid entries are skipped, and a bad file shows
    // an error instead of crashing.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText() ?: ""
                    val existing = dao.getAll().map { it.name }.toSet()
                    val (gestures, result) = Backup.parse(text, existing)
                    if (result.error != null) {
                        withContext(Dispatchers.Main) { backupMessage = result.error }
                    } else {
                        for (gesture in gestures) dao.insert(gesture)
                        withContext(Dispatchers.Main) {
                            backupMessage = buildString {
                                append("Imported ${result.imported} gesture")
                                if (result.imported != 1) append("s")
                                if (result.skippedDuplicates > 0) {
                                    append(", skipped ${result.skippedDuplicates} duplicate")
                                    if (result.skippedDuplicates != 1) append("s")
                                }
                                if (result.skippedInvalid > 0) {
                                    append(", skipped ${result.skippedInvalid} invalid")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { backupMessage = "Could not read this file" }
                }
            }
        }
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }
    var notificationsGranted by remember { mutableStateOf(notificationsGranted(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }
    var overlayGranted by remember { mutableStateOf(overlayGranted(context)) }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { overlayGranted = overlayGranted(context) }
    var accessibilityGranted by remember {
        mutableStateOf(FlickAccessibilityService.isEnabled(context))
    }
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { accessibilityGranted = FlickAccessibilityService.isEnabled(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = FlickBrand.TextWhite
                )
            }
        }
        item {
            SettingsSection(title = "Permissions") {
                PermissionRow(
                    title = "Appear over apps",
                    description = "Ready for the drawing overlay.",
                    granted = overlayGranted,
                    grantedText = "Enabled",
                    onAllow = {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    }
                )
                PermissionRow(
                    title = "Notifications",
                    description = "Allow tap-to-draw notification shortcuts.",
                    granted = notificationsGranted,
                    grantedText = "Allowed",
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                PermissionRow(
                    title = "Accessibility service (Optional)",
                    description = if (accessibilityGranted) {
                        "Enabled. Lock screen and screenshot actions will work."
                    } else {
                        "Needed for the lock screen and screenshot actions."
                    },
                    granted = accessibilityGranted,
                    grantedText = "Enabled",
                    onAllow = {
                        accessibilityLauncher.launch(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    },
                    allowText = "Open"
                )
                PermissionRow(
                    title = "Camera",
                    description = if (cameraGranted) {
                        "Granted. Flashlight actions will work."
                    } else {
                        "Needed for the flashlight actions."
                    },
                    granted = cameraGranted,
                    grantedText = "Granted",
                    onAllow = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
        item {
            SettingsSection(title = "Overlay") {
                SettingSlider(
                    label = "Background Blur",
                    valueText = "${blurPx}px",
                    value = blurPx.toFloat(),
                    range = 0f..SettingsStore.MAX_BLUR_PX.toFloat(),
                    steps = SettingsStore.MAX_BLUR_PX - 1,
                    description = "Frosted glass background blur when the drawing pad is active (Android 12 and up).",
                    onChange = { scope.launch { settings.setOverlayBlur(it.toInt()) } }
                )
                SettingSlider(
                    label = "Overlay opacity",
                    valueText = "${(opacity * 100).toInt()}%",
                    value = opacity,
                    range = 0f..1f,
                    steps = 0,
                    description = "Controls how dim the screen gets while the overlay is open.",
                    onChange = { scope.launch { settings.setOverlayOpacity(it) } }
                )
                SettingSlider(
                    label = "Overlay timeout",
                    valueText = if (timeoutSeconds == 0) "Off" else "${timeoutSeconds}s",
                    value = timeoutSeconds.toFloat(),
                    range = 0f..SettingsStore.MAX_TIMEOUT_S.toFloat(),
                    steps = SettingsStore.MAX_TIMEOUT_S - 1,
                    description = "Seconds the pad waits with no gesture before closing on its own. " +
                        "Disabled while multi-stroke is on — the overlay stays open once you start drawing.",
                    onChange = { scope.launch { settings.setOverlayTimeout(it.toInt()) } }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto close on fail",
                            style = MaterialTheme.typography.bodyLarge,
                            color = FlickBrand.TextWhite
                        )
                        Text(
                            "Close the pad after a gesture that matched nothing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCloseOnFail,
                        onCheckedChange = { scope.launch { settings.setAutoCloseOnFail(it) } }
                    )
                }
            }
        }
        item {
            SettingsSection(title = "Feedback") {
                SettingSwitch(
                    title = "Vibrate on run",
                    description = "A short vibration when a gesture is recognised and runs.",
                    checked = vibrateOnRun,
                    onChange = { scope.launch { settings.setVibrateOnRun(it) } }
                )
                SettingSwitch(
                    title = "Sound on run",
                    description = "A short beep when a gesture is recognised and runs.",
                    checked = soundOnRun,
                    onChange = { scope.launch { settings.setSoundOnRun(it) } }
                )
            }
        }
        item {
            SettingsSection(title = "Backup") {
                Text(
                    "Save all your gestures to a file, or bring them back from one. " +
                        "Importing never duplicates a gesture you already have.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlickBrand.TextWhite
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("flick-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import")
                    }
                }
                backupMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlickBrand.TextWhite
                    )
                }
            }
        }
        item {
            SettingsSection(title = "Triggers") {
                Text(
                    "Add the Flick widget to your home screen: the invisible one is " +
                        "fully transparent, so it looks like empty space. There are " +
                        "also two visible widgets: a round one showing the Flick logo, " +
                        "and a wide one showing the flick wordmark. Tap any of them " +
                        "to open the gesture pad. You can also add the Flick tile " +
                        "from the Quick Settings panel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlickBrand.TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Note: Android widgets only support taps, not stroke tracking. " +
                        "Drawing happens in the frosted-glass pad that opens when you " +
                        "tap the widget, not inside the widget itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SettingsSection(title = "Reliability") {
                Text(
                    "Let Flick run without battery limits, so the widget and tile " +
                        "respond instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlickBrand.TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                ) {
                    Text("Open battery settings")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Your gestures are stored only on this device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun notificationsGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun overlayGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

/** One settings section: a title above a rounded near-black card. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = FlickBrand.TextWhite,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, FlickBrand.Outline, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

/** One permission row: title, description, and a status pill or an Allow button. */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    grantedText: String,
    onAllow: () -> Unit,
    allowText: String = "Allow"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = FlickBrand.TextWhite
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (granted) {
            Text(
                grantedText,
                style = MaterialTheme.typography.bodyMedium,
                color = FlickBrand.TextWhite,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        } else {
            OutlinedButton(onClick = onAllow) {
                Text(allowText)
            }
        }
    }
}

/** A labelled switch row with a description underneath. */
@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = FlickBrand.TextWhite
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A labelled slider with a value chip and a description underneath. */
@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = FlickBrand.TextWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = FlickBrand.TextWhite,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
