package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppCategories
import com.example.data.FinanceTransaction
import com.example.data.Formatters
import com.example.data.MonthlyBudget
import com.example.data.TransactionType
import com.example.data.Wallet
import com.example.data.WalletType
import com.example.ui.theme.MonoAmountStyle
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
    existingTx: FinanceTransaction?,
    initialType: TransactionType,
    wallets: List<Wallet>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        type: TransactionType,
        amount: Long,
        category: String,
        wallet: Wallet,
        note: String,
        dateKey: String,
        timeLabel: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember(existingTx, initialType) {
        mutableStateOf(existingTx?.transactionType ?: initialType)
    }
    var amountText by remember(existingTx) {
        mutableStateOf(existingTx?.amount?.toString().orEmpty())
    }
    var selectedCategory by remember(existingTx, selectedType) {
        mutableStateOf(
            existingTx?.category
                ?: if (selectedType == TransactionType.INCOME) AppCategories.incomeCategories.first().name
                else AppCategories.expenseCategories.first().name
        )
    }
    var selectedWalletId by remember(existingTx, wallets) {
        mutableStateOf(existingTx?.walletId?.takeIf { id -> wallets.any { it.id == id } } ?: wallets.firstOrNull()?.id.orEmpty())
    }
    var note by remember(existingTx) { mutableStateOf(existingTx?.note.orEmpty()) }
    var dateKey by remember(existingTx) { mutableStateOf(existingTx?.dateKey ?: Formatters.todayDateKey()) }
    var timeLabel by remember(existingTx) { mutableStateOf(existingTx?.timeLabel ?: Formatters.currentTimeLabel()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val categories = if (selectedType == TransactionType.INCOME) {
        AppCategories.incomeCategories
    } else {
        AppCategories.expenseCategories
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTx == null) "Catat Transaksi" else "Edit Transaksi",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (existingTx != null && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("delete_transaction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Type Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                    val active = selectedType == type
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (active) {
                            if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        } else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                selectedType = type
                                val pool = if (type == TransactionType.INCOME) {
                                    AppCategories.incomeCategories
                                } else {
                                    AppCategories.expenseCategories
                                }
                                if (pool.none { it.name == selectedCategory }) {
                                    selectedCategory = pool.first().name
                                }
                            }
                            .testTag("tx_type_${type.name.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(12)
                    amountText = digits
                    validationError = null
                },
                label = { Text("Nominal (Rp)") },
                prefix = { Text("Rp ", style = MonoAmountStyle) },
                textStyle = MonoAmountStyle,
                supportingText = {
                    val parsed = amountText.toLongOrNull() ?: 0L
                    if (parsed > 0L) {
                        Text(Formatters.formatRupiah(parsed))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            // Quick amount chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(15_000L, 25_000L, 50_000L, 100_000L, 500_000L)) { preset ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                amountText = preset.toString()
                                validationError = null
                            }
                    ) {
                        Text(
                            text = Formatters.formatCompactRupiah(preset),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Wallet selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dompet", style = MaterialTheme.typography.titleSmall)
                if (wallets.isEmpty()) {
                    Text(
                        text = "Belum ada dompet. Tambahkan dompet terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(wallets, key = { it.id }) { wallet ->
                            FilterChip(
                                selected = selectedWalletId == wallet.id,
                                onClick = { selectedWalletId = wallet.id },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppCategories.walletIcon(wallet.walletType),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(wallet.name) },
                                modifier = Modifier.testTag("tx_wallet_${wallet.id}")
                            )
                        }
                    }
                }
            }

            // Category selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val active = selectedCategory == cat.name
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) cat.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) cat.color else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat.name }
                                .testTag("tx_cat_${cat.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = cat.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) cat.color else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val cal = Formatters.parseDateKey(dateKey) ?: Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    dateKey = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .testTag("tx_date_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Formatters.formatReadableDate(dateKey),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(0.7f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val parts = timeLabel.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, pickedH, pickedM ->
                                    timeLabel = String.format(Locale.US, "%02d:%02d", pickedH, pickedM)
                                },
                                h,
                                min,
                                true
                            ).show()
                        }
                        .testTag("tx_time_picker")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = timeLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Note input
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("Catatan (opsional)") },
                placeholder = { Text("Contoh: Makan siang bersama tim") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_note_input")
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    val wallet = wallets.firstOrNull { it.id == selectedWalletId }
                    when {
                        amount <= 0L -> validationError = "Masukkan nominal lebih dari Rp 0."
                        wallet == null -> validationError = "Pilih dompet terlebih dahulu."
                        else -> onSave(selectedType, amount, selectedCategory, wallet, note, dateKey, timeLabel)
                    }
                },
                enabled = !isSaving && wallets.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("tx_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (existingTx == null) "Simpan Transaksi" else "Simpan Perubahan",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletEditorSheet(
    existingWallet: Wallet?,
    canDelete: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, type: WalletType, balance: Long, colorHex: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(existingWallet) { mutableStateOf(existingWallet?.name.orEmpty()) }
    var type by remember(existingWallet) { mutableStateOf(existingWallet?.walletType ?: WalletType.CASH) }
    var balanceText by remember(existingWallet) {
        mutableStateOf(existingWallet?.balance?.toString() ?: "0")
    }
    var colorHex by remember(existingWallet) {
        mutableStateOf(existingWallet?.colorHex ?: AppCategories.walletColorSwatches.first())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingWallet == null) "Tambah Dompet" else "Edit Dompet",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (existingWallet != null && canDelete && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("delete_wallet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    validationError = null
                },
                label = { Text("Nama Dompet") },
                placeholder = { Text("Contoh: Tunai, BCA, GoPay") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_name_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jenis Dompet", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletType.entries.forEach { wt ->
                        FilterChip(
                            selected = type == wt,
                            onClick = { type = wt },
                            leadingIcon = {
                                Icon(
                                    imageVector = AppCategories.walletIcon(wt),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(wt.label) },
                            modifier = Modifier.testTag("wallet_type_${wt.name.lowercase()}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = balanceText,
                onValueChange = { raw ->
                    val filtered = buildString {
                        raw.forEachIndexed { idx, c ->
                            if (c.isDigit() || (c == '-' && idx == 0)) append(c)
                        }
                    }.take(13)
                    balanceText = filtered
                    validationError = null
                },
                label = { Text(if (existingWallet == null) "Saldo Awal (Rp)" else "Saldo Saat Ini (Rp)") },
                prefix = { Text("Rp ", style = MonoAmountStyle) },
                textStyle = MonoAmountStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_balance_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Warna Penanda", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppCategories.walletColorSwatches.forEach { hex ->
                        val swatchColor = Formatters.parseHexColor(hex)
                        val selected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val cleanName = name.trim()
                    val parsedBalance = balanceText.toLongOrNull()
                    when {
                        cleanName.isEmpty() -> validationError = "Nama dompet wajib diisi."
                        parsedBalance == null -> validationError = "Format saldo tidak valid."
                        else -> onSave(cleanName, type, parsedBalance, colorHex)
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("wallet_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (existingWallet == null) "Simpan Dompet" else "Perbarui Dompet",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorSheet(
    monthKey: String,
    existingBudget: MonthlyBudget?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (category: String, limitAmount: Long) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember(existingBudget) {
        mutableStateOf(existingBudget?.category ?: AppCategories.expenseCategories.first().name)
    }
    var limitText by remember(existingBudget) {
        mutableStateOf(existingBudget?.limitAmount?.toString().orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (existingBudget == null) "Atur Anggaran Bulanan" else "Ubah Anggaran",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = Formatters.formatMonthTitle(monthKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (existingBudget != null && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("delete_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kategori Pengeluaran", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppCategories.expenseCategories.forEach { cat ->
                        val active = selectedCategory == cat.name
                        val locked = existingBudget != null && !active
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) cat.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (locked) 0.25f else 0.6f),
                            border = BorderStroke(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) cat.color else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = existingBudget == null) { selectedCategory = cat.name }
                                .testTag("budget_cat_${cat.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = cat.color.copy(alpha = if (locked) 0.4f else 1f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) cat.color else MaterialTheme.colorScheme.onSurface.copy(alpha = if (locked) 0.4f else 1f)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = limitText,
                onValueChange = { raw ->
                    limitText = raw.filter { it.isDigit() }.take(12)
                    validationError = null
                },
                label = { Text("Batas Anggaran Bulanan (Rp)") },
                prefix = { Text("Rp ", style = MonoAmountStyle) },
                textStyle = MonoAmountStyle,
                supportingText = {
                    val parsed = limitText.toLongOrNull() ?: 0L
                    if (parsed > 0L) Text(Formatters.formatRupiah(parsed))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_limit_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(500_000L, 1_000_000L, 1_500_000L, 2_500_000L, 5_000_000L)) { preset ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                limitText = preset.toString()
                                validationError = null
                            }
                    ) {
                        Text(
                            text = Formatters.formatCompactRupiah(preset),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val parsed = limitText.toLongOrNull() ?: 0L
                    if (parsed <= 0L) {
                        validationError = "Masukkan batas anggaran lebih dari Rp 0."
                    } else {
                        onSave(selectedCategory, parsed)
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("budget_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Simpan Anggaran", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
