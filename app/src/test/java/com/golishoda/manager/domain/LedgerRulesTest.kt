package com.golishoda.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerRulesTest {
    private val day = LocalDate.parse("2026-01-01")

    @Test fun openingStockDeliveryAndReturn() { assertEquals(13L, LedgerRules.stock(listOf(move(12, 0), move(10, 9)))) }
    @Test fun paymentDoesNotChangeStock() { assertEquals(0L, LedgerRules.stock(listOf(move(0, 0, payment = 1500)))) }
    @Test fun multipleMovementsReconcile() { assertEquals(8L, LedgerRules.stock(listOf(move(10, 0), move(0, 4), move(5, 3)))) }
    @Test fun historicalStockUsesSelectedDate() { assertEquals(6L, LedgerRules.stock(listOf(move(6, 0), move(2, 0, day.plusDays(1))), day)) }
    @Test fun returnExceedingStockIsDetectable() { assertTrue(LedgerRules.stock(listOf(move(4, 5))) < 0) }
    @Test fun reversingMovementRestoresStock() { assertEquals(10L, LedgerRules.stock(listOf(move(10, 0)))) }
    @Test fun deliveryValueIsExact() { assertEquals(24_000L, 24L * 1_000L) }
    @Test fun outstandingCarriesForward() { assertEquals(48_000L, LedgerRules.duePaise(24_000, move(24, 0))) }
    @Test fun paymentSettlesBalance() { assertEquals(0L, LedgerRules.duePaise(0, move(24, 0, payment = 24_000))) }
    @Test fun overpaymentCreatesAdvanceCredit() { assertEquals(-6_000L, LedgerRules.duePaise(24_000, move(10, 0, payment = 40_000))) }
    @Test fun returnUsesRecordedPrice() { assertEquals(23_000L, LedgerRules.duePaise(24_000, move(0, 1, price = 1_000))) }
    @Test fun rupeePaiseConversionIsExact() { assertEquals(12_345L, LedgerRules.rupeesToPaise("123.45")); assertEquals("₹123.45", LedgerRules.formatMoney(12_345)) }
    @Test fun paymentOnlyDoesNotCountSuppliedShop() {
        val payment = move(0, 0, payment = 100)
        val delivery = move(3, 0)
        assertEquals(1, LedgerRules.suppliedShopCount(listOf(7L to delivery, 7L to payment)))
        assertEquals(0, LedgerRules.suppliedShopCount(listOf(8L to payment)))
    }
    @Test fun dateRangeIncludesOnlyInRangeEntries() {
        val rows = listOf(move(1, 0, day.minusDays(1)), move(2, 0), move(3, 0, day.plusDays(1)))
        assertEquals(listOf(2), LedgerRules.movementsInRange(rows, day, day).map { it.delivered })
    }
    @Test fun emptyTotalsAreZero() { assertEquals(0L, LedgerRules.totals(emptyList(), emptyList()).paymentsPaise) }

    private fun move(delivered: Int, returned: Int, date: LocalDate = day, price: Long = 1_000, payment: Long = 0) =
        LedgerRules.Movement(date, delivered, returned, price, payment)
}
