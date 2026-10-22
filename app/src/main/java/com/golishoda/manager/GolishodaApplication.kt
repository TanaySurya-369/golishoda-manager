package com.golishoda.manager

import android.app.Application
import androidx.room.Room
import com.golishoda.manager.data.LedgerDatabase
import com.golishoda.manager.data.LedgerRepository

class GolishodaApplication : Application() {
    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, LedgerDatabase::class.java, "golishoda-ledger.db").build()
        repository = LedgerRepository(database)
    }
}
