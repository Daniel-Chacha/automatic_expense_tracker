package com.personal.expensetracker.viewmodel

import app.cash.turbine.test
import com.personal.expensetracker.data.local.AppDatabase
import com.personal.expensetracker.data.local.dao.AccountDao
import com.personal.expensetracker.data.local.dao.BudgetDao
import com.personal.expensetracker.data.local.dao.CategoryDao
import com.personal.expensetracker.data.local.dao.DebtDao
import com.personal.expensetracker.data.local.dao.InvestmentDao
import com.personal.expensetracker.data.local.dao.SavingsGoalDao
import com.personal.expensetracker.data.local.dao.TransactionDao
import com.personal.expensetracker.ui.viewmodel.DashboardViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeDb(
        income: Int? = 0,
        expense: Int? = 0,
        incomeFlow: kotlinx.coroutines.flow.Flow<Int?>? = null,
    ): AppDatabase {
        val txnDao = mockk<TransactionDao>(relaxed = true).apply {
            every { getTotalIncome(any(), any()) } returns (incomeFlow ?: flowOf(income))
            every { getTotalExpense(any(), any()) } returns flowOf(expense)
            every { getExpenseByCategory(any(), any()) } returns flowOf(emptyList())
            every { getRecent(any()) } returns flowOf(emptyList())
            every { getSpentByCategory(any(), any()) } returns flowOf(emptyList())
            every { getAll() } returns flowOf(emptyList())
        }
        val catDao = mockk<CategoryDao>(relaxed = true).apply {
            every { getAll() } returns flowOf(emptyList())
        }
        val budgetDao = mockk<BudgetDao>(relaxed = true).apply {
            every { getByMonth(any()) } returns flowOf(emptyList())
        }
        val savingsDao = mockk<SavingsGoalDao>(relaxed = true).apply {
            every { getActive() } returns flowOf(emptyList())
        }
        val invDao = mockk<InvestmentDao>(relaxed = true).apply {
            every { getTotalValue() } returns flowOf(0)
        }
        val debtDao = mockk<DebtDao>(relaxed = true).apply {
            every { getActive() } returns flowOf(emptyList())
        }
        val accountDao = mockk<AccountDao>(relaxed = true)
        return mockk<AppDatabase>(relaxed = true).apply {
            every { transactionDao() } returns txnDao
            every { categoryDao() } returns catDao
            every { budgetDao() } returns budgetDao
            every { savingsGoalDao() } returns savingsDao
            every { investmentDao() } returns invDao
            every { debtDao() } returns debtDao
            every { accountDao() } returns accountDao
        }
    }

    @Test
    fun totalIncome_emits_initial_value() = runTest {
        val vm = DashboardViewModel(makeDb(income = 1500))
        vm.totalIncome.test {
            assertEquals(0, awaitItem())   // initial
            assertEquals(1500, awaitItem()) // collected from upstream
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun null_income_is_treated_as_zero() = runTest {
        val vm = DashboardViewModel(makeDb(income = null))
        vm.totalIncome.test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upstream_exception_is_caught_and_emits_default() = runTest {
        val errorFlow = flow<Int?> { throw IllegalStateException("kaboom") }
        val vm = DashboardViewModel(makeDb(incomeFlow = errorFlow))

        // .stateIn(WhileSubscribed) only collects upstream when there's a
        // subscriber, so we have to be subscribed to totalIncome for the catch
        // to fire. We collect both side-by-side.
        vm.errors.test {
            vm.totalIncome.test {
                // initial seed value
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            val msg = awaitItem()
            assertTrue(msg.contains("totalIncome"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
