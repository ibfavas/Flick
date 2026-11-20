package com.ibfavas.flick.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibfavas.flick.data.ActionType
import com.ibfavas.flick.data.Gesture
import com.ibfavas.flick.data.GestureDao
import java.text.DateFormat
import java.util.Date

internal enum class LibraryPage { Home, Gestures, Stats, History }

@Composable
internal fun LibraryNavigation(page: LibraryPage, onSelect: (LibraryPage) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            .shadow(5.dp, shape).clip(shape).background(colors.surface)
            .border(1.dp, colors.outline, shape).selectableGroup().padding(4.dp)
    ) {
        LibraryPage.entries.forEach { item ->
            val active = page == item
            val fill by androidx.compose.animation.animateColorAsState(
                if (active) colors.primary else Color.Transparent, label = "Destination")
            val tint = if (active) colors.onPrimary else colors.onSurfaceVariant
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .selectable(active, role = Role.Tab, onClick = { onSelect(item) })
                    .heightIn(min = 50.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(44.dp, 25.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                    when (item) {
                        LibraryPage.Home -> Icon(Icons.Filled.Home, null, Modifier.size(18.dp), tint = tint)
                        else -> {
                            val resource = when (item) {
                                LibraryPage.Gestures -> com.ibfavas.flick.R.drawable.widget_gesture
                                LibraryPage.Stats -> com.ibfavas.flick.R.drawable.ic_stats
                                else -> com.ibfavas.flick.R.drawable.ic_history
                            }
                            Icon(androidx.compose.ui.res.painterResource(resource), null, Modifier.size(18.dp), tint = tint)
                        }
                    }
                }
                Text(item.name, color = if (active) colors.onSurface else colors.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun HomeSummary(gestures: List<Gesture>, running: Boolean) {
    val colors = MaterialTheme.colorScheme
    val apps = remember(gestures) {
        gestures.filter { it.actionType == ActionType.LAUNCH_APP }.map { it.payload }.distinct().size
    }
    Text(
        buildAnnotatedString {
            append("Launch anything\nwith a ")
            withStyle(SpanStyle(color = colors.primary, fontStyle = FontStyle.Italic)) { append("gesture.") }
        },
        fontFamily = FontFamily.Serif, fontSize = 35.sp, lineHeight = 37.sp
    )
    Spacer(Modifier.height(14.dp))
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface)
            .background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = .10f), Color.Transparent)))
            .border(1.dp, colors.primary.copy(alpha = .22f), shape).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryNumber(gestures.size.toLong(), "Gestures")
        SummaryNumber(apps.toLong(), "Apps")
        SummaryNumber(gestures.sumOf { it.useCount }, "Uses")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (running) "Active" else "Inactive", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(7.dp).background(if (running) Color(0xff65cd75) else colors.onSurfaceVariant, CircleShape))
        }
    }
}

@Composable
private fun SummaryNumber(value: Long, label: String) {
    Column {
        Text(value.toString(), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun UsageStats(gestures: List<Gesture>) {
    val ranked = remember(gestures) { gestures.sortedByDescending { it.useCount } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.overviewCard(highlight = true), horizontalArrangement = Arrangement.SpaceAround) {
                SummaryNumber(gestures.sumOf { it.useCount }, "Total launches")
                SummaryNumber(gestures.size.toLong(), "Saved gestures")
            }
        }
        items(ranked, key = { it.id }) { entry ->
            Row(Modifier.overviewCard(), verticalAlignment = Alignment.CenterVertically) {
                GestureGlyph(entry.template, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                    Text("Launches", style = MaterialTheme.typography.labelSmall)
                }
                Text(entry.useCount.toString(), color = accentColor(), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
internal fun UsageHistory(history: List<GestureDao.Use>) {
    if (history.isEmpty()) {
        Column(Modifier.overviewCard(highlight = true), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.DateRange, null, tint = accentColor())
            Spacer(Modifier.height(8.dp))
            Text("No launches yet", style = MaterialTheme.typography.titleMedium)
            Text("Run a gesture to see it here.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val date = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val time = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(history) { entry ->
            Row(Modifier.overviewCard(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accentColor().copy(alpha = .1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.DateRange, null, tint = accentColor()) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.name, fontWeight = FontWeight.SemiBold)
                    Text(date.format(Date(entry.time)), style = MaterialTheme.typography.bodySmall)
                }
                Text(time.format(Date(entry.time)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun FlickWordmark(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
        val size = Modifier.size(52.dp, 21.dp)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.ibfavas.flick.R.drawable.wordmark_letters),
            "Flick", size)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.ibfavas.flick.R.drawable.wordmark_dot),
            null, size, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accentColor()))
    }
}

@Composable
private fun Modifier.overviewCard(highlight: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    return fillMaxWidth().clip(shape).background(colors.surface)
        .background(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = if (highlight) .12f else .025f), Color.Transparent)))
        .border(1.dp, if (highlight) colors.primary.copy(alpha = .25f) else colors.outline, shape).padding(14.dp)
}
