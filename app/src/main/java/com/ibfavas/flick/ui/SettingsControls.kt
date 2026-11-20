package com.ibfavas.flick.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibfavas.flick.R
import com.ibfavas.flick.data.SettingsStore

internal fun LazyListScope.settingsSection(
    title: String, subtitle: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    item(key = title) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            else Icon(Icons.Filled.Settings, null, Modifier.size(32.dp), tint = accentColor())
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = if (onBack != null) FontFamily.Serif else FontFamily.Default,
                    fontWeight = if (onBack != null) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = if (onBack != null) 32.sp else 18.sp)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item(key = "$title-card") {
        val shape = RoundedCornerShape(20.dp)
        val accent = accentColor()
        val rim = Brush.linearGradient(listOf(accent.copy(alpha = .85f), MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline, accent.copy(alpha = .7f)))
        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .085f), Color.Transparent,
                Color.Transparent, accent.copy(alpha = .07f))))
            .border(1.dp, rim, shape).padding(horizontal = 14.dp), content = content)
    }
}

@Composable
private fun SettingTile(label: String) {
    val accent = accentColor()
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
        .background(Brush.linearGradient(listOf(accent.copy(alpha = .15f), Color.Transparent)))
        .border(1.dp, accent.copy(alpha = .22f), shape), contentAlignment = Alignment.Center) {
        val glyph = when (label) {
            "Background Blur" -> R.drawable.setting_blur
            "Camera" -> R.drawable.setting_camera
            "Overlay Opacity" -> R.drawable.setting_opacity
            "Overlay Timeout" -> R.drawable.ic_history
            "Stroke Width", "Drawing pad style" -> R.drawable.widget_gesture
            "Accent colour" -> R.drawable.setting_palette
            else -> null
        }
        if (glyph != null) Icon(painterResource(glyph), null, Modifier.size(24.dp), tint = accent)
        else Icon(when (label) {
            "Accessibility service" -> Icons.Filled.Person
            "Appear over apps" -> Icons.Filled.Lock
            "Haptic feedback", "Test vibration" -> Icons.Filled.Notifications
            "Gesture backup", "Auto backup" -> Icons.Filled.Share
            "Open test pad" -> Icons.Filled.Create
            "Sound on run" -> Icons.Filled.PlayArrow
            "Flick (Round)", "Flick (Invisible)", "Flick (Wide)" -> Icons.Filled.Home
            else -> Icons.Filled.Settings
        }, null, Modifier.size(22.dp), tint = accent)
    }
}

@Composable
internal fun SettingRow(title: String, summary: String, detail: String? = null,
    value: @Composable (() -> Unit)? = null, body: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        SettingTile(title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false))
                        if (detail != null) InfoButton(title, detail)
                    }
                    Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (value != null) { Spacer(Modifier.width(6.dp)); value() }
            }
            body?.invoke()
        }
    }
}

@Composable
private fun InfoButton(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Filled.Info, "About $title", Modifier.size(16.dp), tint = accentColor())
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title, fontFamily = FontFamily.Serif) },
        text = { Text(detail) }, confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } })
}

@Composable
internal fun ValuePill(text: String, onClick: (() -> Unit)? = null) {
    val shape = CircleShape
    Text(text, modifier = Modifier.clip(shape).background(accentColor().copy(alpha = .07f))
        .border(1.dp, accentColor().copy(alpha = .45f), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int, description: String, detail: String? = null, onChange: (Float) -> Unit) {
    SettingRow(label, description, detail, value = { ValuePill(valueText) }) {
        val accent = accentColor()
        val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = valueText },
            interactionSource = remember { MutableInteractionSource() },
            thumb = { Box(Modifier.size(18.dp).background(accent, CircleShape)) },
            track = { slider ->
                Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val start = size.height / 2
                    val length = (size.width - 2 * start).coerceAtLeast(0f)
                    fun at(f: Float) = Offset(start + length * (if (rtl) 1f - f else f), start)
                    val fraction = ((slider.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    drawLine(inactive, at(0f), at(1f), size.height, StrokeCap.Round)
                    if (fraction > 0f) drawLine(accent, at(0f), at(fraction), size.height, StrokeCap.Round)
                    if (label == "Overlay Timeout") for (tick in 0..steps + 1) {
                        val f = tick.toFloat() / (steps + 1)
                        drawCircle(if (f <= fraction) Color.Black.copy(alpha = .60f) else Color.LightGray,
                            1.dp.toPx(), at(f))
                    }
                }
            })
    }
}

@Composable
internal fun AccentSetting(selected: Int, onPick: (Int) -> Unit) {
    val presets = SettingsStore.accentPresets
    val current = presets[selected] ?: "Custom"
    SettingRow("Accent colour", "$current · Controls and gesture strokes.") {
        Column(Modifier.fillMaxWidth().selectableGroup().padding(top = 6.dp)) {
            presets.keys.chunked(4).forEach { row ->
                Row {
                    row.forEach { argb ->
                        val colour = Color(argb)
                        val checked = argb == selected
                        val name = presets.getValue(argb)
                        Box(Modifier.weight(1f).height(39.dp).selectable(checked, role = Role.RadioButton,
                            onClick = { onPick(argb) }).semantics { contentDescription = name },
                            contentAlignment = Alignment.Center) {
                            val ring = if (checked) Modifier.border(1.5.dp, accentColor(), CircleShape) else Modifier
                            Box(Modifier.size(32.dp).then(ring).padding(3.dp).background(colour, CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (checked) Icon(Icons.Filled.Check, null, Modifier.size(17.dp),
                                    tint = if (colour.luminance() > .4f) Color.Black else Color.White)
                            }
                        }
                    }

                }
            }
        }
    }
}
