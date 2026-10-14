package com.quantumff.segue.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.quantumff.segue.core.common.ApplicationScope
import com.quantumff.segue.core.common.Dispatcher
import com.quantumff.segue.core.common.SegueDispatchers
import com.quantumff.segue.core.datastore.StoredAccounts
import com.quantumff.segue.core.datastore.StoredAccountsSerializer
import com.quantumff.segue.core.datastore.crypto.AesGcmPasswordCipher
import com.quantumff.segue.core.datastore.crypto.AndroidKeystoreKeyProvider
import com.quantumff.segue.core.datastore.crypto.PasswordCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** The accounts file name, for backup exclusion rules: `datastore/accounts.json`. */
    const val ACCOUNTS_FILE = "accounts.json"

    @Provides
    @Singleton
    fun preferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(SegueDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile("user_preferences") },
    )

    @Provides
    @Singleton
    fun accountsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(SegueDispatchers.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<StoredAccounts> = DataStoreFactory.create(
        serializer = StoredAccountsSerializer,
        scope = scope + ioDispatcher,
        produceFile = { context.dataStoreFile(ACCOUNTS_FILE) },
    )

    @Provides
    @Singleton
    fun passwordCipher(): PasswordCipher =
        AesGcmPasswordCipher(AndroidKeystoreKeyProvider(alias = "segue_account_passwords"))
}
