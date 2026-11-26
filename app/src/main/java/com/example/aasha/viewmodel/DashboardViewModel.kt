package com.example.aasha.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.LaundryNotice
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.supportedCycles
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.domain.model.typeDisplayName
import com.example.aasha.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

data class MachineCardUiModel(
    val machine: WashingMachine,
    val queueLength: Int,
    val estimatedWaitMinutes: Int,
    val nextAvailableSlot: String,
    val supportedCycles: List<CycleType>
)

data class DashboardUiState(
    val greeting: String = "Good Morning",
    val hostellerName: String = "Hosteller",
    val roomNumber: String = "101",
    val hostelBlock: String = "Block A",
    val notificationsEnabled: Boolean = true,
    val isOnline: Boolean = true,
    val lastSyncTime: Long = 0L,
    val machines: List<WashingMachine> = emptyList(),
    val machineItems: List<MachineCardUiModel> = emptyList(),
    val availableCount: Int = 0,
    val inUseCount: Int = 0,
    val outOfOrderCount: Int = 0,
    val totalCount: Int = 0,
    val upcomingBookingsCount: Int = 0,
    val availableBlocks: List<String> = emptyList(),
    val availableFloors: List<Int> = emptyList(),
    val selectedBlock: String = "All",
    val selectedFloor: Int? = null,
    val selectedTypeFilter: String = "All",
    val selectedStatusFilter: MachineStatus? = null,
    val showAvailableOnly: Boolean = false,
    val searchQuery: String = "",
    val hasActiveFilters: Boolean = false,
    val activeBooking: SlotBooking? = null,
    val notices: List<LaundryNotice> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val selectedMachineForDetails: MachineCardUiModel? = null,
    val expandFaultInDetails: Boolean = false,
    val selectedMachineForQuickStart: WashingMachine? = null,
    val isStartingWash: Boolean = false,
    val quickStartError: String? = null,
    val isSubmittingFault: Boolean = false,
    val faultSubmitError: String? = null
)

sealed interface DashboardEvent {
    data class ShowMessage(val message: String) : DashboardEvent
}

private data class FilterState(
    val block: String = "All",
    val floor: Int? = null,
    val type: String = "All",
    val statusFilter: MachineStatus? = null,
    val availableOnly: Boolean = false,
    val immediateQuery: String = "",
    val debouncedQuery: String = ""
)

private data class SheetInteractionState(
    val detailsMachineId: String? = null,
    val expandFault: Boolean = false,
    val quickStartMachineId: String? = null,
    val isStartingWash: Boolean = false,
    val quickStartError: String? = null,
    val isSubmittingFault: Boolean = false,
    val faultSubmitError: String? = null
)

private data class SessionSnapshot(
    val name: String = "Hosteller",
    val room: String = "101",
    val block: String = "Block A",
    val notificationsEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val isOnline: Boolean = true
)

private data class StatusTuple(
    val loaded: Boolean,
    val refreshing: Boolean,
    val error: String?,
    val message: String?,
    val sheet: SheetInteractionState
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val sessionManager: SessionManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _selectedBlock = MutableStateFlow("All")
    private val _selectedFloor = MutableStateFlow<Int?>(null)
    private val _selectedTypeFilter = MutableStateFlow("All")
    private val _selectedStatusFilter = MutableStateFlow<MachineStatus?>(null)
    private val _showAvailableOnly = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _debouncedSearchQuery = _searchQuery
        .debounce(200.milliseconds)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _todayStartMillis = MutableStateFlow(SlotTimeHelper.getStartOfDay(System.currentTimeMillis()))
    private val _hasLoadedMachines = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _sheetState = MutableStateFlow(SheetInteractionState())
    private val completingBookingIds = mutableSetOf<String>()

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

    private val todayBookingsFlow = _todayStartMillis.flatMapLatest { dayStart ->
        laundryRepository.getBookingsForDate(dayStart)
    }

    private val filterFlow = combine<Any?, FilterState>(
        _selectedBlock,
        _selectedFloor,
        _selectedTypeFilter,
        _selectedStatusFilter,
        _showAvailableOnly,
        _searchQuery,
        _debouncedSearchQuery
    ) { params ->
        FilterState(
            block = params[0] as String,
            floor = params[1] as Int?,
            type = params[2] as String,
            statusFilter = params[3] as MachineStatus?,
            availableOnly = params[4] as Boolean,
            immediateQuery = params[5] as String,
            debouncedQuery = params[6] as String
        )
    }

    private val sessionFlow = combine<Any?, SessionSnapshot>(
        sessionManager.name,
        sessionManager.roomNumber,
        sessionManager.hostelBlock,
        sessionManager.notificationEnabled,
        sessionManager.lastSyncTime,
        networkMonitor.isOnline
    ) { params ->
        SessionSnapshot(
            name = (params[0] as String?) ?: "Hosteller",
            room = params[1] as String,
            block = params[2] as String,
            notificationsEnabled = params[3] as Boolean,
            lastSyncTime = params[4] as Long,
            isOnline = params[5] as Boolean
        )
    }

    private val statusFlow = combine(
        _hasLoadedMachines,
        _isRefreshing,
        _errorMessage,
        _message,
        _sheetState
    ) { loaded, refreshing, error, msg, sheet ->
        StatusTuple(loaded, refreshing, error, msg, sheet)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        laundryRepository.allMachines.onEach { _hasLoadedMachines.value = true },
        todayBookingsFlow,
        laundryRepository.userActiveBookings,
        laundryRepository.userUpcomingBookingsCount,
        filterFlow,
        sessionFlow,
        statusFlow
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val rawMachines = params[0] as List<WashingMachine>
        @Suppress("UNCHECKED_CAST")
        val todayBookings = params[1] as List<SlotBooking>
        @Suppress("UNCHECKED_CAST")
        val activeBookings = params[2] as List<SlotBooking>
        val upcomingCount = params[3] as Int
        val filter = params[4] as FilterState
        val session = params[5] as SessionSnapshot
        val status = params[6] as StatusTuple

        val now = System.currentTimeMillis()
        val allMachineModels = rawMachines.map { machine ->
            buildMachineUiModel(machine, todayBookings, now)
        }

        val availCount = rawMachines.count { it.status == MachineStatus.AVAILABLE }
        val inUseCount = rawMachines.count { it.status == MachineStatus.IN_USE }
        val outOfOrderCount = rawMachines.count { it.status == MachineStatus.OUT_OF_ORDER }

        val dynamicBlocks = rawMachines.asSequence().map { it.blockName }.distinct().sorted().toList()
        val dynamicFloors = rawMachines.asSequence().map { it.floor }.distinct().sorted().toList()

        val query = filter.debouncedQuery.trim().lowercase()
        val filteredModels = allMachineModels.filter { item ->
            val machine = item.machine
            val matchesBlock = (filter.block == "All") ||
                machine.blockName.equals(filter.block, ignoreCase = true)
            val matchesFloor = (filter.floor == null) || (machine.floor == filter.floor)
            val matchesType = when (filter.type) {
                "Washers" -> machine.type != MachineType.DRYER
                "Dryers" -> machine.type == MachineType.DRYER
                else -> true
            }
            val matchesStatus = when {
                filter.statusFilter != null -> machine.status == filter.statusFilter
                filter.availableOnly -> machine.status == MachineStatus.AVAILABLE
                else -> true
            }
            val matchesSearch = query.isEmpty() ||
                machine.name.lowercase().contains(query) ||
                machine.id.lowercase().contains(query) ||
                machine.blockName.lowercase().contains(query) ||
                machine.typeDisplayName().lowercase().contains(query)

            matchesBlock && matchesFloor && matchesType && matchesStatus && matchesSearch
        }

        val hasActiveFilters = filter.block != "All" ||
            filter.floor != null ||
            filter.type != "All" ||
            filter.statusFilter != null ||
            filter.availableOnly ||
            filter.immediateQuery.isNotBlank()

        val selectedDetailsMachine = status.sheet.detailsMachineId?.let { id ->
            allMachineModels.firstOrNull { it.machine.id == id }
        }
        val selectedQuickStartMachine = status.sheet.quickStartMachineId?.let { id ->
            rawMachines.firstOrNull { it.id == id }
        }

        DashboardUiState(
            greeting = resolveGreeting(),
            hostellerName = session.name,
            roomNumber = session.room,
            hostelBlock = session.block,
            notificationsEnabled = session.notificationsEnabled,
            isOnline = session.isOnline,
            lastSyncTime = session.lastSyncTime,
            machines = filteredModels.map { it.machine },
            machineItems = filteredModels,
            availableCount = availCount,
            inUseCount = inUseCount,
            outOfOrderCount = outOfOrderCount,
            totalCount = rawMachines.size,
            upcomingBookingsCount = upcomingCount,
            availableBlocks = dynamicBlocks,
            availableFloors = dynamicFloors,
            selectedBlock = filter.block,
            selectedFloor = filter.floor,
            selectedTypeFilter = filter.type,
            selectedStatusFilter = filter.statusFilter,
            showAvailableOnly = filter.availableOnly || filter.statusFilter == MachineStatus.AVAILABLE,
            searchQuery = filter.immediateQuery,
            hasActiveFilters = hasActiveFilters,
            activeBooking = activeBookings.firstOrNull(),
            notices = laundryRepository.getLaundryNotices(),
            isLoading = !status.loaded,
            isRefreshing = status.refreshing,
            isSyncing = status.refreshing,
            errorMessage = status.error,
            message = status.message,
            selectedMachineForDetails = selectedDetailsMachine,
            expandFaultInDetails = status.sheet.expandFault,
            selectedMachineForQuickStart = selectedQuickStartMachine,
            isStartingWash = status.sheet.isStartingWash,
            quickStartError = status.sheet.quickStartError,
            isSubmittingFault = status.sheet.isSubmittingFault,
            faultSubmitError = status.sheet.faultSubmitError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        viewModelScope.launch {
            laundryRepository.checkAndSeedMachines()
        }
    }

    fun refreshMachines() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _errorMessage.value = null
        _todayStartMillis.value = SlotTimeHelper.getStartOfDay(System.currentTimeMillis())

        viewModelScope.launch {
            val result = laundryRepository.refreshDashboard()
            if (result.isSuccess) {
                _events.emit(DashboardEvent.ShowMessage("Machine status updated"))
            } else {
                val errorText = result.exceptionOrNull()?.message
                    ?: "Could not reach server. Showing cached status."
                _errorMessage.value = errorText
            }
            _isRefreshing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilters(
        block: String = _selectedBlock.value,
        floor: Int? = _selectedFloor.value,
        type: String = _selectedTypeFilter.value,
        availableOnly: Boolean = _showAvailableOnly.value,
        statusFilter: MachineStatus? = _selectedStatusFilter.value
    ) {
        _selectedBlock.value = block
        _selectedFloor.value = floor
        _selectedTypeFilter.value = type
        _showAvailableOnly.value = availableOnly
        _selectedStatusFilter.value = statusFilter
    }

    fun onBlockSelected(block: String) {
        _selectedBlock.value = if (_selectedBlock.value == block && block != "All") "All" else block
    }

    fun onFloorSelected(floor: Int?) {
        _selectedFloor.value = if (_selectedFloor.value == floor) null else floor
    }

    fun onTypeFilterSelected(type: String) {
        _selectedTypeFilter.value = if (_selectedTypeFilter.value == type && type != "All") "All" else type
    }

    fun toggleAvailableOnly() {
        val next = !_showAvailableOnly.value
        _showAvailableOnly.value = next
        _selectedStatusFilter.value = if (next) MachineStatus.AVAILABLE else null
    }

    fun toggleStatusFilter(status: MachineStatus) {
        val nextStatus = if (_selectedStatusFilter.value == status) null else status
        _selectedStatusFilter.value = nextStatus
        _showAvailableOnly.value = (nextStatus == MachineStatus.AVAILABLE)
    }

    fun clearFilters() {
        _selectedBlock.value = "All"
        _selectedFloor.value = null
        _selectedTypeFilter.value = "All"
        _selectedStatusFilter.value = null
        _showAvailableOnly.value = false
        _searchQuery.value = ""
    }

    fun selectMachine(machineId: String, expandFaultSection: Boolean = false) {
        _sheetState.update {
            it.copy(
                detailsMachineId = machineId,
                expandFault = expandFaultSection,
                faultSubmitError = null
            )
        }
    }

    fun dismissMachineDetails() {
        _sheetState.update {
            it.copy(
                detailsMachineId = null,
                expandFault = false,
                faultSubmitError = null,
                isSubmittingFault = false
            )
        }
    }

    fun openQuickStartSheet(machineId: String) {
        _sheetState.update {
            it.copy(
                quickStartMachineId = machineId,
                quickStartError = null,
                isStartingWash = false
            )
        }
    }

    fun dismissQuickStartSheet() {
        _sheetState.update {
            it.copy(
                quickStartMachineId = null,
                quickStartError = null,
                isStartingWash = false
            )
        }
    }

    fun startQuickWash(machineId: String, cycleType: CycleType) {
        if (_sheetState.value.isStartingWash) return

        viewModelScope.launch {
            _sheetState.update { it.copy(isStartingWash = true, quickStartError = null) }

            if (!uiState.value.isOnline) {
                _sheetState.update {
                    it.copy(
                        isStartingWash = false,
                        quickStartError = "You are offline. Connect to the internet to start a machine."
                    )
                }
                return@launch
            }

            val freshMachine = laundryRepository.getMachineById(machineId)
            if (freshMachine == null) {
                _sheetState.update {
                    it.copy(isStartingWash = false, quickStartError = "Machine not found.")
                }
                return@launch
            }
            if (freshMachine.status != MachineStatus.AVAILABLE) {
                val reason = when (freshMachine.status) {
                    MachineStatus.OUT_OF_ORDER -> "Machine is under maintenance and cannot be started."
                    MachineStatus.IN_USE -> "Machine is already running a wash cycle."
                    MachineStatus.RESERVED -> "Machine is reserved for an upcoming slot."
                    else -> "Machine is currently unavailable."
                }
                _sheetState.update { it.copy(isStartingWash = false, quickStartError = reason) }
                return@launch
            }
            if (!freshMachine.supportsCycle(cycleType)) {
                _sheetState.update {
                    it.copy(
                        isStartingWash = false,
                        quickStartError = "${freshMachine.name} does not support ${cycleType.displayName}."
                    )
                }
                return@launch
            }

            val result = laundryRepository.startWashCycle(machineId, cycleType)
            if (result.isSuccess) {
                _sheetState.update {
                    it.copy(
                        quickStartMachineId = null,
                        isStartingWash = false,
                        quickStartError = null
                    )
                }
                val successMsg = "Wash cycle started on ${freshMachine.name}. Reminder set!"
                _message.value = successMsg
                _events.emit(DashboardEvent.ShowMessage(successMsg))
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to start wash cycle"
                _sheetState.update { it.copy(isStartingWash = false, quickStartError = err) }
            }
        }
    }

    fun completeActiveCycle(machineId: String, bookingId: String) {
        if (!completingBookingIds.add(bookingId)) return

        viewModelScope.launch {
            try {
                val result = laundryRepository.completeWashCycle(machineId, bookingId)
                if (result.isSuccess) {
                    val msg = "Laundry cycle marked collected. Machine is now available!"
                    _message.value = msg
                    _events.emit(DashboardEvent.ShowMessage(msg))
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Failed to update cycle"
                    _message.value = msg
                    _events.emit(DashboardEvent.ShowMessage(msg))
                }
            } finally {
                completingBookingIds.remove(bookingId)
            }
        }
    }

    fun reportMachineIssue(
        machineId: String,
        selectedCategories: Set<String>,
        description: String
    ) {
        if (_sheetState.value.isSubmittingFault) return

        if (selectedCategories.isEmpty()) {
            _sheetState.update {
                it.copy(faultSubmitError = "Please select at least one issue category.")
            }
            return
        }
        val trimmedDesc = description.trim()
        if ((selectedCategories == setOf("Other")) && trimmedDesc.isBlank()) {
            _sheetState.update {
                it.copy(faultSubmitError = "Please enter a brief description when selecting Other.")
            }
            return
        }

        val combinedCategory = selectedCategories.joinToString(", ")
        viewModelScope.launch {
            _sheetState.update { it.copy(isSubmittingFault = true, faultSubmitError = null) }
            val result = laundryRepository.reportIssue(machineId, combinedCategory, trimmedDesc)
            if (result.isSuccess) {
                _sheetState.update {
                    it.copy(
                        detailsMachineId = null,
                        expandFault = false,
                        isSubmittingFault = false,
                        faultSubmitError = null
                    )
                }
                val msg = "Issue reported. Machine has been flagged for maintenance."
                _message.value = msg
                _events.emit(DashboardEvent.ShowMessage(msg))
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unable to submit fault report."
                _sheetState.update { it.copy(isSubmittingFault = false, faultSubmitError = err) }
            }
        }
    }

    fun reportBrokenMachine(machineId: String, category: String, description: String) {
        viewModelScope.launch {
            val result = laundryRepository.reportIssue(machineId, category, description)
            if (result.isSuccess) {
                val msg = "Machine reported for maintenance. Thank you!"
                _message.value = msg
                _events.emit(DashboardEvent.ShowMessage(msg))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to report issue"
                _message.value = msg
                _events.emit(DashboardEvent.ShowMessage(msg))
            }
        }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            val next = !uiState.value.notificationsEnabled
            sessionManager.setNotificationEnabled(next)
            val msg = if (next) "Laundry notifications enabled" else "Laundry notifications paused"
            _events.emit(DashboardEvent.ShowMessage(msg))
        }
    }

    private fun buildMachineUiModel(
        machine: WashingMachine,
        todayBookings: List<SlotBooking>,
        now: Long
    ): MachineCardUiModel {
        val activeMachineBookings = todayBookings.filter {
            it.machineId == machine.id &&
                it.status != BookingStatus.CANCELLED &&
                it.status != BookingStatus.COMPLETED
        }

        val queueLength = activeMachineBookings.count {
            it.status == BookingStatus.UPCOMING && it.endTimeMillis > now
        }

        val estimatedWaitMinutes = when (machine.status) {
            MachineStatus.IN_USE -> {
                machine.cycleEstimatedEndTime?.let { end ->
                    ((end - now) / 60_000L).toInt().coerceAtLeast(1)
                } ?: 15
            }
            MachineStatus.RESERVED -> 15
            else -> 0
        }

        val nextAvailableSlot = computeNextAvailableSlot(machine, activeMachineBookings, now)

        return MachineCardUiModel(
            machine = machine,
            queueLength = queueLength,
            estimatedWaitMinutes = estimatedWaitMinutes,
            nextAvailableSlot = nextAvailableSlot,
            supportedCycles = machine.supportedCycles()
        )
    }

    private fun computeNextAvailableSlot(
        machine: WashingMachine,
        todayBookings: List<SlotBooking>,
        now: Long
    ): String {
        if (machine.status == MachineStatus.OUT_OF_ORDER) {
            return "Unavailable (Maintenance)"
        }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = cal[Calendar.HOUR_OF_DAY]
        val bookedSlots = todayBookings.map { it.timeSlot }.toSet()

        if (machine.status == MachineStatus.AVAILABLE && isHourSlotFree(bookedSlots, currentHour)) {
            return "Now"
        }

        val startSearchHour = if (machine.status == MachineStatus.IN_USE) {
            val endHourCal = Calendar.getInstance().apply {
                timeInMillis = machine.cycleEstimatedEndTime ?: (now + 30 * 60_000L)
            }
            (endHourCal[Calendar.HOUR_OF_DAY] + 1).coerceAtLeast(currentHour + 1)
        } else {
            currentHour + 1
        }

        for (hour in startSearchHour.coerceAtLeast(6)..22) {
            val label = SlotTimeHelper.formatHourlySlot(hour)
            if (label !in bookedSlots) {
                return label
            }
        }
        return "Tomorrow, 06:00 AM"
    }

    private fun isHourSlotFree(bookedSlots: Set<String>, hour: Int): Boolean {
        return hour in 6..22 && SlotTimeHelper.formatHourlySlot(hour) !in bookedSlots
    }

    private fun resolveGreeting(): String {
        return when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Hello"
        }
    }
}
