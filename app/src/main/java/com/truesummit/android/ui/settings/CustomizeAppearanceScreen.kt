package com.truesummit.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.truesummit.android.service.MerchantCleaner
import com.truesummit.android.ui.navigation.TabOrderManager
import com.truesummit.android.ui.theme.ThemeManager

val presetColors = listOf(
    Color(0xFF4F46E5), // Indigo
    Color(0xFF10B981), // Emerald
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEF4444), // Red
    Color(0xFFF59E0B), // Amber
    Color(0xFF3B82F6), // Blue
    Color(0xFFEC4899), // Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeAppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val customAccent by ThemeManager.accentColor.collectAsState()
    val customBackground by ThemeManager.backgroundColor.collectAsState()
    var merchantCleanupEnabled by remember { mutableStateOf(MerchantCleaner.isEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                ColorPersonalizationSection(
                    title = "Accent Color",
                    selectedColor = customAccent ?: MaterialTheme.colorScheme.primary,
                    onColorSelected = { ThemeManager.setAccentColor(context, it) },
                    onReset = { ThemeManager.setAccentColor(context, null) }
                )
            }

            item {
                ColorPersonalizationSection(
                    title = "Page Background",
                    selectedColor = customBackground ?: MaterialTheme.colorScheme.background,
                    onColorSelected = { ThemeManager.setBackgroundColor(context, it) },
                    onReset = { ThemeManager.setBackgroundColor(context, null) }
                )
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transactions", style = MaterialTheme.typography.titleMedium)
                    ListItem(
                        headlineContent = { Text("Tidy up merchant names") },
                        supportingContent = { Text("Strips bank prefixes and normalizes known brands for display.") },
                        trailingContent = {
                            Switch(
                                checked = merchantCleanupEnabled,
                                onCheckedChange = {
                                    merchantCleanupEnabled = it
                                    MerchantCleaner.setEnabled(context, it)
                                }
                            )
                        }
                    )
                }
            }

            item { TabOrderSection() }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun TabOrderSection() {
    val order by TabOrderManager.order.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tab Order", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { TabOrderManager.reset() }) { Text("Reset") }
        }
        Text(
            "The first ${TabOrderManager.PRIMARY_TAB_COUNT} get their own tab; the rest live in More.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        order.forEachIndexed { index, screen ->
            val isPrimary = index < TabOrderManager.PRIMARY_TAB_COUNT
            ListItem(
                headlineContent = { Text(screen.title) },
                supportingContent = {
                    Text(
                        if (isPrimary) "Tab ${index + 1}" else "In More",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPrimary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        screen.icon,
                        contentDescription = null,
                        tint = if (isPrimary) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = { TabOrderManager.moveUp(index) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${screen.title} up")
                        }
                        IconButton(
                            onClick = { TabOrderManager.moveDown(index) },
                            enabled = index < order.lastIndex
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${screen.title} down")
                        }
                    }
                }
            )
            // Divider marks where the bar stops and More begins.
            if (index == TabOrderManager.PRIMARY_TAB_COUNT - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun ColorPersonalizationSection(
    title: String,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onReset: () -> Unit
) {
    var isCustomMode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { isCustomMode = !isCustomMode }) {
                    Text(if (isCustomMode) "Presets" else "Custom")
                }
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
            }
        }

        if (isCustomMode) {
            CustomColorPicker(
                initialColor = selectedColor,
                onColorChanged = onColorSelected
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit
) {
    val hsv = remember(initialColor) {
        val hsvArray = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsvArray)
        hsvArray
    }

    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }

    val currentColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hue Slider
        Column {
            Text("Hue", style = MaterialTheme.typography.labelSmall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    )
            )
            Slider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Saturation Slider
        Column {
            Text("Saturation", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = saturation,
                onValueChange = { saturation = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Value (Brightness) Slider
        Column {
            Text("Brightness", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Hex Input
        OutlinedTextField(
            value = "#${Integer.toHexString(currentColor.toArgb()).uppercase().takeLast(6)}",
            onValueChange = { input ->
                val cleaned = input.removePrefix("#")
                if (cleaned.length == 6) {
                    try {
                        val parsed = Color(android.graphics.Color.parseColor("#$cleaned"))
                        onColorChanged(parsed)
                    } catch (e: Exception) {}
                }
            },
            label = { Text("Hex Code") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
