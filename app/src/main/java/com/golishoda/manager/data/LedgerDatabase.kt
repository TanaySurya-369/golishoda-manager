package com.golishoda.manager.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ShopEntity::class, TransactionEntity::class], version = 1, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
