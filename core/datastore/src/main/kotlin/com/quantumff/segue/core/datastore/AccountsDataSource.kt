package com.quantumff.segue.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.quantumff.segue.core.datastore.crypto.PasswordCipher
import com.quantumff.segue.core.model.Server
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Server accounts and which one is active. Passwords are encrypted with [PasswordCipher]. */
class AccountsDataSource @Inject constructor(
    private val dataStore: DataStore<StoredAccounts>,
    private val cipher: PasswordCipher,
) {
    val accounts: Flow<List<Server>> = dataStore.data.map { stored ->
        stored.accounts.map { it.asServer() }
    }.distinctUntilChanged()

    val activeServerId: Flow<String?> = dataStore.data.map { it.activeServerId }.distinctUntilChanged()

    val activeServer: Flow<Server?> = dataStore.data
        .map { stored -> stored.accounts.firstOrNull { it.id == stored.activeServerId }?.asServer() }
        .distinctUntilChanged()

    /** Adds or replaces the account with [Server.id]. */
    suspend fun upsert(server: Server, password: String) {
        val account = StoredAccount(
            id = server.id,
            name = server.name,
            baseUrl = server.baseUrl,
            username = server.username,
            encryptedPassword = cipher.encrypt(password),
        )
        dataStore.updateData { stored ->
            val exists = stored.accounts.any { it.id == server.id }
            val accounts = when {
                exists -> stored.accounts.map { if (it.id == server.id) account else it }
                else -> stored.accounts + account
            }
            stored.copy(accounts = accounts)
        }
    }

    /**
     * Null when the account is unknown or its password can no longer be decrypted. Decrypted
     * passwords are kept in memory by ciphertext, so the Keystore runs once per stored password
     * rather than on every server call.
     */
    suspend fun password(serverId: String): String? {
        val encrypted =
            dataStore.data.first().accounts.firstOrNull { it.id == serverId }?.encryptedPassword ?: return null
        synchronized(decrypted) { decrypted[serverId] }?.takeIf { it.first == encrypted }?.let { return it.second }
        val password = cipher.decrypt(encrypted) ?: return null
        synchronized(decrypted) { decrypted[serverId] = encrypted to password }
        return password
    }

    /** Server ID to (ciphertext, plaintext). A re-stored password has a new ciphertext. */
    private val decrypted = mutableMapOf<String, Pair<String, String>>()

    suspend fun setActive(serverId: String) {
        dataStore.updateData { stored ->
            require(stored.accounts.any { it.id == serverId }) { "No account $serverId" }
            stored.copy(activeServerId = serverId)
        }
    }

    /** Removes the account. If it was active, the first remaining account becomes active. */
    suspend fun remove(serverId: String) {
        dataStore.updateData { stored ->
            val accounts = stored.accounts.filterNot { it.id == serverId }
            val active = stored.activeServerId.takeIf { it != serverId } ?: accounts.firstOrNull()?.id
            StoredAccounts(accounts = accounts, activeServerId = active)
        }
    }
}

@Serializable
data class StoredAccounts(val accounts: List<StoredAccount> = emptyList(), val activeServerId: String? = null)

@Serializable
data class StoredAccount(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val encryptedPassword: String,
)

private fun StoredAccount.asServer() = Server(id = id, name = name, baseUrl = baseUrl, username = username)

/** JSON on disk. Only ciphertext is stored for passwords. */
object StoredAccountsSerializer : Serializer<StoredAccounts> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = StoredAccounts()

    override suspend fun readFrom(input: InputStream): StoredAccounts = try {
        json.decodeFromString(StoredAccounts.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read accounts", e)
    }

    override suspend fun writeTo(t: StoredAccounts, output: OutputStream) {
        output.write(json.encodeToString(StoredAccounts.serializer(), t).encodeToByteArray())
    }
}
