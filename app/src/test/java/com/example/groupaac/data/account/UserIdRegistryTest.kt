package com.example.groupaac.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.groupaac.data.AppDatabase
import com.example.groupaac.model.HomeExperience
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserIdRegistryTest {
    private lateinit var database: AppDatabase
    private lateinit var registry: LocalUserIdRegistry

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        registry = LocalUserIdRegistry(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun createAccountNormalizesUidAndPersistsSettings() = runTest {
        val result = registry.createAccount(
            CreateAccountRequest(
                uid = "  Alice_1 ",
                displayName = " Alice ",
                homeExperience = HomeExperience.ADVANCED
            )
        )

        assertTrue(result is CreateAccountResult.Success)
        val user = database.userDao().getUser("alice_1")
        val settings = database.userDao().getSettings("alice_1")
        assertEquals("Alice", user?.displayName)
        assertEquals(HomeExperience.ADVANCED, settings?.homeExperience)
    }

    @Test
    fun invalidUidIsRejectedBeforeInsert() = runTest {
        val result = registry.createAccount(
            CreateAccountRequest(
                uid = "Bad-Uid",
                displayName = "Alice",
                homeExperience = HomeExperience.SIMPLE
            )
        )

        assertTrue(result is CreateAccountResult.Invalid)
        assertTrue(database.userDao().observeUsers().first().isEmpty())
    }
}
