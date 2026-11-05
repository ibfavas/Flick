package com.ibfavas.flick.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ibfavas.flick.actions.ActionExecutor
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import com.ibfavas.flick.data.SettingsStore
import com.ibfavas.flick.gesture.Recognizer
import com.ibfavas.flick.gesture.StrokePoint
import kotlinx.coroutines.launch

private enum class Screen { LIST, RECORD, TEST, SETTINGS }

/**
 * Flick, redesigned: minimal and dark. The home screen is the gesture list
 * plus a floating add button and a settings entry. No bottom navigation.
 * The draw pad is the centerpiece: a full-screen pad for testing gestures,
 * and a large pad on the record screen.
 */
@Composable
fun MainScreen(dao: GestureDao, settings: SettingsStore) {
    var screen by remember { mutableStateOf(Screen.LIST) }
    BackHandler(enabled = screen != Screen.LIST) { screen = Screen.LIST }
    when (screen) {
        Screen.LIST -> GestureListScreen(
            dao = dao,
            onRecord = { screen = Screen.RECORD },
            onTest = { screen = Screen.TEST },
            onSettings = { screen = Screen.SETTINGS }
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
            onBack = { screen = Screen.LIST }
        )
    }
}

// ---------------------------------------------------------------- list

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GestureListScreen(
    dao: GestureDao,
    onRecord: () -> Unit,
    onTest: () -> Unit,
    onSettings: () -> Unit
) {
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    var deleting by remember { mutableStateOf<Gesture?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRecord,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
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
                Text(
                    "Flick",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
            if (gestures.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No gestures yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to draw your first gesture, then pick what it should do.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(gestures, key = { it.id }) { gesture ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { editing = gesture },
                                    onLongClick = { deleting = gesture }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionIcon(gesture)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    gesture.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    gestureDescription(gesture),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { gesture ->
        EditGestureDialog(
            gesture = gesture,
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

private fun gestureDescription(gesture: Gesture): String {
    val base = ActionExecutor.describe(gesture)
    val strokes = Recognizer.strokesFromString(gesture.template).size
    return if (strokes > 1) "$base, $strokes strokes" else base
}

// Only material-icons-core is in the build deps (see tools/seeds.txt), so
// only core icons are used here: Apps, Link, CameraAlt and the flashlight
// icons live in material-icons-extended and would not compile.
private fun iconFor(type: ActionType): ImageVector = when (type) {
    ActionType.LAUNCH_APP -> Icons.AutoMirrored.Filled.ExitToApp
    ActionType.OPEN_URL -> Icons.Filled.Share
    ActionType.FLASHLIGHT_ON -> Icons.Filled.Star
    ActionType.FLASHLIGHT_OFF -> Icons.Filled.Close
    ActionType.OPEN_CAMERA -> Icons.Filled.Face
    ActionType.MEDIA_PLAY_PAUSE -> Icons.Filled.PlayArrow
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
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var name by remember { mutableStateOf("") }
    var captured by remember { mutableStateOf<List<List<StrokePoint>>?>(null) }
    var action by remember { mutableStateOf(ActionSelection(ActionType.LAUNCH_APP, "")) }

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
            Text("Draw your gesture in the box:")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                DrawPad(
                    strokeColor = Color(strokeColor),
                    strokeWidth = strokeWidth,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    onGestureFinished = { captured = it },
                    onCleared = { captured = null }
                )
            }
            Text(
                if (captured == null) "Nothing drawn yet"
                else {
                    val strokes = captured!!.size
                    val points = captured!!.sumOf { it.size }
                    "Captured: $strokes stroke${if (strokes == 1) "" else "s"}, $points points"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            Text("What should it do?", style = MaterialTheme.typography.titleMedium)
            ActionPicker(action = action, onChange = { action = it })
        }
        item {
            Button(
                enabled = name.isNotBlank() && captured != null && action.isValid(),
                onClick = {
                    scope.launch {
                        dao.insert(
                            Gesture(
                                name = name.trim(),
                                template = Recognizer.strokesToString(captured!!),
                                actionType = action.type,
                                payload = action.payload
                            )
                        )
                        onSaved()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save gesture", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- action picker

private data class ActionSelection(val type: ActionType, val payload: String) {
    fun isValid(): Boolean = when (type) {
        ActionType.LAUNCH_APP -> payload.isNotBlank()
        ActionType.OPEN_URL -> payload.isNotBlank()
        else -> true
    }
}

@Composable
private fun ActionPicker(action: ActionSelection, onChange: (ActionSelection) -> Unit) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    var pickedAppLabel by remember { mutableStateOf<String?>(null) }

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

@Composable
private fun ActionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

private data class LaunchableApp(val label: String, val packageName: String, val icon: Drawable?)

@Composable
private fun AppPickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit
) {
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(main, 0)
        }
        // Dedupe by package: some apps expose several launcher activities
        // (Amazon is one), and a repeated package name breaks LazyColumn,
        // which needs a unique key per item. Keep the first launcher entry
        // per package, so each package appears exactly once.
        apps = infos.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            LaunchableApp(
                label = info.loadLabel(pm).toString(),
                packageName = packageName,
                icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
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
                            app.icon?.let { drawable ->
                                val bitmap = remember(drawable) {
                                    drawable.toBitmap(width = 96, height = 96)
                                }
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                )
                            }
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
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

// ---------------------------------------------------------------- test pad

@Composable
private fun TestPadScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
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
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            DrawPad(
                strokeColor = Color(strokeColor),
                strokeWidth = strokeWidth,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                onGestureFinished = { raw ->
                    scope.launch {
                        val gestures = dao.getAll()
                        val templates = gestures.map { gesture ->
                            Recognizer.MultiTemplate(
                                gesture.name,
                                Recognizer.strokesFromString(gesture.template)
                            )
                        }
                        val match = Recognizer.recognizeMulti(raw, templates)
                        val gesture = match?.let { m ->
                            gestures.firstOrNull { it.name == m.template.name }
                        }
                        matched = gesture
                        result = if (gesture == null) {
                            "No match"
                        } else {
                            val score = match?.let { (it.score * 100).toInt() }
                            "Matched: ${gesture.name} " +
                                (if (score != null) "(score $score%) -> " else "-> ") +
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
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = matched != null,
            onClick = { matched?.let { ActionExecutor.execute(context, it) } },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run action", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    val colors = listOf(
        0xFFFF3B30L to "Red",
        0xFFEDE6D0L to "Cream",
        0xFF8ECDF0L to "Blue",
        0xFFFFFFFFL to "White",
        0xFF4CAF50L to "Green",
        0xFFFFC107L to "Amber"
    )

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
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Stroke colour", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                colors.forEach { (value, name) ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(value))
                            .border(
                                width = if (strokeColor == value) 3.dp else 1.dp,
                                color = if (strokeColor == value) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                scope.launch { settings.setStrokeColor(value) }
                            }
                    )
                }
            }
            Text("Selected: ${colors.firstOrNull { it.first == strokeColor }?.second ?: "Custom"}")
        }
        item {
            Text("Stroke width: ${strokeWidth.toInt()} px", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = strokeWidth,
                onValueChange = { scope.launch { settings.setStrokeWidth(it) } },
                valueRange = 4f..24f
            )
        }
        item {
            Text("Camera permission", style = MaterialTheme.typography.titleMedium)
            Text(
                if (cameraGranted) {
                    "Granted. Flashlight actions will work."
                } else {
                    "Needed for the flashlight actions."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!cameraGranted) {
                Button(onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant camera permission") }
            }
        }
        item {
            Text("Triggers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add the Flick widget to your home screen: it is fully transparent, " +
                    "so it looks like empty space. Tap it to open the gesture pad. " +
                    "You can also add the Flick tile from the Quick Settings panel.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Note: Android widgets only support taps, not stroke tracking. " +
                    "Drawing happens in the transparent pad that opens when you " +
                    "tap the widget, not inside the widget itself.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
