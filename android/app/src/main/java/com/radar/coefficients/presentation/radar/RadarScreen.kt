package com.radar.coefficients.presentation.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radar.coefficients.domain.model.RadarSortMode
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.components.CoefficientBadge
import com.radar.coefficients.presentation.components.DataStatusChip
import com.radar.coefficients.presentation.components.DisclaimerBanner
import com.radar.coefficients.presentation.components.LoadingSkeletonList
import com.radar.coefficients.presentation.components.StatePanel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RadarScreen(
    onOpenCityPicker: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Радар выгодных зон", fontWeight = FontWeight.Bold)
                    Text(
                        state.city?.name ?: "Город не выбран",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            actions = {
                TextButton(onClick = onOpenCityPicker) { Text("Город") }
            }
        )
        DisclaimerBanner()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Сортировка учитывает выгоду = доп. доход − расходы на путь",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip("Макс. выгода", RadarSortMode.MAX_BENEFIT, state.sortMode, viewModel::setSort)
                SortChip("Высокий ×", RadarSortMode.HIGHEST_COEFFICIENT, state.sortMode, viewModel::setSort)
                SortChip("Ближе", RadarSortMode.NEAREST, state.sortMode, viewModel::setSort)
            }
        }

        when {
            state.isLoading -> {
                Column(modifier = Modifier.padding(16.dp)) { LoadingSkeletonList() }
            }
            state.message != null && state.scores.isEmpty() -> {
                StatePanel(
                    message = state.message!!,
                    onRetry = viewModel::reload,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.scores, key = { it.zone.id }) { score ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "#${score.rank}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 28.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            score.zone.districtName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                        DataStatusChip(score.zone.dataStatus())
                                    }
                                    CoefficientBadge(score.zone.coefficient)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Выгода: ${"%.0f".format(score.expectedNetBenefit)} ${state.city?.currencyCode ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text("До зоны: ${"%.1f".format(score.distanceKm)} км · ${score.directionLabelRu}")
                                Text(
                                    "В пути: ~${score.travelTimeWithTrafficMinutes ?: score.travelTimeMinutes} мин" +
                                        if (score.travelTimeWithTrafficMinutes == null) " (без пробок)" else ""
                                )
                                Text("Доп. доход (ориент.): ${"%.0f".format(score.expectedGrossExtra)}")
                                if (!score.commissionKnown) {
                                    Text(
                                        "Комиссия не учтена: данные отсутствуют",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (score.zone.isDemo) {
                                    Text(
                                        DataStatusLabels.DEMO_BANNER,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    mode: RadarSortMode,
    current: RadarSortMode,
    onSelect: (RadarSortMode) -> Unit
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(label, fontSize = 15.sp) }
    )
}
