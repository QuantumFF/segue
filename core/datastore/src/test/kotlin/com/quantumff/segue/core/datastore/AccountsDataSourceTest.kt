package com.quantumff.segue.core.datastore

import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.datastore.crypto.AesGcmPasswordCipher
import com.quantumff.segue.core.datastore.crypto.PasswordCipher
import com.quantumff.segue.core.model.Server
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountsDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val home = Server(id = "home", name = "Home", baseUrl = "https://music.example", username = "me")
    private val office = Server(id = "office", name = "Office", baseUrl = "https://office.example", username = "work")

    private fun TestScope.source(): Pair<AccountsDataSource, File> {
        val file = File(tmp.root, "accounts.json")
        val dataStore = DataStoreFactory.create(serializer = StoredAccountsSerializer, scope = backgroundScope) { file }
        return AccountsDataSource(dataStore, AesGcmPasswordCipher { key }) to file
    }

    @Test
    fun `no accounts and no active server by default`() = runTest {
        val (source, _) = source()

        assertThat(source.accounts.first()).isEmpty()
        assertThat(source.activeServerId.first()).isNull()
    }

    @Test
    fun `passwords are stored encrypted and read back decrypted`() = runTest {
        val (source, file) = source()

        source.upsert(home, password = "hunter2-very-secret")

        assertThat(source.accounts.first()).containsExactly(home)
        assertThat(source.password("home")).isEqualTo("hunter2-very-secret")
        assertThat(file.readText()).doesNotContain("hunter2-very-secret")
    }

    @Test
    fun `upsert replaces an existing account and its password`() = runTest {
        val (source, _) = source()
        source.upsert(home, password = "old")

        source.upsert(home.copy(name = "House"), password = "new")

        assertThat(source.accounts.first().single().name).isEqualTo("House")
        assertThat(source.password("home")).isEqualTo("new")
    }

    @Test
    fun `the active server can be switched`() = runTest {
        val (source, _) = source()
        source.upsert(home, "a")
        source.upsert(office, "b")

        source.setActive("office")

        assertThat(source.activeServerId.first()).isEqualTo("office")
        assertThat(source.activeServer.first()).isEqualTo(office)
    }

    @Test
    fun `the active server must be a stored account`() = runTest {
        val (source, _) = source()

        val error = runCatching { source.setActive("missing") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `removing the active account activates another one`() = runTest {
        val (source, _) = source()
        source.upsert(home, "a")
        source.upsert(office, "b")
        source.setActive("home")

        source.remove("home")

        assertThat(source.accounts.first()).containsExactly(office)
        assertThat(source.activeServerId.first()).isEqualTo("office")
        assertThat(source.password("home")).isNull()
    }

    @Test
    fun `a stored password is decrypted once, and again only after it changes`() = runTest {
        val file = File(tmp.root, "counted.json")
        val dataStore = DataStoreFactory.create(serializer = StoredAccountsSerializer, scope = backgroundScope) { file }
        val cipher = CountingCipher(AesGcmPasswordCipher { key })
        val source = AccountsDataSource(dataStore, cipher)
        source.upsert(home, "first")

        repeat(3) { assertThat(source.password("home")).isEqualTo("first") }
        source.upsert(home, "second")
        repeat(3) { assertThat(source.password("home")).isEqualTo("second") }

        assertThat(cipher.decryptions).isEqualTo(2)
    }

    private class CountingCipher(private val delegate: PasswordCipher) : PasswordCipher by delegate {
        var decryptions = 0

        override fun decrypt(encoded: String): String? {
            decryptions++
            return delegate.decrypt(encoded)
        }
    }

    @Test
    fun `a password that no longer decrypts reads as null`() = runTest {
        val file = File(tmp.root, "accounts.json")
        val writer = DataStoreFactory.create(serializer = StoredAccountsSerializer, scope = backgroundScope) { file }
        AccountsDataSource(writer, AesGcmPasswordCipher { key }).upsert(home, "secret")

        // For example after a restore to a new device, where the Keystore key is gone.
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val reader = AccountsDataSource(writer, AesGcmPasswordCipher { otherKey })

        assertThat(reader.password("home")).isNull()
    }
}
