package com.example.data

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class WalletType(val label: String) {
    CASH("Tunai"),
    BANK("Bank"),
    EWALLET("E-Wallet");

    companion object {
        fun fromRaw(raw: String?): WalletType =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class TransactionType(val label: String) {
    EXPENSE("Pengeluaran"),
    INCOME("Pemasukan");

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class TaskPriority(val label: String, val sortOrder: Int) {
    HIGH("Tinggi", 0),
    MEDIUM("Sedang", 1),
    LOW("Rendah", 2);

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val name: String = "",
    val type: String = WalletType.CASH.name,
    val balance: Long = 0L,
    val colorHex: String = "#0F6A53",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val walletType: WalletType get() = WalletType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class FinanceTransaction(
    @DocumentId val id: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amount: Long = 0L,
    val category: String = "Makanan",
    val walletId: String = "",
    val walletName: String = "",
    val note: String = "",
    val dateKey: String = "",
    val monthKey: String = "",
    val timeLabel: String = "00:00",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val transactionType: TransactionType get() = TransactionType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class MonthlyBudget(
    @DocumentId val id: String = "",
    val monthKey: String = "",
    val category: String = "",
    val limitAmount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AgendaItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val dateKey: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val category: String = "Pribadi",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val taskPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
    val hasScheduleTime: Boolean get() = !startTime.isNullOrBlank()
}

data class CategoryMeta(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

object AppCategories {
    val expenseCategories: List<CategoryMeta> = listOf(
        CategoryMeta("Makanan", Icons.Rounded.Restaurant, Color(0xFFC85A3F)),
        CategoryMeta("Transportasi", Icons.Rounded.DirectionsCar, Color(0xFF2B6E8C)),
        CategoryMeta("Belanja", Icons.Rounded.ShoppingBag, Color(0xFFC98A2C)),
        CategoryMeta("Tagihan", Icons.Rounded.ReceiptLong, Color(0xFF6B5B95)),
        CategoryMeta("Kesehatan", Icons.Rounded.LocalHospital, Color(0xFF2E8B6E)),
        CategoryMeta("Hiburan", Icons.Rounded.Movie, Color(0xFFB55D7A)),
        CategoryMeta("Pendidikan", Icons.Rounded.School, Color(0xFF4C7C9B)),
        CategoryMeta("Rumah", Icons.Rounded.Home, Color(0xFF8C6D46)),
        CategoryMeta("Lainnya", Icons.Rounded.Category, Color(0xFF6E7A74))
    )

    val incomeCategories: List<CategoryMeta> = listOf(
        CategoryMeta("Gaji", Icons.Rounded.Payments, Color(0xFF0F6A53)),
        CategoryMeta("Usaha", Icons.Rounded.Work, Color(0xFF1D7A66)),
        CategoryMeta("Investasi", Icons.Rounded.TrendingUp, Color(0xFF2E856E)),
        CategoryMeta("Bonus", Icons.Rounded.CardGiftcard, Color(0xFFC98A2C)),
        CategoryMeta("Lainnya", Icons.Rounded.Category, Color(0xFF5A786D))
    )

    val taskCategories: List<String> = listOf(
        "Pribadi",
        "Kerja",
        "Keuangan",
        "Kesehatan",
        "Belajar",
        "Keluarga"
    )

    val walletColorSwatches: List<String> = listOf(
        "#0F6A53",
        "#1E5F74",
        "#B86B2B",
        "#6B4E71",
        "#3A6B5C",
        "#9C4A3B"
    )

    fun metaFor(name: String, type: TransactionType): CategoryMeta {
        val pool = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
        return pool.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: (expenseCategories + incomeCategories).firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: CategoryMeta(name, Icons.Rounded.Category, Color(0xFF5A6660))
    }

    fun walletIcon(type: WalletType): ImageVector = when (type) {
        WalletType.CASH -> Icons.Rounded.AccountBalanceWallet
        WalletType.BANK -> Icons.Rounded.AccountBalance
        WalletType.EWALLET -> Icons.Rounded.Smartphone
    }
}
