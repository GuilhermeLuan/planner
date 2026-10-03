package dev.guilhermeluan.planner.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.day.Week
import dev.guilhermeluan.planner.session.LocalPlanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class WaterViewModel(
    private val repository: WaterRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    // A aba Água registra só no dia de hoje, no Fuso da Conta.
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val viewed = MutableStateFlow<LocalDate?>(null)
    private val _uiState = MutableStateFlow(WaterUiState(WaterDay(LocalDate.now(clock), 0, WaterRepository.DEFAULT_GOAL_ML)))
    val uiState: StateFlow<WaterUiState> = _uiState.asStateFlow()

    private var accountId: String? = null
    private var zone: ZoneId = ZoneId.systemDefault()
    private var observeJob: Job? = null
    private var viewedJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(localPlanner: LocalPlanner) {
        if (accountId == localPlanner.account.id && observeJob?.isActive == true) return
        val account = localPlanner.account.id
        accountId = account
        zone = ZoneId.of(localPlanner.account.timezone)
        refreshToday()
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            today
                .flatMapLatest { day ->
                    repository.observeWeek(account, Week.of(day)).map { week ->
                        WaterUiState(week.single { it.day == day }, week)
                    }
                }
                .collect { state -> _uiState.update { state.copy(viewedDay = it.viewedDay) } }
        }
        viewedJob?.cancel()
        viewedJob = viewModelScope.launch {
            combine(today, viewed) { todayDay, viewedDay -> viewedDay ?: todayDay }
                .flatMapLatest { day -> repository.observeDay(account, day) }
                .collect { water -> _uiState.update { it.copy(viewedDay = water) } }
        }
    }

    /** Segue o Fuso da Conta (ADR 0022): o "hoje" da aba passa a ser o do novo fuso. */
    fun updateTimezone(timezone: String) {
        zone = ZoneId.of(timezone)
        refreshToday()
    }

    /** Reavalia o dia de hoje, para a aba virar de Dia à meia-noite. */
    fun refreshToday() {
        today.value = LocalDate.now(clock.withZone(zone))
    }

    /** O resumo "Seu dia" da aba Hoje acompanha o Dia selecionado; registrar continua só em hoje. */
    fun viewDay(day: LocalDate) {
        viewed.value = day
    }

    fun add(ml: Int) = change { account -> repository.add(account, today.value, ml) }

    fun adjustTotal(totalMl: Int) = change { account -> repository.adjustTotal(account, today.value, totalMl) }

    /** A meta muda de hoje em diante; Dias passados mantêm a meta que tinham. */
    fun setGoal(goalMl: Int) = change { account -> repository.setGoal(account, goalMl, from = today.value) }

    private fun change(action: suspend (accountId: String) -> Unit) {
        val account = accountId ?: return
        viewModelScope.launch { action(account) }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WaterViewModel(application.waterRepository) as T
    }
}
