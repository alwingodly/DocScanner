package com.example.docscanner.presentation.enhance

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.docscanner.domain.model.FilterType

/**
 * Enhance screen — filter selection + brightness/contrast adjustment.
 *
 * Layout:
 * ┌──────────────────────┐
 * │  Top Bar (back)       │
 * ├──────────────────────┤
 * │                      │
 * │   Document Preview   │  ← Shows enhancedBitmap (live updates)
 * │                      │
 * ├──────────────────────┤
 * │ [Orig][Enh][Gray]... │  ← Filter chips (horizontal scroll)
 * ├──────────────────────┤
 * │ ☀ Brightness ─────── │  ← Slider
 * │ ◐ Contrast   ─────── │  ← Slider
 * ├──────────────────────┤
 * │        [Confirm]      │
 * └──────────────────────┘
 *
 * @param enhancedBitmap Current filtered/adjusted image
 * @param currentFilter Which filter is active
 * @param isProcessing Whether a filter is being applied
 * @param onFilterSelected Called when user taps a filter chip
 * @param onBrightnessContrastChanged Called when sliders change
 * @param onConfirm Called when user taps confirm
 * @param onBack Called when user taps back
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhanceScreen(
    enhancedBitmap: Bitmap?,
    currentFilter: FilterType,
    isProcessing: Boolean,
    onFilterSelected: (FilterType) -> Unit,
    onBrightnessContrastChanged: (brightness: Double, contrast: Double) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    // Local slider state — we don't want to trigger reprocessing on every pixel drag
    var brightness by remember { mutableDoubleStateOf(0.0) }
    var contrast by remember { mutableDoubleStateOf(1.0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Top Bar ──
        TopAppBar(
            title = { Text("Enhance") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White
            )
        )

        // ── Document Preview ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (enhancedBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val imgWidth = enhancedBitmap.width.toFloat()
                    val imgHeight = enhancedBitmap.height.toFloat()

                    val scale = minOf(canvasWidth / imgWidth, canvasHeight / imgHeight)
                    val scaledWidth = imgWidth * scale
                    val scaledHeight = imgHeight * scale
                    val offX = (canvasWidth - scaledWidth) / 2f
                    val offY = (canvasHeight - scaledHeight) / 2f

                    drawContext.canvas.nativeCanvas.drawBitmap(
                        enhancedBitmap,
                        null,
                        android.graphics.RectF(
                            offX, offY,
                            offX + scaledWidth,
                            offY + scaledHeight
                        ),
                        null
                    )
                }

                // Loading overlay
                if (isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // ── Filter Chips ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Filter",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterType.entries.forEach { filter ->
                    FilterChip(
                        filterType = filter,
                        isSelected = filter == currentFilter,
                        onClick = { onFilterSelected(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Brightness Slider ──
            SliderRow(
                icon = Icons.Default.BrightnessHigh,
                label = "Brightness",
                value = brightness.toFloat(),
                valueRange = -100f..100f,
                onValueChange = { brightness = it.toDouble() },
                onValueChangeFinished = {
                    onBrightnessContrastChanged(brightness, contrast)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Contrast Slider ──
            SliderRow(
                icon = Icons.Default.Contrast,
                label = "Contrast",
                value = contrast.toFloat(),
                valueRange = 0.5f..2.0f,
                onValueChange = { contrast = it.toDouble() },
                onValueChangeFinished = {
                    onBrightnessContrastChanged(brightness, contrast)
                }
            )
        }

        // ── Confirm Button ──
        Button(
            onClick = onConfirm,
            enabled = !isProcessing && enhancedBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Confirm")
        }
    }
}

/**
 * Single filter chip — shows filter name with selected state.
 */
@Composable
private fun FilterChip(
    filterType: FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF2196F3) else Color(0xFF2A2A2A)
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color(0xFF444444)
    val textColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filterType.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Slider row with icon + label + slider.
 *
 * onValueChange fires on every drag pixel (updates local state for smooth UI).
 * onValueChangeFinished fires once when drag ends (triggers actual processing).
 */
@Composable
private fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.width(72.dp)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
    }
}