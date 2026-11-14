package com.quantumff.segue.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.component.SegueIconButton
import com.quantumff.segue.core.designsystem.icon.SegueIcons
import com.quantumff.segue.core.designsystem.preview.PreviewSegueThemes
import com.quantumff.segue.core.designsystem.preview.SeguePreview
import com.quantumff.segue.core.designsystem.theme.SegueShapes
import com.quantumff.segue.core.designsystem.theme.SegueSizes
import com.quantumff.segue.core.designsystem.theme.SegueSpacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge

/** Login / server setup (DESIGN.md §4.11). Calls [onSignedIn] once the user may enter the app. */
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverUrl = rememberTextFieldState()
    val username = rememberTextFieldState()
    val password = rememberTextFieldState()

    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(uiState.step) {
        if (uiState.step == LoginStep.Finished) currentOnSignedIn()
    }
    // Editing any field clears the previous error.
    LaunchedEffect(serverUrl, username, password) {
        merge(
            snapshotFlow { serverUrl.text.toString() }.drop(1),
            snapshotFlow { username.text.toString() }.drop(1),
            snapshotFlow { password.text.toString() }.drop(1),
        ).collect { viewModel.clearError() }
    }

    LoginScreen(
        uiState = uiState,
        serverUrl = serverUrl,
        username = username,
        password = password,
        onConnectClick = {
            viewModel.connect(serverUrl.text.toString(), username.text.toString(), password.text.toString())
        },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/** One centered column, max width 400, 24 padding. */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    serverUrl: TextFieldState,
    username: TextFieldState,
    password: TextFieldState,
    onConnectClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = SegueSizes.loginMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(SegueSpacing.xl),
        ) {
            Text(
                text = stringResource(R.string.feature_login_wordmark),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(SegueSpacing.xs))
            Text(
                text = stringResource(R.string.feature_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SegueSpacing.xl))
            LoginForm(
                uiState = uiState,
                serverUrl = serverUrl,
                username = username,
                password = password,
                onConnectClick = onConnectClick,
            )
        }
        if (uiState.canGoBack && uiState.step == LoginStep.Form) {
            SegueIconButton(
                icon = SegueIcons.Back,
                contentDescription = stringResource(R.string.feature_login_back),
                onClick = onBackClick,
                modifier = Modifier.padding(SegueSpacing.xs),
            )
        }
    }
}

/** Android has no standard autofill hint for a server address, so the field declares a plain "url" hint. */
private val ServerUrlContentType = ContentType("url")

@Composable
private fun LoginForm(
    uiState: LoginUiState,
    serverUrl: TextFieldState,
    username: TextFieldState,
    password: TextFieldState,
    onConnectClick: () -> Unit,
) {
    val checking = uiState.step == LoginStep.Checking
    val error = uiState.error
    val message = error?.let { errorMessage(it, uiState.errorDetail) }
    val focus = remember { LoginField.entries.associateWith { FocusRequester() } }
    // A failed attempt puts focus on the field to fix, again when it repeats the last error. Runs
    // after the composition that re-enables the fields.
    val attempt = uiState.failedAttempts
    LaunchedEffect(error, checking, attempt) {
        if (error != null && !checking) focus.getValue(error.field).requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(SegueSpacing.md)) {
        OutlinedTextField(
            state = serverUrl,
            label = { Text(stringResource(R.string.feature_login_server_url)) },
            placeholder = { Text(stringResource(R.string.feature_login_server_url_placeholder)) },
            isError = error?.field == LoginField.Url,
            supportingText = message?.takeIf { error?.field == LoginField.Url }?.let { { ErrorText(it, attempt) } },
            enabled = !checking,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            shape = SegueShapes.sm,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.getValue(LoginField.Url))
                .semantics { contentType = ServerUrlContentType },
        )
        OutlinedTextField(
            state = username,
            label = { Text(stringResource(R.string.feature_login_username)) },
            isError = error?.field == LoginField.Username,
            supportingText = message?.takeIf {
                error?.field == LoginField.Username
            }?.let { { ErrorText(it, attempt) } },
            enabled = !checking,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            shape = SegueShapes.sm,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus.getValue(LoginField.Username))
                .semantics { contentType = ContentType.Username },
        )
        PasswordField(
            state = password,
            enabled = !checking,
            errorMessage = message?.takeIf { error?.field == LoginField.Password },
            attempt = attempt,
            onDone = onConnectClick,
            modifier = Modifier.focusRequester(focus.getValue(LoginField.Password)),
        )
        Spacer(Modifier.height(SegueSpacing.xs))
        Button(
            onClick = onConnectClick,
            enabled = !checking,
            shape = SegueShapes.full,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SegueSizes.touchTarget),
        ) {
            if (checking) {
                CircularProgressIndicator(
                    strokeWidth = SegueSizes.spinnerStroke,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SegueSizes.spinner),
                )
                Spacer(Modifier.width(SegueSpacing.sm))
                Text(stringResource(R.string.feature_login_checking))
            } else {
                Text(stringResource(R.string.feature_login_connect))
            }
        }
    }
}

@Composable
private fun PasswordField(
    state: TextFieldState,
    enabled: Boolean,
    errorMessage: String?,
    attempt: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        label = { Text(stringResource(R.string.feature_login_password)) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { ErrorText(it, attempt) } },
        enabled = enabled,
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) SegueIcons.Hidden else SegueIcons.Visible,
                    contentDescription = stringResource(
                        if (visible) R.string.feature_login_hide_password else R.string.feature_login_show_password,
                    ),
                    modifier = Modifier.size(SegueSizes.iconSmall),
                )
            }
        },
        shape = SegueShapes.sm,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
    )
}

/**
 * A field's error, announced by screen readers when it appears. Each failed [attempt] shows it
 * afresh, so the same error repeated is announced again.
 */
@Composable
private fun ErrorText(message: String, attempt: Int) {
    key(attempt) {
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun errorMessage(error: LoginError, detail: String?): String {
    val base = stringResource(
        when (error) {
            LoginError.MissingUrl -> R.string.feature_login_error_missing_url
            LoginError.MissingUsername -> R.string.feature_login_error_missing_username
            LoginError.InvalidUrl -> R.string.feature_login_error_invalid_url
            LoginError.ServerNotFound -> R.string.feature_login_error_not_found
            LoginError.Unreachable -> R.string.feature_login_error_unreachable
            LoginError.Tls -> R.string.feature_login_error_tls
            LoginError.NotSubsonic -> R.string.feature_login_error_not_subsonic
            LoginError.ServerError -> R.string.feature_login_error_server
            LoginError.WrongCredentials -> R.string.feature_login_error_credentials
        },
    )
    return if (detail != null) "$base ($detail)" else base
}

@PreviewSegueThemes
@Composable
private fun LoginScreenPreview() {
    SeguePreview {
        LoginScreen(
            uiState = LoginUiState(error = LoginError.WrongCredentials),
            serverUrl = TextFieldState("demo.navidrome.org"),
            username = TextFieldState("demo"),
            password = TextFieldState("wrong"),
            onConnectClick = {},
            onBackClick = {},
        )
    }
}
