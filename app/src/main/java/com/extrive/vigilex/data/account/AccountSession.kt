package com.extrive.vigilex.data.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class Account(val id: String = "", val name: String, val email: String)

/**
 * A signed-in session: the access token, when it expires, which VigilEx server
 * issued it, and the account. Only ever stored encrypted (KeystoreSessionStore).
 * The password is never stored.
 */
@Serializable
data class StoredSession(
    val token: String,
    val expiresAtMillis: Long,
    val serverUrl: String,
    val account: Account
)

/** Where the session is kept between launches. */
interface SessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

/** Not persisted: used before [AccountSession.init] and in tests. */
class InMemorySessionStore(private var session: StoredSession? = null) : SessionStore {
    override fun load() = session
    override fun save(session: StoredSession) { this.session = session }
    override fun clear() { session = null }
}

enum class SignOutReason {
    /** The user signed out. */
    USER,
    /** The token expired or the server no longer accepts it. */
    EXPIRED,
    /** The server address changed; a token is only ever sent to the server that issued it. */
    SERVER_CHANGED
}

sealed interface SessionState {
    data class SignedIn(val account: Account) : SessionState
    /** [reason] is null on a fresh start with no saved session. */
    data class SignedOut(val reason: SignOutReason? = null) : SessionState
}

/** What the HTTP layer needs from the session. */
interface SessionTokens {
    /** The token to send to [url], or null when signed out, expired, or [url] is not the issuing server. */
    fun tokenFor(url: HttpUrl): String?

    /** The server answered 401 to a request that carried [token]. */
    fun rejected(token: String)
}

/**
 * The signed-in account. The VigilEx server is the authority: the access
 * token it issued is attached to every request to that server (AuthInterceptor),
 * and a 401 for it ends the session. The session survives restarts through
 * the encrypted [SessionStore]; an expired one is discarded on restore.
 */
object AccountSession : SessionTokens {
    private var store: SessionStore = InMemorySessionStore()
    @Volatile private var session: StoredSession? = null

    /** Milliseconds; replaceable in tests. */
    var clock: () -> Long = System::currentTimeMillis

    /** Called after every sign-out, to remove the previous account's data from the device. */
    var onSignedOut: (() -> Unit)? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.SignedOut())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _current = MutableStateFlow<Account?>(null)
    val current: StateFlow<Account?> = _current.asStateFlow()

    val isSignedIn: Boolean get() = session != null

    /** The server the current session belongs to, or null when signed out. */
    val serverUrl: String? get() = session?.serverUrl

    /** Loads the saved session. An unreadable or expired one is discarded. */
    @Synchronized
    fun init(store: SessionStore) {
        this.store = store
        val saved = try {
            store.load()
        } catch (e: Exception) {
            null
        }
        if (saved == null || expired(saved)) {
            store.clear()
            set(null, SessionState.SignedOut(if (saved != null) SignOutReason.EXPIRED else null))
        } else {
            set(saved, SessionState.SignedIn(saved.account))
        }
    }

    @Synchronized
    fun signIn(session: StoredSession) {
        store.save(session)
        set(session, SessionState.SignedIn(session.account))
    }

    /** Keeps the session, with the account details the server just returned. */
    @Synchronized
    fun updateAccount(account: Account) {
        val updated = session?.copy(account = account) ?: return
        store.save(updated)
        set(updated, SessionState.SignedIn(account))
    }

    @Synchronized
    fun signOut(reason: SignOutReason = SignOutReason.USER) {
        val wasSignedIn = session != null
        store.clear()
        set(null, SessionState.SignedOut(reason))
        if (wasSignedIn) onSignedOut?.invoke()
    }

    /** Ends the session when [serverUrl] is not the server it was issued by. */
    fun serverChanged(serverUrl: String) {
        val current = session ?: return
        if (current.serverUrl != serverUrl) signOut(SignOutReason.SERVER_CHANGED)
    }

    override fun tokenFor(url: HttpUrl): String? {
        val current = session ?: return null
        if (expired(current)) {
            signOut(SignOutReason.EXPIRED)
            return null
        }
        val issuer = current.serverUrl.toHttpUrlOrNull() ?: return null
        val sameServer = issuer.scheme == url.scheme && issuer.host == url.host && issuer.port == url.port
        return current.token.takeIf { sameServer }
    }

    @Synchronized
    override fun rejected(token: String) {
        // Only the token that was refused: a newer sign-in is not undone by an old request.
        if (session?.token == token) signOut(SignOutReason.EXPIRED)
    }

    private fun expired(s: StoredSession) = clock() >= s.expiresAtMillis

    private fun set(session: StoredSession?, state: SessionState) {
        this.session = session
        _current.value = session?.account
        _state.value = state
    }
}
