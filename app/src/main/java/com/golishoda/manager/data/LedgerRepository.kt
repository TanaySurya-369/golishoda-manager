package com.golishoda.manager.data

import androidx.room.withTransaction
import com.golishoda.manager.domain.LedgerRules
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class LedgerRepository(private val database: LedgerDatabase) {
    private val dao = database.ledgerDao()
    val shops: Flow<List<ShopEntity>> = dao.observeShops()
    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    fun shopTransactions(shopId: Long) = dao.observeShopTransactions(shopId)

    suspend fun saveShop(shop: ShopEntity) {
        require(shop.name.isNotBlank()) { "Shop name is required." }
        require(shop.defaultUnitPricePaise >= 0) { "Default price cannot be negative." }
        if (shop.id == 0L) dao.insertShop(shop) else dao.updateShop(shop.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun archiveShop(shop: ShopEntity) = dao.updateShop(shop.copy(isArchived = true, updatedAt = System.currentTimeMillis()))

    suspend fun addEntry(shopId: Long, date: LocalDate, type: String, delivered: Int, returned: Int, pricePaise: Long, paymentPaise: Long, note: String) = database.withTransaction {
        val shop = requireNotNull(dao.shop(shopId)) { "Select a valid shop." }
        require(!shop.isArchived) { "Archived shops cannot receive new entries." }
        LedgerRules.validate(delivered, returned, pricePaise, paymentPaise, type)
        val history = dao.allTransactions().filter { it.shopId == shopId }.map {
            LedgerRules.Movement(LocalDate.parse(it.transactionDate), it.deliveredQuantity, it.returnedQuantity, it.unitPricePaise, it.paymentAmountPaise)
        }.toMutableList()
        history += LedgerRules.Movement(date, delivered, returned, pricePaise, paymentPaise)
        var runningStock = 0L
        history.sortedBy { it.date }.forEach { movement ->
            runningStock = Math.addExact(runningStock, movement.delivered.toLong() - movement.returned.toLong())
            require(runningStock >= 0) { "This return exceeds the shop's recorded bottle stock on ${movement.date}. Correct the quantity or stock history first." }
        }
        dao.insertTransaction(TransactionEntity(shopId = shopId, transactionDate = date.toString(), transactionType = type, deliveredQuantity = delivered, returnedQuantity = returned, unitPricePaise = pricePaise, paymentAmountPaise = paymentPaise, note = note.trim()))
    }

    suspend fun restore(shops: List<ShopEntity>, transactions: List<TransactionEntity>) = database.withTransaction {
        require(shops.map { it.id }.toSet().size == shops.size) { "Backup contains duplicate shop IDs." }
        require(transactions.all { transaction -> shops.any { it.id == transaction.shopId } }) { "Backup has transactions for a missing shop." }
        transactions.forEach { tx -> LedgerRules.validate(tx.deliveredQuantity, tx.returnedQuantity, tx.unitPricePaise, tx.paymentAmountPaise, tx.transactionType) }
        transactions.forEach { tx -> LocalDate.parse(tx.transactionDate) }
        shops.forEach { require(it.name.isNotBlank() && it.defaultUnitPricePaise >= 0) { "Backup contains invalid shop data." } }
        shops.indices.forEach { index ->
            val shopId = shops[index].id
            var stock = 0L
            transactions.filter { it.shopId == shopId }.sortedBy { it.transactionDate }.forEach { tx ->
                stock = Math.addExact(stock, tx.deliveredQuantity.toLong() - tx.returnedQuantity.toLong())
                require(stock >= 0) { "Backup contains a return before available stock." }
            }
        }
        dao.replaceAll(shops, transactions)
    }

    suspend fun allShops() = dao.allShops()
    suspend fun allTransactions() = dao.allTransactions()
}
