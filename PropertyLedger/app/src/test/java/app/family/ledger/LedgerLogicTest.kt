package app.family.ledger

import app.family.ledger.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerLogicTest {
    private fun snap(
        obligations: List<RentObligation> = emptyList(), payments: List<Payment> = emptyList(),
        allocations: List<PaymentAllocation> = emptyList(), debts: List<Debt> = emptyList(),
        handovers: List<Handover> = emptyList(), expenses: List<Expense> = emptyList()
    ) = Snapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        obligations, payments, allocations, debts, handovers, expenses, emptyList(), emptyList(), emptyList(), emptyMap()
    )

    private fun ob(id: String, y: Int, m: Int, amt: Double = 500.0, forId: String? = null) =
        RentObligation(id = id, tenantId = "t", unitId = "u", year = y, month = m, expected = amt, currency = "USD", dueDate = Dates.due(y, m, 1), collectedForId = forId)

    @Test fun rentPartialThenPaid() {
        val o = ob("o1", 2020, 10)
        val pays = listOf(Payment("p1", "t", "u", 200.0, "USD", "2020-10-02"), Payment("p2", "t", "u", 150.0, "USD", "2020-10-09"))
        var s = snap(listOf(o), pays, listOf(PaymentAllocation("a1", "p1", "o1", null, 200.0)))
        assertEquals("Partially paid", s.status(o)); assertEquals(300.0, s.remaining(o), 0.001)
        s = snap(listOf(o), pays, listOf(PaymentAllocation("a1", "p1", "o1", null, 200.0), PaymentAllocation("a2", "p2", "o1", null, 150.0)))
        assertEquals(150.0, s.remaining(o), 0.001)
        s = snap(listOf(o), pays + Payment("p3", "t", "u", 150.0, "USD", "2020-10-20"),
            listOf(PaymentAllocation("a1", "p1", "o1", null, 200.0), PaymentAllocation("a2", "p2", "o1", null, 150.0), PaymentAllocation("a3", "p3", "o1", null, 150.0)))
        assertEquals("Paid", s.status(o))
    }

    @Test fun oldDebtPartialPayment() {
        val d = Debt("d1", "t", "u", "Old Rent Debt", 3000.0, "USD", "2021-03-01")
        val s = snap(debts = listOf(d), payments = listOf(Payment("p1", "t", "u", 1000.0, "USD", "2026-01-01")),
            allocations = listOf(PaymentAllocation("a1", "p1", null, "d1", 1000.0)))
        assertEquals(2000.0, s.remaining(d), 0.001)
        assertEquals("Partially paid", s.debtStatus(d))
        assertEquals(2000.0, s.oldDebtsRemaining["USD"]!!, 0.001)
    }

    @Test fun tenantBalanceExample() {
        // 500 + 500 + 1200 old + 150 repair = 2350; pay 1000 oldest first -> 1350 left
        val obs = listOf(ob("sep", 2026, 9), ob("oct", 2026, 10))
        val debts = listOf(Debt("old", "t", "u", "Old Rent Debt", 1200.0, "USD", "2025-01-01"), Debt("rep", "t", "u", "Repair Charge", 150.0, "USD", "2026-10-05"))
        val plan = planAllocation(
            listOf(OpenItem(true, "sep", obs[0].dueDate, 500.0), OpenItem(true, "oct", obs[1].dueDate, 500.0),
                OpenItem(false, "old", "2025-01-01", 1200.0), OpenItem(false, "rep", "2026-10-05", 150.0)), 1000.0, null)
        // oldest balance (the 2025 debt of 1200) absorbs the whole 1000 payment
        assertEquals(listOf("old"), plan.map { it.id })
        assertEquals(1000.0, plan.sumOf { it.amount }, 0.001)
        val allocs = plan.mapIndexed { i, a -> PaymentAllocation("x$i", "p1", if (a.isObligation) a.id else null, if (!a.isObligation) a.id else null, a.amount) }
        val s = snap(obs, listOf(Payment("p1", "t", "u", 1000.0, "USD", "2026-10-06")), allocs, debts)
        assertEquals(1350.0, s.balance("t")["USD"]!!, 0.001)
    }

    @Test fun chosenItemIsPaidFirst() {
        val plan = planAllocation(listOf(OpenItem(true, "old", "2020-01-01", 500.0), OpenItem(true, "new", "2026-01-01", 500.0)), 300.0, "new")
        assertEquals(1, plan.size); assertEquals("new", plan[0].id); assertEquals(300.0, plan[0].amount, 0.001)
    }

    @Test fun overpaymentStaysAsCredit() {
        val plan = planAllocation(listOf(OpenItem(true, "a", "2026-01-01", 100.0)), 250.0, null)
        assertEquals(100.0, plan.sumOf { it.amount }, 0.001)
    }

    @Test fun collectedForHistoryAndHandover() {
        val pays = listOf(
            Payment("p1", "t", "u", 500.0, "USD", "2026-01-05", collectedForId = "muhammad"),
            Payment("p2", "t", "u", 500.0, "USD", "2026-02-05", collectedForId = "omar"),
            Payment("p3", "t", "u", 500.0, "USD", "2026-03-05", collectedForId = null),
            Payment("p4", "t", "u", 500.0, "USD", "2026-04-05", collectedForId = "muhammad")
        )
        val s = snap(payments = pays, handovers = listOf(Handover("h1", "muhammad", 700.0, "USD", "2026-04-10")))
        // history is stored on each payment and never rewritten
        assertEquals(listOf("muhammad", "omar", null, "muhammad"), s.payments.sortedBy { it.date }.map { it.collectedForId })
        assertEquals(1000.0, s.collectedFor("muhammad")["USD"]!!, 0.001)
        assertEquals(300.0, s.toHandOver("muhammad")["USD"]!!, 0.001)
        assertEquals(500.0, s.toHandOver("omar")["USD"]!!, 0.001)
    }

    @Test fun othersMoneyIsNotFamilyIncome() {
        val pays = listOf(
            Payment("p1", "t", "u", 500.0, "USD", "2026-10-05", collectedForId = "muhammad"),
            Payment("p2", "t", "u", 300.0, "USD", "2026-10-06")
        )
        val s = snap(payments = pays, expenses = listOf(Expense("e1", amount = 100.0, currency = "USD", date = "2026-10-07")))
        val r = s.Report(Period("2026-10-01", "2026-10-31"))
        assertEquals(300.0, r.collectedOwn["USD"]!!, 0.001)
        assertEquals(500.0, r.collectedForOthers["USD"]!!, 0.001)
        assertEquals(200.0, r.net["USD"]!!, 0.001) // 300 own income - 100 expenses
    }

    @Test fun voidedPaymentsAreIgnored() {
        val pays = listOf(Payment("p1", "t", "u", 500.0, "USD", "2026-10-05", voided = true), Payment("p2", "t", "u", 100.0, "USD", "2026-10-06"))
        val s = snap(payments = pays)
        assertEquals(100.0, s.Report(Period(null, null)).collectedOwn["USD"]!!, 0.001)
    }

    @Test fun currenciesAreNeverConverted() {
        val s = snap(payments = listOf(Payment("p1", "t", "u", 100.0, "USD", "2026-10-05"), Payment("p2", "t", "u", 1500000.0, "LBP", "2026-10-05")))
        val c = s.Report(Period(null, null)).collectedOwn
        assertEquals(100.0, c["USD"]!!, 0.001); assertEquals(1500000.0, c["LBP"]!!, 0.001)
        assertTrue(c.size == 2)
    }

    @Test fun overdueStatus() {
        val o = ob("o", 2020, 1)
        assertEquals("Overdue", snap(listOf(o)).status(o))
        val future = ob("f", 2999, 1)
        assertEquals("Unpaid", snap(listOf(future)).status(future))
    }
}
