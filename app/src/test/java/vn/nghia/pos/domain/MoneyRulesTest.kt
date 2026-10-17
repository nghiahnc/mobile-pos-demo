package vn.nghia.pos.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyRulesTest {
    private val coffee = Product("coffee", "Cà phê đen", "Cà phê", 25000L, "")
    private fun cash(amount: Long?) = PaymentRequest("attempt-1", PaymentMethod.CASH, amount)

    @Test fun totalUsesIntegerVndAndQuantity() {
        assertEquals(79000L, MoneyRules.total(listOf(CartLine(coffee, 2), CartLine(coffee.copy(price = 29000), 1))))
    }
    @Test fun emptyTotalIsZero() { assertEquals(0L, MoneyRules.total(emptyList())) }
    @Test fun emptyCartCannotBePaid() {
        assertThrows(IllegalArgumentException::class.java) { MoneyRules.validate(emptyList(), cash(100000)) }
    }
    @Test fun exactCashIsAccepted() { assertEquals(50000L, MoneyRules.validate(listOf(CartLine(coffee, 2)), cash(50000))) }
    @Test fun insufficientCashIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { MoneyRules.validate(listOf(CartLine(coffee, 2)), cash(49999)) }
    }
    @Test fun missingAndNegativeCashAreRejected() {
        listOf(null, -1L).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { MoneyRules.validate(listOf(CartLine(coffee, 1)), cash(amount)) }
        }
    }
    @Test fun invalidQuantitiesAreRejected() {
        listOf(-1, 0, 100).forEach { quantity ->
            assertThrows(IllegalArgumentException::class.java) { MoneyRules.total(listOf(CartLine(coffee, quantity))) }
        }
    }
    @Test fun invalidPriceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { MoneyRules.total(listOf(CartLine(coffee.copy(price = 0), 1))) }
    }
    @Test fun overflowDoesNotWrapIntoNegativeMoney() {
        assertThrows(ArithmeticException::class.java) { MoneyRules.total(listOf(CartLine(coffee.copy(price = Long.MAX_VALUE), 2))) }
    }
    @Test fun cardDoesNotNeedTenderedCash() {
        assertEquals(25000L, MoneyRules.validate(listOf(CartLine(coffee, 1)), PaymentRequest("card-1", PaymentMethod.CARD, null)))
    }
    @Test fun fakeTerminalHasThreeExplicitCardOutcomes() {
        val terminal = FakePaymentTerminal()
        DemoScenario.entries.zip(PaymentStatus.entries).forEach { (scenario, status) ->
            assertEquals(status, terminal.authorize(PaymentRequest("card", PaymentMethod.CARD, null, scenario)))
        }
    }
    @Test fun cashIgnoresCardScenario() {
        assertEquals(PaymentStatus.SUCCESS, FakePaymentTerminal().authorize(cash(50000).copy(scenario = DemoScenario.DECLINED)))
    }
}
