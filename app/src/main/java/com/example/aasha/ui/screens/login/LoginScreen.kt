package com.example.aasha.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aasha.R
import com.example.aasha.ui.components.LaundryButton
import com.example.aasha.ui.components.LaundryButtonStyle
import com.example.aasha.ui.components.LaundryCard
import com.example.aasha.ui.components.LaundryTextField
import com.example.aasha.viewmodel.LoginUiState
import com.example.aasha.viewmodel.LoginViewModel

sealed class ScreenMode {
    object Selection : ScreenMode()
    object Login : ScreenMode()
    object Register : ScreenMode()
    object SetupMpin : ScreenMode()
    object LoginMpin : ScreenMode()
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    showMpinInitially: Boolean = true,
    initialMode: ScreenMode? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var screenMode by remember {
        mutableStateOf(
            initialMode ?: if (showMpinInitially) ScreenMode.LoginMpin else ScreenMode.Selection
        )
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> onLoginSuccess()
            is LoginUiState.RegistrationSuccess -> screenMode = ScreenMode.SetupMpin
            is LoginUiState.MPinSetupSuccess -> {
                snackbarHostState.showSnackbar(context.getString(R.string.account_setup_success))
                onLoginSuccess()
            }
            else -> {}
        }
    }

    val errorMessage = (uiState as? LoginUiState.Error)?.message

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocalLaundryService,
                            contentDescription = stringResource(R.string.laundry_slot_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.laundry_slot_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = when (screenMode) {
                        ScreenMode.Selection -> stringResource(R.string.welcome_back)
                        ScreenMode.Login -> stringResource(R.string.worker_login)
                        ScreenMode.Register -> stringResource(R.string.create_account)
                        ScreenMode.SetupMpin -> stringResource(R.string.setup_secure_mpin)
                        ScreenMode.LoginMpin -> stringResource(R.string.enter_mpin)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                AnimatedVisibility(visible = errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                LaundryCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    when (screenMode) {
                        ScreenMode.Selection -> SelectionMode(
                            viewModel = viewModel,
                            onModeChange = {
                                viewModel.resetToIdle()
                                screenMode = it
                            }
                        )

                        ScreenMode.Login -> LoginMode(
                            uiState = uiState,
                            onLogin = { id, pw -> viewModel.loginWithStudentId(id, pw) },
                            onModeChange = {
                                viewModel.resetToIdle()
                                screenMode = it
                            }
                        )

                        ScreenMode.Register -> RegisterMode(
                            uiState = uiState,
                            onRegister = { name, id, email, pw, room, block ->
                                viewModel.signUp(name, id, email, pw, room, block)
                            },
                            onModeChange = {
                                viewModel.resetToIdle()
                                screenMode = it
                            }
                        )

                        ScreenMode.SetupMpin -> MpinMode(
                            uiState = uiState,
                            title = stringResource(R.string.mpin_setup_msg),
                            buttonText = stringResource(R.string.set_mpin),
                            onSubmit = { viewModel.setupMPin(it) }
                        )

                        ScreenMode.LoginMpin -> MpinMode(
                            uiState = uiState,
                            title = stringResource(R.string.mpin_entry_msg),
                            buttonText = stringResource(R.string.unlock_app),
                            onSubmit = { viewModel.loginWithMPin(it) },
                            onModeChange = {
                                viewModel.resetToIdle()
                                screenMode = it
                            },
                            onReset = { viewModel.resetToIdle() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SelectionMode(
    viewModel: LoginViewModel,
    onModeChange: (ScreenMode) -> Unit
) {
    val hasMpin by viewModel.hasMpin.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LaundryButton(
            text = stringResource(R.string.login_with_mpin),
            onClick = { onModeChange(ScreenMode.LoginMpin) },
            enabled = hasMpin,
            modifier = Modifier.fillMaxWidth()
        )
        LaundryButton(
            text = stringResource(R.string.login_with_aasha_id),
            onClick = { onModeChange(ScreenMode.Login) },
            style = LaundryButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { onModeChange(ScreenMode.Register) }) {
            Text(
                text = stringResource(R.string.new_user_signup),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LoginMode(
    uiState: LoginUiState,
    onLogin: (String, String) -> Unit,
    onModeChange: (ScreenMode) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var studentId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val idEmptyError = stringResource(R.string.id_cannot_empty)
    val passwordEmptyError = stringResource(R.string.password_cannot_empty)

    val submitLogin = {
        val idBlank = studentId.isBlank()
        val pwBlank = password.isBlank()
        idError = if (idBlank) idEmptyError else null
        passwordError = if (pwBlank) passwordEmptyError else null
        if (!idBlank && !pwBlank) {
            focusManager.clearFocus()
            onLogin(studentId, password)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LaundryTextField(
            value = studentId,
            onValueChange = { studentId = it; idError = null },
            label = stringResource(R.string.student_id_or_email_label),
            placeholder = "e.g., 21CS049 or student@hostel.edu",
            isError = idError != null,
            supportingText = idError,
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        LaundryTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = stringResource(R.string.password),
            isError = passwordError != null,
            supportingText = passwordError,
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submitLogin() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        LaundryButton(
            text = stringResource(R.string.login),
            onClick = submitLogin,
            isLoading = uiState is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = { onModeChange(ScreenMode.Register) }) {
            Text(
                text = stringResource(R.string.new_user_signup),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun RegisterMode(
    uiState: LoginUiState,
    onRegister: (String, String, String, String, String, String) -> Unit,
    onModeChange: (ScreenMode) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var hostelBlock by remember { mutableStateOf("Block A") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var idError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val nameEmptyErr = stringResource(R.string.name_cannot_empty)
    val idEmptyErr = stringResource(R.string.id_cannot_empty)
    val pwShortErr = stringResource(R.string.password_too_short)

    val submitRegister = {
        nameError = if (name.isBlank()) nameEmptyErr else null
        idError = if (studentId.isBlank()) idEmptyErr else null
        passwordError = if (password.length < 6) pwShortErr else null

        if (nameError == null && idError == null && passwordError == null) {
            focusManager.clearFocus()
            onRegister(
                name.trim(),
                studentId.trim(),
                email.trim().ifBlank { "${studentId.trim()}@hostel.edu" },
                password,
                roomNumber.trim().ifBlank { "101" },
                hostelBlock.trim().ifBlank { "Block A" }
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LaundryTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = stringResource(R.string.full_name),
            placeholder = "e.g., Alex Johnson",
            isError = nameError != null,
            supportingText = nameError,
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        LaundryTextField(
            value = studentId,
            onValueChange = { studentId = it; idError = null },
            label = stringResource(R.string.student_id_label),
            placeholder = "e.g., 2024CS102",
            isError = idError != null,
            supportingText = idError,
            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        LaundryTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.email_address),
            placeholder = "e.g., student@hostel.edu",
            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LaundryTextField(
                value = roomNumber,
                onValueChange = { roomNumber = it },
                label = stringResource(R.string.room_number_label),
                placeholder = "e.g., 204",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                modifier = Modifier.weight(1f)
            )
            LaundryTextField(
                value = hostelBlock,
                onValueChange = { hostelBlock = it },
                label = stringResource(R.string.hostel_block_label),
                placeholder = "Block A",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.weight(1f)
            )
        }

        LaundryTextField(
            value = password,
            onValueChange = { password = it; passwordError = null },
            label = stringResource(R.string.set_password),
            isError = passwordError != null,
            supportingText = passwordError,
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submitRegister() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        LaundryButton(
            text = stringResource(R.string.signup),
            onClick = submitRegister,
            isLoading = uiState is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = { onModeChange(ScreenMode.Login) }) {
            Text(
                text = stringResource(R.string.already_have_account),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun MpinMode(
    uiState: LoginUiState,
    title: String,
    buttonText: String,
    onSubmit: (String) -> Unit,
    onModeChange: ((ScreenMode) -> Unit)? = null,
    onReset: (() -> Unit)? = null
) {
    var mPin by remember { mutableStateOf("") }
    var mPinVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        LaundryTextField(
            value = mPin,
            onValueChange = { input ->
                if (input.length <= 4 && input.all { it.isDigit() }) {
                    mPin = input
                }
            },
            label = stringResource(R.string.mpin_label),
            placeholder = "••••",
            supportingText = "${mPin.length}/4 digits",
            visualTransformation = if (mPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { mPinVisible = !mPinVisible }) {
                    Icon(
                        imageVector = if (mPinVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (mPinVisible) "Hide MPIN" else "Show MPIN"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (mPin.length == 4) onSubmit(mPin) }
            ),
            modifier = Modifier.widthIn(max = 240.dp)
        )

        LaundryButton(
            text = buttonText,
            onClick = { if (mPin.length == 4) onSubmit(mPin) },
            enabled = mPin.length == 4,
            isLoading = uiState is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        )

        if (onModeChange != null) {
            TextButton(onClick = {
                onReset?.invoke()
                onModeChange(ScreenMode.Login)
            }) {
                Text(
                    text = stringResource(R.string.use_id_instead),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
