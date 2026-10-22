package com.golishoda.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM shops ORDER BY isArchived, name COLLATE NOCASE") fun observeShops(): Flow<List<ShopEntity>>
    @Query("SELECT * FROM shops ORDER BY name COLLATE NOCASE") suspend fun allShops(): List<ShopEntity>
    @Query("SELECT * FROM shops WHERE id = :id") suspend fun shop(id: Long): ShopEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertShop(shop: ShopEntity): Long
    @Update suspend fun updateShop(shop: ShopEntity)
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, id DESC") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions ORDER BY transactionDate, id") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun transaction(id: Long): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE shopId = :shopId ORDER BY transactionDate DESC, id DESC") fun observeShopTransactions(shopId: Long): Flow<List<TransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransaction(transaction: TransactionEntity): Long
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM shops") suspend fun clearShops()
    @Transaction suspend fun replaceAll(shops: List<ShopEntity>, transactions: List<TransactionEntity>) {
        clearTransactions(); clearShops()
        shops.forEach { insertShop(it) }
        transactions.forEach { insertTransaction(it) }
    }
}
