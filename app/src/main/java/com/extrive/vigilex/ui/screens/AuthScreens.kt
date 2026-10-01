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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.extrive.vigilex.data.account.AccountSession
import com.extrive.vigilex.data.account.MIN_PASSWORD_LENGTH
import com.extrive.vigilex.data.account.confirmPasswordError
import com.extrive.vigilex.data.account.emailError
import com.extrive.vigilex.data.account.nameError
import com.extrive.vigilex.data.account.newPasswordError
import com.extrive.vigilex.data.account.signInPasswordError
import com.extrive.vigilex.ui.components.EmptyState
import com.extrive.vigilex.ui.components.LocalWindowClass
import com.extrive.vigilex.ui.components.PrimaryButton
import com.extrive.vigilex.ui.components.VxTextField
import com.extrive.vigilex.ui.components.gutter
import com.extrive.vigilex.ui.theme.Ink
import com.extrive.vigilex.ui.theme.InkMuted
import com.extrive.vigilex.ui.theme.InkSecondary
import com.extrive.vigilex.ui.theme.Space
import com.extrive.vigilex.ui.theme.VxType

/*
 * The backend has no authentication yet (AccountSession.AUTH_AVAILABLE is
 * false). These screens validate input on the device and then say plainly that
 * accounts are not available, offering to continue without one. They never
 * report a sign-in, account or reset that did not happen.
 */

@Composable
fun SignInScreen(
    onClose: () -> Unit,
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf(false) }

    val emailProblem = emailError(email).takeIf { submitted }
    val passwordProblem = signInPasswordError(password).takeIf { submitted }

    fun submit() {
        submitted = true
        if (emailError(email) != null || signInPasswordError(password) != null) return
        focus.clearFocus()
        if (!AccountSession.AUTH_AVAILABLE) unavailable = true
    }

    AuthScaffold(title = "Sign in", onClose = onClose) {
        VxTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it; unavailable = false },
            error = emailProblem,
            keyboardType = KeyboardType.Email,
            onImeAction = { focus.moveFocus(FocusDirection.Down) }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it; unavailable = false },
            error = passwordProblem,
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = ::submit
        )
        Spacer(Modifier.height(Space.xs))
        TextLink("Forgot password?", onForgotPassword, Modifier.offset(x = (-12).dp))
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(text = "Sign in", onClick = ::submit, modifier = Modifier.fillMaxWidth())

        if (unavailable) AccountsUnavailable("Sign-in is not available yet", onSignedIn)

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New to VigilEx?", style = VxType.bodySmall, color = InkSecondary)
            TextLink("Create account", onCreateAccount)
        }
    }
}

@Composable
fun CreateAccountScreen(
    onClose: () -> Unit,
    onCreated: () -> Unit,
    onSignIn: () -> Unit
) {
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf(false) }

    fun valid() = nameError(name) == null && emailError(email) == null &&
        newPasswordError(password) == null && confirmPasswordError(password, confirmation) == null

    fun submit() {
        submitted = true
        if (!valid()) return
        focus.clearFocus()
        if (!AccountSession.AUTH_AVAILABLE) unavailable = true
    }

    val next = { focus.moveFocus(FocusDirection.Down) }

    AuthScaffold(title = "Create account", onClose = onClose) {
        VxTextField(
            label = "Name",
            value = name,
            onValueChange = { name = it; unavailable = false },
            error = nameError(name).takeIf { submitted },
            onImeAction = { next() }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it; unavailable = false },
            error = emailError(email).takeIf { submitted },
            keyboardType = KeyboardType.Email,
            onImeAction = { next() }
        )
        Spacer(Modifier.height(Space.lg))
        VxTextField(
            label = "Password",
            value = password,
            onValueChange = { password = it; unavailable = false },
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
            onValueChange = { confirmation = it; unavailable = false },
            error = confirmPasswordError(password, confirmation).takeIf { submitted },
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = ::submit
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Create account", onClick = ::submit, modifier = Modifier.fillMaxWidth())

        if (unavailable) AccountsUnavailable("Account creation is not available yet", onCreated)

        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = VxType.bodySmall, color = InkSecondary)
            TextLink("Sign in", onSignIn)
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBackToSignIn: () -> Unit) {
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var unavailable by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        submitted = true
        if (emailError(email) != null) return
        focus.clearFocus()
        if (!AccountSession.AUTH_AVAILABLE) unavailable = true
    }

    AuthScaffold(
        title = "Forgot password",
        supporting = "Enter your email and we'll send you a reset link.",
        onClose = onBackToSignIn
    ) {
        VxTextField(
            label = "Email",
            value = email,
            onValueChange = { email = it; unavailable = false },
            error = emailError(email).takeIf { submitted },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = ::submit
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Send reset link", onClick = ::submit, modifier = Modifier.fillMaxWidth())

        if (unavailable) {
            Spacer(Modifier.height(Space.xl))
            EmptyState(
                title = "Password reset is not available yet",
                body = "VigilEx accounts are not available in this version, so no reset link was sent."
            )
        }

        Spacer(Modifier.height(Space.lg))
        TextLink("Back to sign in", onBackToSignIn, Modifier.offset(x = (-12).dp))
    }
}

/** Shown after a valid submission while the backend has no accounts. */
@Composable
private fun AccountsUnavailable(title: String, onContinue: () -> Unit) {
    Spacer(Modifier.height(Space.xl))
    EmptyState(
        title = title,
        body = "VigilEx accounts are not available in this version. You can keep using VigilEx " +
            "without an account; assessments are saved on this device.",
        actionLabel = "Continue without account",
        onAction = onContinue
    )
}

/** Single centred column, wordmark above the title, no navigation chrome. */
@Composable
private fun AuthScaffold(
    title: String,
    onClose: () -> Unit,
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
            IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
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
