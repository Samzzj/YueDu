package com.yuedu.reader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuedu.reader.ui.theme.LocalLiquidGlass

@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glass.baseBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glass.orbPrimary, Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.14f),
                    radius = size.width * 0.68f
                ),
                radius = size.width * 0.68f,
                center = Offset(size.width * 0.16f, size.height * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glass.orbSecondary, Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.88f, size.height * 0.22f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glass.orbTertiary, Color.Transparent),
                    center = Offset(size.width * 0.55f, size.height * 0.82f),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.55f, size.height * 0.82f)
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    strong: Boolean = false,
    elevation: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val fill = if (strong) glass.glassSurfaceStrong else glass.glassSurface

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (glass.isDark) 0.45f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (glass.isDark) 0.45f else 0.12f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        fill,
                        fill.copy(alpha = (fill.alpha * 0.90f).coerceIn(0f, 1f))
                    )
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        glass.glassHighlightTop.copy(alpha = if (glass.isDark) 0.14f else 0.35f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 0.9.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(glass.glassBorderTop, glass.glassBorderBottom)
                ),
                shape = shape
            ),
        content = content
    )
}

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    cornerRadius: Dp = 999.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(tint ?: glass.glassSurfaceStrong)
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(glass.glassBorderTop, glass.glassBorderBottom)
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

data class GlassTabItem(
    val label: String,
    val icon: ImageVector
)

@Composable
fun LiquidGlassFloatingTabBar(
    tabs: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        cornerRadius = 34.dp,
        strong = true,
        elevation = 20.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tabOffset"
            )

            // Floating inner liquid capsule indicator
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                glass.pillIndicator,
                                glass.pillIndicator.copy(alpha = glass.pillIndicator.alpha * 0.72f)
                            )
                        )
                    )
                    .border(
                        width = 0.7.dp,
                        color = glass.glassBorderTop.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { idx, item ->
                    val selected = idx == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) glass.accent else glass.textSecondary,
                        label = "tabTint"
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) { onSelect(idx) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
