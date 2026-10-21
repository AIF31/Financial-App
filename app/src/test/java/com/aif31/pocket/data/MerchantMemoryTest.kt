package com.aif31.pocket.data

import com.aif31.pocket.domain.SupportedCurrency
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MerchantMemoryTest {
    private val date = LocalDate.of(2026, 9, 5)
    private val period = Period("period", date.minusDays(10), date.plusDays(20), 100_000, 25, accountingCurrency = SupportedCurrency.MXN)
    private val market = Pocket("market", "Mercado", PocketIconKey.SUPERMARKET, 0, false, false)
    private val food = Pocket("food", "Comida", PocketIconKey.RESTAURANT, 1, false, false)
    private fun expense(id: String, merchant: String?, time: Long, pocket: Pocket = market) = Movement(
        id, pocket.id, pocket.name, period.id, MovementType.EXPENSE, 8_123, time, date, "Asia/Riyadh",
        merchant, "Nota", "card", "Tarjeta", 432, "USD", ConversionStatus.ESTIMATED,
        "18.80324", date.minusDays(1), "Banxico",
    )
    private fun summary(pocket: Pocket, retired: Boolean = false) = PocketPeriodSummary(
        pocket, 0, 0, retiredThisPeriod = retired, netSpendMinor = 0, availabilityMinor = 0,
        consumedPercent = 0, atRisk = false, exhausted = false,
    )
    private fun state(vararg movements: Movement) = LedgerState(
        periods = listOf(period), pocketCatalog = listOf(market, food), movements = movements.toList(),
        pocketSummariesByPeriod = mapOf(period.id to listOf(summary(market), summary(food))),
    )

    @Test fun entries_group_normalized_names_and_use_the_latest_expense_across_periods_ignoring_refunds() {
        val oldPeriod = period.copy(id = "old", accountingCurrency = SupportedCurrency.SAR)
        val latest = expense("latest", "TAMIMI-Market", 30, food)
        val ledger = state(
            latest,
            expense("old", "Tamimi Market", 10).copy(periodId = oldPeriod.id),
            expense("refund", "tamimi.market", 50).copy(type = MovementType.REFUND),
            expense("other", "K.F.C", 20),
            expense("blank", "...", 60),
            expense("missing", null, 70),
            expense("only-refund", "Refund shop", 80).copy(type = MovementType.REFUND),
        ).copy(periods = listOf(oldPeriod, period))

        val entries = MerchantMemory.entries(ledger)

        assertEquals(listOf("tamimimarket", "kfc"), entries.map { it.merchantKey })
        val entry = entries.first()
        assertEquals("TAMIMI-Market", entry.displayName)
        assertEquals(food, entry.pocket)
        assertEquals(latest, entry.latestExpense)
        assertEquals(SupportedCurrency.MXN, entry.accountingCurrency)
        assertEquals(2, entry.expenseCount)
        assertFalse(entry.pocketArchived)
    }

    @Test fun latest_expense_marks_an_archived_pocket_without_falling_back_to_an_older_one() {
        val archived = food.copy(archived = true)
        val ledger = state(expense("older", "KFC", 10), expense("latest", "K.F.C", 20, food))
            .copy(pocketCatalog = listOf(market, archived))

        val entry = MerchantMemory.entries(ledger).single()

        assertEquals(archived, entry.pocket)
        assertTrue(entry.pocketArchived)
    }

    @Test fun reassign_resaves_the_latest_expense_preserving_every_command_field_except_pocket() {
        val latest = expense("latest", "TAMIMI", 123456789)
        val command = MerchantMemory.reassign(state(expense("old", "Tamimi", 1), latest), "tamimi", food.id)

        assertEquals(LedgerCommand.AddMovement(
            id = "latest", pocketId = food.id, type = MovementType.EXPENSE, expectedMovement = latest,
            accountingAmountMinor = 8_123, occurredAtUtcMillis = 123456789, localDate = date,
            merchant = "TAMIMI", note = "Nota", paymentMethodId = "card", originalAmountMinor = 432,
            originalCurrencyCode = "USD", conversionStatus = ConversionStatus.ESTIMATED, rate = "18.80324",
            conversionEffectiveDate = date.minusDays(1), conversionSource = "Banxico", accountingCurrency = SupportedCurrency.MXN,
        ), command)
    }

    @Test fun reassign_rejects_the_same_archived_missing_retired_or_absent_period_pocket_and_unknown_merchant() {
        val ledger = state(expense("latest", "Tamimi", 10))
        fun rejected(state: LedgerState, key: String, pocket: String, message: String) {
            val error = assertThrows(IllegalArgumentException::class.java) { MerchantMemory.reassign(state, key, pocket) }
            assertEquals(message, error.message)
        }
        rejected(ledger, "tamimi", market.id, "El comercio ya usa este Pocket")
        rejected(ledger.copy(pocketCatalog = listOf(market, food.copy(archived = true))), "tamimi", food.id, "El Pocket está archivado")
        rejected(ledger, "tamimi", "missing", "Pocket inexistente")
        rejected(ledger.copy(pocketSummariesByPeriod = mapOf(period.id to listOf(summary(food, true)))), "tamimi", food.id, "El Pocket no está activo en este periodo")
        rejected(ledger.copy(pocketSummariesByPeriod = emptyMap()), "tamimi", food.id, "El Pocket no está activo en este periodo")
        rejected(ledger, "unknown", food.id, "El comercio ya no tiene gastos")
    }
}
