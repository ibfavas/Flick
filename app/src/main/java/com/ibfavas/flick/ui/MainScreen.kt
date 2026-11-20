package com.ibfavas.flick.ui

import com.ibfavas.flick.data.PadStyle
import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ibfavas.flick.R
import com.ibfavas.flick.actions.ActionExecutor
import kotlin.math.roundToInt
import com.ibfavas.flick.actions.Feedback
import com.ibfavas.flick.actions.FlickAccessibilityService
import com.ibfavas.flick.widget.FlickRoundWidgetProvider
import com.ibfavas.flick.widget.FlickWidgetProvider
import com.ibfavas.flick.widget.FlickWideWidgetProvider
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

private enum class Screen { LIST, DRAW, RECORD, TEST, SETTINGS }


private enum class GestureCategory(val label: String) {
    APPS("Apps"),
    ACTIONS("Actions"),
    WEBPAGES("Webpages"),
    PEOPLE("People");

    fun matches(gesture: Gesture): Boolean = when (this) {
        APPS -> gesture.actionType == ActionType.LAUNCH_APP
        WEBPAGES -> gesture.actionType == ActionType.OPEN_URL
        PEOPLE -> gesture.actionType == ActionType.CALL_CONTACT
        ACTIONS -> gesture.actionType != ActionType.LAUNCH_APP &&
            gesture.actionType != ActionType.OPEN_URL &&
            !PEOPLE.matches(gesture)
    }

    /** The first action type offered when adding from this tab. */
    fun defaultAction(): ActionType = when (this) {
        APPS -> ActionType.LAUNCH_APP
        WEBPAGES -> ActionType.OPEN_URL
        ACTIONS -> ActionType.FLASHLIGHT_ON
        PEOPLE -> ActionType.CALL_CONTACT
    }
}

private fun openSupportPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/flickapp")))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Install a browser to open this page.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(dao: GestureDao, settings: SettingsStore, openSettings: Boolean = false) {
    var screen by remember { mutableStateOf(if (openSettings) Screen.SETTINGS else Screen.LIST) }
    // The gesture a sample is being recorded for. While set, the add-sample
    // screen replaces the normal screen flow.
    val listState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var addingSampleFor by remember { mutableStateOf<Gesture?>(null) }

    // The new-gesture flow is two steps: DRAW (a dedicated
    // full-screen "Draw Gesture" screen) then RECORD (name +
    // action). The drawn samples and the tab the user came
    // from are held here so step 2 knows them, and "Add
    // another sample" can go back to step 1. recordSamples
    // is only non-empty while a draft is in progress.
    val draftState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var recordCategory by remember { mutableStateOf<GestureCategory?>(null) }
    var recordSamples by remember {
        mutableStateOf<List<List<List<StrokePoint>>>>(emptyList())
    }
    // Where back from DRAW goes: DRAW is entered from RECORD
    // in two ways, and they must not be confused, or back
    // loops between the two screens forever. True only when
    // RECORD sent the user to DRAW to append one more sample
    // ("Add another sample"): back then returns to RECORD.
    // False in the fresh flow: back then leaves the flow for
    // the home list, abandoning the draft.
    var drawReturnToRecord by remember { mutableStateOf(false) }

    fun exitDrawFlow() {
        recordCategory = null
        recordSamples = emptyList()
        drawReturnToRecord = false
        screen = Screen.LIST
    }

    fun backFromDraw() {
        if (drawReturnToRecord) {
            screen = Screen.RECORD
        } else {
            exitDrawFlow()
        }
    }

    fun backFromRecord() {
        recordSamples = recordSamples.dropLast(1)
        drawReturnToRecord = false
        screen = Screen.DRAW
    }

    BackHandler(enabled = addingSampleFor != null || screen != Screen.LIST) {
        when {
            addingSampleFor != null -> addingSampleFor = null
            screen == Screen.DRAW -> backFromDraw()
            screen == Screen.RECORD -> backFromRecord()
            else -> screen = Screen.LIST
        }
    }
    // Every screen sits inside a Surface: without one, Text
    // without an explicit colour falls back to LocalContentColor,
    // which defaults to black, dark text on the dark
    // background. The Surface provides the theme's
    // onBackground (white) as the content colour.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Edge-to-edge: keep every screen below the status bar and above
        // the navigation bar. Screens below use contentWindowInsets =
        // WindowInsets(0, 0, 0, 0) so this padding is not applied twice.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (addingSampleFor != null) {
                AddSampleScreen(
                    dao = dao,
                    settings = settings,
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
                        Screen.LIST -> listState.SaveableStateProvider("library") { GestureListScreen(
                            dao = dao,
                            settings = settings,
                            onAdd = { category ->
                                draftState.removeState("record")
                                recordCategory = category
                                recordSamples = emptyList()
                                drawReturnToRecord = false
                                screen = Screen.DRAW
                            },
                            onSettings = { screen = Screen.SETTINGS },
                            onAddSample = { gesture ->
                                addingSampleFor = gesture
                            }
                        ) }
                        Screen.DRAW -> DrawGestureScreen(
                            dao = dao,
                            category = recordCategory,
                            existingSamples = recordSamples,
                            settings = settings,
                            onBack = { backFromDraw() },
                            onDone = { allSamples ->
                                recordSamples = allSamples
                                screen = Screen.RECORD
                            }
                        )
                        Screen.RECORD -> draftState.SaveableStateProvider("record") { RecordScreen(
                            dao = dao,
                            settings = settings,
                            initialCategory = recordCategory,
                            initialSamples = recordSamples,
                            onAddSample = {
                                drawReturnToRecord = true
                                screen = Screen.DRAW
                            },
                            onRedraw = {
                                recordSamples = emptyList()
                                drawReturnToRecord = false
                                screen = Screen.DRAW
                            },
                            onBack = { backFromRecord() },
                            onSaved = {
                                recordCategory = null
                                recordSamples = emptyList()
                                drawReturnToRecord = false
                                screen = Screen.LIST
                            }
                        ) }
                        Screen.TEST -> TestPadScreen(
                            dao = dao,
                            settings = settings,
                            onBack = { screen = Screen.LIST }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            dao = dao,
                            onBack = { screen = Screen.LIST },
                            onTest = { screen = Screen.TEST }
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.accentHalo(accent: Color): Modifier = drawBehind {
    if (size.width > 0f) drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
        listOf(accent.copy(alpha = .12f), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(size.width, 0f), radius = size.width * .7f))
}

// list

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GestureListScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onAdd: (GestureCategory) -> Unit,
    onSettings: () -> Unit,
    onAddSample: (Gesture) -> Unit
) {
    val context = LocalContext.current
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    var deleting by remember { mutableStateOf<Gesture?>(null) }
    var detail by remember { mutableStateOf<Gesture?>(null) }
    // Tabs are back without an "All" tab. Default: Apps.
    var page by rememberSaveable { mutableStateOf(LibraryPage.Home) }
    BackHandler(enabled = page != LibraryPage.Home) { page = LibraryPage.Home }
    val running by FlickAccessibilityService.running.collectAsState()
    val history by dao.history.collectAsState(initial = emptyList())
    var category by rememberSaveable { mutableStateOf(GestureCategory.APPS) }
    val filtered = remember(gestures, category) {
        gestures.filter { category.matches(it) }
    }
    // Sort: creation order by default, or name A to Z.
    // The dao returns name order, so creation order sorts back
    // to insertion order by id.
    val sortOrder by settings.sortOrder.collectAsState(
        initial = SettingsStore.DEFAULT_SORT_ORDER
    )
    val displayed = remember(filtered, sortOrder) {
        if (sortOrder == SettingsStore.SORT_USED) {
            filtered.sortedWith(compareByDescending<Gesture> { it.useCount }.thenBy { it.name.lowercase() })
        } else if (sortOrder == SettingsStore.SORT_AZ) {
            filtered.sortedBy { it.name.lowercase() }
        } else {
            filtered.sortedBy { it.id }
        }
    }
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = false
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )

    Scaffold(
        // The MainScreen root already applies statusBarsPadding and
        // navigationBarsPadding, so the Scaffold must not add its own
        // system insets on top, or the content would sit too low.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        bottomBar = { LibraryNavigation(page) { page = it } },
        floatingActionButton = {
            if (page == LibraryPage.Home || page == LibraryPage.Gestures)
            androidx.compose.material3.FloatingActionButton(
                onClick = { onAdd(category) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = FlickBrand.OnAccent,
                modifier = Modifier.pressScale()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add gesture to ${category.label}")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .accentHalo(accentColor())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
        ) {
            // Home page: wordmark header with a small support
            // mug and settings gear, count line, category tabs
            // (no "All" tab), cards, and the + Add button. The
            // gear is the discoverable way into Settings; the
            // overlay pad's gear and the detail sheet's Settings
            // entry still work too.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    FlickWordmark()
                    Text("Gesture Launcher", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { openSupportPage(context) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mug),
                        contentDescription = "Buy me a coffee"
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gear_outline),
                        contentDescription = "Settings",
                        tint = accentColor()
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (page == LibraryPage.Stats || page == LibraryPage.History) {
                Text(page.name, fontFamily = FontFamily.Serif, fontSize = 32.sp)
                Spacer(Modifier.height(16.dp))
                if (page == LibraryPage.Stats) UsageStats(gestures) else UsageHistory(history)
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(if (page == LibraryPage.Gestures) 12.dp else 8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)) {
                item {
                    Column {
                        if (page == LibraryPage.Home) HomeSummary(gestures, running)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GestureCategory.entries.forEach { tab ->
                                CategoryTab(
                                    tab = tab,
                                    selected = category == tab,
                                    onClick = { category = tab }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Gestures  ${filtered.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            var sortMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { sortMenuOpen = true }) {
                                    Text(
                                        when (sortOrder) { SettingsStore.SORT_AZ -> "A-Z"; SettingsStore.SORT_USED -> "Most used"; else -> "Created" },
                                        color = accentColor()
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = accentColor()
                                    )
                                }
                                FlickMenu(sortMenuOpen, { sortMenuOpen = false }) {
                                    for ((order, label) in listOf(SettingsStore.SORT_USED to "Most used",
                                        SettingsStore.SORT_CREATED to "Creation order", SettingsStore.SORT_AZ to "Name (A to Z)")) {
                                        FlickMenuItem(label, if (order == sortOrder) Icons.Filled.Check else Icons.AutoMirrored.Filled.List) {
                                            sortMenuOpen = false
                                            scope.launch { settings.setSortOrder(order) }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                if (filtered.isEmpty()) {
                    item { Text("No ${category.label.lowercase()} yet", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)) }
                } else {
                    items(displayed, key = { it.id }) { gesture ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(250)) +
                                expandVertically(animationSpec = tween(250)),
                            modifier = Modifier.animateItem()
                        ) {
                            GestureCard(
                                gesture = gesture,
                                onClick = { detail = gesture },
                                onEdit = { editing = gesture },
                                onDelete = { deleting = gesture },
                                onDuplicate = { scope.launch {
                                    dao.duplicate(gesture)
                                } },
                                onTest = { ActionExecutor.execute(context, gesture, Feedback(vibrateOnRun, soundOnRun)) }
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
                    // Duplicate names made recognition ambiguous, so
                    // a rename that collides gets auto-suffixed.
                    val unique = Gesture.uniqueName(name, gestures, excludeId = gesture.id)
                    dao.update(gesture.copy(name = unique, actionType = type, payload = payload))
                    editing = null
                }
            }
        )
    }

    deleting?.let { gesture ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete gesture?") },
            text = { Text("Delete \"${gesture.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.delete(gesture)
                        deleting = null
                        if (snackbar.showSnackbar("Deleted ${gesture.name}", "Undo",
                            duration = androidx.compose.material3.SnackbarDuration.Long) == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            dao.restore(gesture)
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }

    // Gesture detail bottom sheet: the drawn gesture large
    // on a card, an action row (edit, enable/disable, delete,
    // test), and the name + description below. The sheet reads
    // the gesture from the list flow, so toggling or testing
    // it always sees the latest state after the dao updates it.
    detail?.let { shown ->
        val live = gestures.firstOrNull { it.id == shown.id } ?: shown
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GestureDetailSheet(
                gesture = live,
                onEdit = {
                    detail = null
                    editing = live
                },
                onToggleEnabled = {
                    scope.launch {
                        dao.update(live.copy(enabled = !live.enabled))
                    }
                },
                onDelete = {
                    detail = null
                    deleting = live
                },
                onTest = {
                    ActionExecutor.execute(
                        context,
                        live,
                        Feedback(vibrateOnRun, soundOnRun)
                    )
                },
                onAddSample = {
                    detail = null
                    onAddSample(live)
                }
            )
        }
    }
}

@Composable
private fun CategoryTab(tab: GestureCategory, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.clip(CircleShape)
        .background(if (selected) accentColor() else MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (tab == GestureCategory.PEOPLE) Icons.Filled.Person else iconFor(tab.defaultAction()),
            null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GestureCard(
    gesture: Gesture,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onTest: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val sampleText =
        "${gesture.sampleCount()} sample${if (gesture.sampleCount() == 1) "" else "s"}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .graphicsLayer { alpha = if (gesture.enabled) 1f else 0.55f }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GestureGlyph(
            template = gesture.template,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ActionIcon(gesture, size = 32.dp, iconSize = 24.dp, circular = true)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(if (GestureCategory.PEOPLE.matches(gesture)) ActionExecutor.contactName(gesture.payload) else gesture.name)
                }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    append("\n")
                    if (GestureCategory.PEOPLE.matches(gesture))
                        append("Call · ")
                    append(sampleText)
                }
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onTest, enabled = gesture.enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.PlayArrow, "Test ${gesture.name}", Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, "Actions for ${gesture.name}")
            }
            FlickMenu(menu, { menu = false }) {
                FlickMenuItem("Edit", Icons.Filled.Create) { menu = false; onEdit() }
                FlickMenuItem("Delete", Icons.Filled.Delete, destructive = true) { menu = false; onDelete() }
                FlickMenuItem("Duplicate", null) { menu = false; onDuplicate() }
                FlickMenuItem("Test", Icons.Filled.PlayArrow, enabled = gesture.enabled) { menu = false; onTest() }
            }
        }
    }
}

/** The target app's display name, cached per process. */
private object AppLabelCache {
    val labels = mutableStateMapOf<String, String?>()
}

@Composable
private fun appLabel(packageName: String): String? {
    val context = LocalContext.current
    val cached = AppLabelCache.labels[packageName]
    LaunchedEffect(packageName) {
        if (!AppLabelCache.labels.containsKey(packageName)) {
            val label = withContext(Dispatchers.IO) {
                try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: Exception) {
                    null
                }
            }
            AppLabelCache.labels[packageName] = label
        }
    }
    return cached
}

/**
 * The gesture detail bottom sheet: the drawn gesture large
 * on a card, an action row (edit, enable/disable, delete,
 * test), and the name + description below. Edit opens the
 * edit dialog, the toggle enables/disables the gesture,
 * delete asks for confirmation, and test runs the gesture's
 * action. An "Add sample" button at the bottom opens the
 * sample-capture pad for this gesture; it is disabled once
 * the gesture already has 3 samples.
 */
@Composable
private fun GestureDetailSheet(
    gesture: Gesture,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onAddSample: () -> Unit
) {
    val appLabel = if (gesture.actionType == ActionType.LAUNCH_APP) {
        appLabel(gesture.payload)
    } else {
        null
    }
    val description = remember(gesture, appLabel) {
        gestureDescription(gesture, appLabel)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        GestureGlyph(
            template = gesture.template,
            modifier = Modifier.size(112.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            gesture.name,
            fontSize = 28.sp, fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(gesture, size = 20.dp, iconSize = 12.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        SampleProgress(gesture.sampleCount(), "${gesture.sampleCount()} of ${Gesture.MAX_SAMPLES} samples")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Create, contentDescription = "Edit")
                }
                Text("Edit", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = gesture.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
                Text(
                    if (gesture.enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Text("Delete", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    enabled = gesture.enabled,
                    onClick = onTest
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Test")
                }
                Text("Test", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!gesture.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Disabled, this gesture will not run from the pad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onAddSample,
            enabled = gesture.sampleCount() < Gesture.MAX_SAMPLES,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add sample")
        }
    }
}

/**
 * The gesture itself, drawn small: the stored template strokes scaled
 * into a chip in the selected accent colour. Each card shows the shape to draw, so the
 * list is readable at a glance.
 */
@Composable
internal fun GestureGlyph(template: String, modifier: Modifier = Modifier) {
    val strokes = remember(template) { Recognizer.strokesFromString(template) }
    // Glyphs follow the app accent, read here because the
    // Canvas draw scope below is not a composable scope.
    val accent = accentColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = .045f))
            .border(1.dp, accent.copy(alpha = .48f), RoundedCornerShape(8.dp))
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
                drawPath(path, accent, style = style)
            }
        }
    }
}

/**
 * An empty sample slot: a thin dashed-outline box the same
 * size as a filled sample glyph, showing where the next sample goes.
 */
@Composable
private fun SampleSlotPlaceholder(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier.drawBehind {
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        0f, 0f, size.width, size.height,
                        14.dp.toPx(), 14.dp.toPx()
                    )
                )
            }
            drawPath(
                path,
                outline,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f
                    )
                )
            )
        }
    )
}

private fun gestureDescription(gesture: Gesture, appLabel: String? = null): String {
    val base = when (gesture.actionType) {
        ActionType.LAUNCH_APP -> "Launches ${appLabel ?: gesture.payload}"
        ActionType.OPEN_URL -> "Opens ${gesture.payload}"
        else -> ActionExecutor.describe(gesture)
    }
    val strokes = Recognizer.strokesFromString(gesture.template).size
    val samples = gesture.sampleCount()
    var text = base
    if (strokes > 1) text += ", $strokes strokes"
    if (samples > 1) text += ", $samples samples"
    if (!gesture.enabled) text += " · Disabled"
    return text
}

// The target icons live in ui/TargetIcon.kt (shared with the pad).

/**
 * The gesture name field: an underline field, no box, no
 * border. Centred text over a single thin grey underline,
 * with a small grey label centred above it. [fieldFraction]
 * is how wide the text field is relative to its parent:
 * the record form centres it at 70%, the (already narrow)
 * edit dialog uses the full width.
 */
@Composable
private fun UnderlineNameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fieldFraction: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(accentColor()),
            modifier = Modifier.fillMaxWidth(fieldFraction)
        ) { innerTextField ->
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    innerTextField()
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
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
    // Same auto-fill rule as the record form: an app pick
    // fills the name only while it is blank or holds a previous
    // auto-fill, never over a name the user typed.
    var nameAutoFilled by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf(ActionSelection(gesture.actionType, gesture.payload)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            GestureGlyph(gesture.template, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Text("Edit gesture", fontFamily = FontFamily.Serif)
        } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                UnderlineNameField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameAutoFilled = false
                    },
                    label = "Name",
                    fieldFraction = 1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionPicker(
                    action = action,
                    onChange = { action = it },
                    onAppPicked = { label ->
                        if (name.isBlank() || nameAutoFilled) {
                            name = label
                            nameAutoFilled = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SampleProgress(sampleCount, "$sampleCount of ${Gesture.MAX_SAMPLES} samples")
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

// draw gesture

/**
 * The dedicated "Draw Gesture" screen: a top bar with back,
 * the title and a Done button (disabled until a gesture is
 * drawn), and a large full-screen draw area below. The
 * squiggle + "Draw here" hint shows while the area is empty.
 * Drawing is the same multi-stroke pad as the record screen
 * (900 ms pen-up timer, strokes stay visible). Done returns
 * the drawn gesture appended to the samples already kept,
 * and the caller moves on to the name + action step.
 */
@Composable
private fun DrawGestureScreen(
    dao: GestureDao,
    category: GestureCategory?,
    existingSamples: List<List<List<StrokePoint>>>,
    settings: SettingsStore,
    onBack: () -> Unit,
    onDone: (List<List<List<StrokePoint>>>) -> Unit
) {
    val strokeWidthPx by settings.strokeWidth.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX
    )
    var captured by remember { mutableStateOf<List<List<StrokePoint>>?>(null) }
    var drawn by remember { mutableStateOf(false) }
    var padKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<Gesture?>(null) }
    conflict?.let { existing ->
        AlertDialog(onDismissRequest = { conflict = null },
            shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(Icons.Filled.Info, null, tint = accentColor()) },
            title = { Text("Similar gesture", fontFamily = FontFamily.Serif) },
            text = { Text("This resembles “${existing.name}”. Redraw it or keep both.") },
            confirmButton = { TextButton(onClick = {
                conflict = null
                captured?.let { onDone(existingSamples + listOf(it)) }
            }) { Text("Keep gesture") } },
            dismissButton = { TextButton(onClick = {
                conflict = null
                captured = null
                drawn = false
                padKey++
            }) { Text("Redraw") } })
    }

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
            Column(Modifier.weight(1f)) { FlowHeading("Step 1 of 2", "Draw gesture") }
            TextButton(
                enabled = captured != null && !checking,
                onClick = {
                    captured?.let { sample ->
                        checking = true
                        scope.launch {
                            val library = dao.getAll()
                            // Compare each gesture independently. Two identical existing shapes
                            // would trigger the library matcher's ambiguity rejection.
                            conflict = withContext(Dispatchers.Default) {
                                library.firstOrNull { Recognizer.recognizeMulti(sample, it.multiTemplates()) != null }
                            }
                            checking = false
                            if (conflict == null) onDone(existingSamples + listOf(sample))
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done")
            }
        }
        SampleProgress(existingSamples.size, "Sample ${existingSamples.size + 1} of ${Gesture.MAX_SAMPLES}")
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (captured == null) {
                // A fresh pad per draw: key() drops the old DrawPad
                // state (its strokes, timer and hint) when a sample
                // is kept or redrawn.
                key(padKey) {
                    DrawPad(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        onGestureFinished = { captured = it },
                        onCleared = { captured = null },
                        onFirstStroke = { drawn = true },

                        strokeWidth = strokeWidthPx
                    )
                }
                // Centred hint while nothing is drawn yet. It has no
                // input handler, so touches pass straight through to
                // the pad underneath.
                if (!drawn) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SquiggleIcon(modifier = Modifier.size(72.dp, 48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Draw here",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // The pad clears its strokes once the gesture is
                // captured, so show the captured gesture itself.
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GestureGlyph(
                        template = Recognizer.strokesToString(captured!!),
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Gesture captured: ${captured!!.size} stroke" +
                            if (captured!!.size == 1) "" else "s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(enabled = !checking, onClick = {
                        captured = null
                        drawn = false
                        padKey++
                    }) {
                        Text("Redraw")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            when (category) {
                GestureCategory.APPS -> "Then choose the app it launches."
                GestureCategory.ACTIONS -> "Then choose the phone action it runs."
                GestureCategory.WEBPAGES -> "Then choose the webpage it opens."
                GestureCategory.PEOPLE -> "Then choose a person to call."
                else -> "Then name it and choose what it does."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A small hand-drawn squiggle, the "Draw here" hint glyph. */
@Composable
private fun SquiggleIcon(modifier: Modifier = Modifier) {
    val accent = accentColor()
    Canvas(modifier = modifier) {
        val style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val path = Path().apply {
            moveTo(size.width * 0.05f, size.height * 0.6f)
            cubicTo(
                size.width * 0.2f, size.height * 0.1f,
                size.width * 0.35f, size.height * 1.0f,
                size.width * 0.5f, size.height * 0.5f
            )
            cubicTo(
                size.width * 0.65f, size.height * 0.0f,
                size.width * 0.8f, size.height * 0.9f,
                size.width * 0.95f, size.height * 0.4f
            )
        }
        drawPath(path, accent.copy(alpha = 0.7f), style = style)
    }
}

// record

@Composable
private fun RecordScreen(
    dao: GestureDao,
    settings: SettingsStore,
    initialCategory: GestureCategory?,
    initialSamples: List<List<List<StrokePoint>>>,
    onAddSample: () -> Unit,
    onRedraw: () -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    // Step 2 of the new-gesture flow: the gesture is already
    // drawn on the dedicated Draw Gesture screen, so this
    // screen is only the name + action step. The drawn samples
    // arrive here and are shown as a preview; "Add another
    // sample" goes back to the draw screen, and "Redraw"
    // starts the drawing over. The action list is locked to
    // the tab the user came from (contextual Add): Apps offers
    // only apps, Actions only phone actions, Webpages only
    // links, People only contacts.
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    // True when the name came from an app pick: a later app
    // pick refills it, but a name the user typed by hand is never
    // overwritten. Editing the field by hand clears the flag.
    var nameAutoFilled by rememberSaveable { mutableStateOf(false) }
    var action by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver<ActionSelection, String>(
        save = { listOf(it.type.name, it.payload) },
        restore = { ActionSelection(ActionType.valueOf(it[0]), it[1]) }
    )) {
        mutableStateOf(
            ActionSelection(
                initialCategory?.defaultAction() ?: ActionType.LAUNCH_APP,
                ""
            )
        )
    }

    val accent = accentColor()
    val shape = RoundedCornerShape(18.dp)
    val category = initialCategory ?: GestureCategory.APPS
    val (bindingTitle, bindingSummary) = when (category) {
        GestureCategory.APPS -> "Launch an app" to "Open an app with this gesture."
        GestureCategory.WEBPAGES -> "Open a webpage" to "Open a link with this gesture."
        GestureCategory.ACTIONS -> "Run a phone action" to "Choose what your phone does."
        GestureCategory.PEOPLE -> "Call a contact" to "Choose a person to call."
    }
    Column(Modifier.fillMaxSize().accentHalo(accent).padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column {
                    FlowHeading("Step 2 of 2", "Bind gesture")
                    val subtitle = when (category) {
                        GestureCategory.APPS -> "Give it a name, add samples and choose an app to launch."
                        GestureCategory.WEBPAGES -> "Name your gesture and choose a webpage."
                        GestureCategory.ACTIONS -> "Name your gesture and choose a phone action."
                        GestureCategory.PEOPLE -> "Name your gesture and choose a contact."
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(value = name, onValueChange = {
                name = it
                nameAutoFilled = false
            }, modifier = Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).accentHalo(accent),
                shape = shape, singleLine = true, placeholder = { Text("Gesture name") },
                leadingIcon = { Icon(Icons.Filled.Create, null, tint = accent) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(unfocusedBorderColor = accent.copy(alpha = .45f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your gesture", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Row(Modifier.background(accent.copy(alpha = .10f), CircleShape)
                    .border(1.dp, accent.copy(alpha = .3f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_layers), null, Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(5.dp))
                    Text("${initialSamples.size} of ${Gesture.MAX_SAMPLES} samples", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(Gesture.MAX_SAMPLES) { index ->
                    val slot = Modifier.weight(1f).aspectRatio(1.15f)
                    if (index < initialSamples.size) {
                        Box(slot) {
                            GestureGlyph(Recognizer.strokesToString(initialSamples[index]), Modifier.fillMaxSize())
                            Icon(Icons.Filled.Check, "Sample ${index + 1} recorded",
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                                    .background(accent, CircleShape).padding(2.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Box(slot.clip(shape).clickable(onClickLabel = "Add sample", onClick = onAddSample),
                            contentAlignment = Alignment.Center) {
                            SampleSlotPlaceholder(Modifier.fillMaxSize())
                            Icon(Icons.Filled.Add, "Add sample", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRedraw, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Redraw")
                }
                OutlinedButton(onClick = onAddSample, enabled = initialSamples.size < Gesture.MAX_SAMPLES,
                    modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_layers_add), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add sample")
                }
            }
            Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
                .accentHalo(accent).border(1.dp, accent.copy(alpha = .3f), shape).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (category == GestureCategory.APPS) Icon(painterResource(R.drawable.ic_app_grid), null, tint = accent)
                    else Icon(iconFor(category.defaultAction()), null, tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(bindingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(bindingSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                ActionPicker(action, onChange = { action = it }, filter = initialCategory, onAppPicked = { label ->
                    if (name.isBlank() || nameAutoFilled) {
                        name = label
                        nameAutoFilled = true
                    }
                })
            }
        }
        Button(
            enabled = name.isNotBlank() && initialSamples.isNotEmpty() && action.isValid(),
            onClick = {
                scope.launch {
                    dao.insert(
                        Gesture(
                            name = Gesture.uniqueName(name, dao.getAll()),
                            template = Recognizer.strokesToString(initialSamples[0]),
                            actionType = action.type,
                            payload = action.payload,
                            extraTemplates = initialSamples.drop(1).take(Gesture.MAX_SAMPLES - 1)
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
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save gesture", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
    }
}

// add sample

/**
 * Record one more sample of an existing gesture. The new
 * sample is appended to the gesture's extra samples; the
 * recognizer matches against every sample and takes the
 * best score.
 */
@Composable
private fun AddSampleScreen(
    dao: GestureDao,
    settings: SettingsStore,
    gesture: Gesture,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val strokeWidthPx by settings.strokeWidth.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX
    )
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
                FlowHeading("Record sample", gesture.name)
            }
            SampleProgress(current, "Sample ${current + 1} of ${Gesture.MAX_SAMPLES}")
        }
        item {
            Text(
                "Draw the same gesture again",
                color = MaterialTheme.colorScheme.onSurface
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
                    onCleared = { captured = null },

                    strokeWidth = strokeWidthPx
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
                        // Defensive cap: allTemplates() already caps at
                        // MAX_SAMPLES, but never store more than the
                        // two extra slots even if the data is corrupt.
                        val extras = (gesture.allTemplates().drop(1) +
                            Recognizer.strokesToString(captured!!))
                            .take(Gesture.MAX_SAMPLES - 1)
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

// action picker

private data class ActionSelection(val type: ActionType, val payload: String) {
    fun isValid(): Boolean = when (type) {
        ActionType.LAUNCH_APP -> payload.isNotBlank()
        ActionType.OPEN_URL -> payload.isNotBlank()
        ActionType.CALL_CONTACT -> ActionExecutor.contactNumber(payload).isNotBlank()
        ActionType.CHAT_CONTACT -> ActionExecutor.contactNumber(payload).isNotBlank()
        else -> true
    }
}

@Composable
private fun ActionPicker(
    action: ActionSelection,
    onChange: (ActionSelection) -> Unit,
    filter: GestureCategory? = null,
    // Called with the picked app's label: the record form
    // fills the gesture name from it. Null where no name field
    // exists, so picking there changes nothing.
    onAppPicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAppPicker by remember { mutableStateOf(false) }
    var pickedAppLabel by remember(action.payload) { mutableStateOf<String?>(null) }

    // When editing an existing gesture the payload is a package name
    // and no label was picked yet in this dialog, so the button
    // showed the raw package name. Resolve the label once for the
    // current payload; picking a new app sets the label directly.
    LaunchedEffect(action.type, action.payload) {
        if (action.type == ActionType.LAUNCH_APP && action.payload.isNotBlank()) {
            val label = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationInfo(action.payload, 0)
                        .loadLabel(context.packageManager).toString()
                }.getOrNull()
            }
            if (label != null) pickedAppLabel = label
        }
    }

    // Contact picking needs two permissions: READ_CONTACTS to read the
    // contact, CALL_PHONE to place the call. Both are requested together;
    // picking still works if only the first is granted, and placing the
    // call guides the user if the second is missing.
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val contact = withContext(Dispatchers.IO) { runCatching { readContact(context, uri) }.getOrNull() }
                if (contact != null) {
                    onAppPicked?.invoke(contact.first)
                    onChange(
                        ActionSelection(
                            action.type,
                            "${contact.first}\n${contact.second}\n${contact.third}"
                        )
                    )
                } else Toast.makeText(context, "Choose a contact with a phone number", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val photo = withContext(Dispatchers.IO) { com.ibfavas.flick.data.ContactPhoto.read(context, uri) }
            if (photo != null) onChange(action.copy(payload = "${ActionExecutor.contactName(action.payload)}\n${ActionExecutor.contactNumber(action.payload)}\n$photo"))
            else Toast.makeText(context, "Could not open this image", Toast.LENGTH_SHORT).show()
        }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_CONTACTS] == true) {
            contactPicker.launch(null)
        } else {
            Toast.makeText(context, "Allow contact access to choose a person", Toast.LENGTH_SHORT).show()
        }
    }

    // Contextual Add: when the user adds from a tab, only
    // that tab's category is offered. No filter (edit dialog,
    // for example) means every category.
    val peopleTypes = listOf(ActionType.CALL_CONTACT, ActionType.CHAT_CONTACT)
    val choices = when (filter) {
        GestureCategory.APPS -> listOf(ActionType.LAUNCH_APP)
        GestureCategory.WEBPAGES -> listOf(ActionType.OPEN_URL)
        GestureCategory.PEOPLE -> listOf(ActionType.CALL_CONTACT)
        GestureCategory.ACTIONS -> ActionType.entries.filter { it != ActionType.LAUNCH_APP &&
            it != ActionType.OPEN_URL && it !in peopleTypes }
        null -> ActionType.entries.filter { it !in peopleTypes || it == action.type }
    }
    fun label(type: ActionType): String = when (type) {
        ActionType.LAUNCH_APP -> "Launch an app"
        ActionType.OPEN_URL -> "Open a link"
        ActionType.CALL_CONTACT -> "Call a contact"
        ActionType.CHAT_CONTACT -> "WhatsApp chat"
        else -> ActionExecutor.describeAction(type, "")
    }
    fun select(type: ActionType) {
        val keepPayload = (type in peopleTypes && action.type in peopleTypes) ||
            (type == ActionType.LAUNCH_APP && action.type == type)
        onChange(ActionSelection(type, if (keepPayload) action.payload else ""))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (filter == null) {
            choices.forEach { type -> ActionOption(label(type), action.type == type) { select(type) } }
        } else if (choices.size > 1) {
            var open by remember { mutableStateOf(false) }
            Box {
                BindingPickerRow(label(action.type), { open = true }) {
                    Icon(iconFor(action.type), null, tint = accentColor())
                }
                FlickMenu(open, { open = false }) {
                    choices.forEach { type ->
                        FlickMenuItem(label(type), iconFor(type)) {
                            select(type)
                            open = false
                        }
                    }
                }
            }
        }
        when (action.type) {
            ActionType.LAUNCH_APP -> BindingPickerRow(pickedAppLabel ?: action.payload.ifBlank { "Choose app" },
                { showAppPicker = true }) {
                if (action.payload.isNotBlank()) AppRowIcon(context, action.payload)
                else Icon(painterResource(R.drawable.ic_app_grid), null, tint = accentColor())
            }
            ActionType.OPEN_URL -> OutlinedTextField(value = action.payload,
                onValueChange = { onChange(action.copy(payload = it)) }, label = { Text("URL, e.g. example.com") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            ActionType.CALL_CONTACT, ActionType.CHAT_CONTACT -> {
                BindingPickerRow(ActionExecutor.contactName(action.payload).ifBlank { "Choose contact" }, {
                    contactPermissionLauncher.launch(if (action.type == ActionType.CHAT_CONTACT)
                        arrayOf(Manifest.permission.READ_CONTACTS) else arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                }) { ContactIcon(action.type, action.payload, size = 32.dp) }
                if (ActionExecutor.contactNumber(action.payload).isNotBlank()) {
                    TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Choose custom photo") }
                }
            }
            else -> {
                val hint = when (action.type) {
                    ActionType.LOCK_SCREEN, ActionType.TAKE_SCREENSHOT -> "Requires Accessibility service."
                    ActionType.TOGGLE_WIFI -> "Opens the Wi-Fi panel."
                    ActionType.TOGGLE_BLUETOOTH -> "Opens Bluetooth settings."
                    ActionType.TOGGLE_DND -> "Requires Do Not Disturb access."
                    else -> null
                }
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onAppPicked?.invoke(label)
                showAppPicker = false
            }
        )
    }
}

/**
 * Read a contact's display name and first phone number from the Uri
 * returned by the system contact picker. Runs on a background thread.
 */
private fun readContact(context: Context, uri: Uri): Triple<String, String, String>? {
    val resolver = context.contentResolver
    var photo: String? = null
    var id: String? = null
    var name: String? = null
    resolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            id = cursor.getString(0)
            name = cursor.getString(1)
            photo = cursor.getString(2)
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
    return if (number.isNullOrBlank()) null else Triple(name ?: number!!, number!!, photo?.let { com.ibfavas.flick.data.ContactPhoto.read(context, Uri.parse(it)) }.orEmpty())
}

@Composable
private fun BindingPickerRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(accentColor().copy(alpha = .08f))
        .border(1.dp, accentColor().copy(alpha = .55f), shape).clickable(onClick = onClick)
        .heightIn(min = 54.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon()
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun ActionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
        .background(if (selected) colors.primary.copy(alpha = .10f) else colors.surface)
        .border(1.dp, if (selected) colors.primary.copy(alpha = .55f) else colors.outline, shape)
        .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        RadioButton(selected = selected, onClick = null)
    }
}

private data class LaunchableApp(val label: String, val packageName: String)

/**
 * The app list, cached for the life of the process. The
 * PackageManager query is slow (hundreds of apps), so it
 * runs once on a background thread and every later opening
 * of the picker is instant. Icons are loaded lazily per
 * visible row, also off the main thread, and cached the same
 * way.
 */
private object AppListCache {
    var apps: List<LaunchableApp>? = null
    val icons = mutableStateMapOf<String, ImageBitmap?>()
}

/**
 * The "Choose app" picker, in Flick's own design: a pitch-
 * black sheet with rounded corners, a search field, large
 * icons and the app name only, no package names, which are
 * technical clutter. The list loads once on Dispatchers.IO
 * into AppListCache, icons load lazily per visible row, and
 * packages are deduped. Shared by the add and edit screens.
 */
@Composable
private fun AppPickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit
) {
    var apps by remember { mutableStateOf(AppListCache.apps) }
    var query by remember { mutableStateOf("") }
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
    // Live search: typing filters the list by app name as you type.
    val shown = remember(apps, query) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Choose app", fontFamily = FontFamily.Serif, fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (apps == null) {
                    Text(
                        "Loading apps...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else if (shown.isEmpty()) {
                    Text(
                        if (query.isBlank()) "No apps found"
                        else "No apps match \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onPick(app.label, app.packageName) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppRowIcon(context = context, packageName = app.packageName)
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancel",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
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
                .size(48.dp)
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(48.dp).padding(end = 12.dp)
        )
    }
}

// test pad

@Composable
private fun TestPadScreen(
    dao: GestureDao,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = false
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    val strokeWidthPx by settings.strokeWidth.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX
    )
    var matched by remember { mutableStateOf<Gesture?>(null) }
    var score by remember { mutableStateOf<Int?>(null) }
    var noMatch by remember { mutableStateOf(false) }

    // The gesture library, observed so the prepared templates
    // stay fresh when gestures are added, edited or deleted.
    // Prepared once per library change: normalising every
    // template variant on every recognition was the pad's
    // main CPU cost.
    val gestures by dao.observeAll().collectAsState(initial = emptyList())
    val strictness by settings.strictness.collectAsState(initial = Recognizer.DEFAULT_THRESHOLD)
    val matcher = remember(gestures) { com.ibfavas.flick.gesture.LibraryMatcher(gestures) }
    val matchedGesture = matched
    val matchedAppLabel = if (matchedGesture?.actionType == ActionType.LAUNCH_APP) {
        appLabel(matchedGesture.payload)
    } else {
        null
    }

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
            FlowHeading("Recognition", "Test pad")
        }
        Text(
            "Draw to match. Tap Run to launch.",
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
                        // Recognition is CPU work: off the main
                        // thread, so the pad stays responsive while
                        // it runs.
                        val match = withContext(Dispatchers.Default) { matcher.match(raw, strictness) }
                        val gesture = match?.first
                        matched = gesture
                        score = match?.let { (it.second * 100).toInt() }
                        noMatch = gesture == null
                        if (vibrateOnRun) ActionExecutor.vibrate(context, success = gesture != null)
                    }
                },
                onCleared = {
                    matched = null
                    score = null
                    noMatch = false
                },

                strokeWidth = strokeWidthPx
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedVisibility(
            visible = matchedGesture != null || noMatch,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            val text = when {
                matchedGesture != null -> "Matched ${matchedGesture.name}, ${score ?: 0}%\n" +
                    gestureDescription(matchedGesture, matchedAppLabel)
                noMatch -> "No match, try again"
                else -> ""
            }
            Text(text, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,
                RoundedCornerShape(20.dp)).padding(14.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            enabled = matched != null,
            onClick = {
                matched?.let {
                    ActionExecutor.execute(context, it, Feedback(vibrateOnRun, soundOnRun), recognized = true)
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

// settings

// settings

/**
 * Settings: Permissions, Overlay, Triggers and Reliability.
 * Every label is white or light grey on the black
 * background, never dark text on a dark surface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    settings: SettingsStore,
    dao: GestureDao,
    onBack: () -> Unit,
    onTest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurPx by settings.overlayBlur.collectAsState(initial = SettingsStore.DEFAULT_BLUR_PX)
    val opacity by settings.overlayOpacity.collectAsState(initial = SettingsStore.DEFAULT_OPACITY)
    val timeoutSeconds by settings.overlayTimeout.collectAsState(initial = SettingsStore.DEFAULT_TIMEOUT_S)
    val vibrateOnRun by settings.vibrateOnRun.collectAsState(
        initial = false
    )
    val soundOnRun by settings.soundOnRun.collectAsState(
        initial = SettingsStore.DEFAULT_SOUND_ON_RUN
    )
    val strokeWidthPx by settings.strokeWidth.collectAsState(
        initial = SettingsStore.DEFAULT_STROKE_WIDTH_PX
    )
    val accentColorArgb by settings.accentColor.collectAsState(
        initial = SettingsStore.DEFAULT_ACCENT_COLOR_ARGB
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
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
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
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Cannot open backup")
                    val result = dao.importBackup(text)
                    if (result.error != null) {
                        withContext(Dispatchers.Main) { backupMessage = result.error }
                    } else {
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

    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().accentHalo(accentColor()).padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        settingsSection("Overlay", "Adjust the look and timing of the pad.", onBack) {
            SettingSlider(
                label = "Background Blur",
                valueText = "$blurPx px",
                value = blurPx.toFloat(),
                range = 0f..SettingsStore.MAX_BLUR_PX.toFloat(),
                steps = SettingsStore.MAX_BLUR_PX - 1,
                description = "Soften the background behind the pad.",
                onChange = { scope.launch { settings.setOverlayBlur(it.toInt()) } }
            )
            HorizontalDivider()
            SettingSlider(
                label = "Overlay Opacity",
                valueText = "${(opacity * 100).toInt()}%",
                value = opacity,
                range = 0f..1f,
                steps = 0,
                description = "Dim the screen behind the pad.",
                onChange = { scope.launch { settings.setOverlayOpacity(it) } }
            )
            HorizontalDivider()
            SettingSlider(
                label = "Overlay Timeout",
                valueText = if (timeoutSeconds == 0) "Off" else "$timeoutSeconds s",
                value = timeoutSeconds.toFloat(),
                range = 0f..SettingsStore.MAX_TIMEOUT_S.toFloat(),
                steps = SettingsStore.MAX_TIMEOUT_S - 1,
                description = "Close the idle pad after this delay.",
                detail = "Once you start drawing, the pad stays open until you finish the gesture.",
                onChange = { scope.launch { settings.setOverlayTimeout(it.toInt()) } }
            )
            HorizontalDivider()
            SettingSlider(
                label = "Stroke Width",
                valueText = "${strokeWidthPx.toInt()} px",
                value = strokeWidthPx,
                range = SettingsStore.MIN_STROKE_WIDTH_PX..SettingsStore.MAX_STROKE_WIDTH_PX,
                steps = (SettingsStore.MAX_STROKE_WIDTH_PX - SettingsStore.MIN_STROKE_WIDTH_PX).toInt() - 1,
                description = "Width of the drawn line.",
                onChange = { scope.launch { settings.setStrokeWidth(it) } }
            )
            HorizontalDivider()
            val padStyle by settings.padStyle.collectAsState(initial = PadStyle.FULL_SCREEN)
            val card = padStyle == PadStyle.CARD
            SettingRow("Drawing pad style", if (card) "Card · Turn off for full screen." else "Full screen · Turn on for card.",
                value = {
                    Switch(checked = card, onCheckedChange = { enabled ->
                        scope.launch { settings.setPadStyle(if (enabled) PadStyle.CARD else PadStyle.FULL_SCREEN) }
                    }, modifier = Modifier.semantics { contentDescription = "Card drawing pad" })
                })
        }
        settingsSection("General", "Appearance, recognition and backups.") {
            SettingSwitch(
                title = "Haptic feedback",
                description = "Vibrate on a match or miss.",
                checked = vibrateOnRun,
                onChange = { scope.launch { settings.setVibrateOnRun(it) } }
            )
            HorizontalDivider()
            AccentSetting(accentColorArgb) {
                scope.launch { settings.setAccentColor(it) }
            }
            HorizontalDivider()
            val threshold by settings.strictness.collectAsState(initial = Recognizer.DEFAULT_THRESHOLD)
            val range = .82f.. .92f
            val percent = ((threshold - range.start) / (range.endInclusive - range.start) * 100).roundToInt()
            SettingSlider("Match strictness", "$percent%", threshold, range, 9,
                "Higher values need a closer match.") { scope.launch { settings.setStrictness(it) } }
            HorizontalDivider()
            SettingRow("Test vibration", "Preview haptic feedback.", value = {
                TextButton(enabled = vibrateOnRun, onClick = { ActionExecutor.testVibrate(context) }) { Text("Test") }
            })
            HorizontalDivider()
            SettingSwitch(
                title = "Sound on run",
                description = "Play a tone on launch.",
                checked = soundOnRun,
                onChange = { scope.launch { settings.setSoundOnRun(it) } }
            )
            HorizontalDivider()
            var automatic by remember { mutableStateOf(com.ibfavas.flick.data.GestureBackupAgent.enabled(context)) }
            SettingSwitch("Auto backup", "Include gestures in Android backup and restore.", automatic) {
                automatic = it
                com.ibfavas.flick.data.GestureBackupAgent.setEnabled(context, it)
            }
            HorizontalDivider()
            SettingRow("Gesture backup", "Save or restore a backup file.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportLauncher.launch("flick-backup.json") }) { Text("Export") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import") }
                }
            }
            backupMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider()
            SettingRow("Open test pad", "Check gesture recognition.", value = { ValuePill("Open", onTest) })
            HorizontalDivider()
            SettingRow("Open battery settings", "Manage background power use.", value = {
                ValuePill("Open") { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            })

        }
        settingsSection("Permissions", "Access needed for phone actions.") {
            PermissionRow(
                title = "Appear over apps",
                description = "Display the drawing overlay.",
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
            HorizontalDivider()
            PermissionRow(
                title = "Accessibility service",
                description = "Lock and screenshot actions.",
                granted = accessibilityGranted,
                grantedText = "Enabled",
                onAllow = {
                    accessibilityLauncher.launch(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                },
                allowText = "Open"
            )
            HorizontalDivider()
            PermissionRow(
                title = "Camera",
                description = "Use the flashlight.",
                granted = cameraGranted,
                grantedText = "Granted",
                onAllow = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
        settingsSection("Widgets", "Add a shortcut to your home screen.") {
            WidgetRow(
                title = "Flick (Round)",
                description = "1 × 1 · Gesture pad shortcut",
                onAdd = { pinWidget(context, FlickRoundWidgetProvider::class.java) }
            )
            HorizontalDivider()
            WidgetRow(
                title = "Flick (Invisible)",
                description = "2 × 1 · Invisible tap area",
                onAdd = { pinWidget(context, FlickWidgetProvider::class.java) }
            )
            HorizontalDivider()
            WidgetRow(
                title = "Flick (Wide)",
                description = "3 × 1 · Flick wordmark",
                onAdd = { pinWidget(context, FlickWideWidgetProvider::class.java) }
            )
        }
        item(key = "support") {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.support_button), "Buy me a coffee",
                    Modifier.size(196.dp, 56.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { openSupportPage(context) })
            }
        }
    }
}

private fun overlayGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

@Composable
private fun WidgetRow(title: String, description: String, onAdd: () -> Unit) {
    SettingRow(title, description,
        detail = "Tap Add or choose Flick in your launcher’s widget picker. Tap the widget to draw on the pad. You can also add the Flick tile from Quick Settings.",
        value = { ValuePill("Add", onAdd) })
}

/**
 * Asks the launcher to pin the widget to the home screen (Android 8
 * and up, which is the app's minimum). Returns false when the launcher
 * refuses, in which case the user is told to use the widget picker.
 */
private fun pinWidget(context: Context, provider: Class<*>) {
    val appWidgetManager =
        context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
    val pinned = try {
        appWidgetManager.requestPinAppWidget(ComponentName(context, provider), null, null)
    } catch (e: Exception) {
        false
    }
    if (!pinned) {
        Toast.makeText(
            context,
            "Could not add it here. Add it from the widget picker instead.",
            Toast.LENGTH_LONG
        ).show()
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
    SettingRow(title, description, value = {
        if (granted) ValuePill(grantedText) else ValuePill(allowText, onAllow)
    })
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, description, value = { Switch(checked, onCheckedChange = onChange) })
}

@Composable
private fun FlowHeading(step: String, title: String) {
    Column {
        Text(step, style = MaterialTheme.typography.labelMedium, color = accentColor())
        Text(title, fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 34.sp)
    }
}

@Composable
private fun SampleProgress(count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(Gesture.MAX_SAMPLES) { i ->
            Box(Modifier.size(18.dp, 5.dp).background(
                if (i < count) accentColor() else MaterialTheme.colorScheme.outline, CircleShape))
        }
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
