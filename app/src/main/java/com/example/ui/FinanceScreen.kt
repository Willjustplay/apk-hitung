package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppCategories
import com.example.data.FinanceTransaction
import com.example.data.Formatters
import com.example.data.TransactionType
import com.example.data.UiState
import com.example.data.Wallet
import com.example.ui.theme.MonoAmountStyle
import com.example.viewmodel.PeriodFilter
import com.example.viewmodel.TransactionFilter

@Composable
fun FinanceScreen(
    walletsState: UiState<List<Wallet>>,
    transactionsState: UiState<List<FinanceTransaction>>,
    filteredTransactions: List<FinanceTransaction>,
    filterState: TransactionFilter,
    onSearchQueryChange: (String) -> Unit,
    onTypeFilterChange: (TransactionType?) -> Unit,
    onWalletFilterToggle: (String) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onPeriodFilterChange: (PeriodFilter) -> Unit,
    onResetFilters: () -> Unit,
    onAddWallet: () -> Unit,
    onEditWallet: (Wallet) -> Unit,
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (FinanceTransaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.dateKey }
            .entries
            .sortedByDescending { it.key }
    }
    val filteredIncome = remember(filteredTransactions) {
        filteredTransactions.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amount }
    }
    val filteredExpense = remember(filteredTransactions) {
        filteredTransactions.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val allCategoryNames = remember {
        (AppCategories.expenseCategories + AppCategories.incomeCategories)
            .map { it.name }
            .distinct()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Keuangan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Dompet & riwayat transaksi terperinci",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddWallet)
                        .testTag("finance_add_wallet_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dompet",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Wallets Carousel (tap to filter by wallet, edit button to edit wallet)
        item {
            when (walletsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                is UiState.Error -> ErrorBanner(walletsState.message)
                is UiState.Success -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(walletsState.data, key = { it.id }) { wallet ->
                            val isSelected = filterState.walletIdFilter == wallet.id
                            val accent = Formatters.parseHexColor(wallet.colorHex)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(184.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onWalletFilterToggle(wallet.id) }
                                    .testTag("finance_wallet_filter_${wallet.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppCategories.walletIcon(wallet.walletType),
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = wallet.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = wallet.walletType.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onEditWallet(wallet) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("edit_wallet_${wallet.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = stringResource(R.string.cd_edit_wallet),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = Formatters.formatRupiah(wallet.balance),
                                        style = MonoAmountStyle.copy(fontSize = 15.sp),
                                        color = if (wallet.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Cari catatan, kategori, atau dompet...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_search_input")
            )
        }

        // Filter Chips Row (Type + Period + Category Dropdown)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterState.typeFilter == null,
                            onClick = { onTypeFilterChange(null) },
                            label = { Text("Semua") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_type_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterState.typeFilter == TransactionType.EXPENSE,
                            onClick = {
                                onTypeFilterChange(
                                    if (filterState.typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE
                                )
                            },
                            label = { Text("Pengeluaran") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.testTag("filter_type_expense")
                        )
                    }
                    item {
                        FilterChip(
                            selected = filterState.typeFilter == TransactionType.INCOME,
                            onClick = {
                                onTypeFilterChange(
                                    if (filterState.typeFilter == TransactionType.INCOME) null else TransactionType.INCOME
                                )
                            },
                            label = { Text("Pemasukan") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_type_income")
                        )
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = filterState.categoryFilter != null,
                                onClick = { categoryMenuExpanded = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(filterState.categoryFilter ?: "Kategori") },
                                modifier = Modifier.testTag("filter_category_chip")
                            )
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Kategori") },
                                    onClick = {
                                        onCategoryFilterChange(null)
                                        categoryMenuExpanded = false
                                    }
                                )
                                allCategoryNames.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            onCategoryFilterChange(cat)
                                            categoryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(PeriodFilter.entries) { period ->
                            val selected = filterState.periodFilter == period
                            Surface(
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onPeriodFilterChange(period) }
                                    .testTag("period_filter_${period.name.lowercase()}")
                            ) {
                                Text(
                                    text = period.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    if (filterState.hasActiveFilter) {
                        TextButton(
                            onClick = onResetFilters,
                            modifier = Modifier.testTag("reset_filters_button")
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Filtered Summary Bar
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Pemasukan (${filteredTransactions.count { it.transactionType == TransactionType.INCOME }})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatRupiah(filteredIncome),
                            style = MonoAmountStyle.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Pengeluaran (${filteredTransactions.count { it.transactionType == TransactionType.EXPENSE }})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatRupiah(filteredExpense),
                            style = MonoAmountStyle.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Grouped Transactions List
        when (transactionsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            is UiState.Error -> {
                item { ErrorBanner(transactionsState.message) }
            }
            is UiState.Success -> {
                if (grouped.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.ReceiptLong,
                            title = if (filterState.hasActiveFilter) "Tidak ada hasil yang cocok" else "Belum ada riwayat transaksi",
                            subtitle = if (filterState.hasActiveFilter) {
                                "Coba ubah kata kunci pencarian atau filter periode Anda."
                            } else {
                                "Catat setiap pemasukan dan pengeluaran harian untuk melihat rincian di sini."
                            },
                            actionLabel = if (filterState.hasActiveFilter) "Reset Filter" else "Catat Transaksi",
                            onAction = {
                                if (filterState.hasActiveFilter) onResetFilters()
                                else onAddTransaction(TransactionType.EXPENSE)
                            },
                            testTag = "finance_empty_state"
                        )
                    }
                } else {
                    grouped.forEach { (dateKey, dayItems) ->
                        val dayNet = dayItems.sumOf {
                            if (it.transactionType == TransactionType.INCOME) it.amount else -it.amount
                        }
                        item(key = "header_$dateKey") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Formatters.formatFullDateHeader(dateKey),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = Formatters.formatRupiah(dayNet, showSign = true),
                                    style = MonoAmountStyle.copy(fontSize = 12.sp),
                                    color = if (dayNet >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        items(dayItems, key = { it.id }) { tx ->
                            TransactionRowCard(
                                transaction = tx,
                                onClick = { onEditTransaction(tx) }
                            )
                        }
                    }
                }
            }
        }
    }
}
