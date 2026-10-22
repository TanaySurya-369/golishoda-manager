package com.golishoda.manager.backup

import android.content.ContentResolver
import android.net.Uri
import com.golishoda.manager.data.LedgerRepository
import com.golishoda.manager.data.ShopEntity
import com.golishoda.manager.data.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object BackupManager {
    private const val VERSION = 1

    suspend fun write(repository: LedgerRepository, resolver: ContentResolver, uri: Uri) {
        val root = JSONObject().put("format", "golishoda-manager-backup").put("version", VERSION)
            .put("createdAt", System.currentTimeMillis()).put("photosIncluded", false)
        val shops = JSONArray()
        repository.allShops().forEach { shop -> shops.put(JSONObject().put("id", shop.id).put("name", shop.name).put("nickname", shop.nickname).put("phone", shop.phone).put("address", shop.address).put("photoUri", shop.photoUri).put("defaultUnitPricePaise", shop.defaultUnitPricePaise).put("notes", shop.notes).put("isArchived", shop.isArchived).put("createdAt", shop.createdAt).put("updatedAt", shop.updatedAt)) }
        val transactions = JSONArray()
        repository.allTransactions().forEach { tx -> transactions.put(JSONObject().put("id", tx.id).put("shopId", tx.shopId).put("transactionDate", tx.transactionDate).put("transactionType", tx.transactionType).put("deliveredQuantity", tx.deliveredQuantity).put("returnedQuantity", tx.returnedQuantity).put("unitPricePaise", tx.unitPricePaise).put("paymentAmountPaise", tx.paymentAmountPaise).put("note", tx.note).put("correctionOfId", tx.correctionOfId).put("createdAt", tx.createdAt).put("updatedAt", tx.updatedAt)) }
        root.put("shops", shops).put("transactions", transactions)
        requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(root.toString(2)) }
    }

    suspend fun restore(repository: LedgerRepository, input: InputStream) {
        val root = input.bufferedReader().use { JSONObject(it.readText()) }
        require(root.getString("format") == "golishoda-manager-backup") { "This is not a Golishoda Manager backup." }
        require(root.getInt("version") == VERSION) { "This backup version is not supported." }
        val shopsJson = root.getJSONArray("shops")
        val transactionsJson = root.getJSONArray("transactions")
        require(shopsJson.length() <= 100_000 && transactionsJson.length() <= 1_000_000) { "Backup is too large." }
        val shops = (0 until shopsJson.length()).map { index ->
            val s = shopsJson.getJSONObject(index)
            ShopEntity(id = s.getLong("id"), name = s.getString("name"), nickname = s.optString("nickname"), phone = s.optString("phone"), address = s.optString("address"), photoUri = s.optString("photoUri").takeIf { it.isNotBlank() }, defaultUnitPricePaise = s.getLong("defaultUnitPricePaise"), notes = s.optString("notes"), isArchived = s.optBoolean("isArchived"), createdAt = s.optLong("createdAt"), updatedAt = s.optLong("updatedAt"))
        }
        val transactions = (0 until transactionsJson.length()).map { index ->
            val t = transactionsJson.getJSONObject(index)
            TransactionEntity(id = t.getLong("id"), shopId = t.getLong("shopId"), transactionDate = t.getString("transactionDate"), transactionType = t.getString("transactionType"), deliveredQuantity = t.optInt("deliveredQuantity"), returnedQuantity = t.optInt("returnedQuantity"), unitPricePaise = t.getLong("unitPricePaise"), paymentAmountPaise = t.getLong("paymentAmountPaise"), note = t.optString("note"), correctionOfId = if (t.isNull("correctionOfId")) null else t.getLong("correctionOfId"), createdAt = t.optLong("createdAt"), updatedAt = t.optLong("updatedAt"))
        }
        require(transactions.map { it.id }.toSet().size == transactions.size) { "Backup contains duplicate transaction IDs." }
        require(root.optBoolean("photosIncluded", false).not() || shops.all { it.photoUri == null }) { "Backups with embedded photos are not supported." }
        repository.restore(shops, transactions)
    }
}
