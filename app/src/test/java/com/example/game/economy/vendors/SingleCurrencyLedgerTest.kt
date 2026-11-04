package com.example.game.economy.vendors

import com.example.game.economy.SingleCurrencyVendingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SingleCurrencyLedgerTest {

    @Test
    fun testInitialBalance() {
        val ledger1 = SingleCurrencyLedger()
        assertEquals(0, ledger1.getBalance())

        val ledger2 = SingleCurrencyLedger(500)
        assertEquals(500, ledger2.getBalance())
    }

    @Test
    fun testRecordHit() {
        val ledger = SingleCurrencyLedger()
        val balance = ledger.recordHit()
        assertEquals(SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD, balance)
        assertEquals(SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD, ledger.getBalance())
    }

    @Test
    fun testRecordKill() {
        val ledger = SingleCurrencyLedger(100)
        val balance = ledger.recordKill(50)
        assertEquals(150, balance)
        assertEquals(150, ledger.getBalance())
    }

    @Test
    fun testRecordBoardRepair() {
        val ledger = SingleCurrencyLedger(100)
        val balance = ledger.recordBoardRepair()
        assertEquals(100 + SingleCurrencyVendingEngine.BOARD_REPAIR_CURRENCY_REWARD, balance)
        assertEquals(100 + SingleCurrencyVendingEngine.BOARD_REPAIR_CURRENCY_REWARD, ledger.getBalance())
    }

    @Test
    fun testCanAfford() {
        val ledger = SingleCurrencyLedger(250)
        assertTrue(ledger.canAfford(0))
        assertTrue(ledger.canAfford(250))
        assertFalse(ledger.canAfford(251))
    }

    @Test
    fun testAttemptPurchaseSuccess() {
        val ledger = SingleCurrencyLedger(500)
        assertTrue(ledger.attemptPurchase(300))
        assertEquals(200, ledger.getBalance())
    }

    @Test
    fun testAttemptPurchaseInsufficientFunds() {
        val ledger = SingleCurrencyLedger(500)
        assertFalse(ledger.attemptPurchase(600))
        assertEquals(500, ledger.getBalance())
    }

    @Test
    fun testConcurrentUpdates() {
        val ledger = SingleCurrencyLedger(0)
        val numThreads = 100
        val numUpdatesPerThread = 1000
        val executorService = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)

        for (i in 0 until numThreads) {
            executorService.submit {
                try {
                    for (j in 0 until numUpdatesPerThread) {
                        ledger.recordHit()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executorService.shutdown()

        val expectedBalance = numThreads * numUpdatesPerThread * SingleCurrencyVendingEngine.HIT_MARKER_CURRENCY_REWARD
        assertEquals(expectedBalance, ledger.getBalance())
    }

    @Test
    fun testConcurrentPurchases() {
        val totalBalance = 10000
        val ledger = SingleCurrencyLedger(totalBalance)
        
        val numThreads = 10
        val purchasesPerThread = 100
        val purchaseCost = 5 // Total cost = 10 * 100 * 5 = 5000
        
        val executorService = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        
        var successfulPurchases = 0
        val successLock = Any()

        for (i in 0 until numThreads) {
            executorService.submit {
                try {
                    var localSuccesses = 0
                    for (j in 0 until purchasesPerThread) {
                        if (ledger.attemptPurchase(purchaseCost)) {
                            localSuccesses++
                        }
                    }
                    synchronized(successLock) {
                        successfulPurchases += localSuccesses
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executorService.shutdown()

        assertEquals(1000, successfulPurchases) // All should succeed
        assertEquals(5000, ledger.getBalance()) // 10000 - 5000
    }
}
