package com.radar.coefficients.presentation.sources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radar.coefficients.domain.model.ProviderConnectionStatus
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.components.DisclaimerBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val demandRepository: DemandRepository
) : ViewModel() {
    private val _statuses = MutableStateFlow<List<ProviderStatus>>(emptyList())
    val statuses: StateFlow<List<ProviderStatus>> = _statuses.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _statuses.value = demandRepository.getActiveProvidersStatus()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(viewModel: SourcesViewModel = hiltViewModel()) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Источники данных", fontWeight = FontWeight.Bold) })
        DisclaimerBanner()
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Text(
                    "Реальные коэффициенты показываются только из разрешённых источников. " +
                        "Демо, прогнозы и сообщения водителей всегда помечены отдельно.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
            }
            items(statuses) { status ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(status.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Тип: ${DataStatusLabels.sourceTypeRu(status.sourceType)}")
                        Text("Статус: ${statusRu(status.status)}")
                        Text("Города: ${status.supportedCitiesHint}")
                        Text(
                            "Обновлено: " + (status.lastUpdatedAtEpochMs?.let { fmt.format(Date(it)) } ?: "—")
                        )
                        Text("Тип данных: ${if (status.isDemo) "Демонстрационные" else "Не демо"}")
                        Spacer(Modifier.height(6.dp))
                        Text(status.termsOfUse, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Подключение официального API: backend + DEMAND_API_BASE_URL в local.properties. " +
                        "Ключи поставщиков не хранятся в приложении.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun statusRu(s: ProviderConnectionStatus): String = when (s) {
    ProviderConnectionStatus.CONNECTED -> "Подключён"
    ProviderConnectionStatus.DISCONNECTED -> "Отключён"
    ProviderConnectionStatus.NOT_CONFIGURED -> "Не настроен"
    ProviderConnectionStatus.RATE_LIMITED -> "Лимит запросов"
    ProviderConnectionStatus.ERROR -> "Ошибка"
    ProviderConnectionStatus.DEMO -> "Деморежим"
}
