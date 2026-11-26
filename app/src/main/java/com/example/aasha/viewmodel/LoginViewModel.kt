package com.example.aasha.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aasha.data.local.SessionManager
import com.example.aasha.data.repository.AuthRepository
import com.example.aasha.data.repository.FirestoreRepository
import com.example.aasha.data.repository.LaundryRepository
import com.example.aasha.domain.model.User
import com.example.aasha.util.NetworkMonitor
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object RegistrationSuccess : LoginUiState()
    object MPinSetupSuccess : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val laundryRepository: LaundryRepository,
    private val sessionManager: SessionManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _hasMpin = MutableStateFlow(false)
    val hasMpin: StateFlow<Boolean> = _hasMpin.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.lastHostellerId.collect { id ->
                _hasMpin.value = id != null &&
                    sessionManager.hasMpin(id) &&
                    authRepository.isLoggedIn()
            }
        }
    }

    fun signUp(
        name: String,
        studentId: String,
        email: String,
        password: String,
        roomNumber: String,
        hostelBlock: String = "Block A",
        floor: Int = 1
    ) {
        val trimmedName = name.trim()
        val normalizedId = studentId.trim().lowercase(Locale.ROOT)
        val trimmedEmail = email.trim().lowercase(Locale.ROOT).ifBlank { "$normalizedId@hostel.edu" }
        val trimmedRoom = roomNumber.trim().ifBlank { "101" }
        val trimmedBlock = hostelBlock.trim().ifBlank { "Block A" }

        if (trimmedName.isBlank()) {
            _uiState.value = LoginUiState.Error("Full name cannot be empty.")
            return
        }
        if (normalizedId.length < 3) {
            _uiState.value = LoginUiState.Error("Student ID must be at least 3 characters.")
            return
        }
        if (password.length < 6) {
            _uiState.value = LoginUiState.Error("Password must be at least 6 characters.")
            return
        }
        if (floor !in 1..10) {
            _uiState.value = LoginUiState.Error("Floor must be between 1 and 10.")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                if (!networkMonitor.isCurrentlyOnline()) {
                    _uiState.value = LoginUiState.Error(
                        "Internet connection is required to create an account and verify Student ID availability."
                    )
                    return@launch
                }

                val authResult = try {
                    authRepository.signUp(trimmedEmail, password)
                } catch (_: FirebaseAuthUserCollisionException) {
                    _uiState.value = LoginUiState.Error("An account with this email already exists.")
                    return@launch
                } catch (_: FirebaseAuthWeakPasswordException) {
                    _uiState.value = LoginUiState.Error("Password is too weak. Use at least 6 characters.")
                    return@launch
                } catch (_: FirebaseAuthInvalidCredentialsException) {
                    _uiState.value = LoginUiState.Error("Please enter a valid email address.")
                    return@launch
                } catch (_: FirebaseNetworkException) {
                    _uiState.value = LoginUiState.Error("Network unavailable. Check your connection and retry.")
                    return@launch
                } catch (e: FirebaseAuthException) {
                    _uiState.value = LoginUiState.Error(e.message ?: "Authentication failed.")
                    return@launch
                }

                if (firestoreRepository.isStudentIdTaken(normalizedId)) {
                    runCatching { authResult.user?.delete() }
                    _uiState.value = LoginUiState.Error("Student ID already registered.")
                    return@launch
                }

                val user = User(
                    studentId = normalizedId,
                    authUid = authResult.user?.uid.orEmpty(),
                    name = trimmedName,
                    email = trimmedEmail,
                    roomNumber = trimmedRoom,
                    hostelBlock = trimmedBlock,
                    floor = floor
                )
                try {
                    firestoreRepository.saveUser(user)
                } catch (e: Exception) {
                    // Roll back orphaned auth account if Firestore profile write fails
                    runCatching { authResult.user?.delete() }
                    val msg = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                        "Student ID already registered."
                    } else {
                        e.message ?: "Registration failed."
                    }
                    _uiState.value = LoginUiState.Error(msg)
                    return@launch
                }

                sessionManager.saveSession(
                    hostellerId = normalizedId,
                    name = trimmedName,
                    email = trimmedEmail,
                    roomNumber = trimmedRoom,
                    hostelBlock = trimmedBlock,
                    floor = floor
                )
                sessionManager.savePasswordHash(password, normalizedId)
                sessionManager.saveUserEmail(trimmedEmail, normalizedId)
                laundryRepository.triggerOneTimeSync()

                _uiState.value = LoginUiState.RegistrationSuccess
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Registration failed.")
            }
        }
    }

    fun loginWithStudentId(identifier: String, password: String) {
        val trimmedInput = identifier.trim()
        if (trimmedInput.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Student ID or Email and Password are required.")
            return
        }

        val isEmailInput = trimmedInput.contains("@")
        val normalizedInput = trimmedInput.lowercase(Locale.ROOT)

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                // Online login MUST authenticate with Firebase Auth so Firestore security rules authorize cloud sync
                if (networkMonitor.isCurrentlyOnline()) {
                    val emailToSignIn = if (isEmailInput) {
                        normalizedInput
                    } else {
                        sessionManager.getUserEmail(normalizedInput)?.takeIf { it.isNotBlank() }
                            ?: "$normalizedInput@hostel.edu"
                    }

                    try {
                        authRepository.signIn(emailToSignIn, password)
                    } catch (_: FirebaseAuthInvalidCredentialsException) {
                        val hint = if (!isEmailInput && sessionManager.getUserEmail(normalizedInput) == null) {
                            "Invalid credentials. On a new device, enter your registered email address."
                        } else {
                            "Invalid Student ID/Email or Password."
                        }
                        _uiState.value = LoginUiState.Error(hint)
                        return@launch
                    } catch (_: FirebaseAuthInvalidUserException) {
                        val hint = if (!isEmailInput && sessionManager.getUserEmail(normalizedInput) == null) {
                            "Account not found for $normalizedInput@hostel.edu. If you registered with a custom email, sign in using your email."
                        } else {
                            "Account not found. Please sign up first."
                        }
                        _uiState.value = LoginUiState.Error(hint)
                        return@launch
                    } catch (_: FirebaseNetworkException) {
                        _uiState.value = LoginUiState.Error("Network error while signing in. Please try again.")
                        return@launch
                    }

                    val authUid = authRepository.getCurrentUserUid().orEmpty()
                    val remoteUser = authUid.takeIf { it.isNotBlank() }?.let { uid ->
                        firestoreRepository.getUserByAuthUid(uid)
                    } ?: if (!isEmailInput) {
                        firestoreRepository.getUserByStudentId(normalizedInput)
                    } else {
                        null
                    }

                    if (remoteUser == null || remoteUser.studentId.isBlank()) {
                        _uiState.value = LoginUiState.Error(
                            "Hosteller profile could not be loaded. Please check your account or sign up."
                        )
                        return@launch
                    }

                    val resolvedId = remoteUser.studentId.lowercase(Locale.ROOT)
                    val resolvedEmail = remoteUser.email.ifBlank { emailToSignIn }
                    sessionManager.saveSession(
                        hostellerId = resolvedId,
                        name = remoteUser.name.ifBlank { resolvedId.uppercase(Locale.ROOT) },
                        email = resolvedEmail,
                        roomNumber = remoteUser.roomNumber.ifBlank { "101" },
                        hostelBlock = remoteUser.hostelBlock.ifBlank { "Block A" },
                        floor = remoteUser.floor
                    )
                    sessionManager.savePasswordHash(password, resolvedId)
                    sessionManager.saveUserEmail(resolvedEmail, resolvedId)
                    laundryRepository.triggerOneTimeSync()
                    _uiState.value = LoginUiState.Success
                    return@launch
                }

                // Offline fallback: only permit returning cached device users who still hold an active Firebase session
                if (!isEmailInput &&
                    authRepository.isLoggedIn() &&
                    sessionManager.hasPasswordHash(normalizedInput) &&
                    sessionManager.verifyPassword(password, normalizedInput)
                ) {
                    val activeId = sessionManager.hostellerId.first()
                    if (activeId == normalizedInput) {
                        sessionManager.setLoggedIn(true)
                    } else {
                        sessionManager.saveSession(
                            hostellerId = normalizedInput,
                            name = normalizedInput.uppercase(Locale.ROOT),
                            email = sessionManager.getUserEmail(normalizedInput)
                                ?: "$normalizedInput@hostel.edu",
                            roomNumber = "101",
                            hostelBlock = "Block A",
                            floor = 1
                        )
                    }
                    _uiState.value = LoginUiState.Success
                    return@launch
                }

                _uiState.value = LoginUiState.Error(
                    if (!isEmailInput && sessionManager.hasPasswordHash(normalizedInput) &&
                        !sessionManager.verifyPassword(password, normalizedInput)
                    ) {
                        "Invalid Student ID or Password."
                    } else {
                        "You are offline. Connect to sign in and sync your hostel account."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Invalid credentials.")
            }
        }
    }

    fun loginWithMPin(pin: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val hostellerId = sessionManager.hostellerId.first()
                ?: sessionManager.lastHostellerId.first()
            if (hostellerId == null || !authRepository.isLoggedIn()) {
                _uiState.value = LoginUiState.Error(
                    "Session signed out. Please log in with your Student ID / Email and password."
                )
                return@launch
            }

            if (sessionManager.verifyMpin(pin, hostellerId)) {
                val activeId = sessionManager.hostellerId.first()
                if (activeId.isNullOrBlank()) {
                    val remoteUser = if (networkMonitor.isCurrentlyOnline()) {
                        firestoreRepository.getUserByStudentId(hostellerId)
                    } else null
                    val resolvedEmail = remoteUser?.email?.ifBlank { null }
                        ?: sessionManager.getUserEmail(hostellerId)
                        ?: "$hostellerId@hostel.edu"
                    sessionManager.saveSession(
                        hostellerId = hostellerId,
                        name = remoteUser?.name?.ifBlank { hostellerId.uppercase(Locale.ROOT) }
                            ?: hostellerId.uppercase(Locale.ROOT),
                        email = resolvedEmail,
                        roomNumber = remoteUser?.roomNumber?.ifBlank { "101" } ?: "101",
                        hostelBlock = remoteUser?.hostelBlock?.ifBlank { "Block A" } ?: "Block A",
                        floor = remoteUser?.floor ?: 1
                    )
                } else {
                    sessionManager.setLoggedIn(true)
                }
                if (networkMonitor.isCurrentlyOnline()) {
                    laundryRepository.triggerOneTimeSync()
                }
                _uiState.value = LoginUiState.Success
            } else {
                _uiState.value = LoginUiState.Error("Incorrect 4-Digit MPIN")
            }
        }
    }

    fun setupMPin(pin: String) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val id = sessionManager.hostellerId.first() ?: sessionManager.lastHostellerId.first()
            if (id == null) {
                _uiState.value = LoginUiState.Error("Session not found. Please login again.")
                return@launch
            }

            if (pin.length == 4 && pin.all { it.isDigit() }) {
                sessionManager.saveMpin(pin, id)
                _uiState.value = LoginUiState.MPinSetupSuccess
            } else {
                _uiState.value = LoginUiState.Error("MPIN must be 4 digits")
            }
        }
    }

    fun resetToIdle() {
        _uiState.value = LoginUiState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.clearSession()
            _uiState.value = LoginUiState.Idle
        }
    }
}
