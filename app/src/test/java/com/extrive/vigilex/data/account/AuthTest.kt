package com.extrive.vigilex.data.account

import com.extrive.vigilex.data.api.ApiResult
import com.extrive.vigilex.data.api.AuthApi
import com.extrive.vigilex.data.api.AuthInterceptor
import com.extrive.vigilex.data.api.ErrorKind
import com.extrive.vigilex.data.api.httpError
import com.extrive.vigilex.data.model.AnalysisJson
import com.extrive.vigilex.ui.screens.sessionNotice
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Sign-in, session restore, token attachment, expiry and sign-out, against a
 * real local HTTP server speaking the backend's /api/v1/auth contract.
 */
class AuthTest {

    private val servers = mutableListOf<HttpServer>()
    private var now = 1_000_000L
    private var signedOutCalls = 0

    @Before
    fun setUp() {
        AccountSession.clock = { now }
        AccountSession.onSignedOut = { signedOutCalls++ }
        AccountSession.init(InMemorySessionStore())
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop(0) }
        AccountSession.onSignedOut = null
        AccountSession.clock = System::currentTimeMillis
        AccountSession.init(InMemorySessionStore())
    }

    private class Reply(val status: Int, val body: String = "")

    /** Serves "METHOD /path" -> reply; records each request line with its Authorization header. */
    private fun server(routes: Map<String, Reply>, seen: MutableList<String> = Collections.synchronizedList(mutableListOf())): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val key = "${exchange.requestMethod} ${exchange.requestURI.path}"
            seen += "$key auth=${exchange.requestHeaders.getFirst("Authorization")}"
            exchange.requestBody.readBytes()
            val reply = routes[key] ?: Reply(404, """{"detail":"Not Found"}""")
            val bytes = reply.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) } else exchange.close()
        }
        http.start()
        servers += http
        return "http://127.0.0.1:${http.address.port}/"
    }

    private val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor()).build()

    private fun api(base: String): AuthApi = Retrofit.Builder()
        .baseUrl(base)
        .client(client)
        .addConverterFactory(AnalysisJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApi::class.java)

    private fun token(user: String = "u1", name: String = "Alice", email: String = "alice@example.com", expiresIn: Long = 3600) =
        """{"access_token":"token-$user","token_type":"bearer","expires_at":"2026-10-09T12:00:00Z","expires_in":$expiresIn,
           "user":{"id":"$user","name":"$name","email":"$email","organization_id":"o1","created_at":"2026-10-09T00:00:00Z"}}"""

    private fun session(server: String, token: String = "t1", expiresAt: Long = now + 60_000) =
        StoredSession(token, expiresAt, server, Account("u1", "Alice", "alice@example.com"))

    // ============================================================ sign-in

    @Test
    fun `sign-in starts a session bound to the server that issued the token`() = runBlocking {
        val base = server(mapOf("POST /api/v1/auth/login" to Reply(200, token())))
        val result = AuthRepository(baseUrl = { base }, apiFor = ::api).login(" alice@example.com ", "pw-123456")

        assertEquals(Account("u1", "Alice", "alice@example.com"), (result as ApiResult.Success).data)
        assertTrue(AccountSession.state.value is SessionState.SignedIn)
        assertEquals(base, AccountSession.serverUrl)
        assertEquals("token-u1", AccountSession.tokenFor("${base}api/v1/assessments".toHttpUrl()))
    }

    @Test
    fun `failed sign-in shows the server's generic message and starts no session`() = runBlocking {
        val base = server(mapOf("POST /api/v1/auth/login" to Reply(401, """{"detail":"Incorrect email or password."}""")))
        val error = AuthRepository(baseUrl = { base }, apiFor = ::api).login("a@example.com", "wrong") as ApiResult.Error
        assertEquals(ErrorKind.UNAUTHORIZED, error.kind)
        assertEquals("Incorrect email or password.", error.message)
        assertFalse(AccountSession.isSignedIn)
    }

    @Test
    fun `duplicate email and throttling are reported distinctly`() = runBlocking {
        val base = server(
            mapOf(
                "POST /api/v1/auth/register" to Reply(409, """{"detail":"An account with this email already exists."}"""),
                "POST /api/v1/auth/login" to Reply(429, """{"detail":"Too many attempts. Try again later."}""")
            )
        )
        val repo = AuthRepository(baseUrl = { base }, apiFor = ::api)
        assertEquals(ErrorKind.CONFLICT, (repo.register("A", "a@example.com", "pw-123456") as ApiResult.Error).kind)
        val limited = repo.login("a@example.com", "pw") as ApiResult.Error
        assertEquals(ErrorKind.RATE_LIMITED, limited.kind)
        assertEquals("Too many attempts. Try again later.", limited.message)
    }

    @Test
    fun `registration signs the new account in`() = runBlocking {
        val base = server(mapOf("POST /api/v1/auth/register" to Reply(201, token("u2", "Bob", "bob@example.com"))))
        val account = (AuthRepository(baseUrl = { base }, apiFor = ::api).register("Bob", "bob@example.com", "pw-123456") as ApiResult.Success).data
        assertEquals("bob@example.com", account.email)
        assertEquals(account, AccountSession.current.value)
    }

    @Test
    fun `unreachable server is a network error, not a sign-in failure`() = runBlocking {
        val error = AuthRepository(baseUrl = { "http://127.0.0.1:9/" }, apiFor = ::api).login("a@example.com", "pw") as ApiResult.Error
        assertEquals(ErrorKind.UNREACHABLE, error.kind)
        assertFalse(AccountSession.isSignedIn)
    }

    // ============================================================ restore

    @Test
    fun `a saved session is restored on start`() {
        AccountSession.init(InMemorySessionStore(session("http://server/")))
        assertEquals(SessionState.SignedIn(Account("u1", "Alice", "alice@example.com")), AccountSession.state.value)
        assertEquals("t1", AccountSession.tokenFor("http://server/api/v1/overview".toHttpUrl()))
    }

    @Test
    fun `an expired saved session is discarded on start`() {
        val store = InMemorySessionStore(session("http://server/", expiresAt = now - 1))
        AccountSession.init(store)
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), AccountSession.state.value)
        assertNull(store.load())
    }

    @Test
    fun `an unreadable saved session means signed out`() {
        AccountSession.init(object : SessionStore {
            override fun load(): StoredSession? = throw IllegalStateException("corrupt")
            override fun save(session: StoredSession) = Unit
            override fun clear() = Unit
        })
        assertEquals(SessionState.SignedOut(null), AccountSession.state.value)
    }

    @Test
    fun `a session expiring while the app runs ends at the next request`() {
        AccountSession.init(InMemorySessionStore(session("http://server/")))
        now += 61_000
        assertNull(AccountSession.tokenFor("http://server/api/v1/overview".toHttpUrl()))
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), AccountSession.state.value)
        assertEquals(1, signedOutCalls)
    }

    @Test
    fun `restoring confirms the account with the server`() = runBlocking {
        val base = server(mapOf("GET /api/v1/auth/me" to Reply(200, """{"id":"u1","name":"Alice Renamed","email":"alice@example.com"}""")))
        AccountSession.init(InMemorySessionStore(session(base)))
        AuthRepository(apiFor = ::api).refreshAccount()
        assertEquals("Alice Renamed", AccountSession.current.value?.name)
    }

    @Test
    fun `a restored session the server refuses ends with an expiry notice`() = runBlocking {
        val base = server(mapOf("GET /api/v1/auth/me" to Reply(401, """{"detail":"Your session is invalid or has expired. Sign in again."}""")))
        AccountSession.init(InMemorySessionStore(session(base)))
        assertTrue(AuthRepository(apiFor = ::api).refreshAccount() is ApiResult.Error)
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), AccountSession.state.value)
        assertEquals("Your session has expired. Sign in again to continue.", sessionNotice(SignOutReason.EXPIRED))
    }

    @Test
    fun `offline on start keeps the restored session`() = runBlocking {
        AccountSession.init(InMemorySessionStore(session("http://127.0.0.1:9/")))
        assertEquals(ErrorKind.UNREACHABLE, (AuthRepository(apiFor = ::api).refreshAccount() as ApiResult.Error).kind)
        assertTrue(AccountSession.isSignedIn)
    }

    // ============================================================ requests

    @Test
    fun `the token goes on protected requests, never on sign-in or health`() {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val base = server(
            mapOf(
                "GET /api/v1/assessments" to Reply(200, "{}"),
                "POST /api/v1/auth/login" to Reply(401, "{}"),
                "GET /api/v1/health" to Reply(200, "{}")
            ),
            seen
        )
        AccountSession.init(InMemorySessionStore(session(base)))
        fun call(path: String, post: Boolean = false) = client.newCall(
            Request.Builder().url("$base$path").apply { if (post) post(okhttp3.RequestBody.create(null, "{}")) }.build()
        ).execute().close()

        call("api/v1/assessments")
        call("api/v1/auth/login", post = true)
        call("api/v1/health")
        assertEquals(
            listOf(
                "GET /api/v1/assessments auth=Bearer t1",
                "POST /api/v1/auth/login auth=null",
                "GET /api/v1/health auth=null"
            ),
            seen.toList()
        )
        // A failed sign-in attempt does not end the existing session.
        assertTrue(AccountSession.isSignedIn)
    }

    @Test
    fun `the token is never sent to a different server`() {
        AccountSession.init(InMemorySessionStore(session("http://10.0.2.2:8000/")))
        assertNull(AccountSession.tokenFor("http://192.168.1.20:8000/api/v1/overview".toHttpUrl()))
        assertNull(AccountSession.tokenFor("https://10.0.2.2:8000/api/v1/overview".toHttpUrl()))
        assertNull(AccountSession.tokenFor("http://10.0.2.2:8001/api/v1/overview".toHttpUrl()))
        AccountSession.serverChanged("http://192.168.1.20:8000/")
        assertEquals(SessionState.SignedOut(SignOutReason.SERVER_CHANGED), AccountSession.state.value)
    }

    @Test
    fun `a 401 for the session's token returns the app to sign-in`() {
        val base = server(mapOf("GET /api/v1/overview" to Reply(401, """{"detail":"x"}""")))
        AccountSession.init(InMemorySessionStore(session(base)))
        client.newCall(Request.Builder().url("${base}api/v1/overview").build()).execute().close()
        assertEquals(SessionState.SignedOut(SignOutReason.EXPIRED), AccountSession.state.value)
        assertEquals(1, signedOutCalls)
    }

    @Test
    fun `a late 401 for an old token does not end a newer session`() {
        AccountSession.init(InMemorySessionStore(session("http://server/", token = "new")))
        AccountSession.rejected("old")
        assertTrue(AccountSession.isSignedIn)
    }

    // ============================================================ sign-out

    @Test
    fun `sign-out revokes on the server and clears the device`() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val base = server(mapOf("POST /api/v1/auth/logout" to Reply(204)), seen)
        val store = InMemorySessionStore(session(base))
        AccountSession.init(store)
        AuthRepository(apiFor = ::api).signOut()

        assertEquals(listOf("POST /api/v1/auth/logout auth=Bearer t1"), seen.toList())
        assertEquals(SessionState.SignedOut(SignOutReason.USER), AccountSession.state.value)
        assertNull(store.load())
        assertNull(AccountSession.current.value)
        assertEquals(1, signedOutCalls)  // Local data cleanup ran.
        assertNull(AccountSession.tokenFor("${base}api/v1/assessments".toHttpUrl()))
    }

    @Test
    fun `sign-out still clears the device when the server is unreachable`() = runBlocking {
        AccountSession.init(InMemorySessionStore(session("http://127.0.0.1:9/")))
        AuthRepository(apiFor = ::api).signOut()
        assertFalse(AccountSession.isSignedIn)
        assertEquals(1, signedOutCalls)
    }

    // ============================================================ errors

    @Test
    fun `auth status codes map to their own error kinds`() {
        assertEquals(ErrorKind.UNAUTHORIZED, httpError(401, null).kind)
        assertEquals("Your session has expired. Sign in again.", httpError(401, null).message)
        assertEquals(ErrorKind.CONFLICT, httpError(409, """{"detail":"taken"}""").kind)
        assertEquals(ErrorKind.RATE_LIMITED, httpError(429, null).kind)
        assertEquals(
            "String should have at least 8 characters",
            httpError(422, """{"detail":[{"msg":"String should have at least 8 characters"}]}""").message
        )
    }

    @Test
    fun `public paths are recognised behind a path prefix`() {
        assertTrue(AuthInterceptor.isPublic("http://h/api/v1/auth/login".toHttpUrl()))
        assertTrue(AuthInterceptor.isPublic("http://h/vigilex/api/v1/auth/register".toHttpUrl()))
        assertFalse(AuthInterceptor.isPublic("http://h/api/v1/auth/me".toHttpUrl()))
        assertFalse(AuthInterceptor.isPublic("http://h/api/v1/assessments".toHttpUrl()))
    }
}
