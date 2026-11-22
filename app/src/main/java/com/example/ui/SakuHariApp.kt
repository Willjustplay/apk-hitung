package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AgendaItem
import com.example.data.AgendaRepository
import com.example.data.FinanceRepository
import com.example.data.FinanceTransaction
import com.example.data.MonthlyBudget
import com.example.data.TransactionType
import com.example.data.UiState
import com.example.data.Wallet
import com.example.data.signOutUser
import com.example.viewmodel.AgendaViewModel
import com.example.viewmodel.FinanceViewModel
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_tab_home"),
    FINANCE(R.string.nav_finance, Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_tab_finance"),
    AGENDA(R.string.nav_agenda, Icons.Rounded.EventNote, Icons.Outlined.EventNote, "nav_tab_agenda"),
    REPORTS(R.string.nav_reports, Icons.Rounded.PieChart, Icons.Outlined.PieChart, "nav_tab_reports")
}

private sealed interface ActiveSheet {
    data object QuickAddChooser : ActiveSheet
    data class TransactionEditor(
        val existing: FinanceTransaction? = null,
        val initialType: TransactionType = TransactionType.EXPENSE
    ) : ActiveSheet
    data class WalletEditor(val existing: Wallet? = null) : ActiveSheet
    data class BudgetEditor(val existing: MonthlyBudget? = null) : ActiveSheet
    data class AgendaEditor(val existing: AgendaItem? = null) : ActiveSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakuHariApp(
    user: FirebaseUser
) {
    val uid = user.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val financeViewModel: FinanceViewModel = viewModel(
        key = "finance_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                FinanceViewModel(FinanceRepository(db), uid)
            }
        }
    )

    val agendaViewModel: AgendaViewModel = viewModel(
        key = "agenda_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                AgendaViewModel(AgendaRepository(db), uid)
            }
        }
    )

    val walletsState by financeViewModel.walletsState.collectAsStateWithLifecycle()
    val transactionsState by financeViewModel.transactionsState.collectAsStateWithLifecycle()
    val filterState by financeViewModel.filterState.collectAsStateWithLifecycle()
    val filteredTransactions by financeViewModel.filteredTransactions.collectAsStateWithLifecycle()
    val financeSummary by financeViewModel.dashboardSummary.collectAsStateWithLifecycle()
    val monthlyReport by financeViewModel.monthlyReport.collectAsStateWithLifecycle()
    val selectedMonthKey by financeViewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val financeSaving by financeViewModel.isSaving.collectAsStateWithLifecycle()

    val tasksState by agendaViewModel.tasksState.collectAsStateWithLifecycle()
    val dayAgendaView by agendaViewModel.dayAgendaView.collectAsStateWithLifecycle()
    val todayAgenda by agendaViewModel.todaySummary.collectAsStateWithLifecycle()
    val statusFilter by agendaViewModel.statusFilter.collectAsStateWithLifecycle()
    val priorityFilter by agendaViewModel.priorityFilter.collectAsStateWithLifecycle()
    val agendaSaving by agendaViewModel.isSaving.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(financeViewModel) {
        financeViewModel.uiMessage.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }
    LaunchedEffect(agendaViewModel) {
        agendaViewModel.uiMessage.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = currentTab != AppTab.HOME) {
        currentTab = AppTab.HOME
    }

    val walletsList = (walletsState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        activeSheet = when (currentTab) {
                            AppTab.HOME -> ActiveSheet.QuickAddChooser
                            AppTab.FINANCE -> ActiveSheet.TransactionEditor()
                            AppTab.AGENDA -> ActiveSheet.AgendaEditor()
                            AppTab.REPORTS -> ActiveSheet.BudgetEditor()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("main_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_quick_add)
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                        when (currentTab) {
                            AppTab.HOME -> DashboardScreen(
                                userName = user.displayName,
                                userPhotoUrl = user.photoUrl?.toString(),
                                walletsState = walletsState,
                                financeSummary = financeSummary,
                                todayAgenda = todayAgenda,
                                onOpenProfile = { showProfileDialog = true },
                                onAddTransaction = { type ->
                                    activeSheet = ActiveSheet.TransactionEditor(initialType = type)
                                },
                                onAddWallet = { activeSheet = ActiveSheet.WalletEditor() },
                                onEditWallet = { wallet ->
                                    activeSheet = ActiveSheet.WalletEditor(existing = wallet)
                                },
                                onEditTransaction = { tx ->
                                    activeSheet = ActiveSheet.TransactionEditor(existing = tx)
                                },
                                onAddAgenda = { activeSheet = ActiveSheet.AgendaEditor() },
                                onToggleAgenda = agendaViewModel::toggleCompleted,
                                onEditAgenda = { item ->
                                    activeSheet = ActiveSheet.AgendaEditor(existing = item)
                                },
                                onNavigateToFinance = { currentTab = AppTab.FINANCE },
                                onNavigateToAgenda = { currentTab = AppTab.AGENDA },
                                onNavigateToReports = { currentTab = AppTab.REPORTS }
                            )

                            AppTab.FINANCE -> FinanceScreen(
                                walletsState = walletsState,
                                transactionsState = transactionsState,
                                filteredTransactions = filteredTransactions,
                                filterState = filterState,
                                onSearchQueryChange = financeViewModel::updateSearchQuery,
                                onTypeFilterChange = financeViewModel::updateTypeFilter,
                                onWalletFilterToggle = financeViewModel::updateWalletFilter,
                                onCategoryFilterChange = financeViewModel::updateCategoryFilter,
                                onPeriodFilterChange = financeViewModel::updatePeriodFilter,
                                onResetFilters = financeViewModel::resetFilters,
                                onAddWallet = { activeSheet = ActiveSheet.WalletEditor() },
                                onEditWallet = { wallet ->
                                    activeSheet = ActiveSheet.WalletEditor(existing = wallet)
                                },
                                onAddTransaction = { type ->
                                    activeSheet = ActiveSheet.TransactionEditor(initialType = type)
                                },
                                onEditTransaction = { tx ->
                                    activeSheet = ActiveSheet.TransactionEditor(existing = tx)
                                }
                            )

                            AppTab.AGENDA -> AgendaScreen(
                                tasksState = tasksState,
                                dayView = dayAgendaView,
                                statusFilter = statusFilter,
                                priorityFilter = priorityFilter,
                                onSelectDate = agendaViewModel::selectDate,
                                onJumpToday = agendaViewModel::jumpToToday,
                                onStatusFilterChange = agendaViewModel::updateStatusFilter,
                                onPriorityFilterToggle = agendaViewModel::updatePriorityFilter,
                                onAddAgenda = { activeSheet = ActiveSheet.AgendaEditor() },
                                onToggleCompleted = agendaViewModel::toggleCompleted,
                                onEditAgenda = { item ->
                                    activeSheet = ActiveSheet.AgendaEditor(existing = item)
                                }
                            )

                            AppTab.REPORTS -> ReportsScreen(
                                report = monthlyReport,
                                onPreviousMonth = { financeViewModel.shiftSelectedMonth(-1) },
                                onNextMonth = { financeViewModel.shiftSelectedMonth(1) },
                                onAddBudget = { activeSheet = ActiveSheet.BudgetEditor() },
                                onEditBudget = { budget ->
                                    activeSheet = ActiveSheet.BudgetEditor(existing = budget)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Bottom Sheets
    when (val sheet = activeSheet) {
        ActiveSheet.QuickAddChooser -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tambah Baru", style = MaterialTheme.typography.headlineSmall)
                    QuickAddOptionCard(
                        icon = Icons.Rounded.SwapVert,
                        title = "Catat Transaksi Keuangan",
                        subtitle = "Pemasukan atau pengeluaran harian ke dompet",
                        onClick = { activeSheet = ActiveSheet.TransactionEditor() },
                        testTag = "quick_chooser_transaction"
                    )
                    QuickAddOptionCard(
                        icon = Icons.Rounded.EventNote,
                        title = "Tambah Tugas atau Jadwal",
                        subtitle = "Atur prioritas tugas & penanda waktu harian",
                        onClick = { activeSheet = ActiveSheet.AgendaEditor() },
                        testTag = "quick_chooser_agenda"
                    )
                }
            }
        }

        is ActiveSheet.TransactionEditor -> {
            TransactionEditorSheet(
                existingTx = sheet.existing,
                initialType = sheet.initialType,
                wallets = walletsList,
                isSaving = financeSaving,
                onDismiss = { activeSheet = null },
                onSave = { type, amount, category, wallet, note, dateKey, timeLabel ->
                    financeViewModel.saveTransaction(
                        existingTx = sheet.existing,
                        type = type,
                        amount = amount,
                        category = category,
                        wallet = wallet,
                        note = note,
                        dateKey = dateKey,
                        timeLabel = timeLabel,
                        onComplete = { activeSheet = null }
                    )
                },
                onDelete = sheet.existing?.let { tx ->
                    {
                        financeViewModel.deleteTransaction(tx) { activeSheet = null }
                    }
                }
            )
        }

        is ActiveSheet.WalletEditor -> {
            WalletEditorSheet(
                existingWallet = sheet.existing,
                canDelete = walletsList.size > 1,
                isSaving = financeSaving,
                onDismiss = { activeSheet = null },
                onSave = { name, type, balance, colorHex ->
                    financeViewModel.saveWallet(
                        existingId = sheet.existing?.id,
                        name = name,
                        type = type,
                        balance = balance,
                        colorHex = colorHex,
                        onComplete = { activeSheet = null }
                    )
                },
                onDelete = sheet.existing?.let { wallet ->
                    {
                        financeViewModel.deleteWallet(wallet.id) { activeSheet = null }
                    }
                }
            )
        }

        is ActiveSheet.BudgetEditor -> {
            BudgetEditorSheet(
                monthKey = selectedMonthKey,
                existingBudget = sheet.existing,
                isSaving = financeSaving,
                onDismiss = { activeSheet = null },
                onSave = { category, limitAmount ->
                    financeViewModel.saveBudget(
                        monthKey = selectedMonthKey,
                        category = category,
                        limitAmount = limitAmount,
                        onComplete = { activeSheet = null }
                    )
                },
                onDelete = sheet.existing?.let { budget ->
                    {
                        financeViewModel.deleteBudget(budget.id) { activeSheet = null }
                    }
                }
            )
        }

        is ActiveSheet.AgendaEditor -> {
            AgendaEditorSheet(
                existingItem = sheet.existing,
                defaultDateKey = dayAgendaView.selectedDateKey,
                isSaving = agendaSaving,
                onDismiss = { activeSheet = null },
                onSave = { title, notes, dateKey, startTime, endTime, priority, category ->
                    agendaViewModel.saveAgendaItem(
                        existingItem = sheet.existing,
                        title = title,
                        notes = notes,
                        dateKey = dateKey,
                        startTime = startTime,
                        endTime = endTime,
                        priority = priority,
                        category = category,
                        onComplete = { activeSheet = null }
                    )
                },
                onDelete = sheet.existing?.let { item ->
                    {
                        agendaViewModel.deleteAgendaItem(item.id) { activeSheet = null }
                    }
                }
            )
        }

        null -> Unit
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Akun SakuHari") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (user.photoUrl != null) {
                                AsyncImage(
                                    model = user.photoUrl.toString(),
                                    contentDescription = stringResource(R.string.cd_profile),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (user.displayName?.firstOrNull() ?: 'S').uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.displayName ?: "Pengguna SakuHari",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = user.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sinkronisasi cloud aktif",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        showProfileDialog = false
                        signOutUser(credentialManager, scope)
                    },
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun QuickAddOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
