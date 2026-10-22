package com.golishoda.manager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "shops", indices = [Index("name"), Index("isArchived")])
data class ShopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val phone: String = "",
    val address: String = "",
    val photoUri: String? = null,
    val defaultUnitPricePaise: Long = 0,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = ShopEntity::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shopId"), Index("transactionDate"), Index("transactionType"), Index(value = ["shopId", "transactionDate"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val transactionDate: String,
    val transactionType: String,
    val deliveredQuantity: Int = 0,
    val returnedQuantity: Int = 0,
    val unitPricePaise: Long = 0,
    val paymentAmountPaise: Long = 0,
    val note: String = "",
    val correctionOfId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
