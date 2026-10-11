package com.extrive.vigilex.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.account.AuthRepository
import com.extrive.vigilex.data.account.MIN_PASSWORD_LENGTH
import com.extrive.vigilex.data.account.SessionState
import com.extrive.vigilex.data.account.SignOutReason
import com.extrive.vigilex.data.account.confirmPasswordError
import com.extrive.vigilex.data.account.emailError
import com.extrive.vigilex.data.account.nameError
import com.extrive.vigilex.data.account.newPasswordError
import com.extrive.vigilex.data.account.signInPasswordError
import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.settings.ServerSettings
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.ErrorState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.VxTextField
import com.extrive.vigilex.ui.components.gutter
import com.extrive.vigilex.ui.components.title
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType
import kotlinx.coroutines.launch

/*
 * Accounts live on the VigilEx server. These screens validate on the device,
 * then sign in or create the account there; the session they start is what
 * every other screen uses. Passwords are held only in memory while typed
 * (never in saved state) and are never stored.
 */

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository() }
    val session by AccountSession.state.collectAsState()
    val server by ServerSettings.serverUrl.collectAsState()
    val developer by ServerSettings.developerOptions.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Error?>(null) }

    val emailProblem = emailError(email).takeIf { submitted }
    val passwordProblem = signInPasswordError(password).takeIf { submitted }

    fun submit() {
        submitted = true
        if (busy || emailError(email) != null || signInPasswordError(password) != null) return
        focus.clearFocus()
        busy = true
        failure = null
        scope.launch {
            when (val result = repository.login(email, password)) {
                is ApiResult.Success -> {
                    password = ""
                    onSignedIn()
                }
                is ApiResult.Error -> failure = result
            }
            busy = false
        }
    }

    AuthScaffold(title = "Sign in", onClose = null) {
        sessionNotice((session as? SessionState.SignedOut)?.reason)?.let {
            Text(it, style = VxType.body, color = InkSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(Space.xl))
        }
        VxTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it; failure = null },
            error = emailProblem,
            keyboardType = KeyboardType.Email,
            onImeAction = { focus.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it; failure = null },
            error = passwordProblem,
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = ::submit
        )
        Spacer(Modifier.height(Space.xs))
        TextLink("Forgot password?", onForgotPassword, Modifier.offset(x = (-12).dp))
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(
            text = if (busy) "Signing in…" else "Sign in",
            onClick = ::submit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        failure?.let { AuthFailure(it, onOpenServerSettings.takeIf { developer }) }

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New to VigilEx?", style = VxType.bodySmall, color = InkSecondary)
            TextLink("Create account", onCreateAccount)
        }
        ServerLine(server, onOpenServerSettings.takeIf { developer })
    }
}

@Composable
fun CreateAccountScreen(
    onClose: () -> Unit,
    onCreated: () -> Unit,
    onSignIn: () -> Unit
) {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val repository = remember { AuthRepository() }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Error?>(null) }

    fun valid() = nameError(name) == null && emailError(email) == null &&
        newPasswordError(password) == null && confirmPasswordError(password, confirmation) == null

    fun submit() {
        submitted = true
        if (busy || !valid()) return
        focus.clearFocus()
        busy = true
        failure = null
        scope.launch {
            when (val result = repository.register(name, email, password)) {
                is ApiResult.Success -> {
                    password = ""
                    confirmation = ""
                    onCreated()
                }
                is ApiResult.Error -> failure = result
            }
            busy = false
        }
    }

    val next = { focus.moveFocus(FocusDirection.Down) }
    val edited = { failure = null }

    AuthScaffold(title = "Create account", onClose = onClose) {
        VxTextField(
            label = "Name",
            value = name,
            onValueChange = { name = it; edited() },
            error = nameError(name).takeIf { submitted },
            onImeAction = { next() }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it; edited() },
            error = emailError(email).takeIf { submitted },
            keyboardType = KeyboardType.Email,
            onImeAction = { next() }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it; edited() },
            error = newPasswordError(password).takeIf { submitted },
            password = true,
            onImeAction = { next() }
        )
        if (!submitted || newPasswordError(password) == null) {
            Spacer(Modifier.height(Space.xs))
            Text("At least $MIN_PASSWORD_LENGTH characters.", style = VxType.bodySmall, color = InkMuted)
        }
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Confirm password",
            value = confirmation,
            onValueChange = { confirmation = it; edited() },
            error = confirmPasswordError(password, confirmation).takeIf { submitted },
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = ::submit
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(
            text = if (busy) "Creating account…" else "Create account",
            onClick = ::submit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
        failure?.let { AuthFailure(it, null) }

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = VxType.bodySmall, color = InkSecondary)
            TextLink("Sign in", onSignIn)
        }
    }
}

/**
 * The server has no password-reset email yet. This says so plainly instead of
 * pretending to send a link.
 */
@Composable
fun ForgotPasswordScreen(onBackToSignIn: () -> Unit) {
    AuthScaffold(title = "Forgot password", onClose = onBackToSignIn) {
        EmptyState(
            title = "Password reset is not available yet",
            body = "VigilEx cannot send reset emails yet. Ask your VigilEx administrator to reset your password, " +
                "then sign in with the new one."
        )
        Spacer(Modifier.height(Space.lg))
        TextLink("Back to sign in", onBackToSignIn, Modifier.offset(x = (-12).dp))
    }
}

/** Why the user is on the sign-in screen, when it is not a fresh start. */
internal fun sessionNotice(reason: SignOutReason?): String? = when (reason) {
    SignOutReason.EXPIRED -> "Your session has expired. Sign in again to continue."
    SignOutReason.SERVER_CHANGED -> "The server address changed. Sign in to the new server."
    SignOutReason.USER, null -> null
}

/** A failed sign-in or registration, in words that say what to do next. */
@Composable
private fun AuthFailure(error: ApiResult.Error, onOpenServerSettings: (() -> Unit)?) {
    Spacer(Modifier.height(Space.xl))
    val unreachable = error.kind == ErrorKind.UNREACHABLE || error.kind == ErrorKind.TIMEOUT
    ErrorState(
        title = when (error.kind) {
            ErrorKind.UNAUTHORIZED -> "Not signed in"
            ErrorKind.CONFLICT -> "Email already registered"
            ErrorKind.REJECTED -> "Check your details"
            else -> error.title()
        },
        message = when (error.kind) {
            ErrorKind.CONFLICT -> "${error.message} Sign in instead, or use another email."
            else -> error.message
        },
        actions = if (unreachable && onOpenServerSettings != null) {
            { PrimaryButton("Server settings", onClick = onOpenServerSettings) }
        } else null
    )
}

/** Which server the account is on; developers can change it before signing in. */
@Composable
private fun ServerLine(server: String, onOpenServerSettings: (() -> Unit)?) {
    Spacer(Modifier.height(Space.lg))
    Text("Server: $server", style = VxType.mono, color = InkMuted)
    if (onOpenServerSettings != null) TextLink("Change server", onOpenServerSettings, Modifier.offset(x = (-12).dp))
}

/** Single centred column, wordmark above the title, no navigation chrome. No back action when [onClose] is null. */
@Composable
private fun AuthScaffold(
    title: String,
    onClose: (() -> Unit)?,
    supporting: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalWindowClass.current.gutter())
    ) {
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(top = Space.xxl, bottom = Space.xxxl)
        ) {
            Text("VIGILEX", style = VxType.wordmark, color = Ink)
            Spacer(Modifier.height(Space.xl))
            Text(title, style = VxType.pageTitleCompact, color = Ink)
            if (supporting != null) {
                Spacer(Modifier.height(Space.sm))
                Text(supporting, style = VxType.body, color = InkSecondary)
            }
            Spacer(Modifier.height(Space.xxl))
            content()
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = VxType.bodySmall.copy(fontWeight = VxType.title.fontWeight), color = Ink)
    }
}
