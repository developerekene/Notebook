package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategoryInsight
import com.example.domain.model.DailyActivity
import com.example.domain.model.InsightsSummary
import com.example.ui.theme.Cobalt500
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepNavy700
import com.example.ui.theme.DeepNavy800
import com.example.ui.theme.DeepNavy900
import com.example.ui.theme.ElectricBlue400
import com.example.ui.theme.EmeraldVault
import com.example.ui.theme.IceBlue100
import com.example.ui.theme.IceBlue200

@Composable
fun DashboardInsightsBanner(
    insights: InsightsSummary,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(DeepNavy900, DeepNavy800, DeepNavy700)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(gradientBrush)
            .border(
                width = 1.dp,
                color = ElectricBlue400.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.large
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TrendingUp,
                        contentDescription = null,
                        tint = ElectricBlue400,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Vault Insights",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                Surface(
                    color = EmeraldVault.copy(alpha = 0.18f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Encrypted vault status",
                            tint = EmeraldVault,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricTile(
                    label = "NOTES",
                    value = insights.totalNotes.toString(),
                    sublabel = "${insights.pinnedNotes} pinned",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    label = "WORDS",
                    value = formatCompactNumber(insights.totalWords),
                    sublabel = "${insights.averageWordsPerNote} avg",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    label = "TAGS",
                    value = insights.activeTagCount.toString(),
                    sublabel = "${insights.lockedNotes} locked",
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "7-DAY WRITING CADENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceBlue200.copy(alpha = 0.8f)
                    )
                    val todayWords = insights.dailyActivity.lastOrNull()?.wordCount ?: 0
                    Text(
                        text = "$todayWords words today",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricBlue400
                    )
                }

                CompactCadenceCanvas(
                    dailyActivity = insights.dailyActivity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )
            }
        }
    }
}

@Composable
private fun KpiMetricTile(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IceBlue200.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = IceBlue100.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun CompactCadenceCanvas(
    dailyActivity: List<DailyActivity>,
    modifier: Modifier = Modifier
) {
    val data = if (dailyActivity.isEmpty()) {
        List(7) { DailyActivity("-", it.toLong(), 0, 0) }
    } else {
        dailyActivity
    }
    val maxWords = (data.maxOfOrNull { it.wordCount } ?: 0).coerceAtLeast(20)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val count = data.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.42f).coerceAtMost(24.dp.toPx())
            val maxBarHeight = size.height - 6.dp.toPx()

            val trendPoints = mutableListOf<Offset>()

            data.forEachIndexed { index, item ->
                val ratio = (item.wordCount.toFloat() / maxWords).coerceIn(0f, 1f)
                val barHeight = (maxBarHeight * ratio).coerceAtLeast(4.dp.toPx())
                val left = index * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight

                drawRoundRect(
                    color = if (item.wordCount > 0) ElectricBlue400.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                trendPoints += Offset(
                    x = index * slotWidth + slotWidth / 2f,
                    y = (size.height - maxBarHeight * ratio).coerceIn(4.dp.toPx(), size.height - 4.dp.toPx())
                )
            }

            if (trendPoints.size > 1) {
                val path = Path().apply {
                    moveTo(trendPoints.first().x, trendPoints.first().y)
                    for (i in 1 until trendPoints.size) {
                        val prev = trendPoints[i - 1]
                        val curr = trendPoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(
                    path = path,
                    color = CyanAccent,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                trendPoints.forEachIndexed { idx, pt ->
                    if (data[idx].wordCount > 0 || idx == trendPoints.lastIndex) {
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { day ->
                Text(
                    text = day.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = IceBlue200.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
fun DetailedActivityChart(
    dailyActivity: List<DailyActivity>,
    modifier: Modifier = Modifier
) {
    val maxNotes = (dailyActivity.maxOfOrNull { it.noteCount } ?: 0).coerceAtLeast(1)
    val maxWords = (dailyActivity.maxOfOrNull { it.wordCount } ?: 0).coerceAtLeast(40)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            if (dailyActivity.isEmpty()) return@Canvas
            val count = dailyActivity.size
            val slotW = size.width / count
            val barW = (slotW * 0.48f).coerceAtMost(28.dp.toPx())
            val chartH = size.height - 12.dp.toPx()

            // Subtle baseline grid
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                val y = size.height - chartH * frac
                drawLine(
                    color = trackColor.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val linePoints = mutableListOf<Offset>()
            dailyActivity.forEachIndexed { idx, day ->
                val wordRatio = (day.wordCount.toFloat() / maxWords).coerceIn(0f, 1f)
                val barH = (chartH * wordRatio).coerceAtLeast(4.dp.toPx())
                val left = idx * slotW + (slotW - barW) / 2f
                val top = size.height - barH

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, Cobalt500.copy(alpha = 0.6f))
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                val noteRatio = (day.noteCount.toFloat() / maxNotes).coerceIn(0f, 1f)
                linePoints += Offset(
                    x = idx * slotW + slotW / 2f,
                    y = (size.height - chartH * noteRatio).coerceIn(8.dp.toPx(), size.height - 6.dp.toPx())
                )
            }

            if (linePoints.size > 1) {
                val path = Path().apply {
                    moveTo(linePoints.first().x, linePoints.first().y)
                    for (i in 1 until linePoints.size) {
                        val p = linePoints[i - 1]
                        val c = linePoints[i]
                        val mid = (p.x + c.x) / 2f
                        cubicTo(mid, p.y, mid, c.y, c.x, c.y)
                    }
                }
                drawPath(
                    path = path,
                    color = secondaryColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyActivity.forEachIndexed { idx, day ->
                if (dailyActivity.size <= 7 || idx % 2 == 0 || idx == dailyActivity.lastIndex) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownRow(
    insight: CategoryInsight,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = insight.shareFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "category_progress"
    )
    val dotColor = Color(insight.category.colorHex)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = insight.category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${insight.noteCount} notes · ${insight.wordCount} words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape),
            color = dotColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun formatCompactNumber(value: Int): String {
    return if (value >= 1000) {
        String.format("%.1fk", value / 1000f)
    } else {
        value.toString()
    }
}
