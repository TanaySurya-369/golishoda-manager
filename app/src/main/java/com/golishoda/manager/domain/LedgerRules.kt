package com.golishoda.manager.domain

import java.time.LocalDate

/** Exact integer-paise and stock calculations shared by every screen and report. */
object LedgerRules {
    data class Movement(val date: LocalDate, val delivered: Int, val returned: Int, val unitPricePaise: Long, val paymentPaise: Long)
    data class Totals(val delivered: Long, val returned: Long, val deliveryValuePaise: Long, val returnCreditPaise: Long, val paymentsPaise: Long, val suppliedShops: Int)

    fun validate(delivered: Int, returned: Int, pricePaise: Long, paymentPaise: Long, type: String) {
        require(delivered >= 0) { "Delivered bottles cannot be negative." }
        require(returned >= 0) { "Returned bottles cannot be negative." }
        require(pricePaise >= 0) { "Price cannot be negative." }
        require(paymentPaise >= 0) { "Payment cannot be negative." }
        require(when (type) {
            "DELIVERY" -> delivered > 0
            "RETURN" -> returned > 0
            "PAYMENT" -> paymentPaise > 0 && delivered == 0 && returned == 0
            "ADJUSTMENT" -> delivered > 0 || returned > 0 || paymentPaise > 0
            else -> delivered > 0 || returned > 0 || paymentPaise > 0
        }) { "Enter a delivery, return, or payment amount." }
    }

    fun stock(movements: Iterable<Movement>, through: LocalDate? = null): Long = movements
        .filter { through == null || !it.date.isAfter(through) }
        .sumOf { it.delivered.toLong() - it.returned.toLong() }

    fun duePaise(openingDuePaise: Long, movement: Movement): Long = Math.subtractExact(
        Math.addExact(openingDuePaise, Math.multiplyExact(movement.delivered.toLong(), movement.unitPricePaise)),
        Math.addExact(Math.multiplyExact(movement.returned.toLong(), movement.unitPricePaise), movement.paymentPaise)
    )

    fun totals(movements: Iterable<Movement>, shopIdsByMovement: Iterable<Pair<Long, Movement>>): Totals {
        val selected = movements.toList()
        val pairs = shopIdsByMovement.toList()
        return Totals(
            delivered = selected.sumOf { it.delivered.toLong() },
            returned = selected.sumOf { it.returned.toLong() },
            deliveryValuePaise = selected.sumOf { Math.multiplyExact(it.delivered.toLong(), it.unitPricePaise) },
            returnCreditPaise = selected.sumOf { Math.multiplyExact(it.returned.toLong(), it.unitPricePaise) },
            paymentsPaise = selected.sumOf { it.paymentPaise },
            suppliedShops = pairs.filter { it.second.delivered > 0 }.map { it.first }.toSet().size
        )
    }

    fun rupeesToPaise(input: String): Long {
        val normalized = input.trim().replace(",", "")
        require(normalized.matches(Regex("\\d{1,12}(\\.\\d{0,2})?"))) { "Enter a valid amount (up to two decimal places)." }
        val parts = normalized.split('.')
        val whole = parts[0].toLong()
        val fraction = parts.getOrElse(1) { "" }.padEnd(2, '0').ifEmpty { "00" }.toLong()
        return Math.addExact(Math.multiplyExact(whole, 100L), fraction)
    }

    fun formatMoney(paise: Long): String {
        val abs = kotlin.math.abs(paise)
        val number = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
        return (if (paise < 0) "−" else "") + "₹" + number.format(abs / 100.0)
    }

    fun suppliedShopCount(shopIdsByMovement: Iterable<Pair<Long, Movement>>): Int = shopIdsByMovement
        .filter { it.second.delivered > 0 }
        .map { it.first }
        .toSet()
        .size

    fun movementsInRange(movements: Iterable<Movement>, start: LocalDate, endInclusive: LocalDate): List<Movement> {
        require(!endInclusive.isBefore(start)) { "The end date must not be before the start date." }
        return movements.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }
    }
}
