package com.ibfavas.flick.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibfavas.flick.R

@Composable
internal fun FlickMenu(open: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = dismiss, shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = content)
}

@Composable
internal fun FlickMenuItem(label: String, icon: ImageVector?, enabled: Boolean = true,
    destructive: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (destructive) colors.error else colors.primary
    DropdownMenuItem(text = { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        leadingIcon = {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp))
            else Icon(painterResource(R.drawable.ic_duplicate), null, Modifier.size(20.dp))
        },
        colors = MenuDefaults.itemColors(textColor = if (destructive) colors.error else colors.onSurface,
            leadingIconColor = tint),
        modifier = Modifier.heightIn(min = 48.dp), enabled = enabled, onClick = onClick)
}
