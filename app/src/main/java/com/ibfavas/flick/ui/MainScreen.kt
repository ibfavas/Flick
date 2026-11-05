package com.ibfavas.flick.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
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

private enum class Tab { LIST, RECORD, TEST, SETTINGS }

@Composable
fun MainScreen(dao: GestureDao, settings: SettingsStore) {
    var tab by remember { mutableStateOf(Tab.LIST) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.LIST,
                    onClick = { tab = Tab.LIST },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("Gestures") }
                )
                NavigationBarItem(
                    selected = tab == Tab.RECORD,
                    onClick = { tab = Tab.RECORD },
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    label = { Text("Record") }
                )
                NavigationBarItem(
                    selected = tab == Tab.TEST,
                    onClick = { tab = Tab.TEST },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("Test pad") }
                )
                NavigationBarItem(
                    selected = tab == Tab.SETTINGS,
                    onClick = { tab = Tab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                Tab.LIST -> GestureListScreen(dao)
                Tab.RECORD -> RecordScreen(dao, settings) { tab = Tab.LIST }
                Tab.TEST -> TestPadScreen(dao, settings)
                Tab.SETTINGS -> SettingsScreen(settings)
            }
        }
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun GestureListScreen(dao: GestureDao) {
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    var deleting by remember { mutableStateOf<Gesture?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Flick", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Draw a gesture to launch things. Add the invisible widget to " +
                "your home screen, or use the Quick Settings tile.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (gestures.isEmpty()) {
            Text(
                "No gestures yet. Go to Record, draw a shape, and attach an action.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(gestures, key = { it.id }) { gesture ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(gesture.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    ActionExecutor.describe(gesture),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { editing = gesture }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { deleting = gesture }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
            Column {
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
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var name by remember { mutableStateOf("") }
    var captured by remember { mutableStateOf<List<StrokePoint>?>(null) }
    var action by remember { mutableStateOf(ActionSelection(ActionType.LAUNCH_APP, "")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Record a gesture", style = MaterialTheme.typography.headlineMedium)
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
                    .height(280.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                DrawPad(
                    strokeColor = Color(strokeColor),
                    strokeWidth = strokeWidth,
                    modifier = Modifier.fillMaxSize(),
                    onStrokeFinished = { captured = it }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (captured == null) "Nothing drawn yet"
                    else "Gesture captured (${captured!!.size} points)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { captured = null }) { Text("Clear") }
            }
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
                        val normalized = Recognizer.normalize(captured!!)
                        dao.insert(
                            Gesture(
                                name = name.trim(),
                                template = Recognizer.pointsToString(normalized),
                                actionType = action.type,
                                payload = action.payload
                            )
                        )
                        onSaved()
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Save gesture")
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
        apps = infos.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            LaunchableApp(
                label = info.loadLabel(pm).toString(),
                packageName = packageName,
                icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
            )
        }.sortedBy { it.label.lowercase() }
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
private fun TestPadScreen(dao: GestureDao, settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strokeColor by settings.strokeColor.collectAsState(initial = SettingsStore.DEFAULT_COLOR)
    val strokeWidth by settings.strokeWidth.collectAsState(initial = SettingsStore.DEFAULT_WIDTH)
    var result by remember { mutableStateOf<String?>(null) }
    var matched by remember { mutableStateOf<Gesture?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Test pad", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Draw here to see what Flick recognises. Nothing runs until you tap Run.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            DrawPad(
                strokeColor = Color(strokeColor),
                strokeWidth = strokeWidth,
                modifier = Modifier.fillMaxSize(),
                onStrokeFinished = { raw ->
                    scope.launch {
                        val gestures = dao.getAll()
                        val templates = gestures.map { gesture ->
                            Recognizer.Template(
                                gesture.name,
                                Recognizer.pointsFromString(gesture.template)
                            )
                        }
                        val match = Recognizer.recognize(raw, templates)
                        val gesture = match?.let { m ->
                            gestures.firstOrNull { it.name == m.template.name }
                        }
                        matched = gesture
                        result = if (gesture == null || match == null) {
                            "No match"
                        } else {
                            "Matched: ${gesture.name} " +
                                "(score ${(match.score * 100).toInt()}%) -> " +
                                ActionExecutor.describe(gesture)
                        }
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = matched != null,
            onClick = { matched?.let { ActionExecutor.execute(context, it) } }
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text("Run action")
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(settings: SettingsStore) {
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
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
