package com.z.takatuf.ui.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BrandMark
import com.z.takatuf.ui.common.PrimaryButton

@Composable
fun AuthScreen() {
    val viewModel = appViewModel { c, _ -> AuthViewModel(c.authRepository, c.profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(viewModel) {
        onDispose {
            keyboard?.hide()
            if (activity?.isChangingConfigurations != true) viewModel.clear()
        }
    }
    AuthContent(
        state = state,
        onMode = viewModel::setMode,
        onName = viewModel::onName,
        onEmail = viewModel::onEmail,
        onPassword = viewModel::onPassword,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun AuthContent(
    state: AuthUiState,
    onMode: (AuthMode) -> Unit,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Image(
                        painter = painterResource(R.drawable.takatuf_wordmark_ar),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "auth-mode",
            ) { mode ->
                Column(Modifier.widthIn(max = 480.dp)) {
                    Text(
                        text = stringResource(
                            when (mode) {
                                AuthMode.SignIn -> R.string.auth_signin_title
                                AuthMode.SignUp -> R.string.auth_signup_title
                                AuthMode.Reset -> R.string.auth_reset_title
                            },
                        ),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            when (mode) {
                                AuthMode.SignIn -> R.string.auth_signin_body
                                AuthMode.SignUp -> R.string.auth_signup_body
                                AuthMode.Reset -> R.string.auth_reset_body
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(28.dp))
                    if (mode == AuthMode.Reset && state.resetSentTo != null) {
                        ResetSent(email = state.resetSentTo, onBack = { onMode(AuthMode.SignIn) })
                    } else {
                        AuthForm(state, mode, onMode, onName, onEmail, onPassword, onSubmit)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthForm(
    state: AuthUiState,
    mode: AuthMode,
    onMode: (AuthMode) -> Unit,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mode == AuthMode.SignUp) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.field_full_name)) },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.PersonFullName },
            )
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text(stringResource(R.string.field_university_email)) },
            placeholder = { Text(stringResource(R.string.field_email_placeholder)) },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(stringResource(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (mode == AuthMode.Reset) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
        )
        if (mode != AuthMode.Reset) {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text(stringResource(R.string.field_password)) },
                isError = state.passwordError != null,
                supportingText = (state.passwordError ?: if (mode == AuthMode.SignUp) R.string.auth_password_hint else null)
                    ?.let { message -> { Text(stringResource(message)) } },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.action_hide_password else R.string.action_show_password),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentType = if (mode == AuthMode.SignUp) ContentType.NewPassword else ContentType.Password
                    },
            )
        }
        if (mode == AuthMode.SignIn) {
            TextButton(onClick = { onMode(AuthMode.Reset) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.auth_forgot_password))
            }
        }
        AnimatedVisibility(visible = state.error != null) {
            state.error?.let { error -> AuthError(error, onSignInInstead = { onMode(AuthMode.SignIn) }) }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = stringResource(
                when (mode) {
                    AuthMode.SignIn -> R.string.auth_signin_action
                    AuthMode.SignUp -> R.string.auth_signup_action
                    AuthMode.Reset -> R.string.auth_reset_action
                },
            ),
            onClick = { focus.clearFocus(); onSubmit() },
            loading = state.submitting,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                AuthMode.SignIn -> {
                    Text(stringResource(R.string.auth_new_here), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onMode(AuthMode.SignUp) }) { Text(stringResource(R.string.auth_signup_action)) }
                }
                AuthMode.SignUp -> {
                    Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onMode(AuthMode.SignIn) }) { Text(stringResource(R.string.auth_signin_action)) }
                }
                AuthMode.Reset -> {
                    TextButton(onClick = { onMode(AuthMode.SignIn) }) { Text(stringResource(R.string.auth_back_to_signin)) }
                }
            }
        }
    }
}

@Composable
private fun AuthError(error: AppError, onSignInInstead: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(error.message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (error == AppError.EmailInUse) {
                TextButton(onClick = onSignInInstead) { Text(stringResource(R.string.auth_signin_instead)) }
            }
        }
    }
}

@Composable
private fun ResetSent(email: String, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.auth_reset_sent, email),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PrimaryButton(text = stringResource(R.string.auth_back_to_signin), onClick = onBack)
    }
}
