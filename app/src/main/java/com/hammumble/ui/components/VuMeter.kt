package com.hammumble.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VU Meter component for visualizing audio levels
 * 
 * @param level Current audio level (0.0 to 1.0)
 * @param label Label to display (e.g., "Input", "Output")
 * @param height Height of the meter
 * @param showValue Whether to show numeric value
 * @param showPeakIndicator Whether to show peak level indicator
 * @param orientation Horizontal or Vertical layout
 */
@Composable
fun VuMeter(
    level: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    height: Dp = 24.dp,
    showValue: Boolean = false,
    showPeakIndicator: Boolean = true,
    orientation: VuMeterOrientation = VuMeterOrientation.HORIZONTAL
) {
    // Animate the level changes smoothly
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "VU Meter Level"
    )
    
    // Track peak level
    var peakLevel by remember { mutableStateOf(0f) }
    
    LaunchedEffect(animatedLevel) {
        if (animatedLevel > peakLevel) {
            peakLevel = animatedLevel
        }
    }
    
    // Decay peak indicator over time
    LaunchedEffect(peakLevel) {
        if (peakLevel > 0f) {
            kotlinx.coroutines.delay(1000)
            peakLevel = (peakLevel - 0.05f).coerceAtLeast(0f)
        }
    }
    
    when (orientation) {
        VuMeterOrientation.HORIZONTAL -> {
            Column(modifier = modifier) {
                // Label and value
                if (label != null || showValue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp
                            )
                        }
                        if (showValue) {
                            Text(
                                text = "${(animatedLevel * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = getLevelColor(animatedLevel)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                // Horizontal meter
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                ) {
                    drawHorizontalMeter(animatedLevel, peakLevel, showPeakIndicator)
                }
            }
        }
        
        VuMeterOrientation.VERTICAL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vertical meter
                Canvas(
                    modifier = Modifier
                        .width(height)
                        .fillMaxHeight()
                ) {
                    drawVerticalMeter(animatedLevel, peakLevel, showPeakIndicator)
                }
                
                if (label != null || showValue) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        if (label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp
                            )
                        }
                        if (showValue) {
                            Text(
                                text = "${(animatedLevel * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = getLevelColor(animatedLevel)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHorizontalMeter(
    level: Float,
    peakLevel: Float,
    showPeak: Boolean
) {
    val width = size.width
    val height = size.height
    val cornerRadius = height / 2
    
    // Background (empty meter)
    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    
    if (level > 0f) {
        val filledWidth = width * level
        
        // Create gradient based on level
        val colors = when {
            level < 0.6f -> listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)) // Green
            level < 0.85f -> listOf(Color(0xFFFFC107), Color(0xFFFFEB3B)) // Yellow/Orange
            else -> listOf(Color(0xFFF44336), Color(0xFFE91E63)) // Red
        }
        
        // Filled meter with gradient
        drawRoundRect(
            brush = Brush.horizontalGradient(colors),
            size = Size(filledWidth, height),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
    
    // Peak indicator
    if (showPeak && peakLevel > 0f && peakLevel > level) {
        val peakX = width * peakLevel
        drawLine(
            color = Color.White,
            start = Offset(peakX, 0f),
            end = Offset(peakX, height),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
    
    // Segment lines for scale
    val segments = 10
    for (i in 1 until segments) {
        val x = (width / segments) * i
        drawLine(
            color = Color.Black.copy(alpha = 0.3f),
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawVerticalMeter(
    level: Float,
    peakLevel: Float,
    showPeak: Boolean
) {
    val width = size.width
    val height = size.height
    val cornerRadius = width / 2
    
    // Background (empty meter)
    drawRoundRect(
        color = Color.Gray.copy(alpha = 0.2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    
    if (level > 0f) {
        val filledHeight = height * level
        val startY = height - filledHeight
        
        // Create gradient based on level
        val colors = when {
            level < 0.6f -> listOf(Color(0xFF8BC34A), Color(0xFF4CAF50)) // Green (top to bottom)
            level < 0.85f -> listOf(Color(0xFFFFEB3B), Color(0xFFFFC107)) // Yellow/Orange
            else -> listOf(Color(0xFFE91E63), Color(0xFFF44336)) // Red
        }
        
        // Filled meter with gradient (bottom up)
        drawRoundRect(
            brush = Brush.verticalGradient(colors, startY = startY, endY = height),
            topLeft = Offset(0f, startY),
            size = Size(width, filledHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
    
    // Peak indicator
    if (showPeak && peakLevel > 0f && peakLevel > level) {
        val peakY = height - (height * peakLevel)
        drawLine(
            color = Color.White,
            start = Offset(0f, peakY),
            end = Offset(width, peakY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

private fun getLevelColor(level: Float): Color {
    return when {
        level < 0.6f -> Color(0xFF4CAF50) // Green
        level < 0.85f -> Color(0xFFFFC107) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

enum class VuMeterOrientation {
    HORIZONTAL,
    VERTICAL
}
