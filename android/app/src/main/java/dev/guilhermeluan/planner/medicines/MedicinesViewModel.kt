package dev.guilhermeluan.planner.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.guilhermeluan.planner.PlannerApplication
import dev.guilhermeluan.planner.session.LocalPlanner
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.IdGenerator
import dev.guilhermeluan.planner.tasks.MedicineDraft
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.tasks.RoomPlannerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class MedicinesViewModel(
    private val repository: RoomPlannerRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val selectedDay = MutableStateFlow(LocalDate.now(clock))
    private val _uiState = MutableStateFlow(MedicinesUiState(LocalDate.now(clock)))
    val uiState: StateFlow<MedicinesUiState> = _uiState.asStateFlow()

    private var accountId: String? = null
    private var plannerId: String? = null
    private var zone: ZoneId = ZoneId.systemDefault()
    private var observeJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(localPlanner: LocalPlanner) {
        if (accountId == localPlanner.account.id && plannerId == localPlanner.planner.id && observeJob?.isActive == true) return
        accountId = localPlanner.account.id
        plannerId = localPlanner.planner.id
        zone = ZoneId.of(localPlanner.account.timezone)
        selectedDay.value = today()
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            selectedDay
                .flatMapLatest { day ->
                    combine(
                        repository.observeDoses(localPlanner.account.id, day),
                        repository.observeMedicines(localPlanner.account.id),
                        repository.observeArchivedMedicines(localPlanner.account.id),
                        repository.observeLastRegisteredDays(localPlanner.account.id),
                    ) { doses, medicines, archived, lastRegisteredDays ->
                        MedicinesUiState(day, doses, medicines, archived, lastRegisteredDays)
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    /** Segue o Fuso da Conta (ADR 0022) para editar, arquivar e restaurar contarem a partir do "hoje" certo. */
    fun updateTimezone(timezone: String) {
        zone = ZoneId.of(timezone)
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun setDoseStatus(dose: PlannedDose, status: DoseStatus) {
        val account = accountId ?: return
        viewModelScope.launch { repository.setDoseStatus(account, dose.medicineId, dose.day, dose.time, status) }
    }

    fun createMedicine(draft: MedicineDraft) {
        val account = accountId ?: return
        val planner = plannerId ?: return
        viewModelScope.launch { repository.createMedicine(account, planner, draft) }
    }

    fun editMedicine(medicineId: String, draft: MedicineDraft) {
        val account = accountId ?: return
        viewModelScope.launch { repository.editMedicine(account, medicineId, draft, today()) }
    }

    fun archiveMedicine(medicineId: String) {
        val account = accountId ?: return
        viewModelScope.launch { repository.archiveMedicine(account, medicineId, today()) }
    }

    fun restoreMedicine(medicineId: String) {
        val account = accountId ?: return
        viewModelScope.launch { repository.restoreMedicine(account, medicineId, today()) }
    }

    class Factory(private val application: PlannerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MedicinesViewModel(
                RoomPlannerRepository(application.database, IdGenerator { UUID.randomUUID().toString() }, Clock.systemUTC()),
            ) as T
    }
}
