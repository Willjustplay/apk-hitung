package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppCategories
import com.example.data.FinanceRepository
import com.example.data.FinanceTransaction
import com.example.data.Formatters
import com.example.data.MonthlyBudget
import com.example.data.TransactionType
import com.example.data.UiState
import com.example.data.Wallet
import com.example.data.WalletType
import com.example.data.friendlyFirestoreErrorMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PeriodFilter(val label: String) {
    ALL("Semua"),
    TODAY("Hari Ini"),
    WEEK("7 Hari"),
    MONTH("Bulan Ini")
}

data class TransactionFilter(
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val walletIdFilter: String? = null,
    val categoryFilter: String? = null,
    val periodFilter: PeriodFilter = PeriodFilter.MONTH
) {
    val hasActiveFilter: Boolean
        get() = searchQuery.isNotBlank() ||
            typeFilter != null ||
            walletIdFilter != null ||
            categoryFilter != null ||
            periodFilter != PeriodFilter.MONTH
}

data class CategoryBreakdown(
    val category: String,
    val amount: Long,
    val percentage: Float,
    val transactionCount: Int
)

enum class BudgetHealth { SAFE, WARNING, OVER }

data class BudgetProgressItem(
    val budget: MonthlyBudget,
    val spentAmount: Long,
    val progressFraction: Float,
    val health: BudgetHealth
)

data class MonthlyReportData(
    val monthKey: String = Formatters.currentMonthKey(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val netSavings: Long = 0L,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
    val budgetProgress: List<BudgetProgressItem> = emptyList()
)

data class FinanceDashboardSummary(
    val totalBalance: Long = 0L,
    val todayIncome: Long = 0L,
    val todayExpense: Long = 0L,
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val recentTransactions: List<FinanceTransaction> = emptyList(),
    val topBudgetProgress: List<BudgetProgressItem> = emptyList()
)

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _filterState = MutableStateFlow(TransactionFilter())
    val filterState: StateFlow<TransactionFilter> = _filterState.asStateFlow()

    private val _selectedMonthKey = MutableStateFlow(Formatters.currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultWalletsIfEmpty(currentUserId)
        }
    }

    val walletsState: StateFlow<UiState<List<Wallet>>> = repository.observeWallets(currentUserId)
        .map<List<Wallet>, UiState<List<Wallet>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing wallets", rootError)
            emit(UiState.Error(friendlyFirestoreErrorMessage(rootError)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val transactionsState: StateFlow<UiState<List<FinanceTransaction>>> =
        repository.observeTransactions(currentUserId)
            .map<List<FinanceTransaction>, UiState<List<FinanceTransaction>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing transactions", rootError)
                emit(UiState.Error(friendlyFirestoreErrorMessage(rootError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val budgetsState: StateFlow<UiState<List<MonthlyBudget>>> =
        repository.observeBudgets(currentUserId)
            .map<List<MonthlyBudget>, UiState<List<MonthlyBudget>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing budgets", rootError)
                emit(UiState.Error(friendlyFirestoreErrorMessage(rootError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val filteredTransactions: StateFlow<List<FinanceTransaction>> = combine(
        transactionsState,
        _filterState
    ) { txUiState, filter ->
        val all = (txUiState as? UiState.Success)?.data.orEmpty()
        val today = Formatters.todayDateKey()
        val currentMonth = Formatters.currentMonthKey()
        val query = filter.searchQuery.trim()

        all.filter { tx ->
            val matchType = filter.typeFilter == null || tx.transactionType == filter.typeFilter
            val matchWallet = filter.walletIdFilter == null || tx.walletId == filter.walletIdFilter
            val matchCategory = filter.categoryFilter == null ||
                tx.category.equals(filter.categoryFilter, ignoreCase = true)
            val matchPeriod = when (filter.periodFilter) {
                PeriodFilter.ALL -> true
                PeriodFilter.TODAY -> tx.dateKey == today
                PeriodFilter.WEEK -> Formatters.isWithinLastDays(tx.dateKey, 7)
                PeriodFilter.MONTH -> tx.monthKey == currentMonth
            }
            val matchSearch = query.isEmpty() ||
                tx.note.contains(query, ignoreCase = true) ||
                tx.category.contains(query, ignoreCase = true) ||
                tx.walletName.contains(query, ignoreCase = true)

            matchType && matchWallet && matchCategory && matchPeriod && matchSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val monthlyReport: StateFlow<MonthlyReportData> = combine(
        transactionsState,
        budgetsState,
        _selectedMonthKey
    ) { txUiState, budgetUiState, monthKey ->
        val allTx = (txUiState as? UiState.Success)?.data.orEmpty()
        val allBudgets = (budgetUiState as? UiState.Success)?.data.orEmpty()
        buildMonthlyReport(monthKey, allTx, allBudgets)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MonthlyReportData()
    )

    val dashboardSummary: StateFlow<FinanceDashboardSummary> = combine(
        walletsState,
        transactionsState,
        budgetsState
    ) { walletUiState, txUiState, budgetUiState ->
        val wallets = (walletUiState as? UiState.Success)?.data.orEmpty()
        val transactions = (txUiState as? UiState.Success)?.data.orEmpty()
        val budgets = (budgetUiState as? UiState.Success)?.data.orEmpty()

        val today = Formatters.todayDateKey()
        val currentMonth = Formatters.currentMonthKey()

        val totalBalance = wallets.sumOf { it.balance }
        val todayTx = transactions.filter { it.dateKey == today }
        val monthTx = transactions.filter { it.monthKey == currentMonth }

        val todayIncome = todayTx.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amount }
        val todayExpense = todayTx.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amount }
        val monthIncome = monthTx.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amount }
        val monthExpense = monthTx.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amount }

        val currentReport = buildMonthlyReport(currentMonth, transactions, budgets)

        FinanceDashboardSummary(
            totalBalance = totalBalance,
            todayIncome = todayIncome,
            todayExpense = todayExpense,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            recentTransactions = transactions.take(5),
            topBudgetProgress = currentReport.budgetProgress
                .sortedByDescending { it.progressFraction }
                .take(2)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FinanceDashboardSummary()
    )

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun updateTypeFilter(type: TransactionType?) {
        _filterState.update { it.copy(typeFilter = type) }
    }

    fun updateWalletFilter(walletId: String?) {
        _filterState.update { it.copy(walletIdFilter = if (it.walletIdFilter == walletId) null else walletId) }
    }

    fun updateCategoryFilter(category: String?) {
        _filterState.update { it.copy(categoryFilter = category) }
    }

    fun updatePeriodFilter(period: PeriodFilter) {
        _filterState.update { it.copy(periodFilter = period) }
    }

    fun resetFilters() {
        _filterState.value = TransactionFilter()
    }

    fun shiftSelectedMonth(delta: Int) {
        _selectedMonthKey.update { Formatters.shiftMonthKey(it, delta) }
    }

    fun saveWallet(
        existingId: String?,
        name: String,
        type: WalletType,
        balance: Long,
        colorHex: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.createWallet(currentUserId, name, type, balance, colorHex)
            } else {
                repository.updateWallet(currentUserId, existingId, name, type, balance, colorHex)
            }
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit(if (existingId == null) "Dompet berhasil ditambahkan" else "Dompet diperbarui")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun deleteWallet(walletId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteWallet(currentUserId, walletId)
            _isSaving.value = false
            result.onSuccess {
                if (_filterState.value.walletIdFilter == walletId) {
                    _filterState.update { it.copy(walletIdFilter = null) }
                }
                _uiMessage.tryEmit("Dompet dihapus")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun saveTransaction(
        existingTx: FinanceTransaction?,
        type: TransactionType,
        amount: Long,
        category: String,
        wallet: Wallet,
        note: String,
        dateKey: String,
        timeLabel: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = if (existingTx == null) {
                repository.addTransaction(
                    userId = currentUserId,
                    type = type,
                    amount = amount,
                    category = category,
                    wallet = wallet,
                    note = note,
                    dateKey = dateKey,
                    timeLabel = timeLabel
                )
            } else {
                repository.updateTransaction(
                    userId = currentUserId,
                    oldTx = existingTx,
                    newType = type,
                    newAmount = amount,
                    newCategory = category,
                    newWallet = wallet,
                    newNote = note,
                    newDateKey = dateKey,
                    newTimeLabel = timeLabel
                )
            }
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit(if (existingTx == null) "Transaksi dicatat" else "Transaksi diperbarui")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun deleteTransaction(tx: FinanceTransaction, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteTransaction(currentUserId, tx)
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit("Transaksi dihapus & saldo disesuaikan")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun saveBudget(
        monthKey: String,
        category: String,
        limitAmount: Long,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.upsertBudget(currentUserId, monthKey, category, limitAmount)
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit("Anggaran $category disimpan")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    fun deleteBudget(budgetId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteBudget(currentUserId, budgetId)
            _isSaving.value = false
            result.onSuccess {
                _uiMessage.tryEmit("Anggaran dihapus")
                onComplete()
            }.onFailure { err ->
                _uiMessage.tryEmit(friendlyFirestoreErrorMessage(err))
            }
        }
    }

    private fun buildMonthlyReport(
        monthKey: String,
        allTx: List<FinanceTransaction>,
        allBudgets: List<MonthlyBudget>
    ): MonthlyReportData {
        val monthTx = allTx.filter { it.monthKey == monthKey }
        val expenses = monthTx.filter { it.transactionType == TransactionType.EXPENSE }
        val incomes = monthTx.filter { it.transactionType == TransactionType.INCOME }

        val totalExpense = expenses.sumOf { it.amount }
        val totalIncome = incomes.sumOf { it.amount }

        val expenseByCat = expenses.groupBy { it.category }
        val expenseBreakdown = expenseByCat.map { (cat, list) ->
            val sum = list.sumOf { it.amount }
            CategoryBreakdown(
                category = cat,
                amount = sum,
                percentage = if (totalExpense > 0L) sum.toFloat() / totalExpense.toFloat() else 0f,
                transactionCount = list.size
            )
        }.sortedByDescending { it.amount }

        val incomeBreakdown = incomes.groupBy { it.category }.map { (cat, list) ->
            val sum = list.sumOf { it.amount }
            CategoryBreakdown(
                category = cat,
                amount = sum,
                percentage = if (totalIncome > 0L) sum.toFloat() / totalIncome.toFloat() else 0f,
                transactionCount = list.size
            )
        }.sortedByDescending { it.amount }

        val monthBudgets = allBudgets.filter { it.monthKey == monthKey }
        val budgetProgress = monthBudgets.map { budget ->
            val spent = expenseByCat[budget.category]?.sumOf { it.amount } ?: 0L
            val fraction = if (budget.limitAmount > 0L) spent.toFloat() / budget.limitAmount.toFloat() else 0f
            val health = when {
                fraction >= 1f -> BudgetHealth.OVER
                fraction >= 0.8f -> BudgetHealth.WARNING
                else -> BudgetHealth.SAFE
            }
            BudgetProgressItem(
                budget = budget,
                spentAmount = spent,
                progressFraction = fraction,
                health = health
            )
        }.sortedByDescending { it.progressFraction }

        return MonthlyReportData(
            monthKey = monthKey,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = totalIncome - totalExpense,
            expenseBreakdown = expenseBreakdown,
            incomeBreakdown = incomeBreakdown,
            budgetProgress = budgetProgress
        )
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "FinanceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
