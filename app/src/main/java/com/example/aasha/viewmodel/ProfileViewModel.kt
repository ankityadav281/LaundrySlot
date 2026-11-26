package com.example.aasha.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.repository.AuthRepository
import com.example.aasha.data.repository.FirestoreRepository
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.domain.model.User
import com.example.aasha.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val studentId: String = "",
    val name: String = "Hosteller",
    val email: String = "",
    val roomNumber: String = "101",
    val hostelBlock: String = "Block A",
    val floor: Int = 1,
    val completedWashes: Int = 0,
    val upcomingSlots: Int = 0,
    val pendingSync: Int = 0,
    val currentLanguage: String = "en",
    val notificationsEnabled: Boolean = true,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val hasMpin: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private data class IdentitySnapshot(
        val id: String,
        val name: String,
        val email: String,
        val room: String,
        val block: String,
        val floor: Int
    )

    private data class PreferencesSnapshot(
        val lang: String,
        val notifEnabled: Boolean,
        val lastSyncTime: Long,
        val isSyncing: Boolean,
        val isOnline: Boolean
    )

    private val identityFlow = combine<Any?, IdentitySnapshot>(
        sessionManager.hostellerId,
        sessionManager.name,
        sessionManager.email,
        sessionManager.roomNumber,
        sessionManager.hostelBlock,
        sessionManager.floor
    ) { params ->
        IdentitySnapshot(
            id = (params[0] as String?).orEmpty(),
            name = (params[1] as String?) ?: "Hosteller",
            email = (params[2] as String?).orEmpty(),
            room = params[3] as String,
            block = params[4] as String,
            floor = params[5] as Int
        )
    }

    private val prefsFlow = combine(
        sessionManager.language,
        sessionManager.notificationEnabled,
        sessionManager.lastSyncTime,
        _isSyncing,
        networkMonitor.isOnline
    ) { lang, notifEnabled, lastSync, syncing, online ->
        PreferencesSnapshot(
            lang = lang,
            notifEnabled = notifEnabled,
            lastSyncTime = lastSync,
            isSyncing = syncing,
            isOnline = online
        )
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        identityFlow,
        prefsFlow,
        laundryRepository.userCompletedCount,
        laundryRepository.userUpcomingBookingsCount,
        laundryRepository.pendingSyncCount
    ) { identity, prefs, completedCount, upcomingCount, pendingCount ->
        val hasMpin = identity.id.isNotEmpty() && sessionManager.hasMpin(identity.id)

        ProfileUiState(
            studentId = identity.id,
            name = identity.name,
            email = identity.email,
            roomNumber = identity.room,
            hostelBlock = identity.block,
            floor = identity.floor,
            completedWashes = completedCount,
            upcomingSlots = upcomingCount,
            pendingSync = pendingCount,
            currentLanguage = prefs.lang,
            notificationsEnabled = prefs.notifEnabled,
            lastSyncTime = prefs.lastSyncTime,
            isSyncing = prefs.isSyncing,
            isOnline = prefs.isOnline,
            hasMpin = hasMpin
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )

    fun changeLanguage(langCode: String) {
        viewModelScope.launch {
            sessionManager.saveLanguage(langCode)
            _events.emit("Language preference updated")
        }
    }

    fun updateHostelDetails(roomNumber: String, hostelBlock: String, floor: Int): String? {
        val trimmedRoom = roomNumber.trim()
        val trimmedBlock = hostelBlock.trim()

        if (trimmedRoom.isEmpty()) return "Room number cannot be empty."
        if (trimmedBlock.isEmpty()) return "Hostel block cannot be empty."
        if (floor !in 1..10) return "Floor must be between 1 and 10."

        viewModelScope.launch {
            sessionManager.updateHostelDetails(trimmedRoom, trimmedBlock, floor)

            val currentId = sessionManager.hostellerId.first().orEmpty()
            if (currentId.isNotBlank() && networkMonitor.isCurrentlyOnline()) {
                val snapshot = uiState.value
                runCatching {
                    firestoreRepository.updateUserProfile(
                        User(
                            studentId = currentId,
                            authUid = authRepository.getCurrentUserUid().orEmpty(),
                            name = snapshot.name,
                            email = snapshot.email,
                            roomNumber = trimmedRoom,
                            hostelBlock = trimmedBlock,
                            floor = floor
                        )
                    )
                }
            }
            _events.emit("Hostel details updated")
        }
        return null
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setNotificationEnabled(enabled)
        }
    }

    fun triggerSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true

        viewModelScope.launch {
            try {
                val result = laundryRepository.refreshDashboard()
                if (result.isSuccess) {
                    _events.emit("All bookings and machine statuses synced")
                } else {
                    _events.emit(result.exceptionOrNull()?.message ?: "Sync failed. Please retry.")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.clearSession()
        }
    }
}
