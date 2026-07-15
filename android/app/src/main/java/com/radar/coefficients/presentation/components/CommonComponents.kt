package com.radar.coefficients.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radar.coefficients.domain.model.DataStatus
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.common.UiMessage
import com.radar.coefficients.presentation.common.bodyRu
import com.radar.coefficients.presentation.common.titleRu
import com.radar.coefficients.presentation.theme.DemoAmber
import com.radar.coefficients.presentation.theme.RealTeal
import com.radar.coefficients.presentation.theme.StaleGray
import com.radar.coefficients.presentation.theme.TouchTargetMin
import com.radar.coefficients.presentation.theme.coefficientColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sk")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
fun StatePanel(
    message: UiMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = message.titleRu(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message.bodyRu(),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TouchTargetMin)
            ) {
                Text("Повторить", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun DataStatusChip(status: DataStatus) {
    val color = when (status) {
        DataStatus.REAL -> RealTeal
        DataStatus.PARTNER -> Color(0xFF5C6BC0)
        DataStatus.COMMUNITY -> Color(0xFF8D6E63)
        DataStatus.STALE -> StaleGray
        DataStatus.DEMO -> DemoAmber
        DataStatus.NONE -> MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = DataStatusLabels.statusRu(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun CoefficientBadge(coefficient: Double, large: Boolean = false) {
    val color = coefficientColor(coefficient)
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            text = "×${String.format(Locale.getDefault(), "%.1f", coefficient)}",
            modifier = Modifier.padding(
                horizontal = if (large) 16.dp else 10.dp,
                vertical = if (large) 10.dp else 6.dp
            ),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (large) 22.sp else 16.sp
        )
    }
}

@Composable
fun DisclaimerBanner(text: String = DataStatusLabels.GROUNDED_MODEL) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun SourceMetaRow(zone: DemandZone) {
    val fmt = rememberTimeFormatter()
    Column {
        DataStatusChip(zone.dataStatus())
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Источник: ${zone.sourceName} · ${DataStatusLabels.sourceTypeRu(zone.sourceType)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Обновлено: ${fmt.format(Date(zone.fetchedAtEpochMs))}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Актуально до: ${fmt.format(Date(zone.validUntilEpochMs))}",
            style = MaterialTheme.typography.bodySmall
        )
        if (zone.isDemo) {
            Text(
                text = DataStatusLabels.DEMO_BANNER,
                color = DemoAmber,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun rememberTimeFormatter(): SimpleDateFormat =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun LoadingSkeletonList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(18.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp))
                }
            }
        }
    }
}
